package com.scx.backend.rbac.dict

import com.scx.backend.common.dto.MessageDto
import com.scx.backend.commonaudit.annotation.OperationLog
import com.scx.backend.rbac.dict.dto.CreateDictDataDto
import com.scx.backend.rbac.dict.dto.DictDataOptionDto
import com.scx.backend.rbac.dict.dto.DictDataResponseDto
import com.scx.backend.rbac.dict.dto.UpdateDictDataDto
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
 * @description 字典数据控制器（管理端接口需管理员；by-code 取值登录即可）
 *
 * 路由前缀 /api/dicts/data（context-path=/api）。
 */
@Tag(name = "字典数据", description = "字典数据的增删改查与按类型编码取值")
@RestController
@RequestMapping("/dicts/data", produces = [MediaType.APPLICATION_JSON_VALUE])
class DictDataController(
    private val dictDataService: DictDataService,
) {
    @OperationLog(module = "字典管理", action = "创建字典数据")
    @Operation(summary = "创建字典数据", description = "在指定类型下新建数据项（同类型下 value 唯一）")
    @PostMapping("/create")
    fun create(@Valid @RequestBody dto: CreateDictDataDto, request: HttpServletRequest): DictDataResponseDto {
        AuthGuards.requireAdmin(request)
        return dictDataService.create(dto)
    }

    @Operation(summary = "类型下数据项列表", description = "返回类型下全部数据项（sort 升序），不分页")
    @GetMapping("/list")
    fun findByTypeId(
        request: HttpServletRequest,
        @Parameter(description = "字典类型 ID") @RequestParam typeId: String,
    ): List<DictDataResponseDto> {
        AuthGuards.requireAdmin(request)
        return dictDataService.findByTypeId(typeId)
    }

    @Operation(summary = "字典数据详情", description = "根据 ID 查询字典数据")
    @GetMapping("/detail")
    fun findById(
        request: HttpServletRequest,
        @Parameter(description = "字典数据 ID") @RequestParam id: String,
    ): DictDataResponseDto {
        AuthGuards.requireAdmin(request)
        return dictDataService.findById(id)
    }

    @OperationLog(module = "字典管理", action = "更新字典数据")
    @Operation(summary = "更新字典数据", description = "允许更新显示文本/存储值/排序/状态（typeId 不可修改，换类型需删除重建）")
    @PutMapping("/update")
    fun update(@Valid @RequestBody dto: UpdateDictDataDto, request: HttpServletRequest): DictDataResponseDto {
        AuthGuards.requireAdmin(request)
        return dictDataService.update(dto.id, dto)
    }

    @OperationLog(module = "字典管理", action = "删除字典数据")
    @Operation(summary = "删除字典数据", description = "根据 ID 删除字典数据")
    @DeleteMapping("/delete")
    fun delete(
        request: HttpServletRequest,
        @Parameter(description = "字典数据 ID") @RequestParam id: String,
    ): MessageDto {
        AuthGuards.requireAdmin(request)
        dictDataService.delete(id)
        return MessageDto("字典数据删除成功")
    }

    @Operation(summary = "按类型编码取值", description = "返回启用中类型下的启用数据项（label/value，sort 升序），供前端下拉渲染；登录即可访问")
    @GetMapping("/by-code")
    fun getByCode(
        request: HttpServletRequest,
        @Parameter(description = "字典编码") @RequestParam code: String,
    ): List<DictDataOptionDto> {
        AuthGuards.requireUserId(request)
        return dictDataService.getOptionsByCode(code)
    }
}
