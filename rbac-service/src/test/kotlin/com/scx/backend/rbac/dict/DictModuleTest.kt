package com.scx.backend.rbac.dict

import com.scx.backend.common.exception.SystemException
import com.scx.backend.common.util.IdGenerator
import com.scx.backend.rbac.dict.dto.CreateDictDataDto
import com.scx.backend.rbac.dict.dto.CreateDictTypeDto
import com.scx.backend.rbac.dict.dto.UpdateDictDataDto
import com.scx.backend.rbac.dict.dto.UpdateDictTypeDto
import com.scx.backend.rbac.entity.DictType
import com.scx.backend.rbac.repository.DictDataRepository
import com.scx.backend.rbac.repository.DictTypeRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

/**
 * @description 字典业务规则测试：唯一性校验、系统内置保护、列表过滤（数据用例见 Task 3 追加）
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = [
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.url=jdbc:h2:mem:dictModule;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
    ],
)
class DictModuleTest(
    @Autowired private val dictTypeRepository: DictTypeRepository,
    @Autowired private val dictTypeService: DictTypeService,
    @Autowired private val dictDataRepository: DictDataRepository,
    @Autowired private val dictDataService: DictDataService,
) {
    @Test
    fun `create type rejects duplicate name`() {
        dictTypeService.create(CreateDictTypeDto(name = "性别", code = "gender_dup_name"))
        val ex = assertThrows(SystemException::class.java) {
            dictTypeService.create(CreateDictTypeDto(name = "性别", code = "other_code"))
        }
        assertEquals(9007, ex.code)
    }

    @Test
    fun `create type rejects duplicate code`() {
        dictTypeService.create(CreateDictTypeDto(name = "用户状态", code = "user_status_dup"))
        val ex = assertThrows(SystemException::class.java) {
            dictTypeService.create(CreateDictTypeDto(name = "另一类型", code = "user_status_dup"))
        }
        assertEquals(9007, ex.code)
    }

    @Test
    fun `update type checks name conflict`() {
        dictTypeService.create(CreateDictTypeDto(name = "类型甲", code = "type_a"))
        val b = dictTypeService.create(CreateDictTypeDto(name = "类型乙", code = "type_b"))
        val ex = assertThrows(SystemException::class.java) {
            dictTypeService.update(b.id, UpdateDictTypeDto(id = b.id, name = "类型甲"))
        }
        assertEquals(9007, ex.code)
    }

    @Test
    fun `update type can rename and disable but code unchanged`() {
        val t = dictTypeService.create(CreateDictTypeDto(name = "可更新类型", code = "updatable"))
        val updated = dictTypeService.update(t.id, UpdateDictTypeDto(id = t.id, name = "已改名类型", status = 0))
        assertEquals("已改名类型", updated.name)
        assertEquals(0, updated.status)
        assertEquals("updatable", updated.code)
    }

    @Test
    fun `delete system type is forbidden`() {
        val system = dictTypeRepository.save(
            DictType(id = IdGenerator.nextId(), name = "内置类型", code = "builtin").apply { isSystem = true },
        )
        val ex = assertThrows(SystemException::class.java) { dictTypeService.delete(system.id) }
        assertEquals(9012, ex.code)
    }

    @Test
    fun `list filters by keyword and status`() {
        dictTypeService.create(CreateDictTypeDto(name = "订单类型", code = "order_kind"))
        dictTypeService.create(CreateDictTypeDto(name = "支付方式", code = "pay_way"))
        val byKeyword = dictTypeService.findAll(1, 10, keyword = "订单", status = null)
        assertEquals(1, byKeyword.total)

        val disabled = dictTypeService.create(CreateDictTypeDto(name = "停用类型", code = "disabled_kind"))
        dictTypeService.update(disabled.id, UpdateDictTypeDto(id = disabled.id, status = 0))
        val enabledOnly = dictTypeService.findAll(1, 10, keyword = null, status = 1)
        assertTrue(enabledOnly.list.none { it.code == "disabled_kind" })
    }

    @Test
    fun `create data requires existing type`() {
        val ex = assertThrows(SystemException::class.java) {
            dictDataService.create(CreateDictDataDto(typeId = "01NOTEXISTTYPE0000000000000", label = "男", value = "M"))
        }
        assertEquals(9002, ex.code)
    }

    @Test
    fun `create data rejects duplicate value in same type`() {
        val t = dictTypeService.create(CreateDictTypeDto(name = "重复值类型", code = "dup_value"))
        dictDataService.create(CreateDictDataDto(typeId = t.id, label = "男", value = "M"))
        val ex = assertThrows(SystemException::class.java) {
            dictDataService.create(CreateDictDataDto(typeId = t.id, label = "男性", value = "M"))
        }
        assertEquals(9007, ex.code)
    }

    @Test
    fun `update data checks value conflict`() {
        val t = dictTypeService.create(CreateDictTypeDto(name = "更新冲突类型", code = "upd_conflict"))
        val m = dictDataService.create(CreateDictDataDto(typeId = t.id, label = "男", value = "M"))
        val f = dictDataService.create(CreateDictDataDto(typeId = t.id, label = "女", value = "F"))
        val ex = assertThrows(SystemException::class.java) {
            dictDataService.update(f.id, UpdateDictDataDto(id = f.id, value = "M"))
        }
        assertEquals(9007, ex.code)
        // 改成自己当前的值是允许的
        val same = dictDataService.update(m.id, UpdateDictDataDto(id = m.id, value = "M", label = "男性"))
        assertEquals("男性", same.label)
    }

    @Test
    fun `options by code filters disabled and sorts`() {
        val t = dictTypeService.create(CreateDictTypeDto(name = "优先级", code = "priority"))
        dictDataService.create(CreateDictDataDto(typeId = t.id, label = "高", value = "high", sort = 2))
        dictDataService.create(CreateDictDataDto(typeId = t.id, label = "低", value = "low", sort = 0))
        dictDataService.create(CreateDictDataDto(typeId = t.id, label = "中", value = "mid", sort = 1))
        val off = dictDataService.create(
            CreateDictDataDto(typeId = t.id, label = "停用项", value = "off", sort = -1, status = 0),
        )

        assertEquals(listOf("低", "中", "高"), dictDataService.getOptionsByCode("priority").map { it.label })
        dictDataService.delete(off.id)

        val emptyType = dictTypeService.create(CreateDictTypeDto(name = "停用取值类型", code = "off_type"))
        dictTypeService.update(emptyType.id, UpdateDictTypeDto(id = emptyType.id, status = 0))
        assertTrue(dictDataService.getOptionsByCode("off_type").isEmpty())

        val ex = assertThrows(SystemException::class.java) { dictDataService.getOptionsByCode("no_such_code") }
        assertEquals(9002, ex.code)
    }

    @Test
    fun `delete type removes its data`() {
        val t = dictTypeService.create(CreateDictTypeDto(name = "待删类型", code = "to_delete"))
        dictDataService.create(CreateDictDataDto(typeId = t.id, label = "甲", value = "1"))
        dictTypeService.delete(t.id)
        assertTrue(dictDataRepository.findByTypeIdOrderBySortAscCreatedAtAsc(t.id).isEmpty())
    }
}
