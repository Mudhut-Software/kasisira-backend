package com.mudhut.software.kasisira.profiles.repositories

import com.mudhut.software.kasisira.profiles.entities.AuthProvider
import com.mudhut.software.kasisira.profiles.entities.User
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.ActiveProfiles

@DataJpaTest
@ActiveProfiles("testing")
class UserRepositoryTest {

    @Autowired
    private lateinit var entityManager: TestEntityManager

    @Autowired
    private lateinit var userRepository: UserRepository

    private lateinit var testUser: User

    @BeforeEach
    fun setUp() {
        testUser = User(
            id = 0,
            username = "testuser",
            email = "test@example.com",
            passwordHash = "hashedPassword",
            provider = AuthProvider.LOCAL,
            emailVerified = true,
            isActive = true,
            isEnabled = true
        )
    }

    @Nested
    @DisplayName("findByEmail tests")
    inner class FindByEmailTests {

        @Test
        fun `should find user by email`() {
            // Given
            val savedUser = entityManager.persistAndFlush(testUser)

            // When
            val result = userRepository.findByEmail("test@example.com")

            // Then
            assertTrue(result.isPresent)
            assertEquals(savedUser.id, result.get().id)
            assertEquals("test@example.com", result.get().email)
        }

        @Test
        fun `should return empty when email not found`() {
            // When
            val result = userRepository.findByEmail("notfound@example.com")

            // Then
            assertFalse(result.isPresent)
        }

        @Test
        fun `should be case sensitive for email`() {
            // Given
            entityManager.persistAndFlush(testUser)

            // When
            val result = userRepository.findByEmail("TEST@EXAMPLE.COM")

            // Then
            assertFalse(result.isPresent)
        }
    }

    @Nested
    @DisplayName("findByUsername tests")
    inner class FindByUsernameTests {

        @Test
        fun `should find user by username`() {
            // Given
            val savedUser = entityManager.persistAndFlush(testUser)

            // When
            val result = userRepository.findByUsername("testuser")

            // Then
            assertTrue(result.isPresent)
            assertEquals(savedUser.id, result.get().id)
            assertEquals("testuser", result.get().username)
        }

        @Test
        fun `should return empty when username not found`() {
            // When
            val result = userRepository.findByUsername("notfound")

            // Then
            assertFalse(result.isPresent)
        }
    }

    @Nested
    @DisplayName("findByProviderAndProviderId tests")
    inner class FindByProviderAndProviderIdTests {

        @Test
        fun `should find user by provider and provider id`() {
            // Given
            val googleUser = User(
                id = 0,
                username = "googleuser",
                email = "google@example.com",
                passwordHash = null,
                provider = AuthProvider.GOOGLE,
                providerId = "google-123",
                emailVerified = true,
                isActive = true,
                isEnabled = true
            )
            val savedUser = entityManager.persistAndFlush(googleUser)

            // When
            val result = userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-123")

            // Then
            assertTrue(result.isPresent)
            assertEquals(savedUser.id, result.get().id)
            assertEquals(AuthProvider.GOOGLE, result.get().provider)
            assertEquals("google-123", result.get().providerId)
        }

        @Test
        fun `should return empty when provider id not found`() {
            // When
            val result = userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "unknown-id")

            // Then
            assertFalse(result.isPresent)
        }

        @Test
        fun `should not find user with different provider`() {
            // Given
            val googleUser = User(
                id = 0,
                username = "googleuser",
                email = "google@example.com",
                passwordHash = null,
                provider = AuthProvider.GOOGLE,
                providerId = "provider-123",
                emailVerified = true,
                isActive = true,
                isEnabled = true
            )
            entityManager.persistAndFlush(googleUser)

            // When - searching with same providerId but different provider
            val result = userRepository.findByProviderAndProviderId(AuthProvider.LOCAL, "provider-123")

            // Then
            assertFalse(result.isPresent)
        }
    }

    @Nested
    @DisplayName("existsByEmail tests")
    inner class ExistsByEmailTests {

        @Test
        fun `should return true when email exists`() {
            // Given
            entityManager.persistAndFlush(testUser)

            // When
            val result = userRepository.existsByEmail("test@example.com")

            // Then
            assertTrue(result)
        }

        @Test
        fun `should return false when email does not exist`() {
            // When
            val result = userRepository.existsByEmail("notfound@example.com")

            // Then
            assertFalse(result)
        }
    }

    @Nested
    @DisplayName("existsByUsername tests")
    inner class ExistsByUsernameTests {

        @Test
        fun `should return true when username exists`() {
            // Given
            entityManager.persistAndFlush(testUser)

            // When
            val result = userRepository.existsByUsername("testuser")

            // Then
            assertTrue(result)
        }

        @Test
        fun `should return false when username does not exist`() {
            // When
            val result = userRepository.existsByUsername("notfound")

            // Then
            assertFalse(result)
        }
    }

    @Nested
    @DisplayName("CRUD operations")
    inner class CrudOperations {

        @Test
        fun `should save and retrieve user`() {
            // Given/When
            val savedUser = userRepository.save(testUser)

            // Then
            assertNotNull(savedUser.id)
            assertTrue(savedUser.id > 0)

            val retrieved = userRepository.findById(savedUser.id)
            assertTrue(retrieved.isPresent)
            assertEquals(savedUser.email, retrieved.get().email)
        }

        @Test
        fun `should delete user`() {
            // Given
            val savedUser = entityManager.persistAndFlush(testUser)

            // When
            userRepository.deleteById(savedUser.id)
            entityManager.flush()

            // Then
            val result = userRepository.findById(savedUser.id)
            assertFalse(result.isPresent)
        }

        @Test
        fun `should find all users`() {
            // Given
            entityManager.persistAndFlush(testUser)
            entityManager.persistAndFlush(
                User(
                    id = 0,
                    username = "anotheruser",
                    email = "another@example.com",
                    passwordHash = "hash",
                    provider = AuthProvider.LOCAL,
                    emailVerified = true,
                    isActive = true,
                    isEnabled = true
                )
            )

            // When
            val users = userRepository.findAll()

            // Then
            assertEquals(2, users.size)
        }
    }
}
