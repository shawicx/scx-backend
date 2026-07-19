package com.scx.backend.common.decorator

/**
 * 公共路由标记注解
 *
 * 标注在 Controller 类或方法上，表示该路由不需要鉴权。
 * 由 Spring Security 过滤器与 AuthInterceptor 据此放行。
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Public
