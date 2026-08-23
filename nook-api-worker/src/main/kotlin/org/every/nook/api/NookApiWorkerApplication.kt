package org.every.nook.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class NookApiWorkerApplication

fun main(args: Array<String>) {
    runApplication<NookApiWorkerApplication>(*args) {
        setDefaultProperties(loadDotenvProperties())
    }
}
