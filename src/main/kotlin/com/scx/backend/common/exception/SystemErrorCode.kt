package com.scx.backend.common.exception

/**
 * 系统业务错误码枚举
 * 对标 scx-service: src/common/exceptions/system.exception.ts
 *
 * 注意：源项目另有 AI 错误码段 9500-9509，本项目排除 AI，故不包含。
 */
enum class SystemErrorCode(val code: Int, val defaultMessage: String) {
    /** 缺少 token */
    MISSING_TOKEN(9000, "缺少访问令牌"),

    /** 请求参数错误 */
    INVALID_PARAMETER(9001, "请求参数错误"),

    /** 数据未找到 */
    DATA_NOT_FOUND(9002, "数据未找到"),

    /** 权限不足 */
    INSUFFICIENT_PERMISSION(9003, "权限不足"),

    /** 邮箱已存在 */
    EMAIL_EXISTS(9004, "该邮箱已被注册"),

    /** 验证码无效 */
    INVALID_VERIFICATION_CODE(9005, "验证码无效或已过期"),

    /** 登录凭据无效 */
    INVALID_CREDENTIALS(9006, "用户名或密码错误"),

    /** 资源已存在 */
    RESOURCE_EXISTS(9007, "资源已存在"),

    /** 操作失败 */
    OPERATION_FAILED(9008, "操作失败"),

    /** 服务不可用 */
    SERVICE_UNAVAILABLE(9009, "服务暂时不可用"),

    /** 密钥过期 */
    KEY_EXPIRED(9010, "加密密钥已过期，请重新获取"),

    /** 解密失败 */
    DECRYPTION_FAILED(9011, "数据解密失败"),

    /** 业务规则限制 */
    BUSINESS_RULE_VIOLATION(9012, "业务规则限制"),

    /** 账户已禁用 */
    ACCOUNT_DISABLED(9013, "账户已被禁用");

    companion object {
        /**
         * 将业务错误码映射为 HTTP 状态码
         * 对标 scx-service SystemExceptionFilter.mapToHttpStatus（已去除 AI 分支）
         */
        fun mapToHttpStatus(errorCode: Int): Int = when (errorCode) {
            // 业务规则违反 / 操作失败：HTTP 200（业务层面的失败，非协议错误）
            BUSINESS_RULE_VIOLATION.code,
            OPERATION_FAILED.code -> 200

            MISSING_TOKEN.code,
            INVALID_CREDENTIALS.code,
            KEY_EXPIRED.code,
            ACCOUNT_DISABLED.code -> 401

            INVALID_PARAMETER.code,
            INVALID_VERIFICATION_CODE.code,
            DECRYPTION_FAILED.code -> 400

            DATA_NOT_FOUND.code -> 404

            INSUFFICIENT_PERMISSION.code -> 403

            EMAIL_EXISTS.code,
            RESOURCE_EXISTS.code -> 409

            SERVICE_UNAVAILABLE.code -> 503

            else -> 500
        }
    }
}
