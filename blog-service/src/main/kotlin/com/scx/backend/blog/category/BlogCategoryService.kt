package com.scx.backend.blog.category

import com.scx.backend.blog.category.dto.CategoryAdminListResponseDto
import com.scx.backend.blog.category.dto.CategoryAdminResponseDto
import com.scx.backend.blog.category.dto.CategoryPublicListResponseDto
import com.scx.backend.blog.category.dto.CategoryPublicResponseDto
import com.scx.backend.blog.category.dto.CreateCategoryDto
import com.scx.backend.blog.category.dto.UpdateCategoryDto
import com.scx.backend.blog.entity.BlogCategory
import com.scx.backend.blog.repository.BlogCategoryRepository
import com.scx.backend.blog.repository.BlogPostRepository
import com.scx.backend.common.exception.SystemException
import com.scx.backend.common.util.IdGenerator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * @description 博客分类服务（单层分类；管理端 CRUD + 公开启用列表）
 *
 * 删除保护：仍有未删除文章的分类必须先迁移文章再删除；
 * 停用分类不改变存量文章的公开策略，仅新文章不可选。
 */
@Service
class BlogCategoryService(
    private val categoryRepository: BlogCategoryRepository,
    private val postRepository: BlogPostRepository,
) {

    /**
     * @description 管理端全量分类（排序号升序，含停用；附文章计数）
     * @return CategoryAdminListResponseDto 分类列表
     */
    @Transactional(readOnly = true)
    fun adminList(): CategoryAdminListResponseDto {
        val categories = categoryRepository.findAllByOrderBySortAscCreatedAtAsc()
        return CategoryAdminListResponseDto(
            list = categories.map { category ->
                CategoryAdminResponseDto.from(
                    category,
                    postCount = postRepository.countByCategoryIdAndDeletedAtIsNull(category.id),
                    publicPostCount = categoryRepository.countPublicPostsByCategoryId(category.id),
                )
            },
        )
    }

    /**
     * @description 公开分类列表（仅启用；附公开文章计数）
     * @return CategoryPublicListResponseDto 分类列表
     */
    @Transactional(readOnly = true)
    fun publicList(): CategoryPublicListResponseDto {
        val categories = categoryRepository.findByStatusOrderBySortAscCreatedAtAsc(1)
        return CategoryPublicListResponseDto(
            list = categories.map {
                CategoryPublicResponseDto(
                    name = it.name,
                    slug = it.slug,
                    description = it.description,
                    postCount = categoryRepository.countPublicPostsByCategoryId(it.id),
                )
            },
        )
    }

    /**
     * @description 创建分类（slug 缺省由名称生成，冲突报 9007）
     * @param dto 创建请求
     * @return CategoryAdminResponseDto 创建后的分类
     */
    @Transactional
    fun create(dto: CreateCategoryDto): CategoryAdminResponseDto {
        val slug = requireUniqueSlug(dto.slug, dto.name)
        val category = categoryRepository.save(
            BlogCategory(
                id = IdGenerator.nextId(),
                name = dto.name.trim(),
                slug = slug,
                description = dto.description?.trim()?.takeIf { it.isNotEmpty() },
                sort = dto.sort ?: 0,
                status = normalizeStatus(dto.status),
            ),
        )
        return CategoryAdminResponseDto.from(category, 0, 0)
    }

    /**
     * @description 更新分类（仅元数据；不影响存量文章）
     * @param id 分类 ID
     * @param dto 更新请求
     * @return CategoryAdminResponseDto 更新后的分类
     */
    @Transactional
    fun update(id: String, dto: UpdateCategoryDto): CategoryAdminResponseDto {
        val category = requireCategory(id)
        val newSlug = resolveSlugForUpdate(category, dto.slug, dto.name)
        category.apply {
            name = dto.name.trim()
            slug = newSlug
            description = dto.description?.trim()?.takeIf { it.isNotEmpty() }
            sort = dto.sort ?: 0
            status = normalizeStatus(dto.status)
            updatedAt = LocalDateTime.now()
        }
        categoryRepository.save(category)
        return CategoryAdminResponseDto.from(
            category,
            postRepository.countByCategoryIdAndDeletedAtIsNull(category.id),
            categoryRepository.countPublicPostsByCategoryId(category.id),
        )
    }

    /**
     * @description 删除分类（仍有未删除文章时拒绝，要求先迁移或改为未分类）
     * @param id 分类 ID
     */
    @Transactional
    fun delete(id: String) {
        requireCategory(id)
        val postCount = postRepository.countByCategoryIdAndDeletedAtIsNull(id)
        if (postCount > 0) {
            throw SystemException.businessRuleViolation("分类下仍有 $postCount 篇文章，请先迁移文章或改为未分类")
        }
        categoryRepository.deleteById(id)
    }

    /**
     * @description 查找分类，不存在抛 9002
     * @param id 分类 ID
     * @return BlogCategory 分类实体
     */
    private fun requireCategory(id: String): BlogCategory =
        categoryRepository.findById(id).orElse(null)
            ?: throw SystemException.dataNotFound("分类不存在")

    /**
     * @description 创建场景生成唯一 slug（缺省由名称派生；冲突报 9007）
     * @param requested 指定的 slug（可空）
     * @param name 分类名称
     * @return String 唯一 slug
     */
    private fun requireUniqueSlug(requested: String?, name: String): String {
        val candidate = slugify(requested?.takeIf { it.isNotBlank() } ?: name)
        if (categoryRepository.existsBySlug(candidate)) {
            throw SystemException.resourceExists("slug 已被占用")
        }
        return candidate
    }

    /**
     * @description 更新场景解析 slug（未变化沿用；变化时确保唯一）
     * @param category 分类实体
     * @param requested 请求的 slug
     * @param name 分类名称
     * @return String 唯一 slug
     */
    private fun resolveSlugForUpdate(category: BlogCategory, requested: String?, name: String): String {
        val candidate = slugify(requested?.takeIf { it.isNotBlank() } ?: name)
        if (candidate == category.slug) return category.slug
        if (categoryRepository.existsBySlug(candidate)) {
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
            .take(100)
        return cleaned.ifEmpty { "category-${IdGenerator.nextId().lowercase()}" }
    }

    /**
     * @description 归一化状态入参（缺省/非 0 一律视为启用）
     * @param status 请求的状态
     * @return Int 0 停用 / 1 启用
     */
    private fun normalizeStatus(status: Int?): Int = if (status == 0) 0 else 1
}
