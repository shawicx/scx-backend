package com.scx.backend.rbac.dict

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
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
 * @description 字典模块 HTTP 层回归测试：真实消息转换链（Jackson 3）解析、默认值补齐、管理端守卫
 *
 * 背景：服务层测试曾全绿但线上 create 接口 400——Jackson 3（Spring Framework 7 的
 * JacksonJsonHttpMessageConverter）对请求体省略的非空 Int 字段抛
 * "Cannot map null into type int"。DTO 已改为可空 + 服务端补默认值，
 * 本测试直连服务端口走完整 HTTP 链防止回归。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = [
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.url=jdbc:h2:mem:dictApi;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
    ],
)
class DictApiTest(
    @Autowired private val objectMapper: ObjectMapper,
    @LocalServerPort private val port: Int,
) {
    private val http = HttpClient.newHttpClient()
    private val adminHeaders = listOf(
        "Content-Type" to "application/json",
        "X-User-Id" to "01HSTESTADMIN0000000000000A",
        "X-User-Email" to "test-admin@local",
        "X-User-Admin" to "true",
    )

    /**
     * @description 发送 JSON POST 并返回 (业务 success, 业务码, data 节点)
     * @param path 请求路径
     * @param body 请求体对象
     * @param headers 请求头
     * @returns Triple<Boolean, Int, com.fasterxml.jackson.databind.JsonNode>
     */
    private fun post(path: String, body: Any, headers: List<Pair<String, String>>) =
        roundTrip("POST", path, objectMapper.writeValueAsString(body), headers)

    /**
     * @description 发送 JSON PUT 并返回 (业务 success, 业务码, data 节点)
     */
    private fun put(path: String, body: Any, headers: List<Pair<String, String>>) =
        roundTrip("PUT", path, objectMapper.writeValueAsString(body), headers)

    /**
     * @description 执行请求并解析统一响应体
     */
    private fun roundTrip(
        method: String,
        path: String,
        body: String,
        headers: List<Pair<String, String>>,
    ): Triple<Boolean, Int, com.fasterxml.jackson.databind.JsonNode> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port/api$path"))
            .method(method, HttpRequest.BodyPublishers.ofString(body))
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        val json = objectMapper.readTree(response.body())
        return Triple(
            json.get("success").asBoolean(),
            json.get("statusCode").asInt(),
            json.get("data"),
        )
    }

    @Test
    fun `create data with omitted sort and status applies server defaults`() {
        val (typeOk, _, typeData) = post(
            "/dicts/type/create",
            mapOf("name" to "HTTP回归类型", "code" to "http_regression"),
            adminHeaders,
        )
        assertEquals(true, typeOk)

        // 省略 sort/status：Jackson 3 下应正常解析并由服务端补 0/1
        val (ok, code, data) = post(
            "/dicts/data/create",
            mapOf("typeId" to typeData.get("id").asText(), "label" to "默认项", "value" to "dft"),
            adminHeaders,
        )
        assertEquals(true, ok)
        assertEquals(200, code)
        assertEquals(0, data.get("sort").asInt())
        assertEquals(1, data.get("status").asInt())

        // 显式传值仍生效
        val (ok2, _, data2) = post(
            "/dicts/data/create",
            mapOf(
                "typeId" to typeData.get("id").asText(),
                "label" to "显式项", "value" to "ex", "sort" to 5, "status" to 0,
            ),
            adminHeaders,
        )
        assertEquals(true, ok2)
        assertEquals(5, data2.get("sort").asInt())
        assertEquals(0, data2.get("status").asInt())
    }

    @Test
    fun `non admin write is rejected with 9003`() {
        val (_, code, _) = post(
            "/dicts/type/create",
            mapOf("name" to "越权类型", "code" to "no_perm_http"),
            adminHeaders.dropLast(1) + ("X-User-Admin" to "false"),
        )
        assertEquals(9003, code)
    }

    @Test
    fun `by-code returns enabled options for logged-in user`() {
        val (_, _, typeData) = post(
            "/dicts/type/create",
            mapOf("name" to "取值类型", "code" to "http_options"),
            adminHeaders,
        )
        post(
            "/dicts/data/create",
            mapOf(
                "typeId" to typeData.get("id").asText(),
                "label" to "选项甲", "value" to "a", "sort" to 2,
            ),
            adminHeaders,
        )
        post(
            "/dicts/data/create",
            mapOf(
                "typeId" to typeData.get("id").asText(),
                "label" to "选项乙", "value" to "b", "sort" to 1,
            ),
            adminHeaders,
        )

        val userHeaders = adminHeaders.dropLast(1) + ("X-User-Admin" to "false")
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port/api/dicts/data/by-code?code=http_options"))
            .apply { userHeaders.forEach { (k, v) -> header(k, v) } }
            .GET()
            .build()
        val json = objectMapper.readTree(http.send(request, HttpResponse.BodyHandlers.ofString()).body())
        assertEquals(true, json.get("success").asBoolean())
        assertEquals(
            listOf("选项乙", "选项甲"),
            json.get("data").map { it.get("label").asText() },
        )
    }

    @Test
    fun `update data accepts partial body`() {
        val (_, _, typeData) = post(
            "/dicts/type/create",
            mapOf("name" to "部分更新类型", "code" to "http_partial"),
            adminHeaders,
        )
        val (_, _, data) = post(
            "/dicts/data/create",
            mapOf("typeId" to typeData.get("id").asText(), "label" to "旧名", "value" to "p"),
            adminHeaders,
        )
        val (ok, _, updated) = put(
            "/dicts/data/update",
            mapOf("id" to data.get("id").asText(), "label" to "新名"),
            adminHeaders,
        )
        assertEquals(true, ok)
        assertEquals("新名", updated.get("label").asText())
        assertEquals("p", updated.get("value").asText())
    }
}
