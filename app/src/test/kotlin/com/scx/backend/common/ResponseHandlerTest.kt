package com.scx.backend.common

import com.scx.backend.common.response.ApiResponse
import com.scx.backend.common.response.GlobalResponseHandler
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.http.server.ServletServerHttpResponse

/**
 * GlobalResponseHandler 单元测试
 *
 * 验证成功响应包装逻辑。不依赖 Spring 上下文，直接调用 beforeBodyWrite。
 */
class ResponseHandlerTest {

    private lateinit var handler: GlobalResponseHandler
    private lateinit var request: ServletServerHttpRequest
    private lateinit var response: ServletServerHttpResponse

    @BeforeEach
    fun setUp() {
        handler = GlobalResponseHandler()

        val servletRequest = mock(HttpServletRequest::class.java)
        `when`(servletRequest.requestURI).thenReturn("/api/test")

        val servletResponse = mock(HttpServletResponse::class.java)
        `when`(servletResponse.status).thenReturn(200)

        request = mock(ServletServerHttpRequest::class.java)
        `when`(request.servletRequest).thenReturn(servletRequest)

        response = mock(ServletServerHttpResponse::class.java)
        `when`(response.servletResponse).thenReturn(servletResponse)
    }

    private val returnType = mock(MethodParameter::class.java)
    private val contentType = MediaType.APPLICATION_JSON
    private val converterType = JacksonJsonHttpMessageConverter::class.java

    @Test
    fun `plain object is wrapped with success message and data`() {
        val body = mapOf("name" to "scx")
        val result = handler.beforeBodyWrite(body, returnType, contentType, converterType, request, response) as ApiResponse

        assertTrue(result.success)
        assertEquals(200, result.statusCode)
        assertEquals("请求成功", result.message)
        assertEquals(body, result.data)
        assertEquals("/api/test", result.path)
    }

    @Test
    fun `object with message field extracts message`() {
        val body = mapOf("message" to "自定义消息", "id" to 1)
        val result = handler.beforeBodyWrite(body, returnType, contentType, converterType, request, response) as ApiResponse

        assertEquals("自定义消息", result.message)
        assertEquals(mapOf("id" to 1), result.data)
    }

    @Test
    fun `object with only message field yields null data`() {
        val body = mapOf("message" to "纯消息")
        val result = handler.beforeBodyWrite(body, returnType, contentType, converterType, request, response) as ApiResponse

        assertEquals("纯消息", result.message)
        assertNull(result.data)
    }

    @Test
    fun `existing ApiResponse is returned as-is without re-wrapping`() {
        val existing = ApiResponse.error(9002, "未找到", null, "/api/x")
        val result = handler.beforeBodyWrite(existing, returnType, contentType, converterType, request, response)

        assertEquals(existing, result)
        assertFalse(existing.success)
    }

    @Test
    fun `null body is wrapped with success and null data`() {
        val result = handler.beforeBodyWrite(null, returnType, contentType, converterType, request, response) as ApiResponse

        assertTrue(result.success)
        assertNull(result.data)
    }
}
