package com.scx.backend.repository

import com.scx.backend.entity.UserRole
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface UserRoleRepository : JpaRepository<UserRole, String> {
    fun findByUserId(userId: String): List<UserRole>
    fun findByRoleId(roleId: String): List<UserRole>
    fun findByUserIdAndRoleId(userId: String, roleId: String): UserRole?
    fun findByUserIdAndRoleIdIn(userId: String, roleIds: List<String>): List<UserRole>
    fun deleteByUserId(userId: String): Int
    fun deleteByRoleId(roleId: String): Int
    fun existsByUserIdAndRoleId(userId: String, roleId: String): Boolean
    fun countByRoleId(roleId: String): Long

    /** 用户是否拥有指定 code 的角色（关联 roles 表） */
    @Query(
        "SELECT CASE WHEN COUNT(ur) > 0 THEN true ELSE false END FROM UserRole ur " +
            "JOIN Role r ON ur.roleId = r.id WHERE ur.userId = :userId AND r.code = :roleCode",
    )
    fun existsByUserIdAndRoleCode(@Param("userId") userId: String, @Param("roleCode") roleCode: String): Boolean

    /** 用户是否拥有 code 以指定前缀开头的角色（管理员判定） */
    @Query(
        "SELECT CASE WHEN COUNT(ur) > 0 THEN true ELSE false END FROM UserRole ur " +
            "JOIN Role r ON ur.roleId = r.id WHERE ur.userId = :userId AND r.code LIKE :prefix%",
    )
    fun existsByUserIdAndRoleCodePrefix(@Param("userId") userId: String, @Param("prefix") prefix: String): Boolean
}
