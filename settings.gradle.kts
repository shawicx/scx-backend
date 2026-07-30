rootProject.name = "scx-backend"

pluginManagement {
    repositories {
        // 优先 Gradle Plugin Portal，回退 Maven Central。
        // 本环境下 Plugin Portal 的部分 marker artifact jar 返回 404，
        // 增加 mavenCentral() 作为回退源以保证 Kotlin/Spring 插件可解析。
        gradlePluginPortal()
        mavenCentral()
    }
}

// ============================================================
// 多模块结构（微服务化）
// ============================================================
// - app                 过渡用单体模块（承接原 scx-backend 全部源码，逐步拆解后删除）
// - common              共享契约模块（响应封装/异常/ULID/令牌工具/注解）
// - gateway             API 网关（集中鉴权 + 路由）
// - identity-service    身份认证服务（用户/登录/令牌）
// - rbac-service        角色权限服务（角色/权限/关联表）
// - notification-service 通知服务（邮件）
// - file-service        文件服务（空壳）
// ============================================================
include(
    "app",
    "common",
    "gateway",
    "identity-service",
    "rbac-service",
    "notification-service",
    "file-service",
)
