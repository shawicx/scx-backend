package com.scx.backend.rbac.dict

import com.scx.backend.common.exception.SystemException
import com.scx.backend.common.util.IdGenerator
import com.scx.backend.rbac.dict.dto.CreateDictDataDto
import com.scx.backend.rbac.dict.dto.DictDataOptionDto
import com.scx.backend.rbac.dict.dto.DictDataResponseDto
import com.scx.backend.rbac.dict.dto.UpdateDictDataDto
import com.scx.backend.rbac.entity.DictData
import com.scx.backend.rbac.repository.DictDataRepository
import com.scx.backend.rbac.repository.DictTypeRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * @description 字典数据服务：数据项增删改查与按类型编码取值
 */
@Service
class DictDataService(
    private val dictTypeRepository: DictTypeRepository,
    private val dictDataRepository: DictDataRepository,
) {
    private val logger = LoggerFactory.getLogger(DictDataService::class.java)

    /**
     * @description 创建字典数据（同类型下 value 唯一）
     * @param dto 创建请求
     * @returns DictDataResponseDto 创建后的数据项
     * @throws SystemException 9002 类型不存在；9007 同类型下 value 已存在
     */
    @Transactional
    fun create(dto: CreateDictDataDto): DictDataResponseDto {
        requireTypeExists(dto.typeId)
        dictDataRepository.findByTypeIdAndValue(dto.typeId, dto.value)?.let {
            throw SystemException.resourceExists("Dict data with value '${dto.value}' already exists in this type")
        }
        val saved = dictDataRepository.save(
            DictData(
                id = IdGenerator.nextId(),
                typeId = dto.typeId,
                label = dto.label,
                value = dto.value,
                // 请求体可省略 sort/status（Jackson 3 对省略的非空原生类型报错，DTO 侧收为可空），服务端补默认值
                sort = dto.sort ?: 0,
                status = dto.status ?: 1,
            ),
        )
        logger.info("Dict data created: type={} value={}", dto.typeId, saved.value)
        return DictDataResponseDto.from(saved)
    }

    /**
     * @description 查询类型下全部数据项（sort 升序、创建时间升序，不分页）
     * @param typeId 类型 ID
     * @returns List<DictDataResponseDto> 数据项列表
     * @throws SystemException 9002 类型不存在
     */
    fun findByTypeId(typeId: String): List<DictDataResponseDto> {
        requireTypeExists(typeId)
        return dictDataRepository.findByTypeIdOrderBySortAscCreatedAtAsc(typeId).map { DictDataResponseDto.from(it) }
    }

    /**
     * @description 按 ID 查询字典数据
     * @param id 数据项 ID
     * @returns DictDataResponseDto 数据项详情
     * @throws SystemException 9002 数据项不存在
     */
    fun findById(id: String): DictDataResponseDto {
        val data = dictDataRepository.findById(id).orElseThrow {
            SystemException.dataNotFound("Dict data with ID '$id' not found")
        }
        return DictDataResponseDto.from(data)
    }

    /**
     * @description 更新字典数据（typeId 不可改；value 变更时做同类型唯一校验）
     * @param id 数据项 ID
     * @param dto 更新请求（字段可空，空则不更新）
     * @returns DictDataResponseDto 更新后的数据项
     * @throws SystemException 9002 数据项不存在；9007 同类型下 value 已存在
     */
    @Transactional
    fun update(id: String, dto: UpdateDictDataDto): DictDataResponseDto {
        val data = dictDataRepository.findById(id).orElseThrow {
            SystemException.dataNotFound("Dict data with ID '$id' not found")
        }
        dto.value?.let { newValue ->
            if (newValue != data.value) {
                dictDataRepository.findByTypeIdAndValue(data.typeId, newValue)?.let { existing ->
                    if (existing.id != id) {
                        throw SystemException.resourceExists("Dict data with value '$newValue' already exists in this type")
                    }
                }
            }
        }
        dto.label?.let { data.label = it }
        dto.value?.let { data.value = it }
        dto.sort?.let { data.sort = it }
        dto.status?.let { data.status = it }
        data.updatedAt = LocalDateTime.now()
        val updated = dictDataRepository.save(data)
        logger.info("Dict data updated: id={} value={}", updated.id, updated.value)
        return DictDataResponseDto.from(updated)
    }

    /**
     * @description 删除字典数据
     * @param id 数据项 ID
     * @throws SystemException 9002 数据项不存在
     */
    @Transactional
    fun delete(id: String) {
        val data = dictDataRepository.findById(id).orElseThrow {
            SystemException.dataNotFound("Dict data with ID '$id' not found")
        }
        dictDataRepository.delete(data)
        logger.info("Dict data deleted: id={} value={}", data.id, data.value)
    }

    /**
     * @description 按类型编码取启用数据项（供前端下拉渲染）
     *
     * 类型不存在抛 9002；类型停用返回空数组；仅返回 status=1 的数据项，按 sort 升序。
     *
     * @param code 字典编码
     * @returns List<DictDataOptionDto> 启用项（label/value）
     *
     * @example dictDataService.getOptionsByCode("user_status")
     */
    fun getOptionsByCode(code: String): List<DictDataOptionDto> {
        val type = dictTypeRepository.findByCode(code)
            ?: throw SystemException.dataNotFound("Dict type with code '$code' not found")
        if (type.status != 1) return emptyList()
        return dictDataRepository.findByTypeIdOrderBySortAscCreatedAtAsc(type.id)
            .filter { it.status == 1 }
            .map { DictDataOptionDto(label = it.label, value = it.value) }
    }

    /**
     * @description 校验字典类型存在，不存在抛数据未找到
     * @param typeId 类型 ID
     * @throws SystemException 9002 类型不存在
     */
    private fun requireTypeExists(typeId: String) {
        if (!dictTypeRepository.existsById(typeId)) {
            throw SystemException.dataNotFound("Dict type with ID '$typeId' not found")
        }
    }
}
