package com.scx.backend.notification.message

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component

/**
 * @description 收件人解析器（原生 SQL 只读查询共享库的 users / user_roles）
 *
 * 不为 users / user_roles 建 JPA 实体：identity 过渡期进程内打包本模块时
 * 扫描 com.scx.backend 全包，重复的 users 实体映射会导致 Hibernate 启动失败。
 * 只返回 isActive=true 且未软删除的用户。
 */
@Component
class RecipientResolver(
    private val jdbc: NamedParameterJdbcTemplate,
) {
    private val userFilter = """u."isActive" = TRUE AND u."deletedAt" IS NULL"""

    /**
     * @description 全体有效用户（全局广播）
     * @returns List<String> 用户 ID 列表
     */
    fun resolveAll(): List<String> =
        jdbc.query(
            """SELECT id FROM users u WHERE $userFilter""",
            emptyMap<String, Any>(),
        ) { rs, _ -> rs.getString("id") }

    /**
     * @description 按角色解析用户（多角色命中去重）
     * @param roleIds 角色 ID 列表
     * @returns List<String> 去重后的用户 ID 列表
     */
    fun resolveByRoleIds(roleIds: List<String>): List<String> {
        if (roleIds.isEmpty()) return emptyList()
        return jdbc.query(
            """
            SELECT DISTINCT u.id FROM users u
            JOIN user_roles ur ON ur."userId" = u.id
            WHERE ur."roleId" IN (:roleIds) AND $userFilter
            """,
            mapOf("roleIds" to roleIds),
        ) { rs, _ -> rs.getString("id") }
    }

    /**
     * @description 按用户 ID 解析（过滤禁用/软删/不存在）
     * @param userIds 用户 ID 列表
     * @returns List<String> 有效用户 ID 列表
     */
    fun resolveByUserIds(userIds: List<String>): List<String> {
        if (userIds.isEmpty()) return emptyList()
        return jdbc.query(
            """SELECT id FROM users u WHERE u.id IN (:userIds) AND $userFilter""",
            mapOf("userIds" to userIds),
        ) { rs, _ -> rs.getString("id") }
    }

    /**
     * @description 解析用户邮箱（EMAIL 投递用）
     * @param userIds 用户 ID 列表
     * @returns Map<String, String> userId -> email（仅有效用户）
     */
    fun resolveEmails(userIds: List<String>): Map<String, String> {
        if (userIds.isEmpty()) return emptyMap()
        return jdbc.query(
            """SELECT id, email FROM users u WHERE u.id IN (:userIds) AND $userFilter""",
            mapOf("userIds" to userIds),
        ) { rs, _ -> rs.getString("id") to rs.getString("email") }.toMap()
    }
}
