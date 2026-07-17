package com.scx.backend.common

import com.scx.backend.common.exception.SystemErrorCode
import com.scx.backend.common.exception.SystemException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 错误码与异常体系单元测试（对标源 SystemErrorCode 全部 14 个码）
 */
class ExceptionInfrastructureTest {

    @Test
    fun `error codes match source values 9000-9013`() {
        assertEquals(9000, SystemErrorCode.MISSING_TOKEN.code)
        assertEquals(9001, SystemErrorCode.INVALID_PARAMETER.code)
        assertEquals(9002, SystemErrorCode.DATA_NOT_FOUND.code)
        assertEquals(9003, SystemErrorCode.INSUFFICIENT_PERMISSION.code)
        assertEquals(9004, SystemErrorCode.EMAIL_EXISTS.code)
        assertEquals(9005, SystemErrorCode.INVALID_VERIFICATION_CODE.code)
        assertEquals(9006, SystemErrorCode.INVALID_CREDENTIALS.code)
        assertEquals(9007, SystemErrorCode.RESOURCE_EXISTS.code)
        assertEquals(9008, SystemErrorCode.OPERATION_FAILED.code)
        assertEquals(9009, SystemErrorCode.SERVICE_UNAVAILABLE.code)
        assertEquals(9010, SystemErrorCode.KEY_EXPIRED.code)
        assertEquals(9011, SystemErrorCode.DECRYPTION_FAILED.code)
        assertEquals(9012, SystemErrorCode.BUSINESS_RULE_VIOLATION.code)
        assertEquals(9013, SystemErrorCode.ACCOUNT_DISABLED.code)
    }

    @Test
    fun `mapToHttpStatus returns correct codes`() {
        // 业务层失败映射为 200
        assertEquals(200, SystemErrorCode.mapToHttpStatus(SystemErrorCode.OPERATION_FAILED.code))
        assertEquals(200, SystemErrorCode.mapToHttpStatus(SystemErrorCode.BUSINESS_RULE_VIOLATION.code))
        // 401
        assertEquals(401, SystemErrorCode.mapToHttpStatus(SystemErrorCode.MISSING_TOKEN.code))
        assertEquals(401, SystemErrorCode.mapToHttpStatus(SystemErrorCode.INVALID_CREDENTIALS.code))
        assertEquals(401, SystemErrorCode.mapToHttpStatus(SystemErrorCode.KEY_EXPIRED.code))
        assertEquals(401, SystemErrorCode.mapToHttpStatus(SystemErrorCode.ACCOUNT_DISABLED.code))
        // 400
        assertEquals(400, SystemErrorCode.mapToHttpStatus(SystemErrorCode.INVALID_PARAMETER.code))
        assertEquals(400, SystemErrorCode.mapToHttpStatus(SystemErrorCode.INVALID_VERIFICATION_CODE.code))
        assertEquals(400, SystemErrorCode.mapToHttpStatus(SystemErrorCode.DECRYPTION_FAILED.code))
        // 其它
        assertEquals(404, SystemErrorCode.mapToHttpStatus(SystemErrorCode.DATA_NOT_FOUND.code))
        assertEquals(403, SystemErrorCode.mapToHttpStatus(SystemErrorCode.INSUFFICIENT_PERMISSION.code))
        assertEquals(409, SystemErrorCode.mapToHttpStatus(SystemErrorCode.EMAIL_EXISTS.code))
        assertEquals(409, SystemErrorCode.mapToHttpStatus(SystemErrorCode.RESOURCE_EXISTS.code))
        assertEquals(503, SystemErrorCode.mapToHttpStatus(SystemErrorCode.SERVICE_UNAVAILABLE.code))
        assertEquals(500, SystemErrorCode.mapToHttpStatus(99999))
    }

    @Test
    fun `factory methods produce correct code and default message`() {
        val ex = SystemException.emailExists()
        assertEquals(SystemErrorCode.EMAIL_EXISTS.code, ex.code)
        assertEquals("该邮箱已被注册", ex.message)

        val custom = SystemException.dataNotFound("用户不存在")
        assertEquals(9002, custom.code)
        assertEquals("用户不存在", custom.message)
    }

    @Test
    fun `exception carries optional data`() {
        val ex = SystemException.invalidParameter(data = mapOf("field" to "email"))
        assertEquals(mapOf("field" to "email"), ex.data)
    }
}
