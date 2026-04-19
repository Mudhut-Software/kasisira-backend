package com.mudhut.software.kasisira.properties.services

import com.mudhut.software.kasisira.owner_org.entities.OwnerOrg
import com.mudhut.software.kasisira.owner_org.entities.Permission
import com.mudhut.software.kasisira.owner_org.services.MembershipService
import com.mudhut.software.kasisira.owner_org.services.OwnerOrgService
import com.mudhut.software.kasisira.profiles.entities.AuthProvider
import com.mudhut.software.kasisira.profiles.entities.User
import com.mudhut.software.kasisira.properties.entities.*
import com.mudhut.software.kasisira.properties.mappers.PropertyMapper
import com.mudhut.software.kasisira.properties.models.request.CreatePropertyRequest
import com.mudhut.software.kasisira.properties.models.request.PropertySearchRequest
import com.mudhut.software.kasisira.properties.models.request.UpdatePropertyRequest
import com.mudhut.software.kasisira.properties.models.response.PropertyOrgResponse
import com.mudhut.software.kasisira.properties.models.response.PropertyResponse
import com.mudhut.software.kasisira.properties.models.response.PropertySummaryResponse
import com.mudhut.software.kasisira.properties.repositories.PropertyRepository
import com.mudhut.software.kasisira.utils.exceptions.InvalidPropertyConfigurationException
import com.mudhut.software.kasisira.utils.exceptions.PermissionDeniedException
import com.mudhut.software.kasisira.utils.exceptions.PropertyNotFoundException
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.time.Instant
import java.util.*

@ExtendWith(MockKExtension::class)
class PropertyServiceImplTest {

    @MockK
    private lateinit var propertyRepository: PropertyRepository

    @MockK
    private lateinit var propertyMapper: PropertyMapper

    @MockK
    private lateinit var membershipService: MembershipService

    @MockK
    private lateinit var ownerOrgService: OwnerOrgService

    @InjectMockKs
    private lateinit var propertyService: PropertyServiceImpl

    private lateinit var testUser: User
    private lateinit var testOrg: OwnerOrg
    private lateinit var testProperty: Property
    private lateinit var testPropertyResponse: PropertyResponse
    private lateinit var testPropertySummaryResponse: PropertySummaryResponse

    @BeforeEach
    fun setUp() {
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
            status = PropertyStatus.DRAFT,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        testPropertyResponse = PropertyResponse(
            id = 1L,
            org = PropertyOrgResponse(10L, "Test Org", false),
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
            address = null,
            latitude = null,
            longitude = null,
            bedrooms = null,
            bathrooms = null,
            landSize = null,
            landSizeUnit = null,
            builtArea = null,
            yearBuilt = null,
            features = emptyList(),
            status = PropertyStatus.DRAFT,
            viewCount = 0,
            media = emptyList(),
            primaryImage = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        testPropertySummaryResponse = PropertySummaryResponse(
            id = 1L,
            title = "Beautiful House",
            propertyType = PropertyType.HOUSE,
            listingType = ListingType.FOR_SALE,
            rentalDuration = null,
            price = BigDecimal("500000000"),
            currency = "UGX",
            city = "Kampala",
            district = "Wakiso",
            bedrooms = null,
            bathrooms = null,
            landSize = null,
            status = PropertyStatus.DRAFT,
            primaryImage = null,
            viewCount = 0,
            createdAt = Instant.now()
        )

        // Happy-path defaults: permission granted, org lookup returns testOrg.
        every { membershipService.requirePermission(any(), any(), any()) } just Runs
        every { ownerOrgService.getOrgById(any()) } returns testOrg
    }

    @Nested
    @DisplayName("createProperty tests")
    inner class CreatePropertyTests {

        private lateinit var createRequest: CreatePropertyRequest

        @BeforeEach
        fun setUp() {
            createRequest = CreatePropertyRequest(
                title = "Beautiful House",
                description = "A beautiful house for sale",
                propertyType = PropertyType.HOUSE,
                listingType = ListingType.FOR_SALE,
                price = BigDecimal("500000000"),
                city = "Kampala",
                district = "Wakiso",
                address = "123 Main Street",
                latitude = BigDecimal("0.3476"),
                longitude = BigDecimal("32.5825")
            )
        }

        @Test
        fun `should create property successfully`() {
            // Given
            every { propertyMapper.fromCreateRequest(createRequest, testOrg) } returns testProperty
            every { propertyRepository.save(any()) } returns testProperty
            every { propertyMapper.toResponse(testProperty) } returns testPropertyResponse

            // When
            val result = propertyService.createProperty(1L, 10L, createRequest)

            // Then
            Assertions.assertNotNull(result)
            Assertions.assertEquals(testPropertyResponse, result)
            verify { membershipService.requirePermission(1L, 10L, Permission.MANAGE_LISTINGS) }
            verify { propertyRepository.save(any()) }
        }

        @Test
        fun `should throw PermissionDeniedException when caller lacks MANAGE_LISTINGS`() {
            // Given
            every {
                membershipService.requirePermission(999L, 10L, Permission.MANAGE_LISTINGS)
            } throws PermissionDeniedException("no permission")

            // When/Then
            assertThrows<PermissionDeniedException> {
                propertyService.createProperty(999L, 10L, createRequest)
            }
        }

        @Test
        fun `should throw InvalidPropertyConfigurationException for land plot rental`() {
            // Given
            val invalidRequest = createRequest.copy(
                propertyType = PropertyType.LAND,
                listingType = ListingType.FOR_RENT,
                rentalDuration = RentalDuration.MONTHLY
            )

            // When/Then
            assertThrows<InvalidPropertyConfigurationException> {
                propertyService.createProperty(1L, 10L, invalidRequest)
            }
        }

        @Test
        fun `should throw InvalidPropertyConfigurationException when rental has no duration`() {
            // Given
            val invalidRequest = createRequest.copy(
                listingType = ListingType.FOR_RENT,
                rentalDuration = null
            )

            // When/Then
            assertThrows<InvalidPropertyConfigurationException> {
                propertyService.createProperty(1L, 10L, invalidRequest)
            }
        }

        @Test
        fun `should throw InvalidPropertyConfigurationException when sale has furnishing status`() {
            // Given
            val invalidRequest = createRequest.copy(
                listingType = ListingType.FOR_SALE,
                furnishingStatus = FurnishingStatus.FURNISHED
            )

            // When/Then
            assertThrows<InvalidPropertyConfigurationException> {
                propertyService.createProperty(1L, 10L, invalidRequest)
            }
        }

        @Test
        fun `should create rental property with valid configuration`() {
            // Given
            val rentalRequest = createRequest.copy(
                listingType = ListingType.FOR_RENT,
                rentalDuration = RentalDuration.MONTHLY,
                furnishingStatus = FurnishingStatus.FURNISHED
            )
            val rentalProperty = testProperty.copy(
                listingType = ListingType.FOR_RENT,
                rentalDuration = RentalDuration.MONTHLY,
                furnishingStatus = FurnishingStatus.FURNISHED
            )
            every { propertyMapper.fromCreateRequest(rentalRequest, testOrg) } returns rentalProperty
            every { propertyRepository.save(any()) } returns rentalProperty
            every { propertyMapper.toResponse(rentalProperty) } returns testPropertyResponse

            // When
            val result = propertyService.createProperty(1L, 10L, rentalRequest)

            // Then
            Assertions.assertNotNull(result)
            verify { propertyRepository.save(any()) }
        }
    }

    @Nested
    @DisplayName("getPropertyById tests")
    inner class GetPropertyByIdTests {

        @Test
        fun `should return property when found`() {
            // Given
            every { propertyRepository.findById(1L) } returns Optional.of(testProperty)
            every { propertyMapper.toResponse(testProperty) } returns testPropertyResponse

            // When
            val result = propertyService.getPropertyById(1L)

            // Then
            Assertions.assertEquals(testPropertyResponse, result)
        }

        @Test
        fun `should throw PropertyNotFoundException when not found`() {
            // Given
            every { propertyRepository.findById(999L) } returns Optional.empty()

            // When/Then
            assertThrows<PropertyNotFoundException> {
                propertyService.getPropertyById(999L)
            }
        }
    }

    @Nested
    @DisplayName("updateProperty tests")
    inner class UpdatePropertyTests {

        private lateinit var updateRequest: UpdatePropertyRequest

        @BeforeEach
        fun setUp() {
            updateRequest = UpdatePropertyRequest(
                title = "Updated Title",
                price = BigDecimal("600000000")
            )
        }

        @Test
        fun `should update property successfully`() {
            // Given
            val updatedProperty = testProperty.copy(title = "Updated Title")
            every { propertyRepository.findById(1L) } returns Optional.of(testProperty)
            every { propertyMapper.applyUpdate(testProperty, updateRequest) } returns updatedProperty
            every { propertyRepository.save(any()) } returns updatedProperty
            every { propertyMapper.toResponse(updatedProperty) } returns testPropertyResponse.copy(title = "Updated Title")

            // When
            val result = propertyService.updateProperty(1L, 1L, updateRequest)

            // Then
            Assertions.assertNotNull(result)
            verify { membershipService.requirePermission(1L, testOrg.id, Permission.MANAGE_LISTINGS) }
            verify { propertyRepository.save(any()) }
        }

        @Test
        fun `should throw PropertyNotFoundException when property not found`() {
            // Given
            every { propertyRepository.findById(999L) } returns Optional.empty()

            // When/Then
            assertThrows<PropertyNotFoundException> {
                propertyService.updateProperty(999L, 1L, updateRequest)
            }
        }

        @Test
        fun `should throw PermissionDeniedException when caller lacks MANAGE_LISTINGS`() {
            // Given
            every { propertyRepository.findById(1L) } returns Optional.of(testProperty)
            every {
                membershipService.requirePermission(999L, testOrg.id, Permission.MANAGE_LISTINGS)
            } throws PermissionDeniedException("no permission")

            // When/Then
            assertThrows<PermissionDeniedException> {
                propertyService.updateProperty(1L, 999L, updateRequest)
            }
        }
    }

    @Nested
    @DisplayName("deleteProperty tests")
    inner class DeletePropertyTests {

        @Test
        fun `should delete property successfully`() {
            // Given
            every { propertyRepository.findById(1L) } returns Optional.of(testProperty)
            every { propertyRepository.delete(testProperty) } just runs

            // When
            propertyService.deleteProperty(1L, 1L)

            // Then
            verify { membershipService.requirePermission(1L, testOrg.id, Permission.MANAGE_LISTINGS) }
            verify { propertyRepository.delete(testProperty) }
        }

        @Test
        fun `should throw PropertyNotFoundException when property not found`() {
            // Given
            every { propertyRepository.findById(999L) } returns Optional.empty()

            // When/Then
            assertThrows<PropertyNotFoundException> {
                propertyService.deleteProperty(999L, 1L)
            }
        }

        @Test
        fun `should throw PermissionDeniedException when caller lacks MANAGE_LISTINGS`() {
            // Given
            every { propertyRepository.findById(1L) } returns Optional.of(testProperty)
            every {
                membershipService.requirePermission(999L, testOrg.id, Permission.MANAGE_LISTINGS)
            } throws PermissionDeniedException("no permission")

            // When/Then
            assertThrows<PermissionDeniedException> {
                propertyService.deleteProperty(1L, 999L)
            }
        }
    }

    @Nested
    @DisplayName("getPropertiesByOrg tests")
    inner class GetPropertiesByOrgTests {

        @Test
        fun `should return properties for org`() {
            // Given
            val pageable = PageRequest.of(0, 20)
            val page = PageImpl(listOf(testProperty))
            every { propertyRepository.findByOwnerOrgId(10L, pageable) } returns page
            every { propertyMapper.toSummaryResponse(testProperty) } returns testPropertySummaryResponse

            // When
            val result = propertyService.getPropertiesByOrg(10L, pageable)

            // Then
            Assertions.assertEquals(1, result.totalElements)
        }

        @Test
        fun `should return empty page when no properties`() {
            // Given
            val pageable = PageRequest.of(0, 20)
            val emptyPage = PageImpl<Property>(emptyList())
            every { propertyRepository.findByOwnerOrgId(10L, pageable) } returns emptyPage

            // When
            val result = propertyService.getPropertiesByOrg(10L, pageable)

            // Then
            Assertions.assertEquals(0, result.totalElements)
        }
    }

    @Nested
    @DisplayName("searchProperties tests")
    inner class SearchPropertiesTests {

        @Test
        fun `should search properties with filters`() {
            // Given
            val pageable = PageRequest.of(0, 20)
            val searchRequest = PropertySearchRequest(
                propertyType = PropertyType.HOUSE,
                listingType = ListingType.FOR_SALE,
                city = "Kampala",
                minPrice = BigDecimal("100000000"),
                maxPrice = BigDecimal("1000000000")
            )
            val page = PageImpl(listOf(testProperty))
            every {
                propertyRepository.searchProperties(
                    status = PropertyStatus.ACTIVE,
                    propertyType = PropertyType.HOUSE,
                    listingType = ListingType.FOR_SALE,
                    city = "Kampala",
                    minPrice = BigDecimal("100000000"),
                    maxPrice = BigDecimal("1000000000"),
                    bedrooms = null,
                    pageable = pageable
                )
            } returns page
            every { propertyMapper.toSummaryResponse(testProperty) } returns testPropertySummaryResponse

            // When
            val result = propertyService.searchProperties(searchRequest, pageable)

            // Then
            Assertions.assertEquals(1, result.totalElements)
        }
    }

    @Nested
    @DisplayName("updatePropertyStatus tests")
    inner class UpdatePropertyStatusTests {

        @Test
        fun `should update property status successfully`() {
            // Given
            val updatedProperty = testProperty.copy(status = PropertyStatus.ACTIVE)
            every { propertyRepository.findById(1L) } returns Optional.of(testProperty)
            every { propertyRepository.save(any()) } returns updatedProperty
            every { propertyMapper.toResponse(updatedProperty) } returns testPropertyResponse.copy(status = PropertyStatus.ACTIVE)

            // When
            val result = propertyService.updatePropertyStatus(1L, 1L, PropertyStatus.ACTIVE)

            // Then
            Assertions.assertNotNull(result)
            verify { membershipService.requirePermission(1L, testOrg.id, Permission.MANAGE_LISTINGS) }
            verify { propertyRepository.save(any()) }
        }

        @Test
        fun `should throw PermissionDeniedException when caller lacks MANAGE_LISTINGS`() {
            // Given
            every { propertyRepository.findById(1L) } returns Optional.of(testProperty)
            every {
                membershipService.requirePermission(999L, testOrg.id, Permission.MANAGE_LISTINGS)
            } throws PermissionDeniedException("no permission")

            // When/Then
            assertThrows<PermissionDeniedException> {
                propertyService.updatePropertyStatus(1L, 999L, PropertyStatus.ACTIVE)
            }
        }
    }

    @Nested
    @DisplayName("getPropertyStatsByOrg tests")
    inner class GetPropertyStatsByOrgTests {

        @Test
        fun `should return property stats for org`() {
            // Given
            every { propertyRepository.countByOwnerOrgId(10L) } returns 10
            every { propertyRepository.countByOwnerOrgIdAndStatus(10L, PropertyStatus.ACTIVE) } returns 5
            every { propertyRepository.countByOwnerOrgIdAndStatus(10L, PropertyStatus.DRAFT) } returns 3
            every { propertyRepository.countByOwnerOrgIdAndStatus(10L, PropertyStatus.SOLD) } returns 1
            every { propertyRepository.countByOwnerOrgIdAndStatus(10L, PropertyStatus.RENTED) } returns 1

            // When
            val result = propertyService.getPropertyStatsByOrg(10L)

            // Then
            Assertions.assertEquals(10L, result["total"])
            Assertions.assertEquals(5L, result["active"])
            Assertions.assertEquals(3L, result["draft"])
            Assertions.assertEquals(1L, result["sold"])
            Assertions.assertEquals(1L, result["rented"])
        }
    }

    @Nested
    @DisplayName("incrementViewCount tests")
    inner class IncrementViewCountTests {

        @Test
        fun `should increment view count`() {
            // Given
            every { propertyRepository.incrementViewCount(1L) } just runs

            // When
            propertyService.incrementViewCount(1L)

            // Then
            verify { propertyRepository.incrementViewCount(1L) }
        }
    }
}
