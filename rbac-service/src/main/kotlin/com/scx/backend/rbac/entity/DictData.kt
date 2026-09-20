package com.scx.backend.rbac.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

/**
 * 字典数据实体
 *
 * 通过 typeId 关联字典类型；删除类型时经外键 ON DELETE CASCADE 级联删除数据
 */
@Entity
// 与 V4 生产 DDL 一致：(typeId, value) 组合唯一，create-drop 测试建表同样生成该约束
@Table(
    name = "dict_data",
    uniqueConstraints = [UniqueConstraint(name = "dict_data_type_value_key", columnNames = ["\"typeId\"", "\"value\""])],
)
class DictData(
    @Id
    @Column(name = "id", length = 30, nullable = false)
    var id: String,

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "\"typeId\"", columnDefinition = "char(26)", nullable = false)
    var typeId: String,

    @Column(name = "label", length = 100, nullable = false)
    var label: String,

    // value 为 H2 保留字，生成 SQL 需带引号；PG 侧未加引号列名同为小写 value，与 V4 DDL 一致
    @Column(name = "\"value\"", length = 100, nullable = false)
    var value: String,

    @Column(name = "sort", nullable = false)
    var sort: Int = 0,

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "status", columnDefinition = "smallint", nullable = false)
    var status: Int = 1,

    @Column(name = "\"createdAt\"", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "\"updatedAt\"", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    /**
     * 只读关联（写入走 typeId 字段）；@OnDelete 使测试环境 create-drop 建表时
     * 生成的外键同样带 ON DELETE CASCADE，与 V4 生产 DDL 行为一致
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"typeId\"", referencedColumnName = "id", insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    var type: DictType? = null
}
