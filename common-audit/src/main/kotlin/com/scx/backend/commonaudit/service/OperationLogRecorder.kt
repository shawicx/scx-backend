package com.scx.backend.commonaudit.service

import com.scx.backend.common.util.IdGenerator
import com.scx.backend.commonaudit.entity.OperationLog
import com.scx.backend.commonaudit.repository.OperationLogRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * @description 操作日志写入服务
 *
 * 供 OperationLogAspect 切面调用。与 LoginLogRecorder 相同的两条硬约束：
 *  1. 写日志失败绝不影响业务——内部 try-catch，仅 warn；
 *  2. REQUIRES_NEW 独立事务——操作失败路径外层事务必然回滚，
 *     必须另开事务提交，否则失败日志会随回滚一起消失。
 */
@Service
class OperationLogRecorder(
    private val operationLogRepository: OperationLogRepository,
) {

    private val logger = LoggerFactory.getLogger(OperationLogRecorder::class.java)

    /**
     * @description 记录一条操作日志（各字段按列宽截断，防止超长写入失败）
     * @param userId 操作人用户 ID（公开接口无操作者时为 null，如注册）
     * @param userEmail 操作人邮箱
     * @param module 操作所属模块
     * @param action 操作动作
     * @param httpMethod HTTP 请求方法
     * @param uri 请求 URI
     * @param ip 客户端 IP
     * @param userAgent 客户端 User-Agent
     * @param params 请求入参 JSON 摘要（切面已脱敏/截断）
     * @param success 是否成功
     * @param errorMessage 失败时的错误消息
     * @param costMs 耗时（毫秒）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(
        userId: String?,
        userEmail: String?,
        module: String,
        action: String,
        httpMethod: String?,
        uri: String?,
        ip: String?,
        userAgent: String?,
        params: String?,
        success: Boolean,
        errorMessage: String?,
        costMs: Long,
    ) {
        try {
            operationLogRepository.save(
                OperationLog(
                    id = IdGenerator.nextId(),
                    userId = userId?.take(26),
                    userEmail = userEmail?.take(100),
                    module = module.take(64),
                    action = action.take(64),
                    httpMethod = httpMethod?.take(8),
                    uri = uri?.take(512),
                    ip = ip?.take(45),
                    userAgent = userAgent?.take(512),
                    params = params?.take(2000),
                    success = success,
                    errorMessage = errorMessage?.take(512),
                    costMs = costMs,
                ),
            )
        } catch (e: Exception) {
            logger.warn("操作日志写入失败（忽略，不影响业务）: module=$module action=$action success=$success, ${e.message}")
        }
    }
}
