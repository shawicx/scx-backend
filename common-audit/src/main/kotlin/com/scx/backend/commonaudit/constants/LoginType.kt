package com.scx.backend.commonaudit.constants

/**
 * @description 登录日志事件类型
 *
 * 与 login_logs."loginType" 列的取值一一对应。
 */
enum class LoginType(val value: String) {

    /** 密码登录（前端 AES 加密传输） */
    PASSWORD("PASSWORD"),

    /** 邮箱验证码登录 */
    EMAIL_CODE("EMAIL_CODE"),

    /** 登出 */
    LOGOUT("LOGOUT"),

    /** 刷新令牌 */
    REFRESH("REFRESH"),
}
