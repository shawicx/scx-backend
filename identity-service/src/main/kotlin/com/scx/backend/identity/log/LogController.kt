package com.scx.backend.identity.log

import com.scx.backend.common.exception.SystemException
import com.scx.backend.common.security.AuthPrincipal
import com.scx.backend.identity.log.dto.LoginLogListResponseDto
import com.scx.backend.identity.log.dto.OperationLogListResponseDto
import com.scx.backend.identity.log.dto.QueryLoginLogsDto
import com.scx.backend.identity.log.dto.QueryOperationLogsDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * @description 日志管理控制器（操作日志 + 登录日志查询）
 *
 * 路由前缀 /api/logs（由 server.servlet.context-path=/api 提供）。
 * 仅管理员可访问：项目当前 @Admin 拦截器未注册（鉴权由网关承担且网关
 * 不校验 admin），故本控制器在方法内显式校验 isAdmin（X-User-Admin 头
 * 由网关注入、TokenAuthenticationFilter 重建为 AuthPrincipal）。
 */
@Tag(name = "日志管理", description = "操作日志与登录日志查询（仅管理员）")
@RestController
@RequestMapping("/logs", produces = [MediaType.APPLICATION_JSON_VALUE])
class LogController(
    private val logService: LogService,
) {

    @Operation(summary = "操作日志查询", description = "分页查询操作日志，支持模块/动作/操作人/结果/时间范围过滤与关键字搜索")
    @GetMapping("/operations")
    fun queryOperationLogs(
        dto: QueryOperationLogsDto,
        @AuthenticationPrincipal principal: AuthPrincipal?,
    ): OperationLogListResponseDto {
        requireAdmin(principal)
        return logService.queryOperationLogs(dto)
    }

    @Operation(summary = "登录日志查询", description = "分页查询登录日志（登录/登出/刷新令牌），支持登录类型/用户/结果/时间范围过滤与关键字搜索")
    @GetMapping("/logins")
    fun queryLoginLogs(
        dto: QueryLoginLogsDto,
        @AuthenticationPrincipal principal: AuthPrincipal?,
    ): LoginLogListResponseDto {
        requireAdmin(principal)
        return logService.queryLoginLogs(dto)
    }

    /**
     * @description 校验管理员身份；未认证或非管理员直接抛业务异常
     * @param principal 当前认证主体（可能为 null）
     */
    private fun requireAdmin(principal: AuthPrincipal?) {
        if (principal == null) {
            throw SystemException.missingToken("缺少认证信息（需经网关访问，或直连调试时携带 X-User-Id 请求头）")
        }
        if (!principal.isAdmin) {
            throw SystemException.insufficientPermission("仅管理员可查询日志")
        }
    }
}
