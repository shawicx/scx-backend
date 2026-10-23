package com.scx.backend.ai.chat

import com.fasterxml.jackson.databind.ObjectMapper
import com.scx.backend.ai.chat.dto.DeltaEventDto
import com.scx.backend.ai.chat.dto.DoneEventDto
import com.scx.backend.ai.chat.dto.ErrorEventDto
import com.scx.backend.ai.chat.dto.MessageListResponseDto
import com.scx.backend.ai.chat.dto.MessageResponseDto
import com.scx.backend.ai.chat.dto.SessionEventDto
import com.scx.backend.ai.entity.AiMessage
import com.scx.backend.ai.entity.AiSession
import com.scx.backend.ai.modelconfig.ModelConfigService
import com.scx.backend.ai.provider.AiProviderClient
import com.scx.backend.ai.provider.ProviderMessage
import com.scx.backend.ai.repository.AiMessageRepository
import com.scx.backend.ai.repository.AiSessionRepository
import com.scx.backend.ai.session.SessionService
import com.scx.backend.common.exception.SystemException
import com.scx.backend.common.util.IdGenerator
import jakarta.servlet.ServletOutputStream
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime

/**
 * @description AI 对话服务（多会话：按会话加载历史 + 流式补全）
 *
 * chat() 为长连接流式方法，刻意不加 @Transactional（避免数据库事务
 * 横跨整个生成过程）；每次 repository.save 各自独立提交。
 * SSE 响应直写 HttpServletResponse（void 返回不经过 GlobalResponseHandler），
 * 事件顺序：session（含会话 ID/标题，供前端尽早固定会话）→ delta* → done | error。
 */
@Service
class ChatService(
    private val aiMessageRepository: AiMessageRepository,
    private val aiSessionRepository: AiSessionRepository,
    private val sessionService: SessionService,
    private val modelConfigService: ModelConfigService,
    private val aiProviderClient: AiProviderClient,
    private val objectMapper: ObjectMapper,
) {

    /**
     * @description 查询会话历史消息（最近 200 条，时间正序；归属校验失败抛 9002）
     * @param userId 用户 ID
     * @param sessionId 会话 ID
     * @return MessageListResponseDto 消息列表
     */
    fun getMessages(userId: String, sessionId: String): MessageListResponseDto {
        val session = sessionService.findOwned(userId, sessionId)
        return MessageListResponseDto(
            aiMessageRepository.findTop200BySessionIdOrderByCreatedAtDesc(session.id)
                .reversed()
                .map { MessageResponseDto.from(it) },
        )
    }

    /**
     * @description 流式对话：确定/新建会话 → 保存用户消息 → 组装上下文 → 调上游 → SSE 转发 → 落库助手回复
     * @param userId 用户 ID
     * @param sessionId 会话 ID（null 时新建，标题取消息前 30 字符）
     * @param content 用户消息内容
     * @param response HTTP 响应（直写 text/event-stream）
     */
    fun chat(userId: String, sessionId: String?, content: String, response: HttpServletResponse) {
        // 未配置模型在写任何 SSE 字节前抛出（GlobalExceptionHandler 返回 JSON 9500，前端引导去个人中心）
        val config = modelConfigService.resolveDecrypted(userId)

        val now = LocalDateTime.now()
        val session = sessionId?.let { sessionService.findOwned(userId, it) }
            ?: aiSessionRepository.save(
                AiSession(
                    id = IdGenerator.nextId(),
                    userId = userId,
                    title = deriveTitle(content),
                    createdAt = now,
                    updatedAt = now,
                ),
            )

        val userMessage = aiMessageRepository.save(
            AiMessage(
                id = IdGenerator.nextId(),
                userId = userId,
                sessionId = session.id,
                role = ROLE_USER,
                content = content,
            ),
        )
        touchSession(session)

        val context = aiMessageRepository.findTop20BySessionIdOrderByCreatedAtDesc(session.id).reversed()
        val providerMessages = buildList {
            config.systemPrompt?.takeIf { it.isNotBlank() }?.let { add(ProviderMessage(ROLE_SYSTEM, it)) }
            context.forEach { add(ProviderMessage(it.role, it.content)) }
        }

        response.contentType = MediaType.TEXT_EVENT_STREAM_VALUE
        response.characterEncoding = StandardCharsets.UTF_8.name()
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache")
        val out = response.outputStream

        val buffer = StringBuilder()
        try {
            // 尽早告知会话信息：即使后续中断，前端也已持有 sessionId，可与后端状态对齐
            writeEvent(out, EVENT_SESSION, SessionEventDto(session.id, session.title))

            aiProviderClient.streamChat(config, providerMessages) { piece ->
                buffer.append(piece)
                writeEvent(out, EVENT_DELTA, DeltaEventDto(piece))
            }
            val assistantMessage = aiMessageRepository.save(
                AiMessage(
                    id = IdGenerator.nextId(),
                    userId = userId,
                    sessionId = session.id,
                    role = ROLE_ASSISTANT,
                    content = buffer.toString(),
                ),
            )
            writeEvent(out, EVENT_DONE, DoneEventDto(assistantMessage.id, session.id))
            touchSession(session)
        } catch (ex: Exception) {
            logger.error("AI 对话失败（userId={}, sessionId={}, userMessageId={}）：{}", userId, session.id, userMessage.id, ex.message, ex)
            // 客户端断连或上游失败：已生成部分照常落库，避免会话上下文出现无回复的悬挂 user 消息
            if (buffer.isNotEmpty()) {
                runCatching {
                    aiMessageRepository.save(
                        AiMessage(
                            id = IdGenerator.nextId(),
                            userId = userId,
                            sessionId = session.id,
                            role = ROLE_ASSISTANT,
                            content = buffer.toString(),
                        ),
                    )
                }
                runCatching { touchSession(session) }
            }
            // 响应可能已提交（写失败时此处静默），未提交则补一个 error 事件供前端提示
            runCatching { writeEvent(out, EVENT_ERROR, ErrorEventDto(friendlyMessage(ex))) }
        } finally {
            runCatching { out.flush() }
        }
    }

    /**
     * @description 刷新会话最后活跃时间（会话列表按 updatedAt 排序）
     * @param session 会话实体
     */
    private fun touchSession(session: AiSession) {
        session.updatedAt = LocalDateTime.now()
        aiSessionRepository.save(session)
    }

    /**
     * @description 写出一个 SSE 事件并立即 flush（推给网关/浏览器）
     * @param out 响应输出流
     * @param event 事件名（session/delta/done/error）
     * @param payload 事件负载（序列化为 JSON）
     */
    private fun writeEvent(out: ServletOutputStream, event: String, payload: Any) {
        val json = objectMapper.writeValueAsString(payload)
        out.write("event: $event\ndata: $json\n\n".toByteArray(StandardCharsets.UTF_8))
        out.flush()
    }

    /**
     * @description 异常转对外提示文案（上游 4xx/5xx 细节对用户可见，密钥信息永不包含）
     * @param ex 异常
     * @return String 提示文案
     */
    private fun friendlyMessage(ex: Exception): String =
        (ex as? SystemException)?.message ?: DEFAULT_ERROR_MESSAGE

    companion object {
        private val logger = LoggerFactory.getLogger(ChatService::class.java)
        private const val DEFAULT_ERROR_MESSAGE = "AI 服务调用失败"
        private const val ROLE_USER = "user"
        private const val ROLE_ASSISTANT = "assistant"
        private const val ROLE_SYSTEM = "system"
        private const val EVENT_SESSION = "session"
        private const val EVENT_DELTA = "delta"
        private const val EVENT_DONE = "done"
        private const val EVENT_ERROR = "error"
        private const val TITLE_MAX_LENGTH = 30
        private const val FALLBACK_TITLE = "新对话"

        /**
         * @description 由首条用户消息派生会话标题（压缩空白并截断）
         * @param content 用户消息内容
         * @return String 标题（最长 30 字符，空白回退"新对话"）
         */
        fun deriveTitle(content: String): String {
            val cleaned = content.trim().replace(Regex("\\s+"), " ")
            return if (cleaned.isEmpty()) FALLBACK_TITLE else cleaned.take(TITLE_MAX_LENGTH)
        }
    }
}
