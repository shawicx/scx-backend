package com.scx.backend.blog

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
 * @description 博客模块 HTTP 集成测试：管理端 CRUD/RBAC、公开可见性（草稿/私密/归档 404）、
 * 评论先审后显、点赞幂等、阅读统计与概览
 * （直连服务端口，手工注入 X-User-* 头模拟网关；H2 PostgreSQL 模式内存库）
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = [
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.url=jdbc:h2:mem:blogApi;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
    ],
)
class BlogApiTest(
    @Autowired private val objectMapper: ObjectMapper,
    @LocalServerPort private val port: Int,
) {

    private val http = HttpClient.newHttpClient()
    private val admin = mapOf("X-User-Id" to "U_BLOG_ADMIN", "X-User-Admin" to "true")
    private val nobody = mapOf("X-User-Id" to "U_BLOG_USER", "X-User-Admin" to "false")

    @Test
    fun `admin endpoints reject non-admin`() {
        // 未带身份头：9000；带普通用户头：9003
        val noAuth = get("/blog/admin/posts")
        assertEquals(401, noAuth.statusCode())
        assertEquals(9000, objectMapper.readTree(noAuth.body()).get("statusCode").asInt())

        val forbidden = get("/blog/admin/posts", nobody)
        assertEquals(403, forbidden.statusCode())
        assertEquals(9003, objectMapper.readTree(forbidden.body()).get("statusCode").asInt())
    }

    @Test
    fun `full blog lifecycle from admin crud to public interaction`() {
        // 1. 分类与标签管理
        val category = post(
            "/blog/admin/categories",
            """{"name":"技术","slug":"tech","description":"技术文章","sort":1}""",
            admin,
        )
        assertEquals(200, category.statusCode())
        assertEquals("tech", objectMapper.readTree(category.body()).get("data").get("slug").asText())

        val duplicated = post("/blog/admin/categories", """{"name":"技术2","slug":"tech"}""", admin)
        assertEquals(409, duplicated.statusCode())
        assertEquals(9007, objectMapper.readTree(duplicated.body()).get("statusCode").asInt())

        val tag = post("/blog/admin/tags", """{"name":"Kotlin","slug":"kotlin"}""", admin)
        val tagId = objectMapper.readTree(tag.body()).get("data").get("id").asText()

        val publicCategories = objectMapper.readTree(get("/blog/categories").body()).get("data").get("list")
        assertEquals(1, publicCategories.size())
        assertEquals(0, publicCategories.get(0).get("postCount").asInt())

        // 2. 创建草稿：公开接口不可见
        val categoryId = objectMapper.readTree(category.body()).get("data").get("id").asText()
        val draft = post(
            "/blog/admin/posts",
            """{"title":"Hello Blog","summary":"第一篇","content":"# 你好","categoryId":"$categoryId","tagIds":["$tagId"]}""",
            admin,
        )
        assertEquals(200, draft.statusCode())
        val post1 = objectMapper.readTree(draft.body()).get("data")
        assertEquals(0, post1.get("status").asInt())
        assertEquals(listOf(tagId), post1.get("tagIds").map { it.asText() })
        // 契约锁定：is 前缀 Boolean 必须序列化为 isTop（Jackson getter 折叠问题）
        assertFalse(post1.get("isTop").asBoolean())

        assertEquals(0, objectMapper.readTree(get("/blog/posts").body()).get("data").get("total").asLong())
        assertEquals(404, get("/blog/posts/hello-blog").statusCode())

        // 3. 发布后公开可见（列表/详情/相关）
        val published = post("/blog/admin/posts/${post1.get("id").asText()}/publish", "{}", admin)
        assertEquals(1, objectMapper.readTree(published.body()).get("data").get("status").asInt())

        val list = objectMapper.readTree(get("/blog/posts").body()).get("data")
        assertEquals(1, list.get("total").asLong())
        assertEquals("hello-blog", list.get("list").get(0).get("slug").asText())
        assertEquals(1, list.get("list").get(0).get("tags").size())

        val detail = objectMapper.readTree(get("/blog/posts/hello-blog").body()).get("data")
        assertEquals("# 你好", detail.get("content").asText())
        assertEquals("技术", detail.get("category").get("name").asText())
        assertTrue(detail.get("prev").isNull)
        assertEquals(0, objectMapper.readTree(get("/blog/posts/hello-blog/related").body()).get("data").get("list").size())

        // 4. 匿名互动：阅读、点赞幂等、取消
        post("/blog/posts/hello-blog/views", "{}", emptyMap())
        post("/blog/posts/hello-blog/views", "{}", emptyMap())
        val like1 = post("/blog/posts/hello-blog/like", "{}", emptyMap())
        assertEquals(1, objectMapper.readTree(like1.body()).get("data").get("count").asLong())
        assertTrue(objectMapper.readTree(like1.body()).get("data").get("liked").asBoolean())
        val likeAgain = post("/blog/posts/hello-blog/like", "{}", emptyMap())
        assertEquals(1, objectMapper.readTree(likeAgain.body()).get("data").get("count").asLong())
        val unliked = delete("/blog/posts/hello-blog/like", emptyMap())
        assertEquals(0, objectMapper.readTree(unliked.body()).get("data").get("count").asLong())

        // 5. 评论先审后显
        val comment = post(
            "/blog/posts/hello-blog/comments",
            """{"nickname":"访客甲","content":"写得不错"}""",
            emptyMap(),
        )
        assertTrue(objectMapper.readTree(comment.body()).get("success").asBoolean())
        assertEquals(0, objectMapper.readTree(get("/blog/posts/hello-blog/comments").body()).get("data").get("total").asLong())

        val pending = objectMapper.readTree(get("/blog/admin/comments?status=0", admin).body()).get("data")
        assertEquals(1, pending.get("total").asLong())
        val commentId = pending.get("list").get(0).get("id").asText()

        val badParent = post(
            "/blog/posts/hello-blog/comments",
            """{"nickname":"访客乙","content":"回复","parentId":"NO_SUCH"}""",
            emptyMap(),
        )
        assertEquals(400, badParent.statusCode())

        post("/blog/admin/comments/$commentId/approve", "{}", admin)
        val publicComments = objectMapper.readTree(get("/blog/posts/hello-blog/comments").body()).get("data")
        assertEquals(1, publicComments.get("total").asLong())
        assertEquals("访客甲", publicComments.get("list").get(0).get("nickname").asText())
        // 公开评论不携带邮箱/访客标识字段
        assertFalse(publicComments.get("list").get(0).has("email"))
        assertEquals(
            1,
            objectMapper.readTree(get("/blog/posts/hello-blog").body()).get("data").get("commentCount").asLong(),
        )

        // 6. 概览：今日阅读 2、待审核 0、趋势 7 天
        val overview = objectMapper.readTree(get("/blog/admin/stats/overview", admin).body()).get("data")
        assertEquals(1, overview.get("totalPosts").asLong())
        assertEquals(1, overview.get("publishedPosts").asLong())
        assertEquals(2, overview.get("todayViews").asLong())
        assertEquals(0, overview.get("pendingComments").asLong())
        assertEquals(7, overview.get("trend7d").size())

        // 7. 置顶排序 + 撤回/归档可见性
        val post2 = post(
            "/blog/admin/posts",
            """{"title":"Top Post","content":"置顶文","isTop":true}""",
            admin,
        )
        val post2Id = objectMapper.readTree(post2.body()).get("data").get("id").asText()
        post("/blog/admin/posts/$post2Id/publish", "{}", admin)
        val ordered = objectMapper.readTree(get("/blog/posts").body()).get("data").get("list")
        assertEquals("top-post", ordered.get(0).get("slug").asText())
        assertEquals("hello-blog", ordered.get(1).get("slug").asText())

        post("/blog/admin/posts/$post2Id/archive", "{}", admin)
        assertEquals(404, get("/blog/posts/top-post").statusCode())
        post("/blog/admin/posts/$post2Id/publish", "{}", admin)
        assertEquals(200, get("/blog/posts/top-post").statusCode())
        post("/blog/admin/posts/$post2Id/withdraw", "{}", admin)
        assertEquals(404, get("/blog/posts/top-post").statusCode())

        // 8. 私密文章不公开
        val privatePost = post(
            "/blog/admin/posts",
            """{"title":"Secret","content":"私密","visibility":1}""",
            admin,
        )
        val privateId = objectMapper.readTree(privatePost.body()).get("data").get("id").asText()
        post("/blog/admin/posts/$privateId/publish", "{}", admin)
        assertEquals(404, get("/blog/posts/secret").statusCode())

        // 9. 删除保护与软删除
        val blocked = delete("/blog/admin/categories/$categoryId", admin)
        assertEquals(200, blocked.statusCode())
        assertFalse(objectMapper.readTree(blocked.body()).get("success").asBoolean())

        delete("/blog/admin/posts/${post1.get("id").asText()}", admin)
        assertEquals(404, get("/blog/posts/hello-blog").statusCode())
        val afterDelete = objectMapper.readTree(get("/blog/admin/posts", admin).body()).get("data")
        assertEquals(2, afterDelete.get("total").asLong())

        val categoryDeleted = delete("/blog/admin/categories/$categoryId", admin)
        assertTrue(objectMapper.readTree(categoryDeleted.body()).get("success").asBoolean())
        val tagDeleted = delete("/blog/admin/tags/$tagId", admin)
        assertTrue(objectMapper.readTree(tagDeleted.body()).get("success").asBoolean())
    }

    private fun request(
        method: String,
        path: String,
        body: String?,
        headers: Map<String, String>,
    ): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port/api$path"))
            .header("Content-Type", "application/json")
        if (headers.isNotEmpty()) {
            builder.headers(*headers.flatMap { listOf(it.key, it.value) }.toTypedArray())
        }
        return http.send(
            builder
                .method(method, body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }

    private fun post(path: String, body: String, headers: Map<String, String>): HttpResponse<String> =
        request("POST", path, body, headers)

    private fun get(path: String, headers: Map<String, String> = emptyMap()): HttpResponse<String> =
        request("GET", path, null, headers)

    private fun delete(path: String, headers: Map<String, String> = emptyMap()): HttpResponse<String> =
        request("DELETE", path, null, headers)
}
