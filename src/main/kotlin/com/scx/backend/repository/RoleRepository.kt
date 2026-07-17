package com.scx.backend.repository

import com.scx.backend.entity.Role
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface RoleRepository : JpaRepository<Role, String> {
    fun findByCode(code: String): Role?
    fun existsByCode(code: String): Boolean
    fun existsByName(name: String): Boolean
}
