package com.scx.backend.modules.user

import com.scx.backend.common.decorator.Public
import com.scx.backend.common.util.IpUtils
import com.scx.backend.modules.user.dto.*
import com.scx.backend.security.Admin
import com.scx.backend.security.AuthPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

/**
 * 用户控制器
 *
 * 路由前缀 /api/users（由 server.servlet.context-path=/api 提供）
 */
@Tag(name = "用户管理", description = "用户注册、登录、角色分配与用户管理")
@RestController
@RequestMapping("/users", produces = [MediaType.APPLICATION_JSON_VALUE])
class UserController(
    private val userService: UserService,
) {
    // ============ 公开接口（@Public）============

    @Public
    @Operation(summary = "用户注册", description = "通过邮箱、用户名、密码及邮箱验证码完成注册")
    @PostMapping("/register")
    fun register(@Valid @RequestBody dto: RegisterUserDto, request: HttpServletRequest): UserResponseDto =
        userService.register(dto, IpUtils.getClientIp(request))

    @Public
    @Operation(summary = "邮箱验证码登录", description = "使用邮箱与 6 位验证码登录，返回访问与刷新令牌")
    @PostMapping("/login")
    fun login(@Valid @RequestBody dto: LoginUserDto, request: HttpServletRequest): LoginResponseDto =
        userService.loginWithEmailCode(dto, IpUtils.getClientIp(request))

    @Public
    @Operation(summary = "密码登录", description = "使用邮箱与前端加密后的密码登录，需先获取加密密钥")
    @PostMapping("/login-password")
    fun loginWithPassword(
        @Valid @RequestBody dto: LoginWithPasswordDto,
        request: HttpServletRequest
    ): LoginResponseDto =
        userService.loginWithPassword(dto, IpUtils.getClientIp(request))

    @Public
    @Operation(summary = "获取密码加密密钥", description = "返回一次性 AES 密钥及其 ID，用于前端加密登录密码，5 分钟失效")
    @GetMapping("/encryption-key")
    fun getEncryptionKey() = userService.getEncryptionKey()

    @Public
    @Operation(summary = "发送登录验证码", description = "向指定邮箱发送登录用的 6 位验证码")
    @PostMapping("/send-login-code")
    fun sendLoginCode(@Valid @RequestBody dto: SendCodeDto): MessageDto {
        userService.sendLoginVerificationCode(dto.email)
        return MessageDto("验证码已发送到您的邮箱")
    }

    @Public
    @Operation(summary = "发送邮箱验证码", description = "向指定邮箱发送通用验证码（注册等场景使用）")
    @PostMapping("/send-email-code")
    fun sendEmailCode(@Valid @RequestBody dto: SendCodeDto): MessageDto {
        userService.sendEmailVerificationCode(dto.email)
        return MessageDto("验证码已发送到您的邮箱")
    }

    // ============ 认证接口 ============

    @Operation(summary = "登出", description = "清除当前用户的访问与刷新令牌")
    @PostMapping("/logout")
    fun logout(@Parameter(description = "用户 ID") @RequestParam userId: String): MessageDto {
        userService.logout(userId)
        return MessageDto("登出成功")
    }

    @Operation(summary = "刷新令牌", description = "使用刷新令牌换取新的访问与刷新令牌")
    @PostMapping("/refresh-token")
    fun refreshToken(@RequestBody dto: RefreshTokenDto): Any {
        val tokens = userService.refreshTokens(dto.refreshToken)
            ?: throw com.scx.backend.common.exception.SystemException.invalidParameter("刷新令牌无效或已过期")
        return tokens
    }

    // ============ 角色管理 ============

    @Operation(summary = "分配单个角色", description = "为指定用户分配单个角色")
    @PostMapping("/assign-role")
    fun assignRole(@Valid @RequestBody dto: AssignRoleDto): UserRoleResponseDto =
        userService.assignRole(dto.userId, dto)

    @Operation(summary = "批量分配角色", description = "为指定用户批量分配多个角色")
    @PostMapping("/assign-roles-batch")
    fun assignRoles(@Valid @RequestBody dto: AssignRolesDto): List<UserRoleResponseDto> =
        userService.assignRoles(dto.userId, dto)

    @Operation(summary = "移除角色", description = "移除指定用户的某个角色")
    @DeleteMapping("/remove-role")
    fun removeRole(
        @Parameter(description = "用户 ID") @RequestParam("id") userId: String,
        @Parameter(description = "角色 ID") @RequestParam roleId: String,
    ): MessageDto {
        userService.removeRole(userId, roleId)
        return MessageDto("角色移除成功")
    }

    @Operation(summary = "查询用户角色", description = "返回指定用户拥有的所有角色")
    @GetMapping("/roles")
    fun getUserRoles(@Parameter(description = "用户 ID") @RequestParam("id") userId: String) =
        userService.getUserRoles(userId)

    @Operation(summary = "查询用户权限", description = "返回指定用户通过角色继承的所有权限")
    @GetMapping("/permissions")
    fun getUserPermissions(@Parameter(description = "用户 ID") @RequestParam("id") userId: String) =
        userService.getUserPermissions(userId)

    @Operation(summary = "检查用户角色", description = "判断用户是否拥有指定角色编码")
    @GetMapping("/check-role")
    fun checkUserRole(
        @Parameter(description = "用户 ID") @RequestParam("id") userId: String,
        @Parameter(description = "角色编码") @RequestParam roleCode: String,
    ) = mapOf("hasRole" to userService.hasRole(userId, roleCode))

    @Operation(summary = "检查用户权限", description = "判断用户是否对指定资源拥有指定操作权限")
    @GetMapping("/check-permission")
    fun checkUserPermission(
        @Parameter(description = "用户 ID") @RequestParam("id") userId: String,
        @Parameter(description = "操作动作") @RequestParam action: String,
        @Parameter(description = "资源名称") @RequestParam resource: String,
    ) = mapOf("hasPermission" to userService.hasPermission(userId, action, resource))

    // ============ 管理员接口（@Admin）============

    @Admin
    @Operation(summary = "用户列表查询", description = "分页查询用户，支持搜索、状态过滤与排序")
    @GetMapping("/list")
    fun queryUsers(dto: QueryUsersDto): UserListResponseDto = userService.queryUsers(dto)

    @Admin
    @Operation(summary = "创建用户", description = "管理员创建用户，可同时分配角色")
    @PostMapping("/create")
    fun createUser(@Valid @RequestBody dto: CreateUserDto): UserResponseDto = userService.createUser(dto)

    @Admin
    @Operation(summary = "批量删除用户", description = "逻辑删除多个用户，返回受影响行数")
    @DeleteMapping("/batch-delete")
    fun deleteUsers(
        @Valid @RequestBody dto: DeleteUsersDto,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): CountResultDto {
        val count = userService.deleteUsers(principal.userId, dto)
        return CountResultDto(count, "删除成功")
    }

    @Admin
    @Operation(summary = "切换用户状态", description = "批量启用或停用用户账号，返回受影响行数")
    @PatchMapping("/toggle-status")
    fun toggleUserStatus(
        @Valid @RequestBody dto: ToggleUserStatusDto,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): CountResultDto {
        val count = userService.toggleUserStatus(principal.userId, dto)
        return CountResultDto(count, "状态更新成功")
    }
}
