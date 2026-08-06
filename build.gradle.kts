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

    // 所有 Spring Boot 应用模块统一固化 prod profile + 适配内存受限的 ECS。
    // 仅对应用了 spring-boot 插件的模块生效（common/common-web 等库模块无 bootBuildImage 任务）。
    //
    // Paketo 内存计算器：固定区域 = DirectMem + Metaspace + CodeCache + (Xss × 线程数)
    // 必须小于容器可用内存，否则启动直接失败（exit 1，容器无限 Restarting）。
    // 通过以下调整压缩固定区域：
    //   1. BPL_JVM_THREAD_COUNT=50（默认 250 → 50，线程栈 250M → 50M）
    //   2. ReservedCodeCacheSize=128M（默认 240M，128M 足够 Spring Boot 应用）
    //
    // ⚠️ 前缀选择的关键区别：
    //   - BPL_JVM_THREAD_COUNT 用 BPE_OVERRIDE_（强制覆盖默认值 250）。
    //     若用 BPE_APPEND_ 会把 "50" 追加到 "250" 变成 "25050"，线程数爆炸致内存计算失败。
    //   - JAVA_TOOL_OPTIONS 用 BPE_APPEND_（追加）。buildpack 自身会往 JAVA_TOOL_OPTIONS 塞很多
    //     JVM 参数（security/memory 等），必须追加而非覆盖，否则抹掉 buildpack 的配置致崩溃。
    pluginManager.withPlugin("org.springframework.boot") {
        val bootBuildImage = tasks.named<org.springframework.boot.gradle.tasks.bundling.BootBuildImage>("bootBuildImage")
        bootBuildImage.configure {
            environment.put("SPRING_PROFILES_ACTIVE", "prod")
            environment.put("BPE_OVERRIDE_BPL_JVM_THREAD_COUNT", "50")
            environment.put("BPE_DELIM_JAVA_TOOL_OPTIONS", " ")
            environment.put("BPE_APPEND_JAVA_TOOL_OPTIONS", "-XX:ReservedCodeCacheSize=128M")
        }
    }
}
