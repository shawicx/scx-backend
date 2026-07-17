package com.scx.backend.modules.user

import com.fasterxml.jackson.databind.ObjectMapper
import com.scx.backend.common.constants.CacheKeys
import com.scx.backend.common.util.CryptoUtil
import com.scx.backend.entity.Role
import com.scx.backend.entity.UserRole
import com.scx.backend.modules.auth.AuthService
import com.scx.backend.modules.cache.CacheService
import com.scx.backend.repository.RoleRepository
import com.scx.backend.repository.UserRepository
import com.scx.backend.repository.UserRoleRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.TestPropertySource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * 用户模块端到端集成测试（依赖真实 PG + Redis）
 *
 * 覆盖核心流程：验证码登录、注册、鉴权访问、RBAC 查询、管理员接口。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = [
        // 用真实 PG（Flyway 建表），仅调整日志
        "spring.jpa.show-sql=false",
        "jwt.secret=user-flow-test-secret",
    ],
)
class UserFlowIntegrationTest(
    @Autowired private val userRepository: UserRepository,
    @Autowired private val roleRepository: RoleRepository,
    @Autowired private val userRoleRepository: UserRoleRepository,
    @Autowired private val cacheService: CacheService,
    @Autowired private val authService: AuthService,
    @Autowired private val objectMapper: ObjectMapper,
    @LocalServerPort private val port: Int,
) {
    private val http = HttpClient.newHttpClient()
    private val testEmail = "flow-test-$(System.currentTimeMillis())@scx.dev"
    private val superAdminRoleId: String by lazy { createSuperAdminRole() }

    @BeforeEach
    fun setUp() {
        cacheService.flushAll()
        userRepository.deleteAll()
        userRoleRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        cacheService.flushAll()
        userRepository.deleteAll()
        userRoleRepository.deleteAll()
    }

    private fun post(path: String, bodyJson: String, token: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port/api$path"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
        token?.let { builder.header("Authorization", "Bearer $it") }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun get(path: String, token: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port/api$path"))
            .GET()
        token?.let { builder.header("Authorization", "Bearer $it") }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun body(resp: HttpResponse<String>): Map<*, *> =
        objectMapper.readValue(resp.body(), Map::class.java)

    @Suppress("UNCHECKED_CAST")
    private fun dataOf(resp: HttpResponse<String>): Map<String, Any> = body(resp)["data"] as Map<String, Any>

    private fun createSuperAdminRole(): String {
        roleRepository.deleteAll()
        val role = Role(id = com.scx.backend.common.util.IdGenerator.nextId(), name = "超级管理员", code = "SUPER_ADMIN")
        roleRepository.save(role)
        return role.id
    }

    /**
     * 完整流程：注册 → 登出 → 密码登录 → 鉴权访问
     */
    @Test
    fun `register with verification code then login via password`() {
        val email = "reg-${System.nanoTime()}@scx.dev"
        val password = "Password123!"

        // 1. 直接写验证码到 Redis（绕过邮件）
        val code = "123456"
        cacheService.setWithMilliseconds(CacheKeys.emailVerification(email), code, 600000)

        // 2. 注册
        val regBody = """{"email":"$email","name":"测试用户","password":"$password","emailVerificationCode":"$code"}"""
        val regResp = post("/users/register", regBody)
        assertEquals(200, regResp.statusCode(), "注册应成功: ${regResp.body()}")
        val regData = dataOf(regResp)
        assertNotNull(regData["id"])
        assertEquals(email, regData["email"])

        // 3. 密码登录需要加密密钥
        val keyResp = get("/users/encryption-key")
        assertEquals(200, keyResp.statusCode())
        @Suppress("UNCHECKED_CAST")
        val keyData = body(keyResp)["data"] as Map<String, String>
        val encryptedPassword = CryptoUtil.encrypt(password, keyData["key"]!!)

        val loginBody = """{"email":"$email","password":"$encryptedPassword","keyId":"${keyData["keyId"]}"}"""
        val loginResp = post("/users/login-password", loginBody)
        assertEquals(200, loginResp.statusCode(), "密码登录应成功: ${loginResp.body()}")
        val loginData = dataOf(loginResp)
        assertNotNull(loginData["accessToken"])
        assertNotNull(loginData["refreshToken"])
    }

    /**
     * 验证码登录流程
     */
    @Test
    fun `login with email verification code`() {
        val email = "code-${System.nanoTime()}@scx.dev"
        // 先注册用户（直接写验证码）
        cacheService.setWithMilliseconds(CacheKeys.emailVerification(email), "111111", 600000)
        post("/users/register", """{"email":"$email","name":"验证码登录用户","password":"Password123!","emailVerificationCode":"111111"}""")

        // 写登录验证码
        cacheService.setWithMilliseconds(CacheKeys.loginVerification(email), "222222", 600000)
        val loginResp = post("/users/login", """{"email":"$email","emailVerificationCode":"222222"}""")
        assertEquals(200, loginResp.statusCode(), "验证码登录应成功: ${loginResp.body()}")
        assertNotNull(dataOf(loginResp)["accessToken"])
    }

    /**
     * 鉴权链：无 token → 401，有 token → 200
     */
    @Test
    fun `protected user endpoint requires valid token`() {
        // 无 token
        val noTokenResp = get("/users/check-role?id=x&roleCode=SUPER_ADMIN")
        assertEquals(401, noTokenResp.statusCode())

        // 创建用户并生成 token
        val email = "auth-${System.nanoTime()}@scx.dev"
        cacheService.setWithMilliseconds(CacheKeys.emailVerification(email), "333333", 600000)
        val regResp = post("/users/register", """{"email":"$email","name":"鉴权用户","password":"Password123!","emailVerificationCode":"333333"}""")
        val userId = dataOf(regResp)["id"] as String
        val token = authService.generateAccessToken(userId, email)

        // 有 token
        val withTokenResp = get("/users/check-role?id=$userId&roleCode=SUPER_ADMIN", token)
        assertEquals(200, withTokenResp.statusCode())
    }

    /**
     * 管理员接口：非管理员 → 401，管理员 → 200
     */
    @Test
    fun `admin endpoint requires admin role`() {
        // 创建普通用户
        val email = "normal-${System.nanoTime()}@scx.dev"
        cacheService.setWithMilliseconds(CacheKeys.emailVerification(email), "444444", 600000)
        val regResp = post("/users/register", """{"email":"$email","name":"普通用户","password":"Password123!","emailVerificationCode":"444444"}""")
        val normalUserId = dataOf(regResp)["id"] as String
        val normalToken = authService.generateAccessToken(normalUserId, email)

        // 普通用户访问管理员接口 → 401
        val forbiddenResp = get("/users/list?page=1&limit=10", normalToken)
        assertEquals(401, forbiddenResp.statusCode(), "普通用户应被拒绝")

        // 创建超级管理员用户
        val adminEmail = "admin-${System.nanoTime()}@scx.dev"
        cacheService.setWithMilliseconds(CacheKeys.emailVerification(adminEmail), "555555", 600000)
        val adminRegResp = post("/users/register", """{"email":"$adminEmail","name":"管理员","password":"Password123!","emailVerificationCode":"555555"}""")
        val adminUserId = dataOf(adminRegResp)["id"] as String
        // 分配 SUPER_ADMIN 角色
        userRoleRepository.save(UserRole(id = com.scx.backend.common.util.IdGenerator.nextId(), userId = adminUserId, roleId = superAdminRoleId))
        val adminToken = authService.generateAccessToken(adminUserId, adminEmail)

        // 管理员访问 → 200
        val allowedResp = get("/users/list?page=1&limit=10", adminToken)
        assertEquals(200, allowedResp.statusCode(), "管理员应被允许: ${allowedResp.body()}")
    }

    /**
     * RBAC：角色分配与查询
     */
    @Test
    fun `assign role and query user roles`() {
        val email = "rbac-${System.nanoTime()}@scx.dev"
        cacheService.setWithMilliseconds(CacheKeys.emailVerification(email), "666666", 600000)
        val regResp = post("/users/register", """{"email":"$email","name":"RBAC用户","password":"Password123!","emailVerificationCode":"666666"}""")
        val userId = dataOf(regResp)["id"] as String
        val token = authService.generateAccessToken(userId, email)

        // 分配角色
        val assignResp = post("/users/assign-role", """{"userId":"$userId","roleId":"$superAdminRoleId"}""", token)
        assertEquals(200, assignResp.statusCode(), "角色分配应成功: ${assignResp.body()}")

        // 查询用户角色
        val rolesResp = get("/users/roles?id=$userId", token)
        assertEquals(200, rolesResp.statusCode())
        @Suppress("UNCHECKED_CAST")
        val roles = body(rolesResp)["data"] as List<Map<String, Any>>
        assertTrue(roles.isNotEmpty(), "应有至少一个角色")
        assertEquals("SUPER_ADMIN", roles[0]["code"])

        // check-role 应为 true
        val checkResp = get("/users/check-role?id=$userId&roleCode=SUPER_ADMIN", token)
        assertEquals(true, body(checkResp)["data"].let { @Suppress("UNCHECKED_CAST") (it as Map<String, Any>)["hasRole"] })
    }

    /**
     * 管理员创建用户（免验证码）
     */
    @Test
    fun `admin can create user directly`() {
        // 创建管理员
        val adminEmail = "create-admin-${System.nanoTime()}@scx.dev"
        cacheService.setWithMilliseconds(CacheKeys.emailVerification(adminEmail), "777777", 600000)
        val adminReg = post("/users/register", """{"email":"$adminEmail","name":"管理员","password":"Password123!","emailVerificationCode":"777777"}""")
        val adminId = dataOf(adminReg)["id"] as String
        userRoleRepository.save(UserRole(id = com.scx.backend.common.util.IdGenerator.nextId(), userId = adminId, roleId = superAdminRoleId))
        val adminToken = authService.generateAccessToken(adminId, adminEmail)

        // 管理员创建用户
        val newEmail = "created-${System.nanoTime()}@scx.dev"
        val createResp = post(
            "/users/create",
            """{"email":"$newEmail","name":"被创建用户","password":"Password123!","isActive":true}""",
            adminToken,
        )
        assertEquals(200, createResp.statusCode(), "管理员创建用户应成功: ${createResp.body()}")
        assertEquals(newEmail, dataOf(createResp)["email"])
    }
}
