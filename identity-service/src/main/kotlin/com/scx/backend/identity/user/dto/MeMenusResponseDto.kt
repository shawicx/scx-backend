package com.scx.backend.identity.user.dto

/**
 * @description 当前登录用户的菜单树与按钮权限点集合
 */
data class MeMenusResponseDto(
    /** 可见菜单树（type=MENU、status=1、visible=1） */
    val menus: List<MeMenuNodeDto>,
    /** 按钮权限点，格式 resource:action（如 user:delete）；管理员为通配 ["*"] */
    val permissions: List<String>,
)
