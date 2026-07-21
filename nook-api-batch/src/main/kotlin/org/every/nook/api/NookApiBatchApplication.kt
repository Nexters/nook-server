package org.every.nook.api

import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder

@SpringBootApplication
class NookApiBatchApplication

fun main(args: Array<String>) {
    SpringApplicationBuilder(NookApiBatchApplication::class.java)
        .web(WebApplicationType.NONE)
        .run(*args)
}
