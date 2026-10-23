package com.scx.backend.blog.post

import com.scx.backend.blog.entity.BlogCategory
import com.scx.backend.blog.entity.BlogPost
import com.scx.backend.blog.entity.BlogPostStats
import com.scx.backend.blog.entity.BlogPostTag
import com.scx.backend.blog.entity.BlogTag
import com.scx.backend.blog.post.dto.AdminPostQueryDto
import com.scx.backend.blog.post.dto.AdjacentPostDto
import com.scx.backend.blog.post.dto.CategoryBriefDto
import com.scx.backend.blog.post.dto.CreatePostDto
import com.scx.backend.blog.post.dto.PostAdminListItemDto
import com.scx.backend.blog.post.dto.PostAdminListResponseDto
import com.scx.backend.blog.post.dto.PostDetailResponseDto
import com.scx.backend.blog.post.dto.PostPublicDetailDto
import com.scx.backend.blog.post.dto.PostPublicListItemDto
import com.scx.backend.blog.post.dto.PostPublicListResponseDto
import com.scx.backend.blog.post.dto.PublicPostQueryDto
import com.scx.backend.blog.post.dto.RelatedPostDto
import com.scx.backend.blog.post.dto.RelatedPostsResponseDto
import com.scx.backend.blog.post.dto.SetPostTopDto
import com.scx.backend.blog.post.dto.TagBriefDto
import com.scx.backend.blog.post.dto.UpdatePostDto
import com.scx.backend.blog.repository.BlogCategoryRepository
import com.scx.backend.blog.repository.BlogPostLikeRepository
import com.scx.backend.blog.repository.BlogPostRepository
import com.scx.backend.blog.repository.BlogPostStatsRepository
import com.scx.backend.blog.repository.BlogPostTagRepository
import com.scx.backend.blog.repository.BlogTagRepository
import com.scx.backend.common.exception.SystemException
import com.scx.backend.common.util.IdGenerator
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Subquery
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * @description 博客文章服务（管理端 CRUD / 状态流转 + 公开列表 / 详情 / 相关文章）
 *
 * 状态流转：草稿(0) ⇄ 已发布(1) → 已归档(2)；软删除独立于状态机。
 * 公开可见 = status=1 且 visibility=0 且未删除且 publishedAt 已到；
 * 标签关联在事务内整体替换；文章创建时初始化统计行。
 */
@Service
class BlogPostService(
    private val postRepository: BlogPostRepository,
    private val categoryRepository: BlogCategoryRepository,
    private val tagRepository: BlogTagRepository,
    private val postTagRepository: BlogPostTagRepository,
    private val statsRepository: BlogPostStatsRepository,
    private val likeRepository: BlogPostLikeRepository,
) {

    /** 管理端列表排序字段白名单（防任意属性排序） */
    private val adminSortFields = setOf("createdAt", "updatedAt", "publishedAt", "title")

    /**
     * @description 管理端文章分页列表（含草稿/私密/归档）
     * @param dto 查询参数
     * @return PostAdminListResponseDto 分页列表
     */
    @Transactional(readOnly = true)
    fun adminList(dto: AdminPostQueryDto): PostAdminListResponseDto {
        val sortField = if (dto.sortBy in adminSortFields) dto.sortBy else "createdAt"
        val direction = if (dto.sortOrder.equals("asc", ignoreCase = true)) Sort.Direction.ASC else Sort.Direction.DESC
        val pageable = PageRequest.of(dto.page - 1, dto.limit, Sort.by(direction, sortField))

        val spec = Specification<BlogPost> { root, query, cb ->
            val predicates = mutableListOf<Predicate>()
            predicates.add(cb.isNull(root.get<LocalDateTime>("deletedAt")))
            dto.search?.takeIf { it.isNotBlank() }?.let { kw ->
                val pattern = "%${escapeLike(kw.lowercase())}%"
                cb.or(
                    cb.like(cb.lower(root.get("title")), pattern, ESCAPE_CHAR),
                    cb.like(cb.lower(root.get("slug")), pattern, ESCAPE_CHAR),
                )
            }?.let { predicates.add(it) }
            dto.status?.let { predicates.add(cb.equal(root.get<Int>("status"), it)) }
            dto.categoryId?.takeIf { it.isNotBlank() }?.let {
                predicates.add(cb.equal(root.get<String>("categoryId"), it))
            }
            dto.isTop?.let { predicates.add(cb.equal(root.get<Boolean>("isTop"), it)) }
            query.distinct(true)
            cb.and(*predicates.toTypedArray())
        }

        val page = postRepository.findAll(spec, pageable)
        return PostAdminListResponseDto(
            list = toAdminListItems(page.content),
            total = page.totalElements,
            page = dto.page,
            limit = dto.limit,
        )
    }

    /**
     * @description 管理端文章详情（编辑回显）
     * @param id 文章 ID
     * @return PostDetailResponseDto 详情
     */
    @Transactional(readOnly = true)
    fun adminDetail(id: String): PostDetailResponseDto {
        val post = requirePost(id)
        return PostDetailResponseDto.from(post, postTagRepository.findByPostId(id).map { it.tagId })
    }

    /**
     * @description 创建文章（初始为草稿；slug 缺省由标题生成，冲突自动加序号）
     * @param dto 创建请求
     * @param authorId 创建管理员 ID（网关注入）
     * @return PostDetailResponseDto 创建后的详情
     */
    @Transactional
    fun create(dto: CreatePostDto, authorId: String): PostDetailResponseDto {
        val category = dto.categoryId?.takeIf { it.isNotBlank() }?.let { requireEnabledCategory(it) }
        val tags = requireTags(dto.tagIds.orEmpty())

        val post = postRepository.save(
            BlogPost(
                id = IdGenerator.nextId(),
                title = dto.title.trim(),
                slug = resolveUniqueSlug(dto.slug, dto.title),
                summary = dto.summary?.trim()?.takeIf { it.isNotEmpty() },
                content = dto.content,
                coverFileId = dto.coverFileId?.takeIf { it.isNotBlank() },
                categoryId = category?.id,
                authorId = authorId,
                status = BlogPost.STATUS_DRAFT,
                visibility = normalizeVisibility(dto.visibility),
                isTop = dto.isTop ?: false,
                allowComment = dto.allowComment ?: true,
                seoTitle = dto.seoTitle?.trim()?.takeIf { it.isNotEmpty() },
                seoDescription = dto.seoDescription?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
        replaceTags(post.id, tags)
        statsRepository.save(BlogPostStats(postId = post.id))
        return PostDetailResponseDto.from(post, tags.map { it.id })
    }

    /**
     * @description 更新文章（不改变发布状态；slug 变更需管理员自行承担旧链接处理）
     * @param id 文章 ID
     * @param dto 更新请求
     * @return PostDetailResponseDto 更新后的详情
     */
    @Transactional
    fun update(id: String, dto: UpdatePostDto): PostDetailResponseDto {
        val post = requirePost(id)
        val category = dto.categoryId?.takeIf { it.isNotBlank() }?.let { requireEnabledCategory(it) }
        val tags = requireTags(dto.tagIds.orEmpty())

        val newSlug = resolveSlugForUpdate(post, dto.slug, dto.title)
        post.apply {
            title = dto.title.trim()
            slug = newSlug
            summary = dto.summary?.trim()?.takeIf { it.isNotEmpty() }
            content = dto.content
            coverFileId = dto.coverFileId?.takeIf { it.isNotBlank() }
            categoryId = category?.id
            visibility = normalizeVisibility(dto.visibility)
            isTop = dto.isTop ?: false
            allowComment = dto.allowComment ?: true
            seoTitle = dto.seoTitle?.trim()?.takeIf { it.isNotEmpty() }
            seoDescription = dto.seoDescription?.trim()?.takeIf { it.isNotEmpty() }
            updatedAt = LocalDateTime.now()
        }
        postRepository.save(post)
        replaceTags(post.id, tags)
        return PostDetailResponseDto.from(post, tags.map { it.id })
    }

    /**
     * @description 发布文章（草稿/归档 → 已发布；首次发布落发布时间）
     * @param id 文章 ID
     * @return PostDetailResponseDto 发布后的详情
     */
    @Transactional
    fun publish(id: String): PostDetailResponseDto = transition(id) { post ->
        post.status = BlogPost.STATUS_PUBLISHED
        if (post.publishedAt == null) post.publishedAt = LocalDateTime.now()
    }

    /**
     * @description 撤回发布（已发布 → 草稿；保留首次发布时间）
     * @param id 文章 ID
     * @return PostDetailResponseDto 撤回后的详情
     */
    @Transactional
    fun withdraw(id: String): PostDetailResponseDto = transition(id) { post ->
        if (post.status != BlogPost.STATUS_PUBLISHED) {
            throw SystemException.businessRuleViolation("仅已发布文章可以撤回发布")
        }
        post.status = BlogPost.STATUS_DRAFT
    }

    /**
     * @description 归档文章（任意状态 → 已归档；公开接口不再可见）
     * @param id 文章 ID
     * @return PostDetailResponseDto 归档后的详情
     */
    @Transactional
    fun archive(id: String): PostDetailResponseDto = transition(id) { post ->
        post.status = BlogPost.STATUS_ARCHIVED
    }

    /**
     * @description 设置文章置顶
     * @param id 文章 ID
     * @param dto 置顶设置
     * @return PostDetailResponseDto 设置后的详情
     */
    @Transactional
    fun setTop(id: String, dto: SetPostTopDto): PostDetailResponseDto = transition(id) { post ->
        post.isTop = dto.isTop ?: false
    }

    /**
     * @description 删除文章（软删除；slug 保留占用，公开接口按 404 处理）
     * @param id 文章 ID
     */
    @Transactional
    fun delete(id: String) {
        val post = requirePost(id)
        val now = LocalDateTime.now()
        post.deletedAt = now
        post.updatedAt = now
        postRepository.save(post)
    }

    /**
     * @description 公开文章分页列表（仅已发布公开文章；置顶优先、发布时间倒序）
     * @param dto 查询参数（分类/标签 slug、关键字）
     * @return PostPublicListResponseDto 分页列表
     */
    @Transactional(readOnly = true)
    fun publicList(dto: PublicPostQueryDto): PostPublicListResponseDto {
        val category = dto.categorySlug?.takeIf { it.isNotBlank() }?.let {
            categoryRepository.findFirstBySlug(it)
                ?: throw SystemException.dataNotFound("分类不存在")
        }
        val tag = dto.tagSlug?.takeIf { it.isNotBlank() }?.let {
            tagRepository.findFirstBySlug(it)
                ?: throw SystemException.dataNotFound("标签不存在")
        }

        val spec = Specification<BlogPost> { root, query, cb ->
            val predicates = mutableListOf<Predicate>()
            predicates.add(cb.isNull(root.get<LocalDateTime>("deletedAt")))
            predicates.add(cb.equal(root.get<Int>("status"), BlogPost.STATUS_PUBLISHED))
            predicates.add(cb.equal(root.get<Int>("visibility"), BlogPost.VISIBILITY_PUBLIC))
            predicates.add(cb.lessThanOrEqualTo(root.get("publishedAt"), LocalDateTime.now()))
            category?.let { predicates.add(cb.equal(root.get<String>("categoryId"), it.id)) }
            tag?.let { tagEntity ->
                val sub = query.subquery(String::class.java)
                val pt = sub.from(BlogPostTag::class.java)
                sub.select(pt.get<String>("postId"))
                sub.where(cb.equal(pt.get<String>("tagId"), tagEntity.id))
                predicates.add(cb.`in`(root.get<String>("id")).value(sub))
            }
            dto.search?.takeIf { it.isNotBlank() }?.let { kw ->
                val pattern = "%${escapeLike(kw.lowercase())}%"
                cb.or(
                    cb.like(cb.lower(root.get("title")), pattern, ESCAPE_CHAR),
                    cb.like(cb.lower(root.get("summary")), pattern, ESCAPE_CHAR),
                )
            }?.let { predicates.add(it) }
            cb.and(*predicates.toTypedArray())
        }

        val pageable: Pageable = PageRequest.of(
            dto.page - 1,
            dto.limit,
            Sort.by(Sort.Direction.DESC, "isTop").and(Sort.by(Sort.Direction.DESC, "publishedAt")),
        )
        val page = postRepository.findAll(spec, pageable)
        return PostPublicListResponseDto(
            list = toPublicListItems(page.content),
            total = page.totalElements,
            page = dto.page,
            limit = dto.limit,
        )
    }

    /**
     * @description 公开文章详情（草稿/私密/归档/不存在统一按 404 处理）
     * @param slug URL 标识
     * @return PostPublicDetailDto 详情
     */
    @Transactional(readOnly = true)
    fun publicDetail(slug: String): PostPublicDetailDto {
        val post = requirePublicPost(slug)
        val publishedAt = requireNotNull(post.publishedAt)

        val stats = statsRepository.findById(post.id).orElse(null)
        val prev = postRepository.findPrevPublic(publishedAt, post.id, PageRequest.of(0, 1))
            .firstOrNull()
            ?.let { AdjacentPostDto(slug = it.slug, title = it.title) }
        val next = postRepository.findNextPublic(publishedAt, post.id, PageRequest.of(0, 1))
            .firstOrNull()
            ?.let { AdjacentPostDto(slug = it.slug, title = it.title) }

        return PostPublicDetailDto(
            title = post.title,
            slug = post.slug,
            summary = post.summary,
            content = post.content,
            coverFileId = post.coverFileId,
            category = post.categoryId?.let { id -> categoryRepository.findById(id).orElse(null) }?.let { toBrief(it) },
            tags = loadTagBriefs(post.id),
            publishedAt = publishedAt,
            updatedAt = post.updatedAt,
            viewCount = stats?.viewCount ?: 0,
            likeCount = stats?.likeCount ?: 0,
            commentCount = stats?.commentCount ?: 0,
            allowComment = post.allowComment,
            seoTitle = post.seoTitle,
            seoDescription = post.seoDescription,
            prev = prev,
            next = next,
        )
    }

    /**
     * @description 相关文章（同分类公开文章，无分类时回退最新公开文章）
     * @param slug URL 标识
     * @return RelatedPostsResponseDto 相关文章列表
     */
    @Transactional(readOnly = true)
    fun related(slug: String): RelatedPostsResponseDto {
        val post = postRepository.findFirstBySlugAndDeletedAtIsNull(slug)
            ?: throw SystemException.dataNotFound("文章不存在")
        val related = post.categoryId
            ?.let { categoryId ->
                postRepository.findRelatedByCategory(categoryId, post.id, PageRequest.of(0, RELATED_LIMIT))
            }
            ?.takeIf { it.isNotEmpty() }
            ?: postRepository.findAll(
                Specification<BlogPost> { root, _, cb ->
                    cb.and(
                        cb.notEqual(root.get<String>("id"), post.id),
                        cb.isNull(root.get<LocalDateTime>("deletedAt")),
                        cb.equal(root.get<Int>("status"), BlogPost.STATUS_PUBLISHED),
                        cb.equal(root.get<Int>("visibility"), BlogPost.VISIBILITY_PUBLIC),
                    )
                },
                PageRequest.of(0, RELATED_LIMIT, Sort.by(Sort.Direction.DESC, "publishedAt")),
            ).content
        return RelatedPostsResponseDto(list = related.map { RelatedPostDto(it.slug, it.title, it.summary, it.publishedAt) })
    }

    /**
     * @description 按条件查公开可见的实体文章（评论/互动服务校验目标文章用）
     * @param slug URL 标识
     * @return BlogPost 公开可见的文章实体
     */
    @Transactional(readOnly = true)
    fun requirePublicPost(slug: String): BlogPost {
        val post = postRepository.findFirstBySlugAndDeletedAtIsNull(slug)
            ?: throw SystemException.dataNotFound("文章不存在")
        val publishedAt = post.publishedAt
        if (post.status != BlogPost.STATUS_PUBLISHED ||
            post.visibility != BlogPost.VISIBILITY_PUBLIC ||
            publishedAt == null || publishedAt.isAfter(LocalDateTime.now())
        ) {
            throw SystemException.dataNotFound("文章不存在")
        }
        return post
    }

    /**
     * @description 执行状态流转并返回详情
     * @param id 文章 ID
     * @param mutate 变更函数
     * @return PostDetailResponseDto 变更后的详情
     */
    private fun transition(id: String, mutate: (BlogPost) -> Unit): PostDetailResponseDto {
        val post = requirePost(id)
        mutate(post)
        post.updatedAt = LocalDateTime.now()
        postRepository.save(post)
        return PostDetailResponseDto.from(post, postTagRepository.findByPostId(id).map { it.tagId })
    }

    /**
     * @description 查找未删除文章，不存在抛 9002
     * @param id 文章 ID
     * @return BlogPost 文章实体
     */
    private fun requirePost(id: String): BlogPost =
        postRepository.findFirstByIdAndDeletedAtIsNull(id)
            ?: throw SystemException.dataNotFound("文章不存在")

    /**
     * @description 校验分类存在且启用（新文章不允许选择停用分类）
     * @param categoryId 分类 ID
     * @return BlogCategory 分类实体
     */
    private fun requireEnabledCategory(categoryId: String): BlogCategory =
        categoryRepository.findById(categoryId).orElse(null)?.takeIf { it.status == 1 }
            ?: throw SystemException.invalidParameter("分类不存在或已停用")

    /**
     * @description 校验标签列表存在、启用并去重
     * @param tagIds 标签 ID 列表
     * @return List<BlogTag> 去重后的标签实体
     */
    private fun requireTags(tagIds: List<String>): List<BlogTag> {
        if (tagIds.isEmpty()) return emptyList()
        val distinctIds = tagIds.filter { it.isNotBlank() }.distinct()
        val tags = tagRepository.findAllById(distinctIds)
        if (tags.size != distinctIds.size || tags.any { it.status != 1 }) {
            throw SystemException.invalidParameter("存在无效或已停用的标签")
        }
        return tags
    }

    /**
     * @description 整体替换文章标签关联
     * @param postId 文章 ID
     * @param tags 目标标签实体列表
     */
    private fun replaceTags(postId: String, tags: List<BlogTag>) {
        postTagRepository.deleteByPostId(postId)
        tags.forEach { tag ->
            postTagRepository.save(BlogPostTag(postId = postId, tagId = tag.id))
        }
    }

    /**
     * @description 创建场景生成唯一 slug（缺省由标题派生；冲突追加 -2/-3…序号）
     * @param requested 管理员指定的 slug（可空）
     * @param title 文章标题
     * @return String 唯一 slug
     */
    private fun resolveUniqueSlug(requested: String?, title: String): String {
        val base = slugify(requested?.takeIf { it.isNotBlank() } ?: title)
        if (!postRepository.existsBySlugAndDeletedAtIsNull(base)) return base
        for (seq in 2..50) {
            val candidate = "$base-$seq"
            if (!postRepository.existsBySlugAndDeletedAtIsNull(candidate)) return candidate
        }
        throw SystemException.resourceExists("slug 已被占用，请手动指定其他 slug")
    }

    /**
     * @description 更新场景解析 slug（未变化直接沿用；变化时确保唯一）
     * @param post 文章实体
     * @param requested 请求的 slug
     * @param title 文章标题
     * @return String 唯一 slug
     */
    private fun resolveSlugForUpdate(post: BlogPost, requested: String?, title: String): String {
        val candidate = slugify(requested?.takeIf { it.isNotBlank() } ?: title)
        if (candidate == post.slug) return post.slug
        if (postRepository.existsBySlugAndDeletedAtIsNull(candidate)) {
            throw SystemException.resourceExists("slug 已被占用")
        }
        return candidate
    }

    /**
     * @description 由标题/指定值生成规范 slug（纯中文等无法派生时回退 ULID）
     * @param source 输入字符串
     * @return String 规范化 slug（小写字母/数字/连字符）
     */
    private fun slugify(source: String): String {
        val cleaned = source.trim().lowercase()
            .replace(Regex("\\s+"), "-")
            .replace(Regex("[^a-z0-9-]"), "")
            .replace(Regex("-{2,}"), "-")
            .trim('-')
            .take(200)
        return cleaned.ifEmpty { "post-${IdGenerator.nextId().lowercase()}" }
    }

    /**
     * @description 归一化可见性入参（缺省/非法值一律视为公开）
     * @param visibility 请求的可见性
     * @return Int 0 公开 / 1 私密
     */
    private fun normalizeVisibility(visibility: Int?): Int =
        if (visibility == BlogPost.VISIBILITY_PRIVATE) BlogPost.VISIBILITY_PRIVATE else BlogPost.VISIBILITY_PUBLIC

    /**
     * @description 批量组装管理端列表项（分类/标签/统计一次查齐避免 N+1）
     * @param posts 文章实体列表
     * @return List<PostAdminListItemDto> 列表项
     */
    private fun toAdminListItems(posts: List<BlogPost>): List<PostAdminListItemDto> {
        if (posts.isEmpty()) return emptyList()
        val categories = loadCategoryMap(posts)
        val tagsByPost = loadTagBriefMap(posts.map { it.id })
        val statsById = statsRepository.findAllById(posts.map { it.id }).associateBy { it.postId }
        return posts.map { post ->
            val stats = statsById[post.id]
            PostAdminListItemDto(
                id = post.id,
                title = post.title,
                slug = post.slug,
                category = post.categoryId?.let { categories[it] },
                tags = tagsByPost[post.id].orEmpty(),
                status = post.status,
                visibility = post.visibility,
                isTop = post.isTop,
                allowComment = post.allowComment,
                publishedAt = post.publishedAt,
                viewCount = stats?.viewCount ?: 0,
                likeCount = stats?.likeCount ?: 0,
                commentCount = stats?.commentCount ?: 0,
                createdAt = post.createdAt,
                updatedAt = post.updatedAt,
            )
        }
    }

    /**
     * @description 批量组装公开列表项
     * @param posts 公开文章实体列表
     * @return List<PostPublicListItemDto> 列表项
     */
    private fun toPublicListItems(posts: List<BlogPost>): List<PostPublicListItemDto> {
        if (posts.isEmpty()) return emptyList()
        val categories = loadCategoryMap(posts)
        val tagsByPost = loadTagBriefMap(posts.map { it.id })
        val statsById = statsRepository.findAllById(posts.map { it.id }).associateBy { it.postId }
        return posts.map { post ->
            val stats = statsById[post.id]
            PostPublicListItemDto(
                id = post.id,
                title = post.title,
                slug = post.slug,
                summary = post.summary,
                coverFileId = post.coverFileId,
                category = post.categoryId?.let { categories[it] },
                tags = tagsByPost[post.id].orEmpty(),
                isTop = post.isTop,
                publishedAt = post.publishedAt,
                viewCount = stats?.viewCount ?: 0,
                likeCount = stats?.likeCount ?: 0,
                commentCount = stats?.commentCount ?: 0,
            )
        }
    }

    /**
     * @description 批量查分类并转为简要 DTO 映射
     * @param posts 文章列表
     * @return Map<String, CategoryBriefDto> 分类 ID → 简要信息
     */
    private fun loadCategoryMap(posts: List<BlogPost>): Map<String, CategoryBriefDto> =
        posts.mapNotNull { it.categoryId }.distinct()
            .let { ids -> categoryRepository.findAllById(ids) }
            .associate { it.id to toBrief(it) }

    /**
     * @description 批量查文章标签并转为简要 DTO 分组
     * @param postIds 文章 ID 列表
     * @return Map<String, List<TagBriefDto>> 文章 ID → 标签列表
     */
    private fun loadTagBriefMap(postIds: List<String>): Map<String, List<TagBriefDto>> {
        if (postIds.isEmpty()) return emptyMap()
        val byPost = postIds.associateWith { postTagRepository.findByPostId(it) }
        val tagIds = byPost.values.flatten().map { it.tagId }.distinct()
        if (tagIds.isEmpty()) return emptyMap()
        val tags = tagRepository.findAllById(tagIds).associateBy { it.id }
        return byPost.mapValues { (_, list) ->
            list.mapNotNull { tags[it.tagId] }.map { TagBriefDto(it.id, it.name, it.slug) }
        }
    }

    /**
     * @description 查单篇文章的标签简要列表
     * @param postId 文章 ID
     * @return List<TagBriefDto> 标签列表
     */
    private fun loadTagBriefs(postId: String): List<TagBriefDto> =
        loadTagBriefMap(listOf(postId))[postId].orEmpty()

    /**
     * @description 分类实体转简要 DTO
     * @param category 分类实体
     * @return CategoryBriefDto 简要信息
     */
    private fun toBrief(category: BlogCategory): CategoryBriefDto =
        CategoryBriefDto(category.id, category.name, category.slug)

    /**
     * @description LIKE 通配符转义（配合 ESCAPE_CHAR 使用）
     * @param keyword 原始关键字
     * @return String 转义后的关键字
     */
    private fun escapeLike(keyword: String): String =
        keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    companion object {
        private const val ESCAPE_CHAR = '\\'
        private const val RELATED_LIMIT = 5
    }
}
