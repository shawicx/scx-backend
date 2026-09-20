package com.scx.backend.rbac

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.TestPropertySource

/**
 * rbac 服务 schema 一致性测试
 *
 * 在 H2（PostgreSQL 模式）上执行全量 Flyway 迁移（V1 初始 schema +
 * common-audit V2/V3/V4/V5），并以 ddl-auto=validate 校验 JPA 实体与
 * 最终表结构一致（重点：Role.dataScope ↔ V5 新增 "dataScope" 列）。
 * 上下文加载成功即代表迁移 SQL 可执行且实体映射无误。
 */
@SpringBootTest
@TestPropertySource(
    properties = [
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.url=jdbc:h2:mem:rbacSchemaTest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=SET NON_KEYWORDS=VALUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
    ],
)
class RbacSchemaTest(
    @Autowired private val applicationContext: ApplicationContext,
) {

    @Test
    fun `flyway migrations and jpa entities are consistent`() {
        assertNotNull(applicationContext, "Flyway 迁移 + 实体校验应成功完成")
    }
}
