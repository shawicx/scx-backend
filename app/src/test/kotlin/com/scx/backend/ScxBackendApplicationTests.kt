package com.scx.backend

import com.scx.backend.config.AppProperties
import com.scx.backend.config.SwaggerProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

/**
 * 配置绑定冒烟测试。
 *
 * 设计说明：不依赖真实 DB/Redis/Mail。由于本工程尚未有任何 JPA Repository，
 * 但 `application.yml` 配置了 JPA/Flyway，在测试环境若无数据库会让 Hibernate
 * 因无法解析 Dialect 而启动失败。因此通过 test 属性：
 *   1. 关闭 Flyway；
 *   2. 指定 hibernate.dialect 为 PostgreSQLDialect（绕过 JDBC 元数据探测）；
 *   3. 用 H2 内存库提供 DataSource（避免真实 PG 连接）。
 * 这样可加载完整上下文，真实验证 @ConfigurationProperties 绑定。
 */
@SpringBootTest(
    properties = [
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        // 用嵌入式 H2 提供 DataSource，避免连接真实 PostgreSQL
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
    ],
)
@EnableConfigurationProperties(AppProperties::class, SwaggerProperties::class)
class ScxBackendApplicationTests {

    @Autowired
    lateinit var appProperties: AppProperties

    @Autowired
    lateinit var swaggerProperties: SwaggerProperties

    @Test
    fun `context loads and binds app properties`() {
        assertNotNull(appProperties)
        assertTrue(appProperties.port >= 1)
    }

    @Test
    fun `swagger properties are bound`() {
        assertNotNull(swaggerProperties)
        assertTrue(swaggerProperties.enabled)
        assertEquals("SCX Backend API", swaggerProperties.title)
    }
}
