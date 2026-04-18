package com.mudhut.software.kasisira

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class KasisiraApplication

fun main(args: Array<String>) {
	runApplication<KasisiraApplication>(*args)
}
