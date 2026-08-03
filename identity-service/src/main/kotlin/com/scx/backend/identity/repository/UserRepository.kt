package com.scx.backend.identity.repository

import com.scx.backend.identity.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<User, String>, JpaSpecificationExecutor<User> {
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
}
