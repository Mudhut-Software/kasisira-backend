package com.mudhut.software.kasisira.migration

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * Smoke test for the Flyway baseline (V1__baseline.sql).
 *
 * The testing profile runs Flyway on startup and sets Hibernate to
 * ddl-auto=validate. If the migration's schema diverges from what the JPA
 * entities declare (missing columns, wrong types, missing indexes, etc.),
 * SchemaValidator throws during context initialisation and this test fails
 * with a precise message pointing at the offending element.
 */
@SpringBootTest
@ActiveProfiles("testing")
class FlywayBaselineTest {

    @Test
    fun `flyway baseline matches JPA entities`() {
        // The assertion is implicit: a green context load means Hibernate's
        // SchemaValidator accepted the schema that Flyway produced.
    }
}
