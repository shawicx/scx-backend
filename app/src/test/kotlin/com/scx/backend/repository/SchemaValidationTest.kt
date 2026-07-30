package com.scx.backend.repository

import com.scx.backend.entity.Role
import com.scx.backend.entity.User
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

/**
 * Schema 验证测试：依赖真实 PostgreSQL（127.0.0.1:5433 / scx-backend）。
 *
 * application.yml 已配置 ddl-auto=validate：若 JPA 实体映射与 Flyway 建表不符，
 * 上下文加载阶段即失败（无需显式断言即可暴露问题）。
 * 测试通过即证明：Flyway 迁移成功 + 6 张表结构 + 实体映射全部一致。
 */
@SpringBootTest
@Transactional
class SchemaValidationTest {

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var roleRepository: RoleRepository

    @Autowired
    lateinit var entityManager: EntityManager

    @Test
    fun `schema is valid and repositories are wired`() {
        // 上下文加载成功 + ddl-auto=validate 通过即证明 schema 一致
        // 不断言 count==0（其它测试可能残留数据），仅验证 repository 可用
        assertNotNull(userRepository)
        assertNotNull(roleRepository)
        userRepository.count() // 能执行查询即说明映射正确
        roleRepository.count()
    }

    @Test
    fun `can persist and query user with camelCase columns`() {
        val user = User(
            id = "test-user-001",
            email = "test@scx.dev",
            name = "测试用户",
            password = "hashed-password",
        )
        userRepository.save(user)
        entityManager.flush()
        entityManager.clear()

        val found = userRepository.findByEmail("test@scx.dev")
        assertNotNull(found)
        assertEquals("test@scx.dev", found!!.email)
        assertEquals(false, found.emailVerified)
        assertEquals(true, found.isActive)
    }

    @Test
    fun `can persist role with system flag`() {
        val role = Role(
            id = "test-role-001",
            name = "测试角色",
            code = "TEST_ROLE",
            isSystem = false,
        )
        roleRepository.save(role)
        entityManager.flush()
        entityManager.clear()

        val found = roleRepository.findByCode("TEST_ROLE")
        assertNotNull(found)
        assertEquals("测试角色", found!!.name)
        assertEquals(false, found.isSystem)
    }
}
