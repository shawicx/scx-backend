package com.scx.backend.common.security

/**
 * 数据权限范围
 *
 * 角色级数据可见范围，多角色合并取最宽（按 width 比较）。token 嵌入后由
 * 网关注入 X-User-DataScope 头，下游服务据此做行级过滤。
 *
 * 当前仅开放 ALL / SELF 两档；DEPT / DEPT_AND_CHILD / CUSTOM 为部门
 * 体系预留（角色配置接口会拒绝），未上线前按 SELF 语义生效
 * （下游仅以 scope == ALL 放行）。
 *
 * @property width 宽度（越大可见范围越宽，用于多角色合并）
 */
enum class DataScope(val width: Int) {
    ALL(5),
    DEPT_AND_CHILD(4),
    DEPT(3),
    CUSTOM(2),
    SELF(1),
    ;

    companion object {
        /**
         * @description 按名称解析数据范围（大小写不敏感），未知或缺失回退 SELF（最小权限）
         * @param name 范围名称（如令牌 payload 或 roles."dataScope" 列的值）
         * @returns DataScope 解析结果
         *
         * @example DataScope.fromName("all") // ALL
         */
        fun fromName(name: String?): DataScope =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: SELF
    }
}
