package com.scx.backend.common.exception

/**
 * 系统业务异常类
 *
 * 通过伴生对象的静态工厂方法构造，保留 [data] 附加信息。
 */
class SystemException(
    val errorCode: SystemErrorCode,
    message: String = errorCode.defaultMessage,
    val data: Any? = null,
) : RuntimeException(message) {

    /** 业务错误码（数值） */
    val code: Int get() = errorCode.code

    companion object {
        fun missingToken(message: String = SystemErrorCode.MISSING_TOKEN.defaultMessage) =
            SystemException(SystemErrorCode.MISSING_TOKEN, message)

        fun invalidParameter(message: String = SystemErrorCode.INVALID_PARAMETER.defaultMessage, data: Any? = null) =
            SystemException(SystemErrorCode.INVALID_PARAMETER, message, data)

        fun dataNotFound(message: String = SystemErrorCode.DATA_NOT_FOUND.defaultMessage, data: Any? = null) =
            SystemException(SystemErrorCode.DATA_NOT_FOUND, message, data)

        fun insufficientPermission(message: String = SystemErrorCode.INSUFFICIENT_PERMISSION.defaultMessage) =
            SystemException(SystemErrorCode.INSUFFICIENT_PERMISSION, message)

        fun emailExists(message: String = SystemErrorCode.EMAIL_EXISTS.defaultMessage) =
            SystemException(SystemErrorCode.EMAIL_EXISTS, message)

        fun invalidVerificationCode(message: String = SystemErrorCode.INVALID_VERIFICATION_CODE.defaultMessage) =
            SystemException(SystemErrorCode.INVALID_VERIFICATION_CODE, message)

        fun invalidCredentials(message: String = SystemErrorCode.INVALID_CREDENTIALS.defaultMessage) =
            SystemException(SystemErrorCode.INVALID_CREDENTIALS, message)

        fun resourceExists(message: String = SystemErrorCode.RESOURCE_EXISTS.defaultMessage) =
            SystemException(SystemErrorCode.RESOURCE_EXISTS, message)

        fun operationFailed(message: String = SystemErrorCode.OPERATION_FAILED.defaultMessage, data: Any? = null) =
            SystemException(SystemErrorCode.OPERATION_FAILED, message, data)

        fun serviceUnavailable(message: String = SystemErrorCode.SERVICE_UNAVAILABLE.defaultMessage) =
            SystemException(SystemErrorCode.SERVICE_UNAVAILABLE, message)

        fun keyExpired(message: String = SystemErrorCode.KEY_EXPIRED.defaultMessage) =
            SystemException(SystemErrorCode.KEY_EXPIRED, message)

        fun decryptionFailed(message: String = SystemErrorCode.DECRYPTION_FAILED.defaultMessage) =
            SystemException(SystemErrorCode.DECRYPTION_FAILED, message)

        fun businessRuleViolation(message: String = SystemErrorCode.BUSINESS_RULE_VIOLATION.defaultMessage) =
            SystemException(SystemErrorCode.BUSINESS_RULE_VIOLATION, message)

        fun accountDisabled(message: String = SystemErrorCode.ACCOUNT_DISABLED.defaultMessage) =
            SystemException(SystemErrorCode.ACCOUNT_DISABLED, message)
    }
}
