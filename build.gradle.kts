// ============================================================
// scx-backend 根构建脚本（多模块）
// ============================================================
// 根项目本身不含源码，仅统一声明：
//  - 插件版本（应用到子模块）
//  - 公共仓库
//  - 子模块公共依赖（Kotlin / Spring BOM / Jackson）
//  - 编译器选项与测试配置
// ============================================================

plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.spring") version "2.2.20" apply false
    kotlin("plugin.jpa") version "2.2.20" apply false
    id("org.springframework.boot") version "4.0.7" apply false
}

group = "com.scx"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

// 所有子模块共享的基础配置
subprojects {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    // 纯 Kotlin 库模块（common）仅应用 jvm；Spring 模块由各自构建文件应用额外插件
    apply(plugin = "org.jetbrains.kotlin.jvm")

    // Spring Boot 4.0：用 Gradle 原生 BOM platform 管理依赖版本（替代已废弃的
    // io.spring.dependency-management 插件）。后续 starter 不必再写版本号。
    dependencies {
        implementation(platform("org.springframework.boot:spring-boot-dependencies:4.0.7"))
    }

    // Java/Kotlin 工具链统一
    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    // Kotlin 编译器统一选项
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            freeCompilerArgs = listOf(
                "-Xjsr305=strict",
                // Kotlin 2.2：未显式指定目标的注解（如 @Schema）默认仅作用于 value 参数。
                // 开启后同时应用到参数与属性，消除 KT-73255 警告。
                "-Xannotation-default-target=param-property",
            )
        }
    }

    // 测试统一用 JUnit Platform
    tasks.withType<Test> {
        useJUnitPlatform()
    }

    // 所有 Spring Boot 应用模块统一固化 prod 为默认 profile（镜像内置，不依赖部署时环境变量）。
    // 仅对应用了 spring-boot 插件的模块生效（common/common-web 等库模块无 bootBuildImage 任务）。
    // 网关额外的 Netty 直接内存配置见 gateway/build.gradle.kts。
    pluginManager.withPlugin("org.springframework.boot") {
        val bootBuildImage = tasks.named<org.springframework.boot.gradle.tasks.bundling.BootBuildImage>("bootBuildImage")
        bootBuildImage.configure {
            environment.put("SPRING_PROFILES_ACTIVE", "prod")
        }
    }
}
