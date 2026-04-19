package com.mudhut.software.kasisira.properties.mappers

import com.mudhut.software.kasisira.owner_org.entities.OwnerOrg
import com.mudhut.software.kasisira.profiles.entities.AuthProvider
import com.mudhut.software.kasisira.profiles.entities.User
import com.mudhut.software.kasisira.properties.entities.*
import com.mudhut.software.kasisira.properties.models.request.AddMediaRequest
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal
import java.time.Instant

class PropertyMediaMapperTest {

    private lateinit var propertyMediaMapper: PropertyMediaMapper

    private lateinit var testUser: User
    private lateinit var testOrg: OwnerOrg
    private lateinit var testProperty: Property
    private lateinit var testMedia: PropertyMedia

    @BeforeEach
    fun setUp() {
        propertyMediaMapper = PropertyMediaMapper()

        testUser = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            passwordHash = "hashedPassword",
            provider = AuthProvider.LOCAL,
            emailVerified = true,
            isActive = true,
            isEnabled = true
        )

        testOrg = OwnerOrg(
            id = 10L,
            creator = testUser,
            name = "Test Org"
        )

        testProperty = Property(
            id = 1L,
            ownerOrg = testOrg,
            title = "Beautiful House",
            description = "A beautiful house for sale",
            propertyType = PropertyType.HOUSE,
            listingType = ListingType.FOR_SALE,
            price = BigDecimal("500000000"),
            currency = "UGX",
            city = "Kampala",
            district = "Wakiso",
            address = "123 Main Street",
            latitude = BigDecimal("0.3476"),
            longitude = BigDecimal("32.5825"),
            status = PropertyStatus.ACTIVE
        )

        testMedia = PropertyMedia(
            id = 1L,
            property = testProperty,
            mediaType = MediaType.IMAGE,
            url = "https://example.com/image1.jpg",
            thumbnailUrl = "https://example.com/thumb1.jpg",
            description = "Front view of the house",
            isPrimary = true,
            displayOrder = 0,
            fileSize = 1024000,
            mimeType = "image/jpeg",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }

    @Nested
    @DisplayName("toResponse tests")
    inner class ToResponseTests {

        @Test
        fun `should map media to response correctly`() {
            // When
            val response = propertyMediaMapper.toResponse(testMedia)

            // Then
            assertEquals(testMedia.id, response.id)
            assertEquals(testMedia.mediaType, response.mediaType)
            assertEquals(testMedia.url, response.url)
            assertEquals(testMedia.thumbnailUrl, response.thumbnailUrl)
            assertEquals(testMedia.description, response.description)
            assertEquals(testMedia.isPrimary, response.isPrimary)
            assertEquals(testMedia.displayOrder, response.displayOrder)
            assertEquals(testMedia.createdAt, response.createdAt)
        }

        @Test
        fun `should handle null optional fields`() {
            // Given
            val mediaWithNulls = testMedia.copy(
                thumbnailUrl = null,
                description = null
            )

            // When
            val response = propertyMediaMapper.toResponse(mediaWithNulls)

            // Then
            Assertions.assertNull(response.thumbnailUrl)
            Assertions.assertNull(response.description)
        }

        @Test
        fun `should map video media type`() {
            // Given
            val videoMedia = testMedia.copy(
                mediaType = MediaType.VIDEO,
                url = "https://example.com/video.mp4",
                thumbnailUrl = "https://example.com/video-thumb.jpg"
            )

            // When
            val response = propertyMediaMapper.toResponse(videoMedia)

            // Then
            assertEquals(MediaType.VIDEO, response.mediaType)
            assertEquals("https://example.com/video.mp4", response.url)
            assertEquals("https://example.com/video-thumb.jpg", response.thumbnailUrl)
        }
    }

    @Nested
    @DisplayName("toResponseList tests")
    inner class ToResponseListTests {

        @Test
        fun `should map list of media to responses`() {
            // Given
            val media2 = testMedia.copy(id = 2L, url = "https://example.com/image2.jpg", isPrimary = false)
            val mediaList = listOf(testMedia, media2)

            // When
            val responses = propertyMediaMapper.toResponseList(mediaList)

            // Then
            assertEquals(2, responses.size)
            assertEquals(testMedia.id, responses[0].id)
            assertEquals(media2.id, responses[1].id)
        }

        @Test
        fun `should return empty list for empty input`() {
            // When
            val responses = propertyMediaMapper.toResponseList(emptyList())

            // Then
            assertTrue(responses.isEmpty())
        }
    }

    @Nested
    @DisplayName("fromAddRequest tests")
    inner class FromAddRequestTests {

        @Test
        fun `should map add request to media entity`() {
            // Given
            val request = AddMediaRequest(
                mediaType = MediaType.IMAGE,
                url = "https://example.com/new-image.jpg",
                thumbnailUrl = "https://example.com/new-thumb.jpg",
                description = "New image description",
                isPrimary = true,
                displayOrder = 1,
                fileSize = 2048000,
                mimeType = "image/png"
            )

            // When
            val media = propertyMediaMapper.fromAddRequest(request, testProperty)

            // Then
            assertEquals(0, media.id) // New entity
            assertEquals(testProperty, media.property)
            assertEquals(request.mediaType, media.mediaType)
            assertEquals(request.url, media.url)
            assertEquals(request.thumbnailUrl, media.thumbnailUrl)
            assertEquals(request.description, media.description)
            assertEquals(request.isPrimary, media.isPrimary)
            assertEquals(request.displayOrder, media.displayOrder)
            assertEquals(request.fileSize, media.fileSize)
            assertEquals(request.mimeType, media.mimeType)
        }

        @Test
        fun `should handle minimal request with required fields only`() {
            // Given
            val request = AddMediaRequest(
                mediaType = MediaType.IMAGE,
                url = "https://example.com/image.jpg"
            )

            // When
            val media = propertyMediaMapper.fromAddRequest(request, testProperty)

            // Then
            assertEquals(MediaType.IMAGE, media.mediaType)
            assertEquals("https://example.com/image.jpg", media.url)
            Assertions.assertNull(media.thumbnailUrl)
            Assertions.assertNull(media.description)
            assertFalse(media.isPrimary)
            assertEquals(0, media.displayOrder)
            Assertions.assertNull(media.fileSize)
            Assertions.assertNull(media.mimeType)
        }

        @Test
        fun `should map video request correctly`() {
            // Given
            val request = AddMediaRequest(
                mediaType = MediaType.VIDEO,
                url = "https://example.com/video.mp4",
                thumbnailUrl = "https://example.com/video-thumb.jpg",
                description = "Property tour video",
                isPrimary = false,
                displayOrder = 5,
                fileSize = 50000000,
                mimeType = "video/mp4"
            )

            // When
            val media = propertyMediaMapper.fromAddRequest(request, testProperty)

            // Then
            assertEquals(MediaType.VIDEO, media.mediaType)
            assertEquals("https://example.com/video.mp4", media.url)
            assertEquals("video/mp4", media.mimeType)
        }

        @Test
        fun `should set property reference`() {
            // Given
            val request = AddMediaRequest(
                mediaType = MediaType.IMAGE,
                url = "https://example.com/image.jpg"
            )

            // When
            val media = propertyMediaMapper.fromAddRequest(request, testProperty)

            // Then
            assertEquals(testProperty, media.property)
            assertEquals(testProperty.id, media.property?.id)
        }
    }
}
