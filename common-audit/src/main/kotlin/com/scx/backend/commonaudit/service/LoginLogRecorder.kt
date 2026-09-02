package com.scx.backend.commonaudit.service

import com.scx.backend.common.util.IdGenerator
import com.scx.backend.commonaudit.ClientInfo
import com.scx.backend.commonaudit.constants.LoginType
import com.scx.backend.commonaudit.entity.LoginLog
import com.scx.backend.commonaudit.repository.LoginLogRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * @description 登录日志写入服务
 *
 * 供 identity-service 在登录/登出/刷新令牌的成功与失败路径调用。
 * 两条硬约束：
 *  1. 写日志失败绝不影响业务——内部 try-catch，仅 warn；
 *  2. REQUIRES_NEW 独立事务——登录失败路径外层事务必然回滚，
 *     必须挂起外层事务另开事务提交，否则失败日志会随回滚一起消失。
 */
@Service
class LoginLogRecorder(
    private val loginLogRepository: LoginLogRepository,
) {

    private val logger = LoggerFactory.getLogger(LoginLogRecorder::class.java)

    /**
     * @description 记录一条登录日志（各字段按列宽截断，防止超长写入失败）
     * @param loginType 事件类型（登录/登出/刷新）
     * @param email 登录邮箱（登录失败且邮箱不存在时可为 null）
     * @param userId 用户 ID（未解析出用户时为 null）
     * @param clientInfo 客户端 IP 与 User-Agent
     * @param success 是否成功
     * @param failReason 失败原因（业务错误码 + 消息）
     *
     * @example loginLogRecorder.record(LoginType.EMAIL_CODE, email, user.id, clientInfo, success = true)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(
        loginType: LoginType,
        email: String?,
        userId: String?,
        clientInfo: ClientInfo?,
        success: Boolean,
        failReason: String? = null,
    ) {
        try {
            loginLogRepository.save(
                LoginLog(
                    id = IdGenerator.nextId(),
                    userId = userId?.take(26),
                    email = email?.take(100),
                    loginType = loginType.value,
                    success = success,
                    failReason = failReason?.take(255),
                    ip = clientInfo?.ip?.take(45),
                    userAgent = clientInfo?.userAgent?.take(512),
                ),
            )
        } catch (e: Exception) {
            logger.warn("登录日志写入失败（忽略，不影响业务）: type=${loginType.value} email=$email success=$success, ${e.message}")
        }
    }
}
