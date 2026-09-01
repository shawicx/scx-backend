package com.scx.backend.identity.log

import com.scx.backend.commonaudit.entity.LoginLog
import com.scx.backend.commonaudit.entity.OperationLog
import com.scx.backend.commonaudit.repository.LoginLogRepository
import com.scx.backend.commonaudit.repository.OperationLogRepository
import com.scx.backend.identity.log.dto.LoginLogListResponseDto
import com.scx.backend.identity.log.dto.LoginLogResponseDto
import com.scx.backend.identity.log.dto.OperationLogListResponseDto
import com.scx.backend.identity.log.dto.OperationLogResponseDto
import com.scx.backend.identity.log.dto.QueryLoginLogsDto
import com.scx.backend.identity.log.dto.QueryOperationLogsDto
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service

/**
 * @description 日志查询服务（操作日志 + 登录日志）
 *
 * 仅供管理员查询（Controller 层校验）。动态条件拼接沿用项目现有
 * Specification + PageRequest 分页惯例；日志仅按 createdAt 排序。
 */
@Service
class LogService(
    private val operationLogRepository: OperationLogRepository,
    private val loginLogRepository: LoginLogRepository,
) {

    /**
     * @description 分页查询操作日志
     * @param dto 查询参数（search 模糊匹配模块/动作/操作人邮箱/URI）
     * @returns OperationLogListResponseDto 操作日志分页结果
     *
     * @example logService.queryOperationLogs(QueryOperationLogsDto(module = "用户管理"))
     */
    fun queryOperationLogs(dto: QueryOperationLogsDto): OperationLogListResponseDto {
        val spec = Specification<OperationLog> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()
            if (!dto.search.isNullOrBlank()) {
                val pattern = "%${dto.search.lowercase()}%"
                predicates.add(
                    cb.or(
                        cb.like(cb.lower(root.get("module")), pattern),
                        cb.like(cb.lower(root.get("action")), pattern),
                        cb.like(cb.lower(root.get("userEmail")), pattern),
                        cb.like(cb.lower(root.get("uri")), pattern),
                    ),
                )
            }
            if (!dto.module.isNullOrBlank()) predicates.add(cb.equal(root.get<String>("module"), dto.module))
            if (!dto.action.isNullOrBlank()) predicates.add(cb.equal(root.get<String>("action"), dto.action))
            if (!dto.userId.isNullOrBlank()) predicates.add(cb.equal(root.get<String>("userId"), dto.userId))
            if (dto.success != null) predicates.add(cb.equal(root.get<Boolean>("success"), dto.success))
            if (dto.startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), dto.startTime))
            }
            if (dto.endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), dto.endTime))
            }
            cb.and(*predicates.toTypedArray())
        }
        val (page, limit) = normalizePaging(dto.page, dto.limit)
        val direction = if (dto.sortOrder.equals("ASC", ignoreCase = true)) Sort.Direction.ASC else Sort.Direction.DESC
        val pageable = PageRequest.of(page - 1, limit, Sort.by(direction, "createdAt"))
        val result = operationLogRepository.findAll(spec, pageable)
        return OperationLogListResponseDto(
            list = result.content.map { OperationLogResponseDto.from(it) },
            total = result.totalElements,
            page = page,
            limit = limit,
        )
    }

    /**
     * @description 分页查询登录日志
     * @param dto 查询参数（search 模糊匹配邮箱/IP）
     * @returns LoginLogListResponseDto 登录日志分页结果
     *
     * @example logService.queryLoginLogs(QueryLoginLogsDto(loginType = "PASSWORD"))
     */
    fun queryLoginLogs(dto: QueryLoginLogsDto): LoginLogListResponseDto {
        val spec = Specification<LoginLog> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()
            if (!dto.search.isNullOrBlank()) {
                val pattern = "%${dto.search.lowercase()}%"
                predicates.add(
                    cb.or(
                        cb.like(cb.lower(root.get("email")), pattern),
                        cb.like(cb.lower(root.get("ip")), pattern),
                    ),
                )
            }
            if (!dto.loginType.isNullOrBlank()) predicates.add(cb.equal(root.get<String>("loginType"), dto.loginType))
            if (!dto.userId.isNullOrBlank()) predicates.add(cb.equal(root.get<String>("userId"), dto.userId))
            if (dto.success != null) predicates.add(cb.equal(root.get<Boolean>("success"), dto.success))
            if (dto.startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), dto.startTime))
            }
            if (dto.endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), dto.endTime))
            }
            cb.and(*predicates.toTypedArray())
        }
        val (page, limit) = normalizePaging(dto.page, dto.limit)
        val direction = if (dto.sortOrder.equals("ASC", ignoreCase = true)) Sort.Direction.ASC else Sort.Direction.DESC
        val pageable = PageRequest.of(page - 1, limit, Sort.by(direction, "createdAt"))
        val result = loginLogRepository.findAll(spec, pageable)
        return LoginLogListResponseDto(
            list = result.content.map { LoginLogResponseDto.from(it) },
            total = result.totalElements,
            page = page,
            limit = limit,
        )
    }

    /**
     * @description 收敛分页参数（页码从 1 起、每页 1-100）
     * @param page 请求页码
     * @param limit 请求每页条数
     * @returns Pair<Int, Int> 收敛后的 (页码, 每页条数)
     */
    private fun normalizePaging(page: Int, limit: Int): Pair<Int, Int> =
        Pair(page.coerceAtLeast(1), limit.coerceIn(1, 100))
}
