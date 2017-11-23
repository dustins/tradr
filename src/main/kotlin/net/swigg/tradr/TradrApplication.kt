package net.swigg.tradr

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class TradrApplication

fun main(args: Array<String>) {
    SpringApplication.run(TradrApplication::class.java, *args)
}
