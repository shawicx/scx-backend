package com.scx.backend.modules.user

import com.scx.backend.common.decorator.Public
import com.scx.backend.common.util.IpUtils
import com.scx.backend.modules.user.dto.AssignRoleDto
import com.scx.backend.modules.user.dto.AssignRolesDto
import com.scx.backend.modules.user.dto.CountResultDto
import com.scx.backend.modules.user.dto.CreateUserDto
import com.scx.backend.modules.user.dto.DeleteUsersDto
import com.scx.backend.modules.user.dto.LoginResponseDto
import com.scx.backend.modules.user.dto.LoginUserDto
import com.scx.backend.modules.user.dto.LoginWithPasswordDto
import com.scx.backend.modules.user.dto.MessageDto
import com.scx.backend.modules.user.dto.QueryUsersDto
import com.scx.backend.modules.user.dto.RefreshTokenDto
import com.scx.backend.modules.user.dto.RegisterUserDto
import com.scx.backend.modules.user.dto.SendCodeDto
import com.scx.backend.modules.user.dto.ToggleUserStatusDto
import com.scx.backend.modules.user.dto.UserListResponseDto
import com.scx.backend.modules.user.dto.UserResponseDto
import com.scx.backend.modules.user.dto.UserRoleResponseDto
import com.scx.backend.security.Admin
import com.scx.backend.security.AuthPrincipal
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 用户控制器
 * 对标 scx-service: src/modules/user/user.controller.ts
 *
 * 路由前缀 /api/users（由 server.servlet.context-path=/api 提供）
 */
@RestController
@RequestMapping("/users")
class UserController(
    private val userService: UserService,
) {
    // ============ 公开接口（@Public）============

    @Public
    @PostMapping("/register")
    fun register(@Valid @RequestBody dto: RegisterUserDto, request: HttpServletRequest): UserResponseDto =
        userService.register(dto, IpUtils.getClientIp(request))

    @Public
    @PostMapping("/login")
    fun login(@Valid @RequestBody dto: LoginUserDto, request: HttpServletRequest): LoginResponseDto =
        userService.loginWithEmailCode(dto, IpUtils.getClientIp(request))

    @Public
    @PostMapping("/login-password")
    fun loginWithPassword(@Valid @RequestBody dto: LoginWithPasswordDto, request: HttpServletRequest): LoginResponseDto =
        userService.loginWithPassword(dto, IpUtils.getClientIp(request))

    @Public
    @GetMapping("/encryption-key")
    fun getEncryptionKey() = userService.getEncryptionKey()

    @Public
    @PostMapping("/send-login-code")
    fun sendLoginCode(@Valid @RequestBody dto: SendCodeDto): MessageDto {
        userService.sendLoginVerificationCode(dto.email)
        return MessageDto("验证码已发送到您的邮箱")
    }

    @Public
    @PostMapping("/send-email-code")
    fun sendEmailCode(@Valid @RequestBody dto: SendCodeDto): MessageDto {
        userService.sendEmailVerificationCode(dto.email)
        return MessageDto("验证码已发送到您的邮箱")
    }

    // ============ 认证接口 ============

    @PostMapping("/logout")
    fun logout(@RequestParam userId: String): MessageDto {
        userService.logout(userId)
        return MessageDto("登出成功")
    }

    @PostMapping("/refresh-token")
    fun refreshToken(@RequestBody dto: RefreshTokenDto): Any {
        val tokens = userService.refreshTokens(dto.refreshToken)
            ?: throw com.scx.backend.common.exception.SystemException.invalidParameter("刷新令牌无效或已过期")
        return tokens
    }

    // ============ 角色管理 ============

    @PostMapping("/assign-role")
    fun assignRole(@Valid @RequestBody dto: AssignRoleDto): UserRoleResponseDto =
        userService.assignRole(dto.userId, dto)

    @PostMapping("/assign-roles-batch")
    fun assignRoles(@Valid @RequestBody dto: AssignRolesDto): List<UserRoleResponseDto> =
        userService.assignRoles(dto.userId, dto)

    @DeleteMapping("/remove-role")
    fun removeRole(@RequestParam("id") userId: String, @RequestParam roleId: String): MessageDto {
        userService.removeRole(userId, roleId)
        return MessageDto("角色移除成功")
    }

    @GetMapping("/roles")
    fun getUserRoles(@RequestParam("id") userId: String) = userService.getUserRoles(userId)

    @GetMapping("/permissions")
    fun getUserPermissions(@RequestParam("id") userId: String) = userService.getUserPermissions(userId)

    @GetMapping("/check-role")
    fun checkUserRole(@RequestParam("id") userId: String, @RequestParam roleCode: String) =
        mapOf("hasRole" to userService.hasRole(userId, roleCode))

    @GetMapping("/check-permission")
    fun checkUserPermission(
        @RequestParam("id") userId: String,
        @RequestParam action: String,
        @RequestParam resource: String,
    ) = mapOf("hasPermission" to userService.hasPermission(userId, action, resource))

    // ============ 管理员接口（@Admin）============

    @Admin
    @GetMapping("/list")
    fun queryUsers(dto: QueryUsersDto): UserListResponseDto = userService.queryUsers(dto)

    @Admin
    @PostMapping("/create")
    fun createUser(@Valid @RequestBody dto: CreateUserDto): UserResponseDto = userService.createUser(dto)

    @Admin
    @DeleteMapping("/batch-delete")
    fun deleteUsers(
        @Valid @RequestBody dto: DeleteUsersDto,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): CountResultDto {
        val count = userService.deleteUsers(principal.userId, dto)
        return CountResultDto(count, "删除成功")
    }

    @Admin
    @PatchMapping("/toggle-status")
    fun toggleUserStatus(
        @Valid @RequestBody dto: ToggleUserStatusDto,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): CountResultDto {
        val count = userService.toggleUserStatus(principal.userId, dto)
        return CountResultDto(count, "状态更新成功")
    }
}
