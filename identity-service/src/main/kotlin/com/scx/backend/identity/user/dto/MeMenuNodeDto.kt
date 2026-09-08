package com.scx.backend.identity.user.dto

/**
 * 菜单树节点（当前登录用户可见的菜单）
 *
 * @description 前端侧边栏动态菜单节点；path 为空表示容器型父菜单（仅展开不跳转）
 */
data class MeMenuNodeDto(
    val id: String,
    val name: String,
    val path: String?,
    val icon: String?,
    val sort: Int,
    val children: List<MeMenuNodeDto> = emptyList(),
)
