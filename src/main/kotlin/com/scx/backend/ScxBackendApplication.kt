package com.scx.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = ["com.scx.backend"])
class ScxBackendApplication

fun main(args: Array<String>) {
    runApplication<ScxBackendApplication>(*args)
}
