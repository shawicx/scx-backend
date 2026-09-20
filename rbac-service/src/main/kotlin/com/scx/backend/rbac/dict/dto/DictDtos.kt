package com.scx.backend.rbac.dict.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.scx.backend.rbac.entity.DictData
import com.scx.backend.rbac.entity.DictType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/** 创建字典类型请求 */
@Schema(description = "创建字典类型请求")
data class CreateDictTypeDto(
    @Schema(description = "字典名称（1-100 字符，唯一）", requiredMode = Schema.RequiredMode.REQUIRED)
    @field:NotBlank(message = "字典名称不能为空")
    @field:Size(max = 100, message = "字典名称长度不能超过100个字符")
    val name: String,

    @Schema(description = "字典编码（小写字母开头，仅含小写字母/数字/下划线/中划线，唯一，创建后不可修改）", requiredMode = Schema.RequiredMode.REQUIRED)
    @field:NotBlank(message = "字典编码不能为空")
    @field:Size(max = 100, message = "字典编码长度不能超过100个字符")
    @field:Pattern(regexp = "^[a-z][a-z0-9_-]*$", message = "字典编码必须以小写字母开头，仅可含小写字母、数字、下划线、中划线")
    val code: String,

    @Schema(description = "描述（最长 255 字符）")
    @field:Size(max = 255, message = "描述长度不能超过255个字符")
    val description: String? = null,
)

/** 更新字典类型请求（ID 在 body 中，code 不可修改） */
@Schema(description = "更新字典类型请求")
data class UpdateDictTypeDto(
    @Schema(description = "字典类型 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @field:NotBlank(message = "字典类型ID不能为空")
    val id: String,

    @Schema(description = "字典名称（1-100 字符）")
    @field:Size(max = 100, message = "字典名称长度不能超过100个字符")
    val name: String? = null,

    @Schema(description = "描述（最长 255 字符）")
    @field:Size(max = 255, message = "描述长度不能超过255个字符")
    val description: String? = null,

    @Schema(description = "状态（1 启用 / 0 停用）")
    @field:Min(value = 0, message = "状态取值只能为0或1")
    @field:Max(value = 1, message = "状态取值只能为0或1")
    val status: Int? = null,
)

/** 字典类型列表响应 */
@Schema(description = "字典类型列表响应")
data class DictTypeListResponseDto(
    @Schema(description = "字典类型列表")
    val list: List<DictTypeResponseDto>,

    @Schema(description = "总数")
    val total: Long,

    @Schema(description = "当前页码")
    val page: Int,

    @Schema(description = "每页条数")
    val limit: Int,
)

/** 字典类型响应 */
@Schema(description = "字典类型响应")
data class DictTypeResponseDto(
    @Schema(description = "字典类型 ID")
    val id: String,

    @Schema(description = "字典名称")
    val name: String,

    @Schema(description = "字典编码")
    val code: String,

    @Schema(description = "描述")
    val description: String?,

    @Schema(description = "是否系统内置")
    @get:JsonProperty("isSystem")
    val isSystem: Boolean,

    @Schema(description = "状态（1 启用 / 0 停用）")
    val status: Int,

    @Schema(description = "创建时间")
    val createdAt: LocalDateTime,

    @Schema(description = "更新时间")
    val updatedAt: LocalDateTime,
) {
    companion object {
        /**
         * @description 由实体构造响应 DTO
         * @param entity 字典类型实体
         * @returns DictTypeResponseDto 响应 DTO
         */
        fun from(entity: DictType): DictTypeResponseDto = DictTypeResponseDto(
            id = entity.id,
            name = entity.name,
            code = entity.code,
            description = entity.description,
            isSystem = entity.isSystem,
            status = entity.status,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
    }
}

/** 创建字典数据请求 */
@Schema(description = "创建字典数据请求")
data class CreateDictDataDto(
    @Schema(description = "所属字典类型 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @field:NotBlank(message = "字典类型ID不能为空")
    val typeId: String,

    @Schema(description = "显示文本（1-100 字符）", requiredMode = Schema.RequiredMode.REQUIRED)
    @field:NotBlank(message = "显示文本不能为空")
    @field:Size(max = 100, message = "显示文本长度不能超过100个字符")
    val label: String,

    @Schema(description = "存储值（1-100 字符，同类型下唯一）", requiredMode = Schema.RequiredMode.REQUIRED)
    @field:NotBlank(message = "存储值不能为空")
    @field:Size(max = 100, message = "存储值长度不能超过100个字符")
    val value: String,

    @Schema(description = "排序号（升序，可空，缺省由服务端取 0）")
    val sort: Int? = null,

    @Schema(description = "状态（1 启用 / 0 停用，可空，缺省由服务端取 1）")
    @field:Min(value = 0, message = "状态取值只能为0或1")
    @field:Max(value = 1, message = "状态取值只能为0或1")
    val status: Int? = null,
)

/** 更新字典数据请求（ID 在 body 中，typeId 不可修改，换类型需删除重建） */
@Schema(description = "更新字典数据请求")
data class UpdateDictDataDto(
    @Schema(description = "字典数据 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @field:NotBlank(message = "字典数据ID不能为空")
    val id: String,

    @Schema(description = "显示文本（1-100 字符）")
    @field:Size(max = 100, message = "显示文本长度不能超过100个字符")
    val label: String? = null,

    @Schema(description = "存储值（1-100 字符，同类型下唯一）")
    @field:Size(max = 100, message = "存储值长度不能超过100个字符")
    val value: String? = null,

    @Schema(description = "排序号（升序）")
    val sort: Int? = null,

    @Schema(description = "状态（1 启用 / 0 停用）")
    @field:Min(value = 0, message = "状态取值只能为0或1")
    @field:Max(value = 1, message = "状态取值只能为0或1")
    val status: Int? = null,
)

/** 字典数据响应 */
@Schema(description = "字典数据响应")
data class DictDataResponseDto(
    @Schema(description = "字典数据 ID")
    val id: String,

    @Schema(description = "所属字典类型 ID")
    val typeId: String,

    @Schema(description = "显示文本")
    val label: String,

    @Schema(description = "存储值")
    val value: String,

    @Schema(description = "排序号")
    val sort: Int,

    @Schema(description = "状态（1 启用 / 0 停用）")
    val status: Int,

    @Schema(description = "创建时间")
    val createdAt: LocalDateTime,

    @Schema(description = "更新时间")
    val updatedAt: LocalDateTime,
) {
    companion object {
        /**
         * @description 由实体构造响应 DTO
         * @param entity 字典数据实体
         * @returns DictDataResponseDto 响应 DTO
         */
        fun from(entity: DictData): DictDataResponseDto = DictDataResponseDto(
            id = entity.id,
            typeId = entity.typeId,
            label = entity.label,
            value = entity.value,
            sort = entity.sort,
            status = entity.status,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
    }
}

/** 字典取值项（by-code 接口返回，供前端下拉渲染） */
@Schema(description = "字典取值项")
data class DictDataOptionDto(
    @Schema(description = "显示文本")
    val label: String,

    @Schema(description = "存储值")
    val value: String,
)
