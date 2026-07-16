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
