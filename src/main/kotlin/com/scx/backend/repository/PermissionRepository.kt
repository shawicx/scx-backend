package com.scx.backend.repository

import com.scx.backend.entity.Permission
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PermissionRepository : JpaRepository<Permission, String> {
    fun findByName(name: String): Permission?
    fun findByParentId(parentId: String): List<Permission>
    fun findByLevel(level: Int): List<Permission>
}
