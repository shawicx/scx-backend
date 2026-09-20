package com.scx.backend.rbac

import com.scx.backend.common.util.IdGenerator
import com.scx.backend.rbac.entity.DictData
import com.scx.backend.rbac.entity.DictType
import com.scx.backend.rbac.repository.DictDataRepository
import com.scx.backend.rbac.repository.DictTypeRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.TestPropertySource

/**
 * @description 字典数据层基座测试（H2 内存库，实体由 ddl-auto=create-drop 建表）
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = [
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.url=jdbc:h2:mem:dictData;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
    ],
)
class DictDataLayerTest(
    @Autowired private val dictTypeRepository: DictTypeRepository,
    @Autowired private val dictDataRepository: DictDataRepository,
) {
    /**
     * @description 构造测试用字典类型
     */
    private fun newType(code: String) = DictType(
        id = IdGenerator.nextId(),
        name = "类型-$code",
        code = code,
    )

    /**
     * @description 构造测试用字典数据
     */
    private fun newData(typeId: String, value: String, sort: Int = 0) = DictData(
        id = IdGenerator.nextId(),
        typeId = typeId,
        label = "标签-$value",
        value = value,
        sort = sort,
    )

    @Test
    fun `dict type save and findByCode roundtrip`() {
        val type = dictTypeRepository.save(newType("roundtrip"))
        val found = dictTypeRepository.findByCode("roundtrip")
        assertNotNull(found)
        assertEquals(type.id, found!!.id)
        assertEquals(1, found.status)
        assertEquals(false, found.isSystem)
    }

    @Test
    fun `dict data save and findByTypeId ordered`() {
        val type = dictTypeRepository.save(newType("ordered"))
        dictDataRepository.save(newData(type.id, "B", sort = 2))
        dictDataRepository.save(newData(type.id, "A", sort = 1))
        val list = dictDataRepository.findByTypeIdOrderBySortAscCreatedAtAsc(type.id)
        assertEquals(listOf("标签-A", "标签-B"), list.map { it.label })
    }

    @Test
    fun `duplicate value within same type violates unique constraint`() {
        val type = dictTypeRepository.save(newType("uniq"))
        dictDataRepository.saveAndFlush(newData(type.id, "A"))
        assertThrows(DataIntegrityViolationException::class.java) {
            dictDataRepository.saveAndFlush(newData(type.id, "A"))
        }
    }

    @Test
    fun `deleting type cascades to its data`() {
        val type = dictTypeRepository.save(newType("cascade"))
        dictDataRepository.save(newData(type.id, "X1", sort = 2))
        dictDataRepository.save(newData(type.id, "X2", sort = 1))
        dictTypeRepository.delete(type)
        assertTrue(dictDataRepository.findByTypeIdOrderBySortAscCreatedAtAsc(type.id).isEmpty())
    }
}
