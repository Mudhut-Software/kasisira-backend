package com.mudhut.software.kasisira.utils

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PasswordValidatorTest {

    private lateinit var passwordValidator: PasswordValidator

    @BeforeEach
    fun setUp() {
        passwordValidator = PasswordValidator()
    }

    @Nested
    @DisplayName("Valid passwords")
    inner class ValidPasswords {

        @ParameterizedTest
        @ValueSource(strings = [
            "Password1!",
            "MyP@ssw0rd",
            "Secure123#",
            "Test1234$",
            "Ab1!defgh",
            "Complex1@Password",
            "12345678a!"
        ])
        fun `should accept valid passwords`(password: String) {
            // When/Then - should not throw
            assertDoesNotThrow {
                passwordValidator.validatePassword(password)
            }
        }
    }

    @Nested
    @DisplayName("Password length validation")
    inner class PasswordLengthValidation {

        @Test
        fun `should reject password shorter than 8 characters`() {
            // Given
            val shortPassword = "Ab1!"

            // When/Then
            val exception = assertThrows<IllegalArgumentException> {
                passwordValidator.validatePassword(shortPassword)
            }
            assertTrue(exception.message!!.contains("at least 8 characters"))
        }

        @Test
        fun `should accept password with exactly 8 characters`() {
            // Given
            val password = "Abcdef1!"

            // When/Then
            assertDoesNotThrow {
                passwordValidator.validatePassword(password)
            }
        }

        @Test
        fun `should accept long password`() {
            // Given
            val longPassword = "ThisIsAVeryLongPassword123!WithManyCharacters"

            // When/Then
            assertDoesNotThrow {
                passwordValidator.validatePassword(longPassword)
            }
        }
    }

    @Nested
    @DisplayName("Password character requirements")
    inner class PasswordCharacterRequirements {

        @Test
        fun `should reject password without letters`() {
            // Given
            val noLetters = "12345678!"

            // When/Then
            val exception = assertThrows<IllegalArgumentException> {
                passwordValidator.validatePassword(noLetters)
            }
            assertTrue(exception.message!!.contains("letter"))
        }

        @Test
        fun `should reject password without digits`() {
            // Given
            val noDigits = "Password!"

            // When/Then
            val exception = assertThrows<IllegalArgumentException> {
                passwordValidator.validatePassword(noDigits)
            }
            assertTrue(exception.message!!.contains("number"))
        }

        @Test
        fun `should reject password without special characters`() {
            // Given
            val noSpecial = "Password123"

            // When/Then
            val exception = assertThrows<IllegalArgumentException> {
                passwordValidator.validatePassword(noSpecial)
            }
            assertTrue(exception.message!!.contains("special character"))
        }

        @Test
        fun `should reject password with only letters`() {
            // Given
            val onlyLetters = "abcdefghij"

            // When/Then
            assertThrows<IllegalArgumentException> {
                passwordValidator.validatePassword(onlyLetters)
            }
        }

        @Test
        fun `should reject password with only digits`() {
            // Given
            val onlyDigits = "1234567890"

            // When/Then
            assertThrows<IllegalArgumentException> {
                passwordValidator.validatePassword(onlyDigits)
            }
        }

        @Test
        fun `should reject password with only special characters`() {
            // Given
            val onlySpecial = "!@#$%^&*()"

            // When/Then
            assertThrows<IllegalArgumentException> {
                passwordValidator.validatePassword(onlySpecial)
            }
        }
    }

    @Nested
    @DisplayName("Special character variations")
    inner class SpecialCharacterVariations {

        @ParameterizedTest
        @ValueSource(strings = [
            "Password1!",
            "Password1@",
            "Password1#",
            "Password1$",
            "Password1%",
            "Password1^",
            "Password1&",
            "Password1*",
            "Password1(",
            "Password1)",
            "Password1-",
            "Password1_",
            "Password1=",
            "Password1+",
            "Password1[",
            "Password1]",
            "Password1{",
            "Password1}",
            "Password1|",
            "Password1\\",
            "Password1;",
            "Password1:",
            "Password1'",
            "Password1\"",
            "Password1,",
            "Password1.",
            "Password1<",
            "Password1>",
            "Password1/",
            "Password1?",
            "Password1`",
            "Password1~"
        ])
        fun `should accept various special characters`(password: String) {
            // When/Then
            assertDoesNotThrow {
                passwordValidator.validatePassword(password)
            }
        }
    }

    @Nested
    @DisplayName("Edge cases")
    inner class EdgeCases {

        @Test
        fun `should reject empty password`() {
            // Given
            val emptyPassword = ""

            // When/Then
            assertThrows<IllegalArgumentException> {
                passwordValidator.validatePassword(emptyPassword)
            }
        }

        @Test
        fun `should handle password with spaces`() {
            // Given - spaces are not counted as special characters
            val passwordWithSpaces = "Pass word1"

            // When/Then - should fail because space is whitespace, not special char
            assertThrows<IllegalArgumentException> {
                passwordValidator.validatePassword(passwordWithSpaces)
            }
        }

        @Test
        fun `should accept password with mixed case letters`() {
            // Given
            val mixedCase = "AbCdEf1!"

            // When/Then
            assertDoesNotThrow {
                passwordValidator.validatePassword(mixedCase)
            }
        }
    }
}
