package com.mudhut.software.kasisira.properties.services

import com.mudhut.software.kasisira.owner_org.entities.OwnerOrg
import com.mudhut.software.kasisira.owner_org.entities.Permission
import com.mudhut.software.kasisira.owner_org.services.MembershipService
import com.mudhut.software.kasisira.profiles.entities.AuthProvider
import com.mudhut.software.kasisira.profiles.entities.User
import com.mudhut.software.kasisira.properties.entities.*
import com.mudhut.software.kasisira.properties.mappers.PropertyMediaMapper
import com.mudhut.software.kasisira.properties.models.request.AddMediaRequest
import com.mudhut.software.kasisira.properties.models.request.UpdateMediaRequest
import com.mudhut.software.kasisira.properties.models.response.PropertyMediaResponse
import com.mudhut.software.kasisira.properties.repositories.PropertyMediaRepository
import com.mudhut.software.kasisira.properties.repositories.PropertyRepository
import com.mudhut.software.kasisira.utils.exceptions.MediaNotFoundException
import com.mudhut.software.kasisira.utils.exceptions.PermissionDeniedException
import com.mudhut.software.kasisira.utils.exceptions.PropertyNotFoundException
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.Instant
import java.util.*

@ExtendWith(MockKExtension::class)
class PropertyMediaServiceImplTest {

    @MockK
    private lateinit var propertyMediaRepository: PropertyMediaRepository

    @MockK
    private lateinit var propertyRepository: PropertyRepository

    @MockK
    private lateinit var propertyMediaMapper: PropertyMediaMapper

    @MockK
    private lateinit var membershipService: MembershipService

    @InjectMockKs
    private lateinit var propertyMediaService: PropertyMediaServiceImpl

    private lateinit var testUser: User
    private lateinit var testOrg: OwnerOrg
    private lateinit var testProperty: Property
    private lateinit var testMedia: PropertyMedia
    private lateinit var testMediaResponse: PropertyMediaResponse

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
            status = PropertyStatus.DRAFT
        )

        testMedia = PropertyMedia(
            id = 1L,
            property = testProperty,
            mediaType = MediaType.IMAGE,
            url = "https://example.com/image1.jpg",
            description = "Front view",
            isPrimary = false,
            displayOrder = 0,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        testMediaResponse = PropertyMediaResponse(
            id = 1L,
            mediaType = MediaType.IMAGE,
            url = "https://example.com/image1.jpg",
            thumbnailUrl = null,
            description = "Front view",
            isPrimary = false,
            displayOrder = 0,
            createdAt = Instant.now()
        )

        // Happy-path default: permission granted. Tests that need a denial stub override per test.
        every { membershipService.requirePermission(any(), any(), any()) } just Runs
    }

    @Nested
    @DisplayName("addMedia tests")
    inner class AddMediaTests {

        private lateinit var addMediaRequest: AddMediaRequest

        @BeforeEach
        fun setUp() {
            addMediaRequest = AddMediaRequest(
                mediaType = MediaType.IMAGE,
                url = "https://example.com/image1.jpg",
                description = "Front view",
                isPrimary = false,
                displayOrder = 0
            )
        }

        @Test
        fun `should add media successfully`() {
            // Given
            every { propertyRepository.findById(1L) } returns Optional.of(testProperty)
            every { propertyMediaMapper.fromAddRequest(addMediaRequest, testProperty) } returns testMedia
            every { propertyMediaRepository.save(any()) } returns testMedia
            every { propertyMediaMapper.toResponse(testMedia) } returns testMediaResponse

            // When
            val result = propertyMediaService.addMedia(1L, 1L, addMediaRequest)

            // Then
            Assertions.assertNotNull(result)
            Assertions.assertEquals(testMediaResponse, result)
            verify { membershipService.requirePermission(1L, testOrg.id, Permission.MANAGE_LISTINGS) }
            verify { propertyMediaRepository.save(any()) }
        }

        @Test
        fun `should clear primary flag when adding primary media`() {
            // Given
            val primaryRequest = addMediaRequest.copy(isPrimary = true)
            every { propertyRepository.findById(1L) } returns Optional.of(testProperty)
            every { propertyMediaRepository.clearPrimaryFlagExcept(1L, 0) } just runs
            every { propertyMediaMapper.fromAddRequest(primaryRequest, testProperty) } returns testMedia.copy(isPrimary = true)
            every { propertyMediaRepository.save(any()) } returns testMedia.copy(isPrimary = true)
            every { propertyMediaMapper.toResponse(any()) } returns testMediaResponse.copy(isPrimary = true)

            // When
            val result = propertyMediaService.addMedia(1L, 1L, primaryRequest)

            // Then
            Assertions.assertNotNull(result)
            verify { propertyMediaRepository.clearPrimaryFlagExcept(1L, 0) }
        }

        @Test
        fun `should throw PropertyNotFoundException when property not found`() {
            // Given
            every { propertyRepository.findById(999L) } returns Optional.empty()

            // When/Then
            assertThrows<PropertyNotFoundException> {
                propertyMediaService.addMedia(999L, 1L, addMediaRequest)
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
                propertyMediaService.addMedia(1L, 999L, addMediaRequest)
            }
        }
    }

    @Nested
    @DisplayName("getMediaByPropertyId tests")
    inner class GetMediaByPropertyIdTests {

        @Test
        fun `should return media for property`() {
            // Given
            every { propertyMediaRepository.findByPropertyIdOrderByDisplayOrder(1L) } returns listOf(testMedia)
            every { propertyMediaMapper.toResponse(testMedia) } returns testMediaResponse

            // When
            val result = propertyMediaService.getMediaByPropertyId(1L)

            // Then
            Assertions.assertEquals(1, result.size)
            Assertions.assertEquals(testMediaResponse, result[0])
        }

        @Test
        fun `should return empty list when no media`() {
            // Given
            every { propertyMediaRepository.findByPropertyIdOrderByDisplayOrder(1L) } returns emptyList()

            // When
            val result = propertyMediaService.getMediaByPropertyId(1L)

            // Then
            Assertions.assertTrue(result.isEmpty())
        }
    }

    @Nested
    @DisplayName("updateMedia tests")
    inner class UpdateMediaTests {

        private lateinit var updateMediaRequest: UpdateMediaRequest

        @BeforeEach
        fun setUp() {
            updateMediaRequest = UpdateMediaRequest(
                description = "Updated description",
                displayOrder = 1
            )
        }

        @Test
        fun `should update media successfully`() {
            // Given
            val updatedMedia = testMedia.copy(description = "Updated description", displayOrder = 1)
            every { propertyMediaRepository.findById(1L) } returns Optional.of(testMedia)
            every { propertyMediaRepository.save(any()) } returns updatedMedia
            every { propertyMediaMapper.toResponse(updatedMedia) } returns testMediaResponse.copy(description = "Updated description")

            // When
            val result = propertyMediaService.updateMedia(1L, 1L, updateMediaRequest)

            // Then
            Assertions.assertNotNull(result)
            verify { membershipService.requirePermission(1L, testOrg.id, Permission.MANAGE_LISTINGS) }
            verify { propertyMediaRepository.save(any()) }
        }

        @Test
        fun `should throw MediaNotFoundException when media not found`() {
            // Given
            every { propertyMediaRepository.findById(999L) } returns Optional.empty()

            // When/Then
            assertThrows<MediaNotFoundException> {
                propertyMediaService.updateMedia(999L, 1L, updateMediaRequest)
            }
        }

        @Test
        fun `should throw PermissionDeniedException when caller lacks MANAGE_LISTINGS`() {
            // Given
            every { propertyMediaRepository.findById(1L) } returns Optional.of(testMedia)
            every {
                membershipService.requirePermission(999L, testOrg.id, Permission.MANAGE_LISTINGS)
            } throws PermissionDeniedException("no permission")

            // When/Then
            assertThrows<PermissionDeniedException> {
                propertyMediaService.updateMedia(1L, 999L, updateMediaRequest)
            }
        }
    }

    @Nested
    @DisplayName("deleteMedia tests")
    inner class DeleteMediaTests {

        @Test
        fun `should delete media successfully`() {
            // Given
            every { propertyMediaRepository.findById(1L) } returns Optional.of(testMedia)
            every { propertyMediaRepository.delete(testMedia) } just runs

            // When
            propertyMediaService.deleteMedia(1L, 1L)

            // Then
            verify { membershipService.requirePermission(1L, testOrg.id, Permission.MANAGE_LISTINGS) }
            verify { propertyMediaRepository.delete(testMedia) }
        }

        @Test
        fun `should throw MediaNotFoundException when media not found`() {
            // Given
            every { propertyMediaRepository.findById(999L) } returns Optional.empty()

            // When/Then
            assertThrows<MediaNotFoundException> {
                propertyMediaService.deleteMedia(999L, 1L)
            }
        }

        @Test
        fun `should throw PermissionDeniedException when caller lacks MANAGE_LISTINGS`() {
            // Given
            every { propertyMediaRepository.findById(1L) } returns Optional.of(testMedia)
            every {
                membershipService.requirePermission(999L, testOrg.id, Permission.MANAGE_LISTINGS)
            } throws PermissionDeniedException("no permission")

            // When/Then
            assertThrows<PermissionDeniedException> {
                propertyMediaService.deleteMedia(1L, 999L)
            }
        }
    }

    @Nested
    @DisplayName("setAsPrimary tests")
    inner class SetAsPrimaryTests {

        @Test
        fun `should set media as primary successfully`() {
            // Given
            val primaryMedia = testMedia.copy(isPrimary = true)
            every { propertyMediaRepository.findById(1L) } returns Optional.of(testMedia)
            every { propertyMediaRepository.clearPrimaryFlagExcept(1L, 1L) } just runs
            every { propertyMediaRepository.save(any()) } returns primaryMedia
            every { propertyMediaMapper.toResponse(primaryMedia) } returns testMediaResponse.copy(isPrimary = true)

            // When
            val result = propertyMediaService.setAsPrimary(1L, 1L)

            // Then
            Assertions.assertNotNull(result)
            verify { membershipService.requirePermission(1L, testOrg.id, Permission.MANAGE_LISTINGS) }
            verify { propertyMediaRepository.clearPrimaryFlagExcept(1L, 1L) }
        }

        @Test
        fun `should throw MediaNotFoundException when media not found`() {
            // Given
            every { propertyMediaRepository.findById(999L) } returns Optional.empty()

            // When/Then
            assertThrows<MediaNotFoundException> {
                propertyMediaService.setAsPrimary(999L, 1L)
            }
        }

        @Test
        fun `should throw PermissionDeniedException when caller lacks MANAGE_LISTINGS`() {
            // Given
            every { propertyMediaRepository.findById(1L) } returns Optional.of(testMedia)
            every {
                membershipService.requirePermission(999L, testOrg.id, Permission.MANAGE_LISTINGS)
            } throws PermissionDeniedException("no permission")

            // When/Then
            assertThrows<PermissionDeniedException> {
                propertyMediaService.setAsPrimary(1L, 999L)
            }
        }
    }

    @Nested
    @DisplayName("reorderMedia tests")
    inner class ReorderMediaTests {

        @Test
        fun `should reorder media successfully`() {
            // Given
            val media2 = testMedia.copy(id = 2L, displayOrder = 1)
            every { propertyRepository.findById(1L) } returns Optional.of(testProperty)
            every { propertyMediaRepository.findById(1L) } returns Optional.of(testMedia)
            every { propertyMediaRepository.findById(2L) } returns Optional.of(media2)
            every { propertyMediaRepository.save(any()) } answers { firstArg() }

            // When
            propertyMediaService.reorderMedia(1L, 1L, listOf(2L, 1L))

            // Then
            verify { membershipService.requirePermission(1L, testOrg.id, Permission.MANAGE_LISTINGS) }
            verify(exactly = 2) { propertyMediaRepository.save(any()) }
        }

        @Test
        fun `should throw PropertyNotFoundException when property not found`() {
            // Given
            every { propertyRepository.findById(999L) } returns Optional.empty()

            // When/Then
            assertThrows<PropertyNotFoundException> {
                propertyMediaService.reorderMedia(999L, 1L, listOf(1L, 2L))
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
                propertyMediaService.reorderMedia(1L, 999L, listOf(1L, 2L))
            }
        }

        @Test
        fun `should throw MediaNotFoundException when media not found during reorder`() {
            // Given
            every { propertyRepository.findById(1L) } returns Optional.of(testProperty)
            every { propertyMediaRepository.findById(999L) } returns Optional.empty()

            // When/Then
            assertThrows<MediaNotFoundException> {
                propertyMediaService.reorderMedia(1L, 1L, listOf(999L))
            }
        }
    }
}
