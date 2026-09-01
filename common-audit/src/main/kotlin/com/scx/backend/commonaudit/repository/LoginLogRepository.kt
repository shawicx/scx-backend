package com.scx.backend.commonaudit.repository

import com.scx.backend.commonaudit.entity.LoginLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

/**
 * @description 登录日志仓库
 */
interface LoginLogRepository : JpaRepository<LoginLog, String>, JpaSpecificationExecutor<LoginLog>
