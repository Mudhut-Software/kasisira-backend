package com.mudhut.software.kasisira.properties.repositories

import com.mudhut.software.kasisira.profiles.entities.AuthProvider
import com.mudhut.software.kasisira.profiles.entities.User
import com.mudhut.software.kasisira.properties.entities.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal

@DataJpaTest
@ActiveProfiles("testing")
class PropertyMediaRepositoryTest {

    @Autowired
    private lateinit var entityManager: TestEntityManager

    @Autowired
    private lateinit var propertyMediaRepository: PropertyMediaRepository

    private lateinit var testUser: User
    private lateinit var testProperty: Property
    private lateinit var testMedia: PropertyMedia

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
        testUser = entityManager.persistAndFlush(testUser)

        testProperty = Property(
            id = 0,
            owner = testUser,
            title = "Beautiful House",
            description = "A beautiful house for sale in Kampala",
            propertyType = PropertyType.HOUSE,
            listingType = ListingType.FOR_SALE,
            price = BigDecimal("500000000"),
            currency = "UGX",
            city = "Kampala",
            status = PropertyStatus.ACTIVE
        )
        testProperty = entityManager.persistAndFlush(testProperty)

        testMedia = PropertyMedia(
            id = 0,
            property = testProperty,
            mediaType = MediaType.IMAGE,
            url = "https://example.com/image1.jpg",
            description = "Front view",
            isPrimary = false,
            displayOrder = 0
        )
    }

    @Nested
    @DisplayName("findByPropertyIdOrderByDisplayOrder tests")
    inner class FindByPropertyIdOrderByDisplayOrderTests {

        @Test
        fun `should find media ordered by display order`() {
            // Given
            val media1 = entityManager.persistAndFlush(testMedia.copy(displayOrder = 2))
            val media2 = entityManager.persistAndFlush(testMedia.copy(id = 0, url = "https://example.com/image2.jpg", displayOrder = 0))
            val media3 = entityManager.persistAndFlush(testMedia.copy(id = 0, url = "https://example.com/image3.jpg", displayOrder = 1))

            // When
            val result = propertyMediaRepository.findByPropertyIdOrderByDisplayOrder(testProperty.id)

            // Then
            assertEquals(3, result.size)
            assertEquals(media2.id, result[0].id)
            assertEquals(media3.id, result[1].id)
            assertEquals(media1.id, result[2].id)
        }

        @Test
        fun `should return empty list when no media`() {
            // When
            val result = propertyMediaRepository.findByPropertyIdOrderByDisplayOrder(testProperty.id)

            // Then
            assertTrue(result.isEmpty())
        }
    }

    @Nested
    @DisplayName("findByPropertyIdAndMediaType tests")
    inner class FindByPropertyIdAndMediaTypeTests {

        @Test
        fun `should find media by property and type`() {
            // Given
            entityManager.persistAndFlush(testMedia)
            entityManager.persistAndFlush(testMedia.copy(id = 0, mediaType = MediaType.VIDEO, url = "https://example.com/video.mp4"))

            // When
            val images = propertyMediaRepository.findByPropertyIdAndMediaType(testProperty.id, MediaType.IMAGE)
            val videos = propertyMediaRepository.findByPropertyIdAndMediaType(testProperty.id, MediaType.VIDEO)

            // Then
            assertEquals(1, images.size)
            assertEquals(1, videos.size)
        }

        @Test
        fun `should return empty list when no media of type`() {
            // Given
            entityManager.persistAndFlush(testMedia)

            // When
            val result = propertyMediaRepository.findByPropertyIdAndMediaType(testProperty.id, MediaType.VIDEO)

            // Then
            assertTrue(result.isEmpty())
        }
    }

    @Nested
    @DisplayName("findByPropertyIdAndIsPrimary tests")
    inner class FindByPropertyIdAndIsPrimaryTests {

        @Test
        fun `should find primary media`() {
            // Given
            entityManager.persistAndFlush(testMedia)
            val primaryMedia = entityManager.persistAndFlush(testMedia.copy(id = 0, url = "https://example.com/primary.jpg", isPrimary = true))

            // When
            val result = propertyMediaRepository.findByPropertyIdAndIsPrimary(testProperty.id, true)

            // Then
            Assertions.assertNotNull(result)
            assertEquals(primaryMedia.id, result?.id)
        }

        @Test
        fun `should return null when no primary media`() {
            // Given
            entityManager.persistAndFlush(testMedia)

            // When
            val result = propertyMediaRepository.findByPropertyIdAndIsPrimary(testProperty.id, true)

            // Then
            Assertions.assertNull(result)
        }
    }

    @Nested
    @DisplayName("countByPropertyId tests")
    inner class CountByPropertyIdTests {

        @Test
        fun `should count media by property`() {
            // Given
            entityManager.persistAndFlush(testMedia)
            entityManager.persistAndFlush(testMedia.copy(id = 0, url = "https://example.com/image2.jpg"))
            entityManager.persistAndFlush(testMedia.copy(id = 0, url = "https://example.com/image3.jpg"))

            // When
            val count = propertyMediaRepository.countByPropertyId(testProperty.id)

            // Then
            assertEquals(3, count)
        }

        @Test
        fun `should return zero when no media`() {
            // When
            val count = propertyMediaRepository.countByPropertyId(testProperty.id)

            // Then
            assertEquals(0, count)
        }
    }

    @Nested
    @DisplayName("countByPropertyIdAndMediaType tests")
    inner class CountByPropertyIdAndMediaTypeTests {

        @Test
        fun `should count media by property and type`() {
            // Given
            entityManager.persistAndFlush(testMedia)
            entityManager.persistAndFlush(testMedia.copy(id = 0, url = "https://example.com/image2.jpg"))
            entityManager.persistAndFlush(testMedia.copy(id = 0, mediaType = MediaType.VIDEO, url = "https://example.com/video.mp4"))

            // When
            val imageCount = propertyMediaRepository.countByPropertyIdAndMediaType(testProperty.id, MediaType.IMAGE)
            val videoCount = propertyMediaRepository.countByPropertyIdAndMediaType(testProperty.id, MediaType.VIDEO)

            // Then
            assertEquals(2, imageCount)
            assertEquals(1, videoCount)
        }
    }

    @Nested
    @DisplayName("CRUD operations")
    inner class CrudOperations {

        @Test
        fun `should save and retrieve media`() {
            // Given/When
            val savedMedia = propertyMediaRepository.save(testMedia)

            // Then
            Assertions.assertNotNull(savedMedia.id)
            assertTrue(savedMedia.id > 0)

            val retrieved = propertyMediaRepository.findById(savedMedia.id)
            assertTrue(retrieved.isPresent)
            assertEquals(savedMedia.url, retrieved.get().url)
        }

        @Test
        fun `should delete media`() {
            // Given
            val savedMedia = entityManager.persistAndFlush(testMedia)

            // When
            propertyMediaRepository.deleteById(savedMedia.id)
            entityManager.flush()

            // Then
            val result = propertyMediaRepository.findById(savedMedia.id)
            assertFalse(result.isPresent)
        }

        @Test
        fun `should update media`() {
            // Given
            val savedMedia = entityManager.persistAndFlush(testMedia)

            // When
            val updatedMedia = savedMedia.copy(description = "Updated description")
            propertyMediaRepository.save(updatedMedia)
            entityManager.flush()
            entityManager.clear()

            // Then
            val retrieved = propertyMediaRepository.findById(savedMedia.id)
            assertTrue(retrieved.isPresent)
            assertEquals("Updated description", retrieved.get().description)
        }
    }
}
