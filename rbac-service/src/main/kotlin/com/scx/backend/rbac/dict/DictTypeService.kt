package com.scx.backend.rbac.dict

import com.scx.backend.common.exception.SystemException
import com.scx.backend.common.util.IdGenerator
import com.scx.backend.rbac.dict.dto.CreateDictTypeDto
import com.scx.backend.rbac.dict.dto.DictTypeListResponseDto
import com.scx.backend.rbac.dict.dto.DictTypeResponseDto
import com.scx.backend.rbac.dict.dto.UpdateDictTypeDto
import com.scx.backend.rbac.entity.DictType
import com.scx.backend.rbac.repository.DictTypeRepository
import jakarta.persistence.criteria.Predicate
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * @description 字典类型服务：类型的增删改查与唯一性 / 系统内置保护规则
 */
@Service
class DictTypeService(
    private val dictTypeRepository: DictTypeRepository,
) {
    private val logger = LoggerFactory.getLogger(DictTypeService::class.java)

    /**
     * @description 创建字典类型（name/code 唯一；isSystem 仅由种子数据产生，不接受客户端设置）
     * @param dto 创建请求
     * @returns DictTypeResponseDto 创建后的类型
     * @throws SystemException 9007 名称或编码已存在
     */
    @Transactional
    fun create(dto: CreateDictTypeDto): DictTypeResponseDto {
        dictTypeRepository.findByName(dto.name)?.let {
            throw SystemException.resourceExists("Dict type with name '${dto.name}' already exists")
        }
        dictTypeRepository.findByCode(dto.code)?.let {
            throw SystemException.resourceExists("Dict type with code '${dto.code}' already exists")
        }
        val saved = dictTypeRepository.save(
            DictType(
                id = IdGenerator.nextId(),
                name = dto.name,
                code = dto.code,
                description = dto.description,
            ),
        )
        logger.info("Dict type created: {} ({})", saved.name, saved.code)
        return DictTypeResponseDto.from(saved)
    }

    /**
     * @description 分页查询字典类型（keyword 模糊匹配 name/code，status 可选过滤）
     * @param page 页码（从 1 开始）
     * @param limit 每页条数
     * @param keyword 关键字（name/code 不区分大小写模糊，可空）
     * @param status 状态过滤（1 启用 / 0 停用，可空）
     * @returns DictTypeListResponseDto 分页结果，按创建时间倒序
     *
     * @example dictTypeService.findAll(1, 10, "status", 1)
     */
    fun findAll(page: Int, limit: Int, keyword: String?, status: Int?): DictTypeListResponseDto {
        val spec = Specification<DictType> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()
            if (!keyword.isNullOrBlank()) {
                val like = "%${keyword.trim().lowercase()}%"
                predicates.add(
                    cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("code")), like),
                    ),
                )
            }
            status?.let { predicates.add(cb.equal(root.get<Int>("status"), it)) }
            cb.and(*predicates.toTypedArray())
        }
        val result = dictTypeRepository.findAll(
            spec,
            PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt")),
        )
        return DictTypeListResponseDto(
            list = result.content.map { DictTypeResponseDto.from(it) },
            total = result.totalElements,
            page = page,
            limit = limit,
        )
    }

    /**
     * @description 按 ID 查询字典类型
     * @param id 类型 ID
     * @returns DictTypeResponseDto 类型详情
     * @throws SystemException 9002 类型不存在
     */
    fun findById(id: String): DictTypeResponseDto {
        val type = dictTypeRepository.findById(id).orElseThrow {
            SystemException.dataNotFound("Dict type with ID '$id' not found")
        }
        return DictTypeResponseDto.from(type)
    }

    /**
     * @description 更新字典类型（code 不提供修改；name 冲突校验；系统内置类型允许改名称/描述/状态）
     * @param id 类型 ID
     * @param dto 更新请求（字段可空，空则不更新）
     * @returns DictTypeResponseDto 更新后的类型
     * @throws SystemException 9002 类型不存在；9007 名称已存在
     */
    @Transactional
    fun update(id: String, dto: UpdateDictTypeDto): DictTypeResponseDto {
        val type = dictTypeRepository.findById(id).orElseThrow {
            SystemException.dataNotFound("Dict type with ID '$id' not found")
        }
        dto.name?.let { newName ->
            if (newName != type.name) {
                dictTypeRepository.findByName(newName)?.let { existing ->
                    if (existing.id != id) {
                        throw SystemException.resourceExists("Dict type with name '$newName' already exists")
                    }
                }
            }
        }
        dto.name?.let { type.name = it }
        dto.description?.let { type.description = it }
        dto.status?.let { type.status = it }
        type.updatedAt = LocalDateTime.now()
        val updated = dictTypeRepository.save(type)
        logger.info("Dict type updated: {} ({})", updated.name, updated.code)
        return DictTypeResponseDto.from(updated)
    }

    /**
     * @description 删除字典类型（系统内置不可删；数据项经外键 ON DELETE CASCADE 级联删除）
     * @param id 类型 ID
     * @throws SystemException 9002 类型不存在；9012 系统内置类型不可删除
     */
    @Transactional
    fun delete(id: String) {
        val type = dictTypeRepository.findById(id).orElseThrow {
            SystemException.dataNotFound("Dict type with ID '$id' not found")
        }
        if (type.isSystem) {
            throw SystemException.businessRuleViolation("系统内置字典类型不可删除")
        }
        dictTypeRepository.delete(type)
        logger.info("Dict type deleted: {} ({})", type.name, type.code)
    }
}
