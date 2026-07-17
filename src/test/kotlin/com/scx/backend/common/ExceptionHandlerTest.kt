package com.scx.backend.common

import com.scx.backend.common.exception.GlobalExceptionHandler
import com.scx.backend.common.exception.SystemException
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus

/**
 * GlobalExceptionHandler 单元测试
 * 验证 SystemException → 业务码 + HTTP 状态的映射（对标 SystemExceptionFilter）。
 */
class ExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    private fun mockRequest(uri: String = "/api/test"): HttpServletRequest =
        mock(HttpServletRequest::class.java).apply {
            `when`(this.requestURI).thenReturn(uri)
            `when`(this.method).thenReturn("GET")
            `when`(this.getHeader("x-forwarded-for")).thenReturn(null)
            `when`(this.getHeader("x-real-ip")).thenReturn(null)
            `when`(this.remoteAddr).thenReturn("127.0.0.1")
        }

    @Test
    fun `dataNotFound maps to 404 with code 9002`() {
        val ex = SystemException.dataNotFound()
        val resp = handler.handleSystemException(ex, mockRequest())

        assertEquals(HttpStatus.NOT_FOUND, resp.statusCode)
        assertEquals(9002, resp.body!!.statusCode)
        assertEquals("数据未找到", resp.body!!.message)
        assertEquals("/api/test", resp.body!!.path)
        assertFalse(resp.body!!.success)
    }

    @Test
    fun `emailExists maps to 409 with code 9004`() {
        val ex = SystemException.emailExists()
        val resp = handler.handleSystemException(ex, mockRequest())

        assertEquals(HttpStatus.CONFLICT, resp.statusCode)
        assertEquals(9004, resp.body!!.statusCode)
    }

    @Test
    fun `invalidCredentials maps to 401 with code 9006`() {
        val ex = SystemException.invalidCredentials()
        val resp = handler.handleSystemException(ex, mockRequest())

        assertEquals(HttpStatus.UNAUTHORIZED, resp.statusCode)
        assertEquals(9006, resp.body!!.statusCode)
    }

    @Test
    fun `insufficientPermission maps to 403 with code 9003`() {
        val ex = SystemException.insufficientPermission()
        val resp = handler.handleSystemException(ex, mockRequest())

        assertEquals(HttpStatus.FORBIDDEN, resp.statusCode)
    }

    @Test
    fun `operationFailed maps to 200 with code 9008`() {
        val ex = SystemException.operationFailed()
        val resp = handler.handleSystemException(ex, mockRequest())

        assertEquals(HttpStatus.OK, resp.statusCode)
        assertEquals(9008, resp.body!!.statusCode)
    }

    @Test
    fun `generic exception maps to 500`() {
        val ex = RuntimeException("boom")
        val resp = handler.handleGenericException(ex, mockRequest())

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.statusCode)
        assertEquals(500, resp.body!!.statusCode)
        assertEquals("服务器内部错误", resp.body!!.message)
        assertNull(resp.body!!.data)
    }

    @Test
    fun `exception data is preserved in response`() {
        val ex = SystemException.invalidParameter(data = mapOf("field" to "email"))
        val resp = handler.handleSystemException(ex, mockRequest())

        assertEquals(mapOf("field" to "email"), resp.body!!.data)
    }
}
