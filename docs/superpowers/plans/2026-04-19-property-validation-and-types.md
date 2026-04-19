# Property Create Validation, Location & Type Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Require location data on property creation, expand `PropertyType` with common dwelling types, default `furnishingStatus` to `UNFURNISHED`, and bound latitude/longitude to valid Earth coordinates.

**Architecture:** Four coordinated, ordered changes: (1) expand `PropertyType` enum, (2) tighten `CreatePropertyRequest` validation + add controller validation tests, (3) tighten `Property` entity columns and sweep test fixtures, (4) add a Flyway migration that applies matching `NOT NULL` constraints to the (empty) `properties` table.

**Tech Stack:** Kotlin 2.2, Spring Boot 4.0.0-M2, Jakarta Validation (`jakarta.validation.constraints`), JPA/Hibernate, Postgres, Flyway, JUnit 5, MockK, springmockk, Spring Security Test.

**Spec:** `docs/superpowers/specs/2026-04-19-property-validation-and-types-design.md`

---

## File Structure

**New files:**
- `src/test/kotlin/com/mudhut/software/kasisira/properties/entities/PropertyTypeTest.kt` — asserts the expanded enum has all 11 expected values (Task 1).
- `src/test/kotlin/com/mudhut/software/kasisira/properties/controllers/PropertyControllerTest.kt` — MockMvc validation tests for `POST /v1/orgs/{orgId}/properties` (Task 2).
- `src/main/resources/db/migration/V8__tighten_property_location.sql` — applies `NOT NULL` to `district`, `address`, `latitude`, `longitude` (Task 4).

**Modified files:**
- `src/main/kotlin/com/mudhut/software/kasisira/properties/entities/PropertyType.kt` — add nine dwelling-type values (Task 1).
- `src/main/kotlin/com/mudhut/software/kasisira/properties/models/request/CreatePropertyRequest.kt` — tighten district/address/lat/lng to required + bound lat/lng; default furnishingStatus (Task 2).
- `src/main/kotlin/com/mudhut/software/kasisira/properties/entities/Property.kt` — make the four location columns `nullable = false` with non-nullable Kotlin types (Task 3).
- `src/test/kotlin/com/mudhut/software/kasisira/properties/services/PropertyServiceImplTest.kt` — update `testProperty` fixture (Task 3).
- `src/test/kotlin/com/mudhut/software/kasisira/properties/repositories/PropertyRepositoryTest.kt` — update three `Property(` fixtures (Task 3).
- `src/test/kotlin/com/mudhut/software/kasisira/properties/services/PropertyMediaServiceImplTest.kt` — update `testProperty` fixture (Task 3).
- `src/test/kotlin/com/mudhut/software/kasisira/properties/repositories/PropertyMediaRepositoryTest.kt` — update `testProperty` fixture (Task 3).
- `src/test/kotlin/com/mudhut/software/kasisira/properties/mappers/PropertyMediaMapperTest.kt` — update `testProperty` fixture (Task 3).

**Unchanged:**
- `PropertyMapperTest.kt` fixture already supplies realistic `address`, `latitude`, `longitude` — no update needed.
- `UpdatePropertyRequest.kt` stays PATCH-style (all optional).
- `PropertyResponse.kt` / `PropertySummaryResponse.kt` stay as-is (their location fields can remain nullable — the mapper narrows non-nullable entity values into permissive DTO slots, which is always safe).
- `PropertyMapper.kt` — no code changes; types tighten transparently.
- `PropertyServiceImpl.kt` — `validatePropertyRequest` still holds; no new rules.

---

## Task 1: Expand PropertyType enum

**Files:**
- Create: `src/test/kotlin/com/mudhut/software/kasisira/properties/entities/PropertyTypeTest.kt`
- Modify: `src/main/kotlin/com/mudhut/software/kasisira/properties/entities/PropertyType.kt`

- [ ] **Step 1.1: Write the failing test**

Create `src/test/kotlin/com/mudhut/software/kasisira/properties/entities/PropertyTypeTest.kt`:

```kotlin
package com.mudhut.software.kasisira.properties.entities

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PropertyTypeTest {

    @Test
    fun `enum contains all expected dwelling types`() {
        val expected = setOf(
            "LAND",
            "HOUSE",
            "APARTMENT",
            "CONDO",
            "STUDIO",
            "BUNGALOW",
            "MANSION",
            "TOWNHOUSE",
            "DUPLEX",
            "PENTHOUSE",
            "VILLA"
        )
        val actual = PropertyType.values().map { it.name }.toSet()
        assertEquals(expected, actual)
    }
}
```

- [ ] **Step 1.2: Run the test to verify it fails**

```bash
cd kasisira-backend && ./gradlew test --tests "com.mudhut.software.kasisira.properties.entities.PropertyTypeTest"
```

Expected: FAIL — the actual set only contains `LAND` and `HOUSE`; the assertion reports the nine missing names.

- [ ] **Step 1.3: Expand the enum**

Replace the contents of `src/main/kotlin/com/mudhut/software/kasisira/properties/entities/PropertyType.kt`:

```kotlin
package com.mudhut.software.kasisira.properties.entities

enum class PropertyType {
    LAND,       // only sale, never rent (enforced in PropertyServiceImpl)
    HOUSE,      // generic standalone dwelling
    APARTMENT,
    CONDO,
    STUDIO,
    BUNGALOW,
    MANSION,
    TOWNHOUSE,
    DUPLEX,
    PENTHOUSE,
    VILLA
}
```

- [ ] **Step 1.4: Run the test to verify it passes**

```bash
cd kasisira-backend && ./gradlew test --tests "com.mudhut.software.kasisira.properties.entities.PropertyTypeTest"
```

Expected: PASS.

- [ ] **Step 1.5: Run the full test suite to confirm no regressions**

```bash
cd kasisira-backend && ./gradlew test
```

Expected: BUILD SUCCESSFUL. Adding enum values is additive; no existing `when` expression against `PropertyType` in the codebase (only uses are equality checks like `PropertyType.LAND`). A non-exhaustive `when` on `PropertyType` would surface as a compile warning — verify none appear.

- [ ] **Step 1.6: Commit**

```bash
cd kasisira-backend && git add src/main/kotlin/com/mudhut/software/kasisira/properties/entities/PropertyType.kt src/test/kotlin/com/mudhut/software/kasisira/properties/entities/PropertyTypeTest.kt
git commit -m "feat(properties): expand PropertyType enum with dwelling types"
```

---

## Task 2: Tighten CreatePropertyRequest + add PropertyControllerTest

**Files:**
- Modify: `src/main/kotlin/com/mudhut/software/kasisira/properties/models/request/CreatePropertyRequest.kt`
- Create: `src/test/kotlin/com/mudhut/software/kasisira/properties/controllers/PropertyControllerTest.kt`

- [ ] **Step 2.1: Create the controller test directory and write the failing test**

Create `src/test/kotlin/com/mudhut/software/kasisira/properties/controllers/PropertyControllerTest.kt`:

```kotlin
package com.mudhut.software.kasisira.properties.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.software.kasisira.owner_org.entities.OwnerOrg
import com.mudhut.software.kasisira.owner_org.services.MembershipService
import com.mudhut.software.kasisira.profiles.entities.AuthProvider
import com.mudhut.software.kasisira.profiles.entities.User
import com.mudhut.software.kasisira.properties.entities.FurnishingStatus
import com.mudhut.software.kasisira.properties.entities.ListingType
import com.mudhut.software.kasisira.properties.entities.PropertyStatus
import com.mudhut.software.kasisira.properties.entities.PropertyType
import com.mudhut.software.kasisira.properties.models.request.CreatePropertyRequest
import com.mudhut.software.kasisira.properties.models.response.PropertyOrgResponse
import com.mudhut.software.kasisira.properties.models.response.PropertyResponse
import com.mudhut.software.kasisira.properties.services.PropertyService
import com.mudhut.software.kasisira.security.UserPrincipal
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.math.BigDecimal

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("testing")
class PropertyControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    @MockkBean private lateinit var propertyService: PropertyService
    @MockkBean private lateinit var membershipService: MembershipService

    private val user = User(id = 1L, username = "u", email = "u@x.com", provider = AuthProvider.LOCAL)
    private val principal = UserPrincipal.create(user, setOf("TENANT", "OWNER"))

    private fun validBody(
        overrides: Map<String, Any?> = emptyMap()
    ): Map<String, Any?> {
        val base = mutableMapOf<String, Any?>(
            "title" to "Beautiful House",
            "description" to "A beautiful house for sale in Kampala with lots of space",
            "propertyType" to "HOUSE",
            "listingType" to "FOR_SALE",
            "price" to 500000000,
            "city" to "Kampala",
            "district" to "Wakiso",
            "address" to "123 Main Street",
            "latitude" to 0.3476,
            "longitude" to 32.5825
        )
        for ((k, v) in overrides) {
            if (v == null) base.remove(k) else base[k] = v
        }
        return base
    }

    private fun mockCreatedResponse(): PropertyResponse = PropertyResponse(
        id = 1L,
        org = PropertyOrgResponse(10L, "Test Org", false),
        title = "Beautiful House",
        description = "A beautiful house for sale in Kampala with lots of space",
        propertyType = PropertyType.HOUSE,
        listingType = ListingType.FOR_SALE,
        rentalDuration = null,
        furnishingStatus = FurnishingStatus.UNFURNISHED,
        price = BigDecimal("500000000"),
        currency = "UGX",
        city = "Kampala",
        district = "Wakiso",
        address = "123 Main Street",
        latitude = BigDecimal("0.3476"),
        longitude = BigDecimal("32.5825"),
        bedrooms = null,
        bathrooms = null,
        landSize = null,
        landSizeUnit = "sqm",
        builtArea = null,
        yearBuilt = null,
        features = emptyList(),
        status = PropertyStatus.DRAFT,
        viewCount = 0,
        media = emptyList(),
        primaryImage = null,
        createdAt = null,
        updatedAt = null
    )

    @Test
    fun `POST properties with all required fields returns 201`() {
        val captured = slot<CreatePropertyRequest>()
        every { propertyService.createProperty(1L, 10L, capture(captured)) } returns mockCreatedResponse()

        mockMvc.post("/v1/orgs/10/properties") {
            with(authentication(UsernamePasswordAuthenticationToken(principal, null, principal.authorities)))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(validBody())
        }.andExpect {
            status { isCreated() }
        }

        assertEquals("123 Main Street", captured.captured.address)
        assertEquals("Wakiso", captured.captured.district)
        assertEquals(BigDecimal("0.3476"), captured.captured.latitude)
        assertEquals(BigDecimal("32.5825"), captured.captured.longitude)
    }

    @Test
    fun `POST properties omitting address returns 400 VALIDATION_ERROR`() {
        mockMvc.post("/v1/orgs/10/properties") {
            with(authentication(UsernamePasswordAuthenticationToken(principal, null, principal.authorities)))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(validBody(mapOf("address" to null)))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errorCode") { value("VALIDATION_ERROR") }
            jsonPath("$.errors.address") { exists() }
        }
    }

    @Test
    fun `POST properties omitting district returns 400 VALIDATION_ERROR`() {
        mockMvc.post("/v1/orgs/10/properties") {
            with(authentication(UsernamePasswordAuthenticationToken(principal, null, principal.authorities)))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(validBody(mapOf("district" to null)))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errorCode") { value("VALIDATION_ERROR") }
            jsonPath("$.errors.district") { exists() }
        }
    }

    @Test
    fun `POST properties omitting latitude returns 400 VALIDATION_ERROR`() {
        mockMvc.post("/v1/orgs/10/properties") {
            with(authentication(UsernamePasswordAuthenticationToken(principal, null, principal.authorities)))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(validBody(mapOf("latitude" to null)))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errorCode") { value("VALIDATION_ERROR") }
            jsonPath("$.errors.latitude") { exists() }
        }
    }

    @Test
    fun `POST properties omitting longitude returns 400 VALIDATION_ERROR`() {
        mockMvc.post("/v1/orgs/10/properties") {
            with(authentication(UsernamePasswordAuthenticationToken(principal, null, principal.authorities)))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(validBody(mapOf("longitude" to null)))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errorCode") { value("VALIDATION_ERROR") }
            jsonPath("$.errors.longitude") { exists() }
        }
    }

    @Test
    fun `POST properties with out-of-range latitude returns 400 VALIDATION_ERROR`() {
        mockMvc.post("/v1/orgs/10/properties") {
            with(authentication(UsernamePasswordAuthenticationToken(principal, null, principal.authorities)))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(validBody(mapOf("latitude" to 91)))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errorCode") { value("VALIDATION_ERROR") }
            jsonPath("$.errors.latitude") { exists() }
        }
    }

    @Test
    fun `POST properties with out-of-range longitude returns 400 VALIDATION_ERROR`() {
        mockMvc.post("/v1/orgs/10/properties") {
            with(authentication(UsernamePasswordAuthenticationToken(principal, null, principal.authorities)))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(validBody(mapOf("longitude" to -181)))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errorCode") { value("VALIDATION_ERROR") }
            jsonPath("$.errors.longitude") { exists() }
        }
    }

    @Test
    fun `POST properties omitting furnishingStatus defaults to UNFURNISHED`() {
        val captured = slot<CreatePropertyRequest>()
        every { propertyService.createProperty(1L, 10L, capture(captured)) } returns mockCreatedResponse()

        mockMvc.post("/v1/orgs/10/properties") {
            with(authentication(UsernamePasswordAuthenticationToken(principal, null, principal.authorities)))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(validBody())
        }.andExpect {
            status { isCreated() }
        }

        assertEquals(FurnishingStatus.UNFURNISHED, captured.captured.furnishingStatus)
    }

    @Test
    fun `POST properties with new STUDIO PropertyType is accepted`() {
        val captured = slot<CreatePropertyRequest>()
        every { propertyService.createProperty(1L, 10L, capture(captured)) } returns mockCreatedResponse()

        mockMvc.post("/v1/orgs/10/properties") {
            with(authentication(UsernamePasswordAuthenticationToken(principal, null, principal.authorities)))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(validBody(mapOf("propertyType" to "STUDIO")))
        }.andExpect {
            status { isCreated() }
        }

        assertEquals(PropertyType.STUDIO, captured.captured.propertyType)
    }
}
```

- [ ] **Step 2.2: Run the test to verify it fails**

```bash
cd kasisira-backend && ./gradlew test --tests "com.mudhut.software.kasisira.properties.controllers.PropertyControllerTest"
```

Expected: FAIL. Several test methods (`omitting address`, `omitting district`, `omitting latitude`, `omitting longitude`, `out-of-range latitude`, `out-of-range longitude`, `defaults to UNFURNISHED`) will fail because the current DTO accepts any of those inputs. The happy-path and STUDIO tests may pass; that's expected.

- [ ] **Step 2.3: Tighten CreatePropertyRequest**

Replace the contents of `src/main/kotlin/com/mudhut/software/kasisira/properties/models/request/CreatePropertyRequest.kt`:

```kotlin
package com.mudhut.software.kasisira.properties.models.request

import com.mudhut.software.kasisira.properties.entities.FurnishingStatus
import com.mudhut.software.kasisira.properties.entities.ListingType
import com.mudhut.software.kasisira.properties.entities.PropertyType
import com.mudhut.software.kasisira.properties.entities.RentalDuration
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class CreatePropertyRequest(
    @field:NotBlank(message = "Title is required")
    @field:Size(min = 5, max = 200, message = "Title must be between 5 and 200 characters")
    val title: String,

    @field:NotBlank(message = "Description is required")
    @field:Size(min = 20, message = "Description must be at least 20 characters")
    val description: String,

    @field:NotNull(message = "Property type is required")
    val propertyType: PropertyType,

    @field:NotNull(message = "Listing type is required")
    val listingType: ListingType,

    val rentalDuration: RentalDuration? = null,

    val furnishingStatus: FurnishingStatus = FurnishingStatus.UNFURNISHED,

    @field:NotNull(message = "Price is required")
    @field:Positive(message = "Price must be positive")
    val price: BigDecimal,

    val currency: String = "UGX",

    @field:NotBlank(message = "City is required")
    val city: String,

    @field:NotBlank(message = "District is required")
    val district: String,

    @field:NotBlank(message = "Address is required")
    val address: String,

    @field:NotNull(message = "Latitude is required")
    @field:DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @field:DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    val latitude: BigDecimal,

    @field:NotNull(message = "Longitude is required")
    @field:DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @field:DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    val longitude: BigDecimal,

    @field:Min(value = 0, message = "Bedrooms cannot be negative")
    val bedrooms: Int? = null,

    @field:Min(value = 0, message = "Bathrooms cannot be negative")
    val bathrooms: Int? = null,

    @field:Positive(message = "Land size must be positive")
    val landSize: BigDecimal? = null,

    val landSizeUnit: String? = "sqm",

    @field:Positive(message = "Built area must be positive")
    val builtArea: BigDecimal? = null,

    @field:Min(value = 1900, message = "Invalid year built")
    val yearBuilt: Int? = null,

    val features: List<String>? = null
)
```

- [ ] **Step 2.4: Run the controller test to verify it passes**

```bash
cd kasisira-backend && ./gradlew test --tests "com.mudhut.software.kasisira.properties.controllers.PropertyControllerTest"
```

Expected: All 9 tests PASS.

- [ ] **Step 2.5: Run the full test suite to confirm no regressions**

```bash
cd kasisira-backend && ./gradlew test
```

Expected: BUILD SUCCESSFUL.

Note: `PropertyMapper.fromCreateRequest` currently passes `request.district`, `request.address`, etc. into the entity's nullable slots. With the request now non-nullable, the mapper still compiles (non-null → nullable is always safe). Entity will be tightened in Task 3.

- [ ] **Step 2.6: Commit**

```bash
cd kasisira-backend && git add src/main/kotlin/com/mudhut/software/kasisira/properties/models/request/CreatePropertyRequest.kt src/test/kotlin/com/mudhut/software/kasisira/properties/controllers/PropertyControllerTest.kt
git commit -m "feat(properties): require location fields and bound lat/lng on create"
```

---

## Task 3: Tighten Property entity + fix fixtures

**Files:**
- Modify: `src/main/kotlin/com/mudhut/software/kasisira/properties/entities/Property.kt`
- Modify: `src/test/kotlin/com/mudhut/software/kasisira/properties/services/PropertyServiceImplTest.kt`
- Modify: `src/test/kotlin/com/mudhut/software/kasisira/properties/repositories/PropertyRepositoryTest.kt`
- Modify: `src/test/kotlin/com/mudhut/software/kasisira/properties/services/PropertyMediaServiceImplTest.kt`
- Modify: `src/test/kotlin/com/mudhut/software/kasisira/properties/repositories/PropertyMediaRepositoryTest.kt`
- Modify: `src/test/kotlin/com/mudhut/software/kasisira/properties/mappers/PropertyMediaMapperTest.kt`

- [ ] **Step 3.1: Tighten the Property entity**

Modify `src/main/kotlin/com/mudhut/software/kasisira/properties/entities/Property.kt`. Replace the four location-field declarations (currently lines 73–83 of the file) with the non-nullable versions. The old block is:

```kotlin
    @Column(length = 100)
    val district: String? = null,

    @Column(length = 255)
    val address: String? = null,

    @Column(precision = 10, scale = 7)
    val latitude: BigDecimal? = null,

    @Column(precision = 10, scale = 7)
    val longitude: BigDecimal? = null,
```

Replace it with:

```kotlin
    @Column(nullable = false, length = 100)
    val district: String,

    @Column(nullable = false, length = 255)
    val address: String,

    @Column(nullable = false, precision = 10, scale = 7)
    val latitude: BigDecimal,

    @Column(nullable = false, precision = 10, scale = 7)
    val longitude: BigDecimal,
```

No other lines in the entity change. The `equals`, `hashCode`, `toString`, helper functions, imports, and other columns stay as-is.

- [ ] **Step 3.2: Try to compile and observe the fixture failures**

```bash
cd kasisira-backend && ./gradlew compileTestKotlin
```

Expected: FAIL with a handful of Kotlin compilation errors about missing required parameters `district`, `address`, `latitude`, `longitude` on the `Property(...)` constructor. The failing locations will be the test fixtures we are about to fix.

- [ ] **Step 3.3: Fix PropertyServiceImplTest fixture**

In `src/test/kotlin/com/mudhut/software/kasisira/properties/services/PropertyServiceImplTest.kt`, the `testProperty` fixture (around lines 77–91) currently is:

```kotlin
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
            status = PropertyStatus.DRAFT,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
```

Replace it with:

```kotlin
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
```

- [ ] **Step 3.4: Fix PropertyRepositoryTest fixtures (three `Property(` constructions)**

In `src/test/kotlin/com/mudhut/software/kasisira/properties/repositories/PropertyRepositoryTest.kt`, three fixtures need updating. In each, insert the same three lines (`address`, `latitude`, `longitude`) immediately after the existing `district = "..."` line. Use these values for all three fixtures:

```kotlin
            address = "123 Main Street",
            latitude = BigDecimal("0.3476"),
            longitude = BigDecimal("32.5825"),
```

The three constructor sites to update are at approximately:
- Line 52 (`testProperty` in `setUp`)
- Line 294 (`anotherProperty`)
- Line 333 (`draftProperty`)

Locate each by its surrounding context (same line pattern: `district = "Wakiso",` immediately followed by `bedrooms = ...` or `status = ...`) and insert the three new lines between `district` and the next field.

- [ ] **Step 3.5: Fix PropertyMediaServiceImplTest fixture**

In `src/test/kotlin/com/mudhut/software/kasisira/properties/services/PropertyMediaServiceImplTest.kt`, the `testProperty` fixture (around line 72). Insert the same three lines after its `district = "Wakiso"` (or equivalent) line:

```kotlin
            address = "123 Main Street",
            latitude = BigDecimal("0.3476"),
            longitude = BigDecimal("32.5825"),
```

If the fixture has no `district` line currently, add all four in order (`district`, `address`, `latitude`, `longitude`) in the same position where other string fields live — immediately after `city = "Kampala"`.

- [ ] **Step 3.6: Fix PropertyMediaRepositoryTest fixture**

In `src/test/kotlin/com/mudhut/software/kasisira/properties/repositories/PropertyMediaRepositoryTest.kt`, the `testProperty` fixture (around line 52). Same pattern — insert after `district`:

```kotlin
            address = "123 Main Street",
            latitude = BigDecimal("0.3476"),
            longitude = BigDecimal("32.5825"),
```

If the fixture lacks `district`, add all four after `city`.

- [ ] **Step 3.7: Fix PropertyMediaMapperTest fixture**

In `src/test/kotlin/com/mudhut/software/kasisira/properties/mappers/PropertyMediaMapperTest.kt`, the `testProperty` fixture (around line 44). Insert after `district`:

```kotlin
            address = "123 Main Street",
            latitude = BigDecimal("0.3476"),
            longitude = BigDecimal("32.5825"),
```

Add `import java.math.BigDecimal` at the top of the file if it isn't already imported. If the fixture lacks `district`, add all four after `city`.

- [ ] **Step 3.8: Confirm compilation succeeds**

```bash
cd kasisira-backend && ./gradlew compileTestKotlin
```

Expected: BUILD SUCCESSFUL. If any file still fails, its fixture was missed — repeat the `district` → `address, latitude, longitude` insertion pattern.

- [ ] **Step 3.9: Run the full test suite**

```bash
cd kasisira-backend && ./gradlew test
```

Expected: BUILD SUCCESSFUL. No assertion changes — the new fixture values don't break any existing assertions because no test previously asserted `address`/`latitude`/`longitude` were `null`.

- [ ] **Step 3.10: Commit**

```bash
cd kasisira-backend && git add src/main/kotlin/com/mudhut/software/kasisira/properties/entities/Property.kt \
  src/test/kotlin/com/mudhut/software/kasisira/properties/services/PropertyServiceImplTest.kt \
  src/test/kotlin/com/mudhut/software/kasisira/properties/repositories/PropertyRepositoryTest.kt \
  src/test/kotlin/com/mudhut/software/kasisira/properties/services/PropertyMediaServiceImplTest.kt \
  src/test/kotlin/com/mudhut/software/kasisira/properties/repositories/PropertyMediaRepositoryTest.kt \
  src/test/kotlin/com/mudhut/software/kasisira/properties/mappers/PropertyMediaMapperTest.kt
git commit -m "feat(properties): require location columns on Property entity"
```

---

## Task 4: Flyway V8 migration + final verification

**Files:**
- Create: `src/main/resources/db/migration/V8__tighten_property_location.sql`

- [ ] **Step 4.1: Create the migration**

Create `src/main/resources/db/migration/V8__tighten_property_location.sql`:

```sql
-- V8__tighten_property_location.sql
-- Requires district, address, latitude, and longitude on every property.
-- Safe because the properties table is empty in all environments.

ALTER TABLE properties
    ALTER COLUMN district  SET NOT NULL,
    ALTER COLUMN address   SET NOT NULL,
    ALTER COLUMN latitude  SET NOT NULL,
    ALTER COLUMN longitude SET NOT NULL;
```

- [ ] **Step 4.2: Run the Flyway baseline test to confirm the migration applies cleanly**

```bash
cd kasisira-backend && ./gradlew test --tests "com.mudhut.software.kasisira.migration.FlywayBaselineTest"
```

Expected: PASS. `FlywayBaselineTest` applies every migration in order from V1 against a fresh database; if V8's `ALTER TABLE` fails, this is where it surfaces.

- [ ] **Step 4.3: Run the full test suite**

```bash
cd kasisira-backend && ./gradlew test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4.4: Commit**

```bash
cd kasisira-backend && git add src/main/resources/db/migration/V8__tighten_property_location.sql
git commit -m "feat(properties): V8 migration requires location columns on properties"
```

---

## Done Criteria

- All four tasks committed on `feature/owner-orgs-and-property-migration`.
- `./gradlew test` passes cleanly (the suite should grow by 10 tests: 1 from `PropertyTypeTest` + 9 from `PropertyControllerTest`).
- A manual `curl` against `POST /api/v1/orgs/10/properties` with a body missing `latitude` returns `HTTP/1.1 400` and `{"errorCode":"VALIDATION_ERROR","errors":{"latitude":"Latitude is required"}, ...}`.
- `POST` with valid body and no `furnishingStatus` causes the service to see `furnishingStatus = UNFURNISHED`.
- `POST` with `"propertyType":"STUDIO"` is accepted without `bedrooms`/`bathrooms`.
