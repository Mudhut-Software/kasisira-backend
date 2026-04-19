# Property Create Validation, Location, and Type Expansion

**Date:** 2026-04-19
**Status:** Approved — ready for implementation planning

## Problem

Three related gaps in the property creation flow:

1. **Location data is optional.** `district`, `address`, `latitude`, and `longitude` on `CreatePropertyRequest` are nullable with no validation, so a property can be created with no geographic information beyond `city`. This makes map-based browsing and address display unreliable.

2. **`PropertyType` is too coarse.** The enum has only `LAND` and `HOUSE`. Real listings need to distinguish apartments, studios, condos, mansions, and other common dwelling types for browse, search, and display.

3. **`furnishingStatus` has no default.** It's nullable with no default value, so an omitted field yields `null`, which isn't a meaningful state — every unit is either furnished, unfurnished, or partially so.

## Goal

- All location fields on property creation are required and validated.
- `latitude` / `longitude` are bounded to Earth's coordinate space.
- `PropertyType` enum covers the common residential listing types.
- `furnishingStatus` defaults to `UNFURNISHED` when not provided.
- Existing optional fields (bedrooms, bathrooms, etc.) stay optional — a studio listing should not need a bedroom count.
- Update semantics (`UpdatePropertyRequest`) stay PATCH-style — no fields become required on update.

## Approach

Four coordinated changes:

1. Expand the `PropertyType` enum with additional dwelling types.
2. Tighten validation on `CreatePropertyRequest` — promote four location fields to required, bound lat/lng, default `furnishingStatus`.
3. Tighten the `Property` JPA entity — matching columns become `NOT NULL` with non-nullable Kotlin types.
4. Flyway migration `V8` applies the `NOT NULL` constraints. Safe because the `properties` table is empty in all environments.

No business rule changes beyond what validation enforces. The existing rule at `PropertyServiceImpl.kt:140` (`LAND` cannot be `FOR_RENT`) continues to hold and still applies only to `LAND` — the new dwelling types all support both sale and rent.

## Design

### PropertyType enum

`src/main/kotlin/com/mudhut/software/kasisira/properties/entities/PropertyType.kt`:

```kotlin
enum class PropertyType {
    LAND,        // only sale, never rent (enforced in PropertyServiceImpl)
    HOUSE,       // generic standalone dwelling
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

Since `Property.propertyType` is stored via `@Enumerated(EnumType.STRING)`, no SQL schema change is needed to accept the new values. Existing `LAND` and `HOUSE` rows remain valid.

### CreatePropertyRequest validation

`src/main/kotlin/com/mudhut/software/kasisira/properties/models/request/CreatePropertyRequest.kt`:

- `district: String` — add `@field:NotBlank(message = "District is required")`. Remove nullability and default.
- `address: String` — add `@field:NotBlank(message = "Address is required")`. Remove nullability and default.
- `latitude: BigDecimal` — add `@field:NotNull`, `@field:DecimalMin("-90.0")`, `@field:DecimalMax("90.0")` with a single clear message `"Latitude must be between -90 and 90"`. Remove nullability and default.
- `longitude: BigDecimal` — add `@field:NotNull`, `@field:DecimalMin("-180.0")`, `@field:DecimalMax("180.0")` with message `"Longitude must be between -180 and 180"`. Remove nullability and default.
- `furnishingStatus: FurnishingStatus` — remove nullability, set default to `FurnishingStatus.UNFURNISHED`. The client may still send an explicit value; omitted or deserialized-as-default will resolve to `UNFURNISHED`.

All other fields unchanged:

- Already required (no change): `title`, `description`, `propertyType`, `listingType`, `price`, `city`.
- Still optional (no change): `bedrooms`, `bathrooms`, `rentalDuration`, `landSize`, `landSizeUnit`, `builtArea`, `yearBuilt`, `features`, `currency` (defaults to `"UGX"`).

### Property entity

`src/main/kotlin/com/mudhut/software/kasisira/properties/entities/Property.kt`:

- `district: String` — change column to `nullable = false`, remove Kotlin nullability and default.
- `address: String` — column `nullable = false`, non-nullable Kotlin type, no default.
- `latitude: BigDecimal` — column `nullable = false`, non-nullable Kotlin type, no default. Precision/scale (`10, 7`) unchanged.
- `longitude: BigDecimal` — column `nullable = false`, non-nullable Kotlin type, no default. Precision/scale (`10, 7`) unchanged.

`furnishingStatus` on the entity stays nullable-with-default-null — the DTO resolves a concrete value before the service layer constructs the entity, so the entity never persists `null` for new rows. Existing rows created under the old schema may have `null`, which remains acceptable.

### UpdatePropertyRequest — no change

`UpdatePropertyRequest` is PATCH-style. All fields remain nullable so clients can update any subset. A client that sends a partial update without `latitude` is explicitly not trying to change latitude — not a validation failure.

### Database migration

New file: `src/main/resources/db/migration/V8__tighten_property_location.sql`

```sql
ALTER TABLE properties
    ALTER COLUMN district  SET NOT NULL,
    ALTER COLUMN address   SET NOT NULL,
    ALTER COLUMN latitude  SET NOT NULL,
    ALTER COLUMN longitude SET NOT NULL;
```

Safe without a backfill because the `properties` table is empty in all environments. The user has confirmed dropping any dev data is acceptable.

### PropertyServiceImpl — no signature change

The service already constructs the `Property` entity from `CreatePropertyRequest`. Once the request's new fields are non-nullable, the service code that passes them through needs no structural change — it just stops seeing nullable types. Existing business rule at line 140 (reject `LAND + FOR_RENT`) stays as-is.

## Testing

Integration tests via MockMvc against `POST /v1/orgs/{orgId}/properties`:

1. Valid request with all required fields → `201 Created`, response body echoes submitted values.
2. Missing `address` → `400 Bad Request`, body `errorCode = "VALIDATION_ERROR"`, `errors.address` present.
3. Missing `district` → `400`, `errors.district` present.
4. Missing `latitude` → `400`, `errors.latitude` present.
5. Missing `longitude` → `400`, `errors.longitude` present.
6. `latitude = 91` (out of range) → `400`, `errors.latitude` mentions the bound.
7. `longitude = -181` (out of range) → `400`, `errors.longitude` mentions the bound.
8. Request omits `furnishingStatus` → `201`, response reflects `UNFURNISHED`.
9. New enum value round-trip: `propertyType = "STUDIO"` with `bedrooms = null` → `201` (confirms studios don't need bedrooms).

Unit / repository tests that construct `Property` or `CreatePropertyRequest` directly need to supply the newly-required fields. Affected test files (seven identified via grep):

- `PropertyRepositoryTest`
- `PropertyServiceImplTest`
- `PropertyMapperTest`
- `PropertyMediaServiceImplTest`
- `PropertyMediaRepositoryTest`
- `PropertyMediaMapperTest`
- `PropertyControllerTest` if it exists; otherwise the integration tests above live in a new file

Sweep each file, add the new required values (any plausible district / address / lat / lng) to every fixture, and rerun the suite.

## Out of Scope (YAGNI)

- Address format or postal-code validation — treated as freeform.
- Enum-specific business rules (e.g., disallow `bedrooms > 0` on `STUDIO`) — not requested.
- Geographic fencing (restricting lat/lng to Uganda or any country) — not requested.
- Backfilling existing property rows with default location values — table is confirmed empty.
- Search / browse / mapper changes to surface the new `PropertyType` values — mappers already passthrough enums as strings.
- Updating `UpdatePropertyRequest` semantics.
- Reverse-geocoding `address` from `latitude`/`longitude` or vice versa.
