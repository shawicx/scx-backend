package com.scx.backend.ai.provider

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.scx.backend.ai.modelconfig.dto.TestConnectionResultDto
import com.scx.backend.common.exception.SystemException
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** 发送给上游模型的消息（OpenAI 兼容 role/content 结构） */
data class ProviderMessage(val role: String, val content: String)

/** 已解密的可用配置（调用上游时在内存中短暂存在，禁止落日志） */
data class ResolvedAiConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val systemPrompt: String?,
    val temperature: Double?,
)

/** OpenAI 兼容 chat/completions 请求体（stream 固定开启） */
@JsonInclude(JsonInclude.Include.NON_NULL)
private data class ChatCompletionRequest(
    val model: String,
    val messages: List<ProviderMessage>,
    val stream: Boolean,
    val temperature: Double?,
)

/**
 * @description OpenAI 兼容上游客户端（JDK HttpClient 实现，零新增依赖）
 *
 * 流式对话走 POST {baseUrl}/chat/completions（stream=true，SSE 逐行解析
 * data: {...} / data: [DONE]）；连通性测试走 GET {baseUrl}/models。
 * baseUrl 末尾斜杠自动兼容；apiKey 永不写入日志。
 */
@Component
class AiProviderClient(
    private val objectMapper: ObjectMapper,
) {

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    /**
     * @description 流式对话补全：逐 delta 回调，返回完整回复文本
     * @param config 已解密配置
     * @param messages 上下文消息（system + 历史对话）
     * @param onDelta 每收到一段增量文本的回调
     * @return String 完整回复文本（所有 delta 拼接）
     *
     * @example streamChat(config, msgs) { piece -> sink.write(piece) }
     */
    fun streamChat(config: ResolvedAiConfig, messages: List<ProviderMessage>, onDelta: (String) -> Unit): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("${config.baseUrl.trimEnd('/')}/chat/completions"))
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${config.apiKey}")
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .header(HttpHeaders.ACCEPT, "text/event-stream")
            // 不设总超时：LLM 长回复耗时远超常规接口，由客户端断连兜底
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    objectMapper.writeValueAsString(
                        ChatCompletionRequest(
                            model = config.model,
                            messages = messages,
                            stream = true,
                            temperature = config.temperature,
                        ),
                    ),
                ),
            )
            .build()

        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        } catch (ex: IOException) {
            throw SystemException.aiProviderError("无法连接 AI 服务：${ex.message}")
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw SystemException.aiProviderError("请求被中断")
        }

        if (response.statusCode() !in 200..299) {
            val errorBody = response.body().use { it.readBytes().toString(Charsets.UTF_8) }
            throw SystemException.aiProviderError("AI 服务返回 HTTP ${response.statusCode()}${extractErrorMessage(errorBody)}")
        }

        val full = StringBuilder()
        response.body().bufferedReader(Charsets.UTF_8).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                if (!line.startsWith(DATA_PREFIX)) continue
                val payload = line.removePrefix(DATA_PREFIX).trim()
                if (payload.isEmpty() || payload == DONE_MARKER) break
                val node = runCatching { objectMapper.readTree(payload) }.getOrNull() ?: continue
                if (node.has(ERROR_FIELD)) {
                    throw SystemException.aiProviderError("AI 服务返回错误${extractErrorMessage(payload)}")
                }
                val delta = node.path("choices").path(0).path("delta").path("content")
                if (delta.isTextual) {
                    val piece = delta.asText()
                    full.append(piece)
                    onDelta(piece)
                }
            }
        }
        if (full.isEmpty()) {
            throw SystemException.aiProviderError("模型未返回内容")
        }
        return full.toString()
    }

    /**
     * @description 连通性测试（GET {baseUrl}/models，10 秒总超时）
     * @param baseUrl OpenAI 兼容 API 地址
     * @param apiKey API Key 明文
     * @return TestConnectionResultDto 连通结果与耗时（失败不抛异常，返回 ok=false）
     */
    fun testConnection(baseUrl: String, apiKey: String): TestConnectionResultDto {
        val startAt = System.currentTimeMillis()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("${baseUrl.trimEnd('/')}/models"))
            .header(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()
        return try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            val latency = System.currentTimeMillis() - startAt
            if (response.statusCode() in 200..299) {
                TestConnectionResultDto(ok = true, latencyMs = latency, message = "连接成功")
            } else {
                TestConnectionResultDto(
                    ok = false,
                    latencyMs = latency,
                    message = "AI 服务返回 HTTP ${response.statusCode()}${extractErrorMessage(response.body())}",
                )
            }
        } catch (ex: IOException) {
            TestConnectionResultDto(ok = false, latencyMs = System.currentTimeMillis() - startAt, message = "无法连接：${ex.message}")
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            TestConnectionResultDto(ok = false, latencyMs = System.currentTimeMillis() - startAt, message = "请求被中断")
        }
    }

    /**
     * @description 从上游错误响应体提取 error.message（提取失败返回空串）
     * @param body 上游响应体文本
     * @return String 形如 "：invalid api key"（可直接拼接在状态码后），失败为 ""
     */
    private fun extractErrorMessage(body: String): String {
        val message = runCatching {
            objectMapper.readTree(body).path(ERROR_FIELD).path("message").takeIf { it.isTextual }?.asText()
        }.getOrNull() ?: return ""
        return if (message.isNotBlank()) "：$message" else ""
    }

    companion object {
        private const val DATA_PREFIX = "data:"
        private const val DONE_MARKER = "[DONE]"
        private const val ERROR_FIELD = "error"
    }
}
