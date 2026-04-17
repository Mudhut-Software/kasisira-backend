package com.mudhut.software.kasisira.properties.mappers

import com.mudhut.software.kasisira.profiles.entities.AuthProvider
import com.mudhut.software.kasisira.profiles.entities.User
import com.mudhut.software.kasisira.properties.entities.*
import com.mudhut.software.kasisira.properties.models.request.CreatePropertyRequest
import com.mudhut.software.kasisira.properties.models.request.UpdatePropertyRequest
import com.mudhut.software.kasisira.properties.models.response.PropertyMediaResponse
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal
import java.time.LocalDateTime

class PropertyMapperTest {

    private lateinit var propertyMediaMapper: PropertyMediaMapper
    private lateinit var propertyMapper: PropertyMapper

    private lateinit var testUser: User
    private lateinit var testProperty: Property
    private lateinit var testMedia: PropertyMedia
    private lateinit var testMediaResponse: PropertyMediaResponse

    @BeforeEach
    fun setUp() {
        propertyMediaMapper = mockk()
        propertyMapper = PropertyMapper(propertyMediaMapper)

        testUser = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            passwordHash = "hashedPassword",
            provider = AuthProvider.LOCAL,
            imageUrl = "https://example.com/avatar.jpg",
            emailVerified = true,
            isActive = true,
            isEnabled = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        testMedia = PropertyMedia(
            id = 1L,
            mediaType = MediaType.IMAGE,
            url = "https://example.com/image1.jpg",
            description = "Front view",
            isPrimary = true,
            displayOrder = 0,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        testMediaResponse = PropertyMediaResponse(
            id = 1L,
            mediaType = MediaType.IMAGE,
            url = "https://example.com/image1.jpg",
            thumbnailUrl = null,
            description = "Front view",
            isPrimary = true,
            displayOrder = 0,
            createdAt = LocalDateTime.now()
        )

        testProperty = Property(
            id = 1L,
            owner = testUser,
            title = "Beautiful House",
            description = "A beautiful house for sale",
            propertyType = PropertyType.HOUSE,
            listingType = ListingType.FOR_SALE,
            rentalDuration = null,
            furnishingStatus = null,
            price = BigDecimal("500000000"),
            currency = "UGX",
            city = "Kampala",
            district = "Wakiso",
            address = "123 Main Street",
            latitude = BigDecimal("0.3476"),
            longitude = BigDecimal("32.5825"),
            bedrooms = 3,
            bathrooms = 2,
            landSize = BigDecimal("500"),
            landSizeUnit = "sqm",
            builtArea = BigDecimal("200"),
            yearBuilt = 2020,
            features = "parking,garden,security",
            status = PropertyStatus.ACTIVE,
            viewCount = 100,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        testProperty.media.add(testMedia)
        testMedia.property = testProperty
    }

    @Nested
    @DisplayName("toResponse tests")
    inner class ToResponseTests {

        @Test
        fun `should map property to response correctly`() {
            // Given
            every { propertyMediaMapper.toResponse(testMedia) } returns testMediaResponse

            // When
            val response = propertyMapper.toResponse(testProperty)

            // Then
            assertEquals(testProperty.id, response.id)
            assertEquals(testProperty.title, response.title)
            assertEquals(testProperty.description, response.description)
            assertEquals(testProperty.propertyType, response.propertyType)
            assertEquals(testProperty.listingType, response.listingType)
            assertEquals(testProperty.price, response.price)
            assertEquals(testProperty.currency, response.currency)
            assertEquals(testProperty.city, response.city)
            assertEquals(testProperty.district, response.district)
            assertEquals(testProperty.bedrooms, response.bedrooms)
            assertEquals(testProperty.bathrooms, response.bathrooms)
            assertEquals(testProperty.status, response.status)
            assertEquals(testProperty.viewCount, response.viewCount)
        }

        @Test
        fun `should map owner correctly`() {
            // Given
            every { propertyMediaMapper.toResponse(testMedia) } returns testMediaResponse

            // When
            val response = propertyMapper.toResponse(testProperty)

            // Then
            assertEquals(testUser.id, response.owner.id)
            assertEquals(testUser.username, response.owner.username)
            assertEquals(testUser.imageUrl, response.owner.imageUrl)
        }

        @Test
        fun `should map features as list`() {
            // Given
            every { propertyMediaMapper.toResponse(testMedia) } returns testMediaResponse

            // When
            val response = propertyMapper.toResponse(testProperty)

            // Then
            assertEquals(3, response.features.size)
            assertTrue(response.features.contains("parking"))
            assertTrue(response.features.contains("garden"))
            assertTrue(response.features.contains("security"))
        }

        @Test
        fun `should return empty features list when null`() {
            // Given
            val propertyWithoutFeatures = testProperty.copy(features = null)
            every { propertyMediaMapper.toResponse(testMedia) } returns testMediaResponse

            // When
            val response = propertyMapper.toResponse(propertyWithoutFeatures)

            // Then
            assertTrue(response.features.isEmpty())
        }

        @Test
        fun `should set primary image from primary media`() {
            // Given
            every { propertyMediaMapper.toResponse(testMedia) } returns testMediaResponse

            // When
            val response = propertyMapper.toResponse(testProperty)

            // Then
            assertEquals(testMedia.url, response.primaryImage)
        }

        @Test
        fun `should use first media when no primary`() {
            // Given
            val nonPrimaryMedia = testMedia.copy(isPrimary = false)
            val propertyWithNonPrimary = testProperty.copy()
            propertyWithNonPrimary.media.clear()
            propertyWithNonPrimary.media.add(nonPrimaryMedia)
            every { propertyMediaMapper.toResponse(nonPrimaryMedia) } returns testMediaResponse.copy(isPrimary = false)

            // When
            val response = propertyMapper.toResponse(propertyWithNonPrimary)

            // Then
            assertEquals(nonPrimaryMedia.url, response.primaryImage)
        }

        @Test
        fun `should map media list`() {
            // Given
            every { propertyMediaMapper.toResponse(testMedia) } returns testMediaResponse

            // When
            val response = propertyMapper.toResponse(testProperty)

            // Then
            assertEquals(1, response.media.size)
        }
    }

    @Nested
    @DisplayName("toSummaryResponse tests")
    inner class ToSummaryResponseTests {

        @Test
        fun `should map property to summary response`() {
            // When
            val response = propertyMapper.toSummaryResponse(testProperty)

            // Then
            assertEquals(testProperty.id, response.id)
            assertEquals(testProperty.title, response.title)
            assertEquals(testProperty.propertyType, response.propertyType)
            assertEquals(testProperty.listingType, response.listingType)
            assertEquals(testProperty.price, response.price)
            assertEquals(testProperty.city, response.city)
            assertEquals(testProperty.bedrooms, response.bedrooms)
            assertEquals(testProperty.status, response.status)
            assertEquals(testProperty.viewCount, response.viewCount)
        }

        @Test
        fun `should set primary image in summary`() {
            // When
            val response = propertyMapper.toSummaryResponse(testProperty)

            // Then
            assertEquals(testMedia.url, response.primaryImage)
        }
    }

    @Nested
    @DisplayName("fromCreateRequest tests")
    inner class FromCreateRequestTests {

        @Test
        fun `should map create request to property`() {
            // Given
            val request = CreatePropertyRequest(
                title = "New House",
                description = "A new house for sale",
                propertyType = PropertyType.HOUSE,
                listingType = ListingType.FOR_SALE,
                price = BigDecimal("600000000"),
                city = "Kampala",
                district = "Nakawa",
                bedrooms = 4,
                bathrooms = 3,
                features = listOf("parking", "pool")
            )

            // When
            val property = propertyMapper.fromCreateRequest(request, testUser)

            // Then
            assertEquals(0, property.id) // New entity
            assertEquals(testUser, property.owner)
            assertEquals(request.title, property.title)
            assertEquals(request.description, property.description)
            assertEquals(request.propertyType, property.propertyType)
            assertEquals(request.listingType, property.listingType)
            assertEquals(request.price, property.price)
            assertEquals(request.city, property.city)
            assertEquals(request.district, property.district)
            assertEquals(request.bedrooms, property.bedrooms)
            assertEquals(request.bathrooms, property.bathrooms)
            assertEquals(PropertyStatus.DRAFT, property.status)
        }

        @Test
        fun `should convert features list to comma-separated string`() {
            // Given
            val request = CreatePropertyRequest(
                title = "New House",
                description = "A new house for sale",
                propertyType = PropertyType.HOUSE,
                listingType = ListingType.FOR_SALE,
                price = BigDecimal("600000000"),
                city = "Kampala",
                features = listOf("parking", "pool", "garden")
            )

            // When
            val property = propertyMapper.fromCreateRequest(request, testUser)

            // Then
            assertEquals("parking,pool,garden", property.features)
        }

        @Test
        fun `should set null features when not provided`() {
            // Given
            val request = CreatePropertyRequest(
                title = "New House",
                description = "A new house for sale",
                propertyType = PropertyType.HOUSE,
                listingType = ListingType.FOR_SALE,
                price = BigDecimal("600000000"),
                city = "Kampala",
                features = null
            )

            // When
            val property = propertyMapper.fromCreateRequest(request, testUser)

            // Then
            Assertions.assertNull(property.features)
        }

        @Test
        fun `should map rental property with duration and furnishing`() {
            // Given
            val request = CreatePropertyRequest(
                title = "Rental Apartment",
                description = "A furnished apartment for rent",
                propertyType = PropertyType.HOUSE,
                listingType = ListingType.FOR_RENT,
                rentalDuration = RentalDuration.MONTHLY,
                furnishingStatus = FurnishingStatus.FURNISHED,
                price = BigDecimal("2000000"),
                city = "Kampala"
            )

            // When
            val property = propertyMapper.fromCreateRequest(request, testUser)

            // Then
            assertEquals(ListingType.FOR_RENT, property.listingType)
            assertEquals(RentalDuration.MONTHLY, property.rentalDuration)
            assertEquals(FurnishingStatus.FURNISHED, property.furnishingStatus)
        }
    }

    @Nested
    @DisplayName("applyUpdate tests")
    inner class ApplyUpdateTests {

        @Test
        fun `should update only provided fields`() {
            // Given
            val updateRequest = UpdatePropertyRequest(
                title = "Updated Title",
                price = BigDecimal("700000000")
            )

            // When
            val updated = propertyMapper.applyUpdate(testProperty, updateRequest)

            // Then
            assertEquals("Updated Title", updated.title)
            assertEquals(BigDecimal("700000000"), updated.price)
            // Other fields should remain unchanged
            assertEquals(testProperty.description, updated.description)
            assertEquals(testProperty.city, updated.city)
            assertEquals(testProperty.bedrooms, updated.bedrooms)
        }

        @Test
        fun `should not update null fields`() {
            // Given
            val updateRequest = UpdatePropertyRequest(
                title = null,
                description = null
            )

            // When
            val updated = propertyMapper.applyUpdate(testProperty, updateRequest)

            // Then
            assertEquals(testProperty.title, updated.title)
            assertEquals(testProperty.description, updated.description)
        }

        @Test
        fun `should update features from list`() {
            // Given
            val updateRequest = UpdatePropertyRequest(
                features = listOf("new_feature1", "new_feature2")
            )

            // When
            val updated = propertyMapper.applyUpdate(testProperty, updateRequest)

            // Then
            assertEquals("new_feature1,new_feature2", updated.features)
        }

        @Test
        fun `should keep existing features when not provided`() {
            // Given
            val updateRequest = UpdatePropertyRequest(
                title = "Updated Title",
                features = null
            )

            // When
            val updated = propertyMapper.applyUpdate(testProperty, updateRequest)

            // Then
            assertEquals(testProperty.features, updated.features)
        }
    }

    @Nested
    @DisplayName("toResponseList tests")
    inner class ToResponseListTests {

        @Test
        fun `should map list of properties`() {
            // Given
            val property2 = testProperty.copy(id = 2L, title = "Second Property")
            val properties = listOf(testProperty, property2)
            every { propertyMediaMapper.toResponse(any()) } returns testMediaResponse

            // When
            val responses = propertyMapper.toResponseList(properties)

            // Then
            assertEquals(2, responses.size)
        }

        @Test
        fun `should return empty list for empty input`() {
            // When
            val responses = propertyMapper.toResponseList(emptyList())

            // Then
            assertTrue(responses.isEmpty())
        }
    }

    @Nested
    @DisplayName("toSummaryResponseList tests")
    inner class ToSummaryResponseListTests {

        @Test
        fun `should map list of properties to summaries`() {
            // Given
            val property2 = testProperty.copy(id = 2L, title = "Second Property")
            val properties = listOf(testProperty, property2)

            // When
            val responses = propertyMapper.toSummaryResponseList(properties)

            // Then
            assertEquals(2, responses.size)
        }
    }
}
