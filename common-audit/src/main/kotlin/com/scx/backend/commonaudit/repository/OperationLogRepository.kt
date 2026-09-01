package com.scx.backend.commonaudit.repository

import com.scx.backend.commonaudit.entity.OperationLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

/**
 * @description 操作日志仓库
 */
interface OperationLogRepository : JpaRepository<OperationLog, String>, JpaSpecificationExecutor<OperationLog>
