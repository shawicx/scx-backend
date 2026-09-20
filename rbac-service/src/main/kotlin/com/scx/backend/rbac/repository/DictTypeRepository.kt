package com.scx.backend.rbac.repository

import com.scx.backend.rbac.entity.DictType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository

/**
 * @description 字典类型仓储
 */
@Repository
interface DictTypeRepository : JpaRepository<DictType, String>, JpaSpecificationExecutor<DictType> {
    fun findByName(name: String): DictType?
    fun findByCode(code: String): DictType?
}
