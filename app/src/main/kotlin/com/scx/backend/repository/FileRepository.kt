package com.scx.backend.repository

import com.scx.backend.entity.File
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface FileRepository : JpaRepository<File, String> {
    fun findByUserId(userId: String): List<File>
}
