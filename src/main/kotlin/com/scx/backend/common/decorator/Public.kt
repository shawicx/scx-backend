package com.scx.backend.common.decorator

/**
 * 公共路由标记注解
 * 对标 scx-service: src/common/decorators/public.decorator.ts (@Public())
 *
 * 标注在 Controller 类或方法上，表示该路由不需要鉴权。
 * Step 5 的 Spring Security 过滤器将据此放行。
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Public
