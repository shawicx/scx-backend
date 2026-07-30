package com.scx.backend.modules.role

import com.fasterxml.jackson.databind.ObjectMapper
import com.scx.backend.common.constants.CacheKeys
import com.scx.backend.common.util.IdGenerator
import com.scx.backend.entity.Permission
import com.scx.backend.entity.Role
import com.scx.backend.entity.UserRole
import com.scx.backend.modules.auth.AuthService
import com.scx.backend.modules.cache.CacheService
import com.scx.backend.repository.PermissionRepository
import com.scx.backend.repository.RoleRepository
import com.scx.backend.repository.UserRepository
import com.scx.backend.repository.UserRoleRepository
import org.junit.jupiter.api.AfterEach
import org.springframework.jdbc.core.JdbcTemplate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
 * 角色 + 权限模块集成测试（依赖真实 PG）
 *
 * 覆盖：角色 CRUD、系统角色保护、权限树构建、level 计算、角色权限分配。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = ["spring.jpa.show-sql=false", "jwt.secret=role-perm-test"])
class RolePermissionIntegrationTest(
    @Autowired private val roleRepository: RoleRepository,
    @Autowired private val permissionRepository: PermissionRepository,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val userRoleRepository: UserRoleRepository,
    @Autowired private val cacheService: CacheService,
    @Autowired private val authService: AuthService,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @LocalServerPort private val port: Int,
) {
    private val http = HttpClient.newHttpClient()
    private var adminToken: String = ""

    /**
     * 清理表数据（按外键依赖顺序，用原生 SQL TRUNCATE CASCADE 避免 Hibernate 逐行删除
     * 在自引用 CASCADE 外键上引发的 ObjectOptimisticLockingFailureException）
     */
    private fun cleanTables() {
        jdbcTemplate.execute("TRUNCATE TABLE role_permissions, user_roles, permissions, roles, users RESTART IDENTITY CASCADE")
    }

    @BeforeEach
    fun setUp() {
        cacheService.flushAll()
        cleanTables()

        // 创建超管角色 + 超管用户，生成 token 供测试使用
        val role = Role(id = IdGenerator.nextId(), name = "超级管理员", code = "SUPER_ADMIN")
        roleRepository.save(role)
        val email = "rp-admin-${System.nanoTime()}@scx.dev"
        cacheService.setWithMilliseconds(CacheKeys.emailVerification(email), "111111", 600000)
        // 注册
        post("/users/register", """{"email":"$email","name":"管理员","password":"Password123!","emailVerificationCode":"111111"}""")
        val user = userRepository.findByEmail(email)!!
        userRoleRepository.save(UserRole(id = IdGenerator.nextId(), userId = user.id, roleId = role.id))
        adminToken = authService.generateAccessToken(user.id, email)
    }

    @AfterEach
    fun tearDown() {
        cacheService.flushAll()
        cleanTables()
    }

    private fun post(path: String, body: String, token: String? = adminToken): HttpResponse<String> = send("POST", path, body, token)
    private fun get(path: String, token: String? = adminToken): HttpResponse<String> = send("GET", path, null, token)
    private fun put(path: String, body: String, token: String? = adminToken): HttpResponse<String> = send("PUT", path, body, token)
    private fun delete(path: String, token: String? = adminToken): HttpResponse<String> = send("DELETE", path, null, token)

    private fun send(method: String, path: String, body: String?, token: String?): HttpResponse<String> {
        val builder = HttpRequest.newBuilder().uri(URI.create("http://localhost:$port/api$path"))
        if (body != null) {
            builder.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body))
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody())
        }
        token?.let { builder.header("Authorization", "Bearer $it") }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    @Suppress("UNCHECKED_CAST")
    private fun data(resp: HttpResponse<String>): Map<String, Any> = objectMapper.readValue(resp.body(), Map::class.java)["data"] as Map<String, Any>

    @Suppress("UNCHECKED_CAST")
    private fun dataList(resp: HttpResponse<String>): List<Map<String, Any>> = objectMapper.readValue(resp.body(), Map::class.java)["data"] as List<Map<String, Any>>

    // ============ 角色测试 ============

    @Test
    fun `create role and query by id and code`() {
        val resp = post("/roles/create", """{"name":"编辑","code":"EDITOR"}""")
        assertEquals(200, resp.statusCode(), resp.body())
        val roleId = data(resp)["id"] as String

        val byId = get("/roles/detail?id=$roleId")
        assertEquals(200, byId.statusCode())
        assertEquals("编辑", data(byId)["name"])

        val byCode = get("/roles/by-code?code=EDITOR")
        assertEquals(200, byCode.statusCode())
        assertEquals("EDITOR", data(byCode)["code"])
    }

    @Test
    fun `create duplicate role name fails with 409`() {
        post("/roles/create", """{"name":"重复","code":"DUP1"}""")
        val resp = post("/roles/create", """{"name":"重复","code":"DUP2"}""")
        assertEquals(409, resp.statusCode())
    }

    @Test
    fun `system role cannot be updated or deleted`() {
        // 直接创建系统角色
        val role = Role(id = IdGenerator.nextId(), name = "系统角色", code = "SYS", isSystem = true)
        roleRepository.save(role)

        val updateResp = put("/roles/update", """{"id":"${role.id}","name":"改名"}""")
        // businessRuleViolation 映射 HTTP 200 + 业务码 9012
        assertEquals(200, updateResp.statusCode())
        assertEquals(9012, objectMapper.readValue(updateResp.body(), Map::class.java)["statusCode"])

        val delResp = delete("/roles/delete?id=${role.id}")
        assertEquals(200, delResp.statusCode())
        assertEquals(9012, objectMapper.readValue(delResp.body(), Map::class.java)["statusCode"])

        // 角色仍存在
        assertNotNull(roleRepository.findById(role.id).orElse(null))
    }

    @Test
    fun `update role name and verify`() {
        val resp = post("/roles/create", """{"name":"旧名","code":"CHG","description":"desc"}""")
        val roleId = data(resp)["id"] as String

        val upd = put("/roles/update", """{"id":"$roleId","name":"新名","description":"新描述"}""")
        assertEquals(200, upd.statusCode())
        assertEquals("新名", data(upd)["name"])
        assertEquals("新描述", data(upd)["description"])
    }

    @Test
    fun `delete role succeeds`() {
        val resp = post("/roles/create", """{"name":"待删","code":"DEL"}""")
        val roleId = data(resp)["id"] as String

        val del = delete("/roles/delete?id=$roleId")
        assertEquals(200, del.statusCode())
        assertTrue(roleRepository.findById(roleId).isEmpty)
    }

    // ============ 权限树测试 ============

    @Test
    fun `create permission menu level 1 and build tree`() {
        // 创建一级菜单
        val resp = post("/permissions/create", """{"name":"用户管理","type":"MENU","path":"/user","icon":"User"}""")
        assertEquals(200, resp.statusCode(), resp.body())
        val menu = data(resp)
        assertEquals(1, menu["level"]) // 一级菜单 level=1
        val menuId = menu["id"] as String

        // 创建二级菜单（挂在一级下）
        val subResp = post("/permissions/create", """{"name":"用户列表","type":"MENU","parentId":"$menuId","path":"/user/list"}""")
        assertEquals(200, subResp.statusCode())
        assertEquals(2, data(subResp)["level"])

        // 创建按钮（挂在二级菜单下）
        val btnResp = post("/permissions/create", """{"name":"删除用户","type":"BUTTON","parentId":"${data(subResp)["id"]}","action":"delete","resource":"user"}""")
        assertEquals(200, btnResp.statusCode())
        assertEquals(3, data(btnResp)["level"])

        // 获取权限树，验证层级
        val tree = get("/permissions/tree")
        assertEquals(200, tree.statusCode())
        val treeData = dataList(tree)
        assertTrue(treeData.isNotEmpty())
        val firstMenu = treeData.find { it["name"] == "用户管理" }!!
        @Suppress("UNCHECKED_CAST")
        val children = firstMenu["children"] as List<Map<String, Any>>
        assertTrue(children.isNotEmpty())
        assertEquals("用户列表", children[0]["name"])
    }

    @Test
    fun `button without parent fails`() {
        val resp = post("/permissions/create", """{"name":"孤立按钮","type":"BUTTON","action":"read","resource":"x"}""")
        assertEquals(400, resp.statusCode())
    }

    @Test
    fun `get menu tree only includes visible enabled menus`() {
        val menuResp = post("/permissions/create", """{"name":"可见菜单","type":"MENU","path":"/v","visible":1,"status":1}""")
        val visibleId = data(menuResp)["id"] as String
        post("/permissions/create", """{"name":"隐藏菜单","type":"MENU","path":"/h","visible":0,"status":1}""")

        val tree = get("/permissions/menu-tree")
        assertEquals(200, tree.statusCode())
        val menus = dataList(tree)
        assertTrue(menus.any { it["id"] == visibleId })
        assertTrue(menus.none { it["name"] == "隐藏菜单" })
    }

    @Test
    fun `get buttons by menu id`() {
        val menuResp = post("/permissions/create", """{"name":"按钮菜单","type":"MENU","path":"/b"}""")
        assertEquals(200, menuResp.statusCode(), "菜单创建失败: ${menuResp.body()}")
        val menuId = data(menuResp)["id"] as String
        val btnAResp = post("/permissions/create", """{"name":"按钮A","type":"BUTTON","parentId":"$menuId","action":"aa","resource":"xx"}""")
        assertEquals(200, btnAResp.statusCode(), "按钮A创建失败: ${btnAResp.body()}")
        val btnBResp = post("/permissions/create", """{"name":"按钮B","type":"BUTTON","parentId":"$menuId","action":"bb","resource":"xx"}""")
        assertEquals(200, btnBResp.statusCode(), "按钮B创建失败: ${btnBResp.body()}")

        val buttonsInDb = permissionRepository.findButtonsByMenuId(menuId)
        assertEquals(2, buttonsInDb.size, "DB 中应有 2 个按钮，实际: ${buttonsInDb.size}，menuId=$menuId")

        val resp = get("/permissions/$menuId/buttons")
        assertEquals(200, resp.statusCode())
        val buttons = dataList(resp)
        assertEquals(2, buttons.size, "HTTP 返回按钮数不对: ${resp.body()}")
        assertTrue(buttons.all { it["type"] == "BUTTON" })
    }

    // ============ 角色权限分配 ============

    @Test
    fun `assign permissions to role and query back`() {
        val roleResp = post("/roles/create", """{"name":"测试角色","code":"TEST_R"}""")
        val roleId = data(roleResp)["id"] as String

        val perm1Resp = post("/permissions/create", """{"name":"权限1","type":"MENU","path":"/p1"}""")
        val perm2Resp = post("/permissions/create", """{"name":"权限2","type":"MENU","path":"/p2"}""")
        val perm1Id = data(perm1Resp)["id"] as String
        val perm2Id = data(perm2Resp)["id"] as String

        // 分配权限
        val assignResp = post("/roles/assign-permissions", """{"id":"$roleId","permissionIds":["$perm1Id","$perm2Id"]}""")
        assertEquals(200, assignResp.statusCode(), assignResp.body())

        // 查询角色权限
        val permsResp = get("/roles/permissions?id=$roleId")
        assertEquals(200, permsResp.statusCode())
        val perms = dataList(permsResp)
        assertEquals(2, perms.size)

        // 覆盖式重新分配（只留 perm1）
        post("/roles/assign-permissions", """{"id":"$roleId","permissionIds":["$perm1Id"]}""")
        val perms2 = dataList(get("/roles/permissions?id=$roleId"))
        assertEquals(1, perms2.size)

        // 移除单个权限
        delete("/roles/remove-permission?id=$roleId&permissionId=$perm1Id")
        val perms3 = dataList(get("/roles/permissions?id=$roleId"))
        assertTrue(perms3.isEmpty())
    }

    @Test
    fun `delete permission assigned to role fails`() {
        val roleResp = post("/roles/create", """{"name":"引用角色","code":"REF_R"}""")
        val roleId = data(roleResp)["id"] as String
        val permResp = post("/permissions/create", """{"name":"被引用权限","type":"MENU","path":"/ref"}""")
        val permId = data(permResp)["id"] as String
        post("/roles/assign-permissions", """{"id":"$roleId","permissionIds":["$permId"]}""")

        // 删除被引用的权限应失败
        val delResp = delete("/permissions/delete?id=$permId")
        assertEquals(409, delResp.statusCode())
    }

    @Test
    fun `delete permission cascade removes children`() {
        val menuResp = post("/permissions/create", """{"name":"级联父","type":"MENU","path":"/c"}""")
        val parentId = data(menuResp)["id"] as String
        post("/permissions/create", """{"name":"级联子","type":"MENU","parentId":"$parentId","path":"/c/sub"}""")

        // 删除父节点，子节点应被 DB CASCADE 自动删除
        val delResp = delete("/permissions/delete?id=$parentId")
        assertEquals(200, delResp.statusCode())
        assertNull(permissionRepository.findById(parentId).orElse(null))
        // 子节点也被删除
        val remaining = permissionRepository.findAll().filter { it.parentId == parentId }
        assertTrue(remaining.isEmpty())
    }
}
