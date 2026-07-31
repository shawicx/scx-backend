package com.scx.backend.gateway

import com.fasterxml.jackson.databind.ObjectMapper
import com.scx.backend.common.constants.CacheKeys
import com.scx.backend.common.security.PublicPaths
import com.scx.backend.common.security.TokenCodec
import org.slf4j.LoggerFactory
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets

/**
 * @description 网关集中鉴权全局过滤器
 *
 * 在路由转发前对请求做集中鉴权，切断下游服务对令牌解析与 DB 回查的依赖：
 *  1. 公开路径白名单（见 [PublicPaths]）→ 直接放行
 *  2. 提取 `Authorization: Bearer <token>`，用 [TokenCodec] 验签并解析 payload
 *     （拿到 userId / email / isAdmin，零 DB 访问）
 *  3. 验证 Redis 单点令牌：`access_token:{userId}` 必须与请求令牌完全相等
 *  4. 通过 → 注入 `X-User-Id` / `X-User-Email` / `X-User-Admin` 请求头后转发；
 *     失败 → 直接返回 401 + 统一 ApiResponse 错误体（业务码 9000）
 *
 * @param tokenCodec 令牌编解码工具（来自 common，与 identity 共用协议）
 * @param redisTemplate 响应式 Redis（单点令牌校验，非阻塞）
 * @param objectMapper JSON 序列化（构造错误响应体）
 *
 * @example 请求流：客户端 → AuthGlobalFilter(验签+验Redis+注入头) → 下游服务
 */
@Component
class AuthGlobalFilter(
    private val tokenCodec: TokenCodec,
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val objectMapper: ObjectMapper,
) : GlobalFilter, Ordered {

    private val logger = LoggerFactory.getLogger(AuthGlobalFilter::class.java)
    private val pathMatcher = AntPathMatcher()

    companion object {
        /** 透传给下游的用户信息请求头 */
        const val HEADER_USER_ID = "X-User-Id"
        const val HEADER_USER_EMAIL = "X-User-Email"
        const val HEADER_USER_ADMIN = "X-User-Admin"
        /** Bearer 令牌前缀 */
        private const val BEARER_PREFIX = "Bearer "
    }

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    /**
     * @description 过滤逻辑：公开路径放行；其余验签 + 验单点令牌 + 注入用户头
     * @param exchange 服务器 web 交换上下文
     * @param chain 网关过滤链
     * @return Mono<Void> 完成信号（鉴权失败时短路返回 401）
     */
    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val path = exchange.request.path.value()

        // 1. 公开路径放行（清理可能携带的伪造 X-User-* 头，防绕过）
        if (PublicPaths.isPublic(path) { pattern -> pathMatcher.match(pattern, path) }) {
            return chain.filter(sanitizeHeaders(exchange))
        }

        // 2. 提取 Bearer 令牌
        val token = extractToken(exchange.request)
            ?: return unauthorized(exchange, "缺少访问令牌")

        // 3. 验签并解析 payload（TokenCodec 纯计算，无 IO，可在 reactive 链同步调用）
        val payload = tokenCodec.decode(token)
            ?: return unauthorized(exchange, "访问令牌无效")

        // 4. 验证 Redis 单点令牌（响应式，非阻塞）
        val cacheKey = CacheKeys.accessToken(payload.userId)
        return redisTemplate.opsForValue().get(cacheKey)
            .defaultIfEmpty("")
            .flatMap { cached ->
                if (cached != token) {
                    unauthorized(exchange, "访问令牌已失效")
                } else {
                    // 5. 通过：清理可能伪造的 X-User-* 头后注入受信任的用户信息
                    val builder = exchange.request.mutate()
                        .headers { h ->
                            h.remove(HEADER_USER_ID)
                            h.remove(HEADER_USER_EMAIL)
                            h.remove(HEADER_USER_ADMIN)
                        }
                        .header(HEADER_USER_ID, payload.userId)
                        .header(HEADER_USER_EMAIL, payload.email)
                        .header(HEADER_USER_ADMIN, payload.isAdmin.toString())
                    val mutated = builder.build()
                    chain.filter(exchange.mutate().request(mutated).build())
                }
            }
    }

    /**
     * @description 从 Authorization 头提取 Bearer 令牌
     * @return 令牌字符串，缺失或格式错误返回 null
     */
    private fun extractToken(request: ServerHttpRequest): String? {
        val header = request.headers.getFirst("Authorization") ?: return null
        if (!header.startsWith(BEARER_PREFIX, ignoreCase = true)) return null
        return header.substring(BEARER_PREFIX.length).trim().takeIf { it.isNotEmpty() }
    }

    /**
     * @description 清理请求中可能携带的 X-User-* 头，防止绕过网关伪造身份。
     * 公开路径虽不鉴权，但同样不能信任客户端传入的用户身份头。
     */
    private fun sanitizeHeaders(exchange: ServerWebExchange): ServerWebExchange =
        exchange.mutate().request { req ->
            req.headers { h -> h.remove(HEADER_USER_ID); h.remove(HEADER_USER_EMAIL); h.remove(HEADER_USER_ADMIN) }
        }.build()

    /**
     * @description 构造 401 统一错误响应并短路（业务码 9000 MISSING_TOKEN）
     */
    private fun unauthorized(exchange: ServerWebExchange, message: String): Mono<Void> {
        logger.warn("鉴权失败: {} | path={}", message, exchange.request.path.value())
        val body = mapOf(
            "success" to false,
            "statusCode" to 9000,
            "message" to message,
            "data" to null,
            "timestamp" to java.time.Instant.now().toString(),
            "path" to exchange.request.path.value(),
        )
        val json = objectMapper.writeValueAsString(body)
        val buffer = exchange.response.bufferFactory().wrap(json.toByteArray(StandardCharsets.UTF_8))
        exchange.response.statusCode = HttpStatus.UNAUTHORIZED
        exchange.response.headers.contentType = MediaType.APPLICATION_JSON
        return writeAndDisconnect(exchange, buffer)
    }

    /** 写入响应体并完成（确保 buffer 释放） */
    private fun writeAndDisconnect(exchange: ServerWebExchange, buffer: DataBuffer): Mono<Void> =
        exchange.response.writeWith(Mono.just(buffer)).then(exchange.response.setComplete())
}
