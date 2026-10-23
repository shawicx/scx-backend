package com.scx.backend.blog.tag

import com.scx.backend.blog.entity.BlogTag
import com.scx.backend.blog.repository.BlogPostTagRepository
import com.scx.backend.blog.repository.BlogTagRepository
import com.scx.backend.blog.tag.dto.CreateTagDto
import com.scx.backend.blog.tag.dto.TagAdminListResponseDto
import com.scx.backend.blog.tag.dto.TagAdminResponseDto
import com.scx.backend.blog.tag.dto.TagPublicListResponseDto
import com.scx.backend.blog.tag.dto.TagPublicResponseDto
import com.scx.backend.blog.tag.dto.UpdateTagDto
import com.scx.backend.common.exception.SystemException
import com.scx.backend.common.util.IdGenerator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * @description 博客标签服务（管理端 CRUD + 公开启用列表）
 *
 * 停用标签不允许新文章选择（文章保存时校验），历史关联保留；
 * 删除标签时级联清理文章关联（DDL CASCADE + 服务层显式删除双保险）。
 */
@Service
class BlogTagService(
    private val tagRepository: BlogTagRepository,
    private val postTagRepository: BlogPostTagRepository,
) {

    /**
     * @description 管理端全量标签（排序号升序，含停用；附公开文章计数）
     * @return TagAdminListResponseDto 标签列表
     */
    @Transactional(readOnly = true)
    fun adminList(): TagAdminListResponseDto {
        val tags = tagRepository.findAllByOrderBySortAscCreatedAtAsc()
        return TagAdminListResponseDto(
            list = tags.map { TagAdminResponseDto.from(it, tagRepository.countPublicPostsByTagId(it.id)) },
        )
    }

    /**
     * @description 公开标签列表（仅启用；附公开文章计数）
     * @return TagPublicListResponseDto 标签列表
     */
    @Transactional(readOnly = true)
    fun publicList(): TagPublicListResponseDto {
        val tags = tagRepository.findByStatusOrderBySortAscCreatedAtAsc(1)
        return TagPublicListResponseDto(
            list = tags.map {
                TagPublicResponseDto(
                    name = it.name,
                    slug = it.slug,
                    description = it.description,
                    postCount = tagRepository.countPublicPostsByTagId(it.id),
                )
            },
        )
    }

    /**
     * @description 创建标签（slug 缺省由名称生成，冲突报 9007）
     * @param dto 创建请求
     * @return TagAdminResponseDto 创建后的标签
     */
    @Transactional
    fun create(dto: CreateTagDto): TagAdminResponseDto {
        val slug = requireUniqueSlug(dto.slug, dto.name)
        val tag = tagRepository.save(
            BlogTag(
                id = IdGenerator.nextId(),
                name = dto.name.trim(),
                slug = slug,
                description = dto.description?.trim()?.takeIf { it.isNotEmpty() },
                sort = dto.sort ?: 0,
                status = normalizeStatus(dto.status),
            ),
        )
        return TagAdminResponseDto.from(tag, 0)
    }

    /**
     * @description 更新标签（仅元数据；slug 变更后需刷新对应公开页面）
     * @param id 标签 ID
     * @param dto 更新请求
     * @return TagAdminResponseDto 更新后的标签
     */
    @Transactional
    fun update(id: String, dto: UpdateTagDto): TagAdminResponseDto {
        val tag = requireTag(id)
        val newSlug = resolveSlugForUpdate(tag, dto.slug, dto.name)
        tag.apply {
            name = dto.name.trim()
            slug = newSlug
            description = dto.description?.trim()?.takeIf { it.isNotEmpty() }
            sort = dto.sort ?: 0
            status = normalizeStatus(dto.status)
            updatedAt = LocalDateTime.now()
        }
        tagRepository.save(tag)
        return TagAdminResponseDto.from(tag, tagRepository.countPublicPostsByTagId(tag.id))
    }

    /**
     * @description 删除标签（显式清理文章关联后删除标签）
     * @param id 标签 ID
     */
    @Transactional
    fun delete(id: String) {
        requireTag(id)
        postTagRepository.deleteByTagId(id)
        tagRepository.deleteById(id)
    }

    /**
     * @description 查找标签，不存在抛 9002
     * @param id 标签 ID
     * @return BlogTag 标签实体
     */
    private fun requireTag(id: String): BlogTag =
        tagRepository.findById(id).orElse(null) ?: throw SystemException.dataNotFound("标签不存在")

    /**
     * @description 创建场景生成唯一 slug（缺省由名称派生；冲突报 9007）
     * @param requested 指定的 slug（可空）
     * @param name 标签名称
     * @return String 唯一 slug
     */
    private fun requireUniqueSlug(requested: String?, name: String): String {
        val candidate = slugify(requested?.takeIf { it.isNotBlank() } ?: name)
        if (tagRepository.existsBySlug(candidate)) {
            throw SystemException.resourceExists("slug 已被占用")
        }
        return candidate
    }

    /**
     * @description 更新场景解析 slug（未变化沿用；变化时确保唯一）
     * @param tag 标签实体
     * @param requested 请求的 slug
     * @param name 标签名称
     * @return String 唯一 slug
     */
    private fun resolveSlugForUpdate(tag: BlogTag, requested: String?, name: String): String {
        val candidate = slugify(requested?.takeIf { it.isNotBlank() } ?: name)
        if (candidate == tag.slug) return tag.slug
        if (tagRepository.existsBySlug(candidate)) {
            throw SystemException.resourceExists("slug 已被占用")
        }
        return candidate
    }

    /**
     * @description 由名称/指定值生成规范 slug（无法派生时回退 ULID）
     * @param source 输入字符串
     * @return String 规范化 slug
     */
    private fun slugify(source: String): String {
        val cleaned = source.trim().lowercase()
            .replace(Regex("\\s+"), "-")
            .replace(Regex("[^a-z0-9-]"), "")
            .replace(Regex("-{2,}"), "-")
            .trim('-')
            .take(80)
        return cleaned.ifEmpty { "tag-${IdGenerator.nextId().lowercase()}" }
    }

    /**
     * @description 归一化状态入参（缺省/非 0 一律视为启用）
     * @param status 请求的状态
     * @return Int 0 停用 / 1 启用
     */
    private fun normalizeStatus(status: Int?): Int = if (status == 0) 0 else 1
}
