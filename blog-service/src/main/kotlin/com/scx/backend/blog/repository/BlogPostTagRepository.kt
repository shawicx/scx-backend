package com.scx.backend.blog.repository

import com.scx.backend.blog.entity.BlogPostTag
import com.scx.backend.blog.entity.BlogPostTagKey
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional

/**
 * @description 文章标签关联仓库（整体替换式维护）
 */
interface BlogPostTagRepository : JpaRepository<BlogPostTag, BlogPostTagKey> {

    /**
     * @description 查文章的全部标签关联
     * @param postId 文章 ID
     * @return List<BlogPostTag> 关联列表
     */
    fun findByPostId(postId: String): List<BlogPostTag>

    /**
     * @description 删除文章的全部标签关联（编辑替换/文章删除时调用）
     * @param postId 文章 ID
     */
    @Transactional
    fun deleteByPostId(postId: String)

    /**
     * @description 删除标签的全部文章关联（删除标签时调用，不依赖数据库级联）
     * @param tagId 标签 ID
     */
    @Transactional
    fun deleteByTagId(tagId: String)
}
