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
// - common              共享契约模块（纯协议层：响应封装/异常/ULID/令牌工具/注解，无 Servlet 依赖）
// - common-web          Servlet 专属共享组件（GlobalResponseHandler/GlobalExceptionHandler/
//                       AccessLogInterceptor/AuthContextResolver，依赖 common，供 Servlet 服务使用；
//                       WebFlux 网关不依赖本模块）
// - common-audit        审计日志共享组件（操作/登录日志实体、仓库与建表迁移，依赖 common-web，
//                       供有数据库的服务使用；notification/网关不依赖本模块）
// - gateway             API 网关（集中鉴权 + 路由，WebFlux 栈）
// - identity-service    身份认证服务（用户/登录/令牌）
// - rbac-service        角色权限服务（角色/权限/关联表）
// - notification-service 通知服务（邮件）
// - file-service        文件服务（空壳）
// ============================================================
include(
    "common",
    "common-web",
    "common-audit",
    "gateway",
    "identity-service",
    "rbac-service",
    "notification-service",
    "file-service",
)
