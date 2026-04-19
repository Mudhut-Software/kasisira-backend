package com.mudhut.software.kasisira.properties.repositories

import com.mudhut.software.kasisira.owner_org.entities.OwnerOrg
import com.mudhut.software.kasisira.profiles.entities.AuthProvider
import com.mudhut.software.kasisira.profiles.entities.User
import com.mudhut.software.kasisira.properties.entities.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal

@DataJpaTest
@ActiveProfiles("testing")
class PropertyRepositoryTest {

    @Autowired
    private lateinit var entityManager: TestEntityManager

    @Autowired
    private lateinit var propertyRepository: PropertyRepository

    private lateinit var testUser: User
    private lateinit var testOrg: OwnerOrg
    private lateinit var testProperty: Property

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

        testOrg = OwnerOrg(
            id = 0,
            creator = testUser,
            name = "Test Org"
        )
        testOrg = entityManager.persistAndFlush(testOrg)

        testProperty = Property(
            id = 0,
            ownerOrg = testOrg,
            title = "Beautiful House",
            description = "A beautiful house for sale in Kampala",
            propertyType = PropertyType.HOUSE,
            listingType = ListingType.FOR_SALE,
            price = BigDecimal("500000000"),
            currency = "UGX",
            city = "Kampala",
            district = "Wakiso",
            address = "123 Main Street",
            latitude = BigDecimal("0.3476"),
            longitude = BigDecimal("32.5825"),
            bedrooms = 3,
            bathrooms = 2,
            status = PropertyStatus.ACTIVE
        )
    }

    @Nested
    @DisplayName("findByOwnerId tests")
    inner class FindByOwnerIdTests {

        @Test
        fun `should find properties by owner id`() {
            // Given
            val savedProperty = entityManager.persistAndFlush(testProperty)
            val pageable = PageRequest.of(0, 20)

            // When
            val result = propertyRepository.findByOwnerOrgId(testOrg.id, pageable)

            // Then
            assertEquals(1, result.totalElements)
            assertEquals(savedProperty.id, result.content[0].id)
        }

        @Test
        fun `should return empty page when owner has no properties`() {
            // Given
            val pageable = PageRequest.of(0, 20)

            // When
            val result = propertyRepository.findByOwnerOrgId(testOrg.id, pageable)

            // Then
            assertEquals(0, result.totalElements)
        }
    }

    @Nested
    @DisplayName("findByStatus tests")
    inner class FindByStatusTests {

        @Test
        fun `should find properties by status`() {
            // Given
            entityManager.persistAndFlush(testProperty)
            val pageable = PageRequest.of(0, 20)

            // When
            val result = propertyRepository.findByStatus(PropertyStatus.ACTIVE, pageable)

            // Then
            assertEquals(1, result.totalElements)
        }

        @Test
        fun `should return empty when no properties with status`() {
            // Given
            entityManager.persistAndFlush(testProperty)
            val pageable = PageRequest.of(0, 20)

            // When
            val result = propertyRepository.findByStatus(PropertyStatus.SOLD, pageable)

            // Then
            assertEquals(0, result.totalElements)
        }
    }

    @Nested
    @DisplayName("findByPropertyType tests")
    inner class FindByPropertyTypeTests {

        @Test
        fun `should find properties by type`() {
            // Given
            entityManager.persistAndFlush(testProperty)
            val pageable = PageRequest.of(0, 20)

            // When
            val result = propertyRepository.findByPropertyType(PropertyType.HOUSE, pageable)

            // Then
            assertEquals(1, result.totalElements)
        }

        @Test
        fun `should return empty when no properties of type`() {
            // Given
            entityManager.persistAndFlush(testProperty)
            val pageable = PageRequest.of(0, 20)

            // When
            val result = propertyRepository.findByPropertyType(PropertyType.LAND, pageable)

            // Then
            assertEquals(0, result.totalElements)
        }
    }

    @Nested
    @DisplayName("findByListingType tests")
    inner class FindByListingTypeTests {

        @Test
        fun `should find properties by listing type`() {
            // Given
            entityManager.persistAndFlush(testProperty)
            val pageable = PageRequest.of(0, 20)

            // When
            val result = propertyRepository.findByListingType(ListingType.FOR_SALE, pageable)

            // Then
            assertEquals(1, result.totalElements)
        }

        @Test
        fun `should return empty when no properties with listing type`() {
            // Given
            entityManager.persistAndFlush(testProperty)
            val pageable = PageRequest.of(0, 20)

            // When
            val result = propertyRepository.findByListingType(ListingType.FOR_RENT, pageable)

            // Then
            assertEquals(0, result.totalElements)
        }
    }

    @Nested
    @DisplayName("searchProperties tests")
    inner class SearchPropertiesTests {

        @Test
        fun `should search properties with all filters`() {
            // Given
            entityManager.persistAndFlush(testProperty)
            val pageable = PageRequest.of(0, 20)

            // When
            val result = propertyRepository.searchProperties(
                status = PropertyStatus.ACTIVE,
                propertyType = PropertyType.HOUSE,
                listingType = ListingType.FOR_SALE,
                city = "Kampala",
                minPrice = BigDecimal("100000000"),
                maxPrice = BigDecimal("1000000000"),
                bedrooms = 2,
                pageable = pageable
            )

            // Then
            assertEquals(1, result.totalElements)
        }

        @Test
        fun `should search with null filters`() {
            // Given
            entityManager.persistAndFlush(testProperty)
            val pageable = PageRequest.of(0, 20)

            // When
            val result = propertyRepository.searchProperties(
                status = PropertyStatus.ACTIVE,
                propertyType = null,
                listingType = null,
                city = null,
                minPrice = null,
                maxPrice = null,
                bedrooms = null,
                pageable = pageable
            )

            // Then
            assertEquals(1, result.totalElements)
        }

        @Test
        fun `should filter by price range`() {
            // Given
            entityManager.persistAndFlush(testProperty)
            val pageable = PageRequest.of(0, 20)

            // When - price too high
            val result = propertyRepository.searchProperties(
                status = PropertyStatus.ACTIVE,
                propertyType = null,
                listingType = null,
                city = null,
                minPrice = BigDecimal("600000000"),
                maxPrice = null,
                bedrooms = null,
                pageable = pageable
            )

            // Then
            assertEquals(0, result.totalElements)
        }

        @Test
        fun `should filter by minimum bedrooms`() {
            // Given
            entityManager.persistAndFlush(testProperty)
            val pageable = PageRequest.of(0, 20)

            // When - require more bedrooms than available
            val result = propertyRepository.searchProperties(
                status = PropertyStatus.ACTIVE,
                propertyType = null,
                listingType = null,
                city = null,
                minPrice = null,
                maxPrice = null,
                bedrooms = 5,
                pageable = pageable
            )

            // Then
            assertEquals(0, result.totalElements)
        }
    }

    @Nested
    @DisplayName("countByOwnerId tests")
    inner class CountByOwnerIdTests {

        @Test
        fun `should count properties by owner`() {
            // Given
            entityManager.persistAndFlush(testProperty)
            val anotherProperty = Property(
                id = 0,
                ownerOrg = testOrg,
                title = "Another Property",
                description = "Another property description",
                propertyType = PropertyType.HOUSE,
                listingType = ListingType.FOR_SALE,
                price = BigDecimal("400000000"),
                currency = "UGX",
                city = "Kampala",
                district = "Wakiso",
                address = "123 Main Street",
                latitude = BigDecimal("0.3476"),
                longitude = BigDecimal("32.5825"),
                status = PropertyStatus.DRAFT
            )
            entityManager.persistAndFlush(anotherProperty)

            // When
            val count = propertyRepository.countByOwnerOrgId(testOrg.id)

            // Then
            assertEquals(2, count)
        }

        @Test
        fun `should return zero when owner has no properties`() {
            // When
            val count = propertyRepository.countByOwnerOrgId(testOrg.id)

            // Then
            assertEquals(0, count)
        }
    }

    @Nested
    @DisplayName("countByOwnerIdAndStatus tests")
    inner class CountByOwnerIdAndStatusTests {

        @Test
        fun `should count properties by owner and status`() {
            // Given
            entityManager.persistAndFlush(testProperty)
            val draftProperty = Property(
                id = 0,
                ownerOrg = testOrg,
                title = "Draft Property",
                description = "A draft property description",
                propertyType = PropertyType.HOUSE,
                listingType = ListingType.FOR_SALE,
                price = BigDecimal("400000000"),
                currency = "UGX",
                city = "Kampala",
                district = "Wakiso",
                address = "123 Main Street",
                latitude = BigDecimal("0.3476"),
                longitude = BigDecimal("32.5825"),
                status = PropertyStatus.DRAFT
            )
            entityManager.persistAndFlush(draftProperty)

            // When
            val activeCount = propertyRepository.countByOwnerOrgIdAndStatus(testOrg.id, PropertyStatus.ACTIVE)
            val draftCount = propertyRepository.countByOwnerOrgIdAndStatus(testOrg.id, PropertyStatus.DRAFT)

            // Then
            assertEquals(1, activeCount)
            assertEquals(1, draftCount)
        }
    }

    @Nested
    @DisplayName("CRUD operations")
    inner class CrudOperations {

        @Test
        fun `should save and retrieve property`() {
            // Given/When
            val savedProperty = propertyRepository.save(testProperty)

            // Then
            Assertions.assertNotNull(savedProperty.id)
            assertTrue(savedProperty.id > 0)

            val retrieved = propertyRepository.findById(savedProperty.id)
            assertTrue(retrieved.isPresent)
            assertEquals(savedProperty.title, retrieved.get().title)
        }

        @Test
        fun `should delete property`() {
            // Given
            val savedProperty = entityManager.persistAndFlush(testProperty)

            // When
            propertyRepository.deleteById(savedProperty.id)
            entityManager.flush()

            // Then
            val result = propertyRepository.findById(savedProperty.id)
            assertFalse(result.isPresent)
        }

        @Test
        fun `should update property`() {
            // Given
            val savedProperty = entityManager.persistAndFlush(testProperty)

            // When
            val updatedProperty = savedProperty.copy(title = "Updated Title")
            propertyRepository.save(updatedProperty)
            entityManager.flush()
            entityManager.clear()

            // Then
            val retrieved = propertyRepository.findById(savedProperty.id)
            assertTrue(retrieved.isPresent)
            assertEquals("Updated Title", retrieved.get().title)
        }
    }
}
