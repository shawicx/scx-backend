package com.scx.backend.rbac.repository

import com.scx.backend.rbac.entity.DictData
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * @description 字典数据仓储
 */
@Repository
interface DictDataRepository : JpaRepository<DictData, String> {
    fun findByTypeIdOrderBySortAscCreatedAtAsc(typeId: String): List<DictData>
    fun findByTypeIdAndValue(typeId: String, value: String): DictData?
}
