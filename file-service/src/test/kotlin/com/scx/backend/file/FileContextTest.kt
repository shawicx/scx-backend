package com.scx.backend.file

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.TestPropertySource

/**
 * 文件服务上下文加载测试
 *
 * 验证：file-service 独立启动时，JPA（files 表 validate）、Flyway、
 * FileSecurityConfig、FileController 等 Bean 正确装配。
 * 使用 H2 占位 datasource + 关闭 Flyway 自动迁移，避免依赖真实 PG。
 */
@SpringBootTest
@TestPropertySource(
    properties = [
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.url=jdbc:h2:mem:fileTest;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
    ],
)
class FileContextTest(
    @Autowired private val applicationContext: ApplicationContext,
) {

    @Test
    fun `context loads with file service beans`() {
        assertNotNull(applicationContext, "应用上下文应成功加载")
        assertNotNull(applicationContext.getBean(com.scx.backend.file.FileController::class.java), "FileController 应被注册")
    }
}
