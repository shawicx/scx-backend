package com.scx.backend.rbac.dict

import com.scx.backend.common.dto.MessageDto
import com.scx.backend.commonaudit.annotation.OperationLog
import com.scx.backend.rbac.dict.dto.CreateDictTypeDto
import com.scx.backend.rbac.dict.dto.DictTypeListResponseDto
import com.scx.backend.rbac.dict.dto.DictTypeResponseDto
import com.scx.backend.rbac.dict.dto.UpdateDictTypeDto
import com.scx.backend.rbac.security.AuthGuards
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * @description 字典类型控制器（管理端，需管理员）
 *
 * 路由前缀 /api/dicts/type（context-path=/api）；身份经网关注入的 X-User-* 头解析。
 */
@Tag(name = "字典类型", description = "字典类型的创建、查询、更新与删除（系统内置类型不可删除，code 创建后不可修改）")
@RestController
@RequestMapping("/dicts/type", produces = [MediaType.APPLICATION_JSON_VALUE])
class DictTypeController(
    private val dictTypeService: DictTypeService,
) {
    @OperationLog(module = "字典管理", action = "创建字典类型")
    @Operation(summary = "创建字典类型", description = "新建字典类型（name/code 唯一，code 创建后不可修改）")
    @PostMapping("/create")
    fun create(@Valid @RequestBody dto: CreateDictTypeDto, request: HttpServletRequest): DictTypeResponseDto {
        AuthGuards.requireAdmin(request)
        return dictTypeService.create(dto)
    }

    @Operation(summary = "字典类型分页列表", description = "按关键字（name/code 模糊）与状态筛选，创建时间倒序")
    @GetMapping("/list")
    fun findAll(
        request: HttpServletRequest,
        @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") limit: Int,
        @Parameter(description = "关键字（name/code 模糊）") @RequestParam(required = false) keyword: String?,
        @Parameter(description = "状态（1 启用 / 0 停用）") @RequestParam(required = false) status: Int?,
    ): DictTypeListResponseDto {
        AuthGuards.requireAdmin(request)
        return dictTypeService.findAll(page, limit, keyword, status)
    }

    @Operation(summary = "字典类型详情", description = "根据 ID 查询字典类型")
    @GetMapping("/detail")
    fun findById(
        request: HttpServletRequest,
        @Parameter(description = "字典类型 ID") @RequestParam id: String,
    ): DictTypeResponseDto {
        AuthGuards.requireAdmin(request)
        return dictTypeService.findById(id)
    }

    @OperationLog(module = "字典管理", action = "更新字典类型")
    @Operation(summary = "更新字典类型", description = "仅允许更新名称/描述/启用状态（code 不可修改）")
    @PutMapping("/update")
    fun update(@Valid @RequestBody dto: UpdateDictTypeDto, request: HttpServletRequest): DictTypeResponseDto {
        AuthGuards.requireAdmin(request)
        return dictTypeService.update(dto.id, dto)
    }

    @OperationLog(module = "字典管理", action = "删除字典类型")
    @Operation(summary = "删除字典类型", description = "系统内置类型不可删除；删除后类型下数据项级联删除")
    @DeleteMapping("/delete")
    fun delete(
        request: HttpServletRequest,
        @Parameter(description = "字典类型 ID") @RequestParam id: String,
    ): MessageDto {
        AuthGuards.requireAdmin(request)
        dictTypeService.delete(id)
        return MessageDto("字典类型删除成功")
    }
}
