package com.scx.backend.file.repository

import com.scx.backend.file.entity.File
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface FileRepository : JpaRepository<File, String> {
    fun findByUserId(userId: String): List<File>
}
