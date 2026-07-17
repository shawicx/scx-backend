package com.scx.backend.repository

import com.scx.backend.entity.UserRole
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRoleRepository : JpaRepository<UserRole, String> {
    fun findByUserId(userId: String): List<UserRole>
    fun findByRoleId(roleId: String): List<UserRole>
    fun findByUserIdAndRoleId(userId: String, roleId: String): UserRole?
    fun deleteByUserId(userId: String): Int
    fun existsByUserIdAndRoleId(userId: String, roleId: String): Boolean
    fun countByRoleId(roleId: String): Long
}
