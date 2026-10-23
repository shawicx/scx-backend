package com.scx.backend.blog.repository

import com.scx.backend.blog.entity.BlogPostLike
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional

/**
 * @description 匿名点赞记录仓库（幂等点赞/取消）
 */
interface BlogPostLikeRepository : JpaRepository<BlogPostLike, String> {

    /**
     * @description 判断访客是否已点赞该文章
     * @param postId 文章 ID
     * @param visitorKeyHash 访客标识哈希
     * @return Boolean 是否已点赞
     */
    fun existsByPostIdAndVisitorKeyHash(postId: String, visitorKeyHash: String): Boolean

    /**
     * @description 删除访客在该文章的点赞记录（取消点赞）
     * @param postId 文章 ID
     * @param visitorKeyHash 访客标识哈希
     */
    @Transactional
    fun deleteByPostIdAndVisitorKeyHash(postId: String, visitorKeyHash: String)
}
