package com.scx.backend.commonaudit.annotation

/**
 * @description 操作日志注解
 *
 * 标注在 Controller 的写操作方法上，由 common-audit 的 OperationLogAspect
 * 切面拦截并落库到 operation_logs 表（操作人、模块动作、请求上下文、
 * 入参摘要、结果与耗时）。
 *
 * @param module 操作所属模块（如：用户管理、角色管理、文件管理）
 * @param action 操作动作（如：创建用户、批量删除）
 *
 * @example @OperationLog(module = "用户管理", action = "创建用户")
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class OperationLog(
    val module: String,
    val action: String,
)
