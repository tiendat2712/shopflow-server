package org.shopflow.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ShopflowBackendApplication

fun main(args: Array<String>) {
    runApplication<ShopflowBackendApplication>(*args)
}
