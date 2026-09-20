package com.scx.backend.identity.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.scx.backend.common.security.DataScope
import com.scx.backend.common.security.TokenCodec
import com.scx.backend.identity.cache.CacheService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock

/**
 * @description 认证服务数据权限单元测试
 *
 * 纯 Mockito 单元测试：验证 dataScope 嵌入令牌后可被网关侧 TokenCodec
 * 解出（identity 签发 ↔ 网关验签两端协议一致）、旧令牌缺失字段时默认
 * SELF（向后兼容）、刷新令牌时按回调重算 dataScope。
 */
class AuthServiceTest {

    private val cacheService = mock(CacheService::class.java)
    private val objectMapper = ObjectMapper()
    private val secret = "unit-test-secret"
    private val authService = AuthService(cacheService, objectMapper, secret)
    private val gatewayCodec = TokenCodec(objectMapper, secret)

    @Test
    fun `generateAccessToken embeds dataScope decodable by gateway codec`() {
        val token = authService.generateAccessToken("u1", "a@b.com", isAdmin = true, dataScope = DataScope.ALL)

        val payload = gatewayCodec.decode(token)
        assertNotNull(payload, "令牌应可被网关侧编解码器验签解析")
        assertEquals(DataScope.ALL, payload?.dataScope, "dataScope 应嵌入令牌且网关可解")
        assertTrue(payload?.isAdmin == true)
    }

    @Test
    fun `legacy token without dataScope decodes as SELF`() {
        val legacy = gatewayCodec.encode(
            linkedMapOf<String, Any>(
                "userId" to "u1",
                "email" to "a@b.com",
                "type" to "access",
                "timestamp" to 1L,
                "isAdmin" to true,
            ),
        )

        assertEquals(DataScope.SELF, gatewayCodec.decode(legacy)?.dataScope, "旧令牌缺失 dataScope 应默认 SELF")
    }

    @Test
    fun `validateAccessToken returns embedded dataScope`() {
        val token = authService.generateAccessToken("u1", "a@b.com", isAdmin = false, dataScope = DataScope.SELF)
        given(cacheService.get<String>(anyString())).willReturn(token)

        val payload = authService.validateAccessToken(token)

        assertEquals(DataScope.SELF, payload?.dataScope)
    }

    @Test
    fun `refreshTokens recomputes dataScope via provider`() {
        val refresh = authService.generateRefreshToken("u1", "a@b.com", isAdmin = false, dataScope = DataScope.SELF)
        given(cacheService.get<String>(anyString())).willReturn(refresh)

        val pair = authService.refreshTokens(
            refreshToken = refresh,
            isAdminProvider = { true },
            dataScopeProvider = { DataScope.ALL },
        )

        assertNotNull(pair)
        assertEquals(DataScope.ALL, gatewayCodec.decode(pair!!.accessToken)?.dataScope, "刷新后应按回调重算 dataScope")
    }

    /**
     * @description 任意字符串匹配器（Kotlin 非空参数需空安全兜底，避免 any() 返回 null 触发空检查）
     * @returns String 匹配占位值
     */
    private fun anyString(): String = any<String>() ?: ""
}
