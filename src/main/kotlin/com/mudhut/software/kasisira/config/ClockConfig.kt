package com.mudhut.software.kasisira.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * Provides an injectable java.time.Clock so services that depend on "now" can be
 * unit-tested deterministically by replacing the bean with Clock.fixed(...).
 */
@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
