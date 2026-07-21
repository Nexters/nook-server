package org.every.nook.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class NookApiApplication

fun main(args: Array<String>) {
    runApplication<NookApiApplication>(*args)
}
