package com.scx.backend.security

/**
 * 管理员权限标记注解
 *
 * 标注在 Controller 方法或类上，表示该路由需要管理员权限。
 * 由 [AdminInterceptor] 检测并校验。
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Admin
