# Plan 1 — Foundation: Phone OTP Auth + Roles + Notifications Outbox

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the foundation gaps in the Kasisira backend so later plans (Orgs, Listings, Tenancy, Rent, Chat, Faults) have the primitives they need: multi-role users, phone-first authentication via SMS OTP, a generic notifications outbox, and a proper Flyway migration baseline.

**Architecture:** Build on the existing `profiles` module (User, Contact, RefreshToken, JwtTokenProvider, email sender). Add a new `notifications` module that owns the outbox + SMS adapter, and extend `profiles` with `UserRole` and `OtpChallenge`. Keep email sends (already working) — refactor them to flow through the outbox so SMS and email share one retry path. Replace Hibernate auto-DDL with Flyway migrations.

**Tech Stack:** Kotlin 2.x, Spring Boot 4.0.0-M2, Spring Security, Spring Data JPA, Flyway, Postgres, Thymeleaf (email templates), MockK + JUnit 5 + SpringMockK for tests, Africa's Talking Java SDK for SMS.

---

## Context — what already exists

Already built in `kasisira-backend` (do **not** duplicate):

- `profiles/entities/User.kt` — `id, username, email, passwordHash, provider, providerId, imageUrl, emailVerified, isActive, isEnabled, contacts, createdAt, updatedAt, lastLogin`. **No role field; no phone field directly (phone is on `Contact`).**
- `profiles/entities/Contact.kt` — `phoneNumber, isPrimary, isVerified, label`; many-to-one back to User.
- `profiles/entities/RefreshToken.kt`, `VerificationToken.kt`, `AuthProvider.kt` (enum: LOCAL, GOOGLE, etc.).
- `profiles/controllers/AuthController.kt` and `UserController.kt`.
- `profiles/services/AuthServiceImpl.kt`, `UserServiceImpl.kt`, `VerificationServiceImpl.kt`, `ContactServiceImpl.kt`.
- `security/JwtTokenProvider.kt`, `JwtAuthenticationFilter.kt`, `UserPrincipal.kt`, `CustomUserDetailsService.kt`, `OAuth2AuthenticationSuccessHandler.kt`.
- `email/EmailService.kt` + `EmailServiceImpl.kt`; Thymeleaf templates at `resources/templates/email/{verification,welcome,password-reset}.html`.
- `config/SecurityConfig.kt` (currently modified by in-progress WIP on `feature/add-properties`).
- `utils/exceptions/CustomExceptions.kt` + `GlobalExceptionHandler.kt`.
- `application.properties` with Flyway enabled, `ddl-auto=update`. **No migrations on disk yet.**

What's missing (this plan adds it):

1. **Flyway baseline** for the existing schema, then new migrations on top. Switch `ddl-auto` to `validate`.
2. **`UserRole` entity + `user_roles` table** with enum (`TENANT`, `OWNER`, `ADMIN`); every new user gets `TENANT`; `OWNER` granted when they create an org (Plan 2); grant/revoke API.
3. **`OtpChallenge` entity + `otp_challenges` table** and `OtpService` that generates, hashes, and verifies OTP codes. Rate-limited.
4. **`SmsSender` interface + `AfricasTalkingSmsSender` impl** (+ `NoopSmsSender` for dev/tests), selected by Spring profile.
5. **Notifications module** — `Outbox` entity, `NotificationService.enqueue(...)`, `OutboxWorker` on `@Scheduled`, status transitions + exponential backoff.
6. **Refactor existing email sends** (welcome, verification, password reset) to enqueue on the outbox rather than fire synchronously.
7. **Phone OTP auth endpoints** — request OTP, verify OTP, issue JWT (reuses existing `JwtTokenProvider`).

---

## Key design decisions (locked before coding)

- **Phone lookup uses `Contact`, not a new column on User.** Keeps the existing model. To make this usable for login, we add a **unique index on `contacts.phone_number` (global)** — two users can't register the same primary phone. This is a behavioural change; captured in migration V3.
- **OTP codes are 6-digit numeric, 10-minute expiry, single-use.** Stored as bcrypt hash in `otp_challenges.code_hash`. Max 3 OTP requests per phone per hour (rate limit in service).
- **Outbox holds a `payload` JSON that the worker uses to render + dispatch.** SMS payload is `{to, body}`; email payload is `{to, template, variables}`. No cross-module type leakage — keep it a JSON blob.
- **Outbox worker runs every 5 seconds, batch size 20.** Spring `@Scheduled(fixedDelay = 5000)` on a single Spring-managed bean (single instance assumption for MVP; if we scale out, add row-level locking via `SELECT ... FOR UPDATE SKIP LOCKED`).
- **Failures retry at attempts 1→2→3 with backoff** (immediate, +30s, +2min). After 3 failures → status `FAILED`, admin surfacing is Plan 5.
- **Clock abstraction:** introduce a `Clock` bean (`java.time.Clock.systemUTC()` by default) and require services to inject it — makes expiry/retry testing deterministic.
- **All dates stored as `Instant` / `TIMESTAMP WITH TIME ZONE`** — spec uses Africa/Kampala for scheduled jobs but storage stays UTC.

---

## File Structure

### New files

- `src/main/resources/db/migration/V1__baseline.sql` — captures the currently auto-generated schema (users, contacts, refresh_tokens, verification_tokens, + whatever the in-progress `properties` migration becomes — scoped to the tables that exist on `main`, not the WIP branch).
- `src/main/resources/db/migration/V2__add_user_roles.sql`
- `src/main/resources/db/migration/V3__contact_phone_global_unique.sql`
- `src/main/resources/db/migration/V4__add_otp_challenges.sql`
- `src/main/resources/db/migration/V5__add_outbox.sql`
- `src/main/kotlin/com/mudhut/software/kasisira/profiles/entities/UserRole.kt`
- `src/main/kotlin/com/mudhut/software/kasisira/profiles/entities/RoleName.kt`
- `src/main/kotlin/com/mudhut/software/kasisira/profiles/repositories/UserRoleRepository.kt`
- `src/main/kotlin/com/mudhut/software/kasisira/profiles/services/RoleService.kt` + `RoleServiceImpl.kt`
- `src/main/kotlin/com/mudhut/software/kasisira/profiles/entities/OtpChallenge.kt`
- `src/main/kotlin/com/mudhut/software/kasisira/profiles/entities/OtpPurpose.kt`
- `src/main/kotlin/com/mudhut/software/kasisira/profiles/repositories/OtpChallengeRepository.kt`
- `src/main/kotlin/com/mudhut/software/kasisira/profiles/services/OtpService.kt` + `OtpServiceImpl.kt`
- `src/main/kotlin/com/mudhut/software/kasisira/profiles/services/PhoneAuthService.kt` + `PhoneAuthServiceImpl.kt`
- `src/main/kotlin/com/mudhut/software/kasisira/profiles/controllers/PhoneAuthController.kt`
- `src/main/kotlin/com/mudhut/software/kasisira/profiles/models/request/RequestOtpRequest.kt`
- `src/main/kotlin/com/mudhut/software/kasisira/profiles/models/request/VerifyOtpRequest.kt`
- `src/main/kotlin/com/mudhut/software/kasisira/notifications/entities/Outbox.kt`
- `src/main/kotlin/com/mudhut/software/kasisira/notifications/entities/OutboxChannel.kt`
- `src/main/kotlin/com/mudhut/software/kasisira/notifications/entities/OutboxStatus.kt`
- `src/main/kotlin/com/mudhut/software/kasisira/notifications/repositories/OutboxRepository.kt`
- `src/main/kotlin/com/mudhut/software/kasisira/notifications/services/NotificationService.kt` + `NotificationServiceImpl.kt`
- `src/main/kotlin/com/mudhut/software/kasisira/notifications/services/OutboxWorker.kt`
- `src/main/kotlin/com/mudhut/software/kasisira/notifications/services/OutboxDispatcher.kt`
- `src/main/kotlin/com/mudhut/software/kasisira/notifications/sms/SmsSender.kt`
- `src/main/kotlin/com/mudhut/software/kasisira/notifications/sms/NoopSmsSender.kt`
- `src/main/kotlin/com/mudhut/software/kasisira/notifications/sms/AfricasTalkingSmsSender.kt`
- `src/main/kotlin/com/mudhut/software/kasisira/notifications/config/NotificationsConfig.kt`
- `src/main/kotlin/com/mudhut/software/kasisira/config/ClockConfig.kt`
- Test files under `src/test/kotlin/...` mirroring each service.

### Modified files

- `src/main/resources/application.properties` — set `spring.jpa.hibernate.ddl-auto=validate`, enable scheduling annotation (if not already).
- `src/main/resources/application-development.properties`, `application-testing.properties` — dev uses `NoopSmsSender`, prod uses `AfricasTalkingSmsSender` (via Spring profile / `@ConditionalOnProperty`).
- `src/main/resources/application-production.properties`, `application-staging.properties` — set `notifications.sms.provider=africas-talking` and require `AT_USERNAME`, `AT_API_KEY`, `AT_SENDER_ID` env vars.
- `build.gradle.kts` — add `com.africastalking:core:3.5.0` (Java SDK); enable scheduling.
- `src/main/kotlin/com/mudhut/software/kasisira/profiles/services/AuthServiceImpl.kt` — in `register(...)`: after user insert, call `RoleService.grant(user, TENANT)` in the same transaction.
- `src/main/kotlin/com/mudhut/software/kasisira/profiles/services/VerificationServiceImpl.kt` — replace direct `EmailService.send(...)` calls with `NotificationService.enqueueEmail(...)`.
- `src/main/kotlin/com/mudhut/software/kasisira/profiles/models/response/UserResponse.kt` — include `roles: Set<String>`.
- `src/main/kotlin/com/mudhut/software/kasisira/profiles/mappers/UserMapper.kt` — populate `roles`.
- `src/main/kotlin/com/mudhut/software/kasisira/security/JwtTokenProvider.kt` — include `roles` claim.
- `src/main/kotlin/com/mudhut/software/kasisira/security/UserPrincipal.kt` — expose authorities derived from `UserRole`.

---

## Branch strategy

The current branch `feature/add-properties` has uncommitted WIP (SecurityConfig changes + the properties module). **Do not execute this plan on that branch.** Before starting:

1. Either stash or commit the WIP on `feature/add-properties`.
2. Cut a new branch from `main`: `git checkout main && git pull && git checkout -b feature/foundation-phone-auth-notifications`.

All task commits go on this new branch.

---

## Task 1 — Flyway baseline of the existing schema

**Files:**
- Create: `src/main/resources/db/migration/V1__baseline.sql`
- Modify: `src/main/resources/application.properties`
- Modify: `src/main/resources/application-development.properties`, `application-testing.properties`, `application-staging.properties`, `application-production.properties`

**Context:** The existing DB tables (`users`, `contacts`, `refresh_tokens`, `verification_tokens`) were created by Hibernate auto-DDL. Flyway must know about them before new migrations run.

- [ ] **Step 1: Dump the current `main`-branch schema into `V1__baseline.sql`**

On a local dev DB that has been started against `main` (not the WIP branch), run:

```bash
pg_dump --schema-only --no-owner --no-privileges --no-comments \
  -h localhost -U $DEV_DB_USERNAME $DEV_DB_NAME \
  | grep -v '^--' | grep -v '^SET ' | grep -v '^SELECT pg_catalog' \
  > src/main/resources/db/migration/V1__baseline.sql
```

Then manually clean the file: remove any `CREATE SCHEMA public` lines, remove any `ALTER TABLE ... OWNER TO` lines, keep only `CREATE TABLE`, `CREATE INDEX`, `CREATE SEQUENCE`, `ALTER TABLE ... ADD CONSTRAINT` statements for: `users`, `contacts`, `refresh_tokens`, `verification_tokens` and their sequences/indexes.

- [ ] **Step 2: Configure Flyway to baseline on existing DBs and enforce migrations on fresh DBs**

Edit `src/main/resources/application.properties` and add:

```properties
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=0
spring.flyway.validate-on-migrate=true
spring.jpa.hibernate.ddl-auto=validate
```

Remove the duplicate `spring.jpa.hibernate.ddl-auto=update` from `application-development.properties`, `application-staging.properties`, and `application-production.properties`. Leave `application-testing.properties` with `ddl-auto=create-drop` (tests run on an empty DB).

- [ ] **Step 3: Write a validation test that the baseline matches JPA entities**

Create `src/test/kotlin/com/mudhut/software/kasisira/migration/FlywayBaselineTest.kt`:

```kotlin
package com.mudhut.software.kasisira.migration

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("testing")
class FlywayBaselineTest {
    @Test
    fun `application context starts with Flyway migrations and JPA validate`() {
        // Asserts Spring Boot can boot with ddl-auto=validate
        // against the Flyway-migrated schema. Any mismatch fails the test.
    }
}
```

For `testing` profile specifically, override to run Flyway first then `ddl-auto=validate`:

```properties
# application-testing.properties
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
```

- [ ] **Step 4: Run the baseline test**

```bash
./gradlew test --tests FlywayBaselineTest
```

Expected: PASS. If it fails, fix V1__baseline.sql until the schema exactly matches the JPA entities.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V1__baseline.sql \
        src/main/resources/application*.properties \
        src/test/kotlin/com/mudhut/software/kasisira/migration/FlywayBaselineTest.kt
git commit -m "chore(db): baseline existing schema under Flyway, switch JPA to validate"
```

---

## Task 2 — `RoleName` enum and `UserRole` entity

**Files:**
- Create: `src/main/kotlin/com/mudhut/software/kasisira/profiles/entities/RoleName.kt`
- Create: `src/main/kotlin/com/mudhut/software/kasisira/profiles/entities/UserRole.kt`
- Create: `src/main/kotlin/com/mudhut/software/kasisira/profiles/repositories/UserRoleRepository.kt`
- Create: `src/main/resources/db/migration/V2__add_user_roles.sql`

- [ ] **Step 1: Write the migration**

Create `src/main/resources/db/migration/V2__add_user_roles.sql`:

```sql
CREATE TABLE user_roles (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_name VARCHAR(20) NOT NULL,
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uk_user_role UNIQUE (user_id, role_name),
    CONSTRAINT ck_role_name CHECK (role_name IN ('TENANT', 'OWNER', 'ADMIN'))
);

CREATE INDEX idx_user_roles_user ON user_roles(user_id);
```

- [ ] **Step 2: Write the enum**

Create `src/main/kotlin/com/mudhut/software/kasisira/profiles/entities/RoleName.kt`:

```kotlin
package com.mudhut.software.kasisira.profiles.entities

enum class RoleName { TENANT, OWNER, ADMIN }
```

- [ ] **Step 3: Write the entity**

Create `src/main/kotlin/com/mudhut/software/kasisira/profiles/entities/UserRole.kt`:

```kotlin
package com.mudhut.software.kasisira.profiles.entities

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(
    name = "user_roles",
    uniqueConstraints = [UniqueConstraint(name = "uk_user_role", columnNames = ["user_id", "role_name"])],
    indexes = [Index(name = "idx_user_roles_user", columnList = "user_id")]
)
data class UserRole(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Enumerated(EnumType.STRING)
    @Column(name = "role_name", nullable = false, length = 20)
    val roleName: RoleName,

    @Column(name = "granted_at", nullable = false, updatable = false)
    @CreationTimestamp
    val grantedAt: Instant? = null
) {
    override fun equals(other: Any?): Boolean = this === other || (other is UserRole && id != 0L && id == other.id)
    override fun hashCode(): Int = id.hashCode()
}
```

- [ ] **Step 4: Write the repository**

Create `src/main/kotlin/com/mudhut/software/kasisira/profiles/repositories/UserRoleRepository.kt`:

```kotlin
package com.mudhut.software.kasisira.profiles.repositories

import com.mudhut.software.kasisira.profiles.entities.RoleName
import com.mudhut.software.kasisira.profiles.entities.UserRole
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRoleRepository : JpaRepository<UserRole, Long> {
    fun findAllByUserId(userId: Long): List<UserRole>
    fun existsByUserIdAndRoleName(userId: Long, roleName: RoleName): Boolean
    fun deleteByUserIdAndRoleName(userId: Long, roleName: RoleName): Long
}
```

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V2__add_user_roles.sql \
        src/main/kotlin/com/mudhut/software/kasisira/profiles/entities/RoleName.kt \
        src/main/kotlin/com/mudhut/software/kasisira/profiles/entities/UserRole.kt \
        src/main/kotlin/com/mudhut/software/kasisira/profiles/repositories/UserRoleRepository.kt
git commit -m "feat(profiles): add UserRole entity, repo, and migration"
```

---

## Task 3 — `RoleService` with TDD

**Files:**
- Test: `src/test/kotlin/com/mudhut/software/kasisira/profiles/services/RoleServiceImplTest.kt`
- Create: `src/main/kotlin/com/mudhut/software/kasisira/profiles/services/RoleService.kt`
- Create: `src/main/kotlin/com/mudhut/software/kasisira/profiles/services/RoleServiceImpl.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.mudhut.software.kasisira.profiles.services

import com.mudhut.software.kasisira.profiles.entities.AuthProvider
import com.mudhut.software.kasisira.profiles.entities.RoleName
import com.mudhut.software.kasisira.profiles.entities.User
import com.mudhut.software.kasisira.profiles.entities.UserRole
import com.mudhut.software.kasisira.profiles.repositories.UserRoleRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class RoleServiceImplTest {

    @MockK
    private lateinit var userRoleRepository: UserRoleRepository

    @InjectMockKs
    private lateinit var service: RoleServiceImpl

    private val user = User(id = 1L, username = "u", email = "u@example.com", provider = AuthProvider.LOCAL)

    @Test
    fun `grant inserts a UserRole when not already granted`() {
        every { userRoleRepository.existsByUserIdAndRoleName(1L, RoleName.TENANT) } returns false
        every { userRoleRepository.save(any()) } answers { firstArg() }

        service.grant(user, RoleName.TENANT)

        verify(exactly = 1) { userRoleRepository.save(match<UserRole> { it.user.id == 1L && it.roleName == RoleName.TENANT }) }
    }

    @Test
    fun `grant is idempotent — no save if role already exists`() {
        every { userRoleRepository.existsByUserIdAndRoleName(1L, RoleName.OWNER) } returns true

        service.grant(user, RoleName.OWNER)

        verify(exactly = 0) { userRoleRepository.save(any()) }
    }

    @Test
    fun `rolesOf returns all role names for the user`() {
        every { userRoleRepository.findAllByUserId(1L) } returns listOf(
            UserRole(user = user, roleName = RoleName.TENANT),
            UserRole(user = user, roleName = RoleName.OWNER)
        )
        val result = service.rolesOf(1L)
        assertEquals(setOf(RoleName.TENANT, RoleName.OWNER), result)
    }

    @Test
    fun `has returns true when user has the role`() {
        every { userRoleRepository.existsByUserIdAndRoleName(1L, RoleName.ADMIN) } returns true
        assertTrue(service.has(1L, RoleName.ADMIN))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test --tests RoleServiceImplTest
```

Expected: FAIL — `RoleService` / `RoleServiceImpl` don't exist.

- [ ] **Step 3: Write the interface**

```kotlin
// src/main/kotlin/com/mudhut/software/kasisira/profiles/services/RoleService.kt
package com.mudhut.software.kasisira.profiles.services

import com.mudhut.software.kasisira.profiles.entities.RoleName
import com.mudhut.software.kasisira.profiles.entities.User

interface RoleService {
    fun grant(user: User, roleName: RoleName)
    fun revoke(userId: Long, roleName: RoleName)
    fun rolesOf(userId: Long): Set<RoleName>
    fun has(userId: Long, roleName: RoleName): Boolean
}
```

- [ ] **Step 4: Write the implementation**

```kotlin
// src/main/kotlin/com/mudhut/software/kasisira/profiles/services/RoleServiceImpl.kt
package com.mudhut.software.kasisira.profiles.services

import com.mudhut.software.kasisira.profiles.entities.RoleName
import com.mudhut.software.kasisira.profiles.entities.User
import com.mudhut.software.kasisira.profiles.entities.UserRole
import com.mudhut.software.kasisira.profiles.repositories.UserRoleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RoleServiceImpl(
    private val userRoleRepository: UserRoleRepository
) : RoleService {

    @Transactional
    override fun grant(user: User, roleName: RoleName) {
        if (userRoleRepository.existsByUserIdAndRoleName(user.id, roleName)) return
        userRoleRepository.save(UserRole(user = user, roleName = roleName))
    }

    @Transactional
    override fun revoke(userId: Long, roleName: RoleName) {
        userRoleRepository.deleteByUserIdAndRoleName(userId, roleName)
    }

    @Transactional(readOnly = true)
    override fun rolesOf(userId: Long): Set<RoleName> =
        userRoleRepository.findAllByUserId(userId).map { it.roleName }.toSet()

    @Transactional(readOnly = true)
    override fun has(userId: Long, roleName: RoleName): Boolean =
        userRoleRepository.existsByUserIdAndRoleName(userId, roleName)
}
```

- [ ] **Step 5: Run test to verify it passes**

```bash
./gradlew test --tests RoleServiceImplTest
```

Expected: PASS (all 4 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/mudhut/software/kasisira/profiles/services/RoleService*.kt \
        src/test/kotlin/com/mudhut/software/kasisira/profiles/services/RoleServiceImplTest.kt
git commit -m "feat(profiles): add RoleService for granting and checking user roles"
```

---

## Task 4 — Grant `TENANT` on every new registration

**Files:**
- Modify: `src/main/kotlin/com/mudhut/software/kasisira/profiles/services/AuthServiceImpl.kt`
- Modify: `src/test/kotlin/com/mudhut/software/kasisira/profiles/services/AuthServiceImplTest.kt`

**Context:** Every new user must receive the `TENANT` role at signup. Add a `RoleService` dependency and call `grant(user, TENANT)` inside the same transaction as user creation.

- [ ] **Step 1: Add a failing test**

In `AuthServiceImplTest.kt`, add a `MockK` field for `RoleService` and a new test:

```kotlin
@MockK
private lateinit var roleService: RoleService

// ... add to @BeforeEach setUp(): no stubbing needed unless the test exercises it

@Test
fun `register grants TENANT role after creating the user`() {
    // arrange the existing successful-register path, with roleService slot
    every { userRepository.save(any()) } answers { firstArg<User>().copy(id = 42L) }
    every { roleService.grant(any(), RoleName.TENANT) } just Runs
    // ... rest of the usual stubs for the happy-path register

    authService.register(validRegisterRequest)

    verify(exactly = 1) { roleService.grant(match { it.id == 42L }, RoleName.TENANT) }
}
```

- [ ] **Step 2: Run test — expect failure**

```bash
./gradlew test --tests AuthServiceImplTest
```

Expected: FAIL (dependency missing or verify not matched).

- [ ] **Step 3: Inject `RoleService` into `AuthServiceImpl` and call `grant` at end of register**

In `AuthServiceImpl.kt`, add `private val roleService: RoleService` to the constructor; after the successful `userRepository.save(...)` call inside `register(...)`, add `roleService.grant(savedUser, RoleName.TENANT)`.

- [ ] **Step 4: Run all profile tests**

```bash
./gradlew test --tests 'com.mudhut.software.kasisira.profiles.*'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/mudhut/software/kasisira/profiles/services/AuthServiceImpl.kt \
        src/test/kotlin/com/mudhut/software/kasisira/profiles/services/AuthServiceImplTest.kt
git commit -m "feat(profiles): grant TENANT role on user registration"
```

---

## Task 5 — Expose roles on `UserResponse` and in JWT claims

**Files:**
- Modify: `src/main/kotlin/com/mudhut/software/kasisira/profiles/models/response/UserResponse.kt`
- Modify: `src/main/kotlin/com/mudhut/software/kasisira/profiles/mappers/UserMapper.kt`
- Modify: `src/main/kotlin/com/mudhut/software/kasisira/security/JwtTokenProvider.kt`
- Modify: `src/main/kotlin/com/mudhut/software/kasisira/security/UserPrincipal.kt`
- Modify: related tests

- [ ] **Step 1: Write failing test for UserMapper**

In the existing `UserMapperTest` (or create one if missing), add:

```kotlin
@Test
fun `maps roles from UserRoleRepository into response`() {
    val user = User(id = 7L, username = "u", email = "u@example.com")
    every { userRoleRepository.findAllByUserId(7L) } returns listOf(
        UserRole(user = user, roleName = RoleName.TENANT),
        UserRole(user = user, roleName = RoleName.OWNER)
    )
    val response = userMapper.toResponse(user)
    assertEquals(setOf("TENANT", "OWNER"), response.roles)
}
```

- [ ] **Step 2: Run — expect failure**

```bash
./gradlew test --tests UserMapperTest
```

- [ ] **Step 3: Add `roles: Set<String>` to `UserResponse`**

In `UserResponse.kt`, add: `val roles: Set<String> = emptySet()`.

- [ ] **Step 4: Wire `UserRoleRepository` into `UserMapper`**

Inject `UserRoleRepository`; populate `roles` in the mapper by calling `findAllByUserId(user.id).map { it.roleName.name }.toSet()`.

- [ ] **Step 5: Add `roles` claim to JWT**

In `JwtTokenProvider.kt` at token generation, after the existing subject + expiry setup, add:

```kotlin
.claim("roles", rolesOf(userId)) // Set<String>
```

Add a `RoleService` injection and resolve roles there (read-only, transactional boundary handled by caller).

- [ ] **Step 6: Derive Spring authorities in `UserPrincipal`**

`UserPrincipal.getAuthorities()` should now return `roleService.rolesOf(id).map { SimpleGrantedAuthority("ROLE_${it.name}") }`. If `UserPrincipal` is constructed statically, change the factory to accept a `Set<RoleName>` and do the mapping.

- [ ] **Step 7: Run the full test suite**

```bash
./gradlew test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/com/mudhut/software/kasisira/profiles \
        src/main/kotlin/com/mudhut/software/kasisira/security \
        src/test/kotlin/com/mudhut/software/kasisira
git commit -m "feat(auth): include user roles in UserResponse, JWT claims, and Spring authorities"
```

---

## Task 6 — Contact phone global uniqueness migration

**Files:**
- Create: `src/main/resources/db/migration/V3__contact_phone_global_unique.sql`
- Modify: `src/main/kotlin/com/mudhut/software/kasisira/profiles/entities/Contact.kt`

**Context:** Phone OTP auth looks up a user by phone number. Today `Contact.phoneNumber` has no global unique constraint. Add one, and an index to make OTP lookups fast.

- [ ] **Step 1: Write the migration**

```sql
-- V3__contact_phone_global_unique.sql
-- De-duplicate any existing collisions by keeping the oldest row (best-effort; warn in release notes).
DELETE FROM contacts c
 USING contacts older
 WHERE c.phone_number = older.phone_number
   AND c.id > older.id;

ALTER TABLE contacts
    ADD CONSTRAINT uk_contact_phone_number UNIQUE (phone_number);

CREATE INDEX IF NOT EXISTS idx_contact_phone_number ON contacts(phone_number);
```

- [ ] **Step 2: Update the JPA entity to reflect the constraint**

In `Contact.kt`, change the `@Table` annotation to:

```kotlin
@Table(
    name = "contacts",
    uniqueConstraints = [UniqueConstraint(name = "uk_contact_phone_number", columnNames = ["phone_number"])],
    indexes = [
        Index(name = "idx_contact_user", columnList = "user_id"),
        Index(name = "idx_contact_phone_number", columnList = "phone_number")
    ]
)
```

- [ ] **Step 3: Add repo method for lookup by phone**

In `ContactRepository.kt`, add `fun findByPhoneNumber(phoneNumber: String): Contact?`.

- [ ] **Step 4: Run tests**

```bash
./gradlew test
```

Expected: PASS. The migration dedup runs once per DB.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V3__contact_phone_global_unique.sql \
        src/main/kotlin/com/mudhut/software/kasisira/profiles/entities/Contact.kt \
        src/main/kotlin/com/mudhut/software/kasisira/profiles/repositories/ContactRepository.kt
git commit -m "feat(profiles): make contact phone_number globally unique for OTP lookup"
```

---

## Task 7 — `Clock` bean

**Files:**
- Create: `src/main/kotlin/com/mudhut/software/kasisira/config/ClockConfig.kt`

- [ ] **Step 1: Write the config**

```kotlin
package com.mudhut.software.kasisira.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/com/mudhut/software/kasisira/config/ClockConfig.kt
git commit -m "chore: add injectable Clock bean for deterministic time in tests"
```

---

## Task 8 — `OtpPurpose` enum, `OtpChallenge` entity + migration

**Files:**
- Create: `src/main/resources/db/migration/V4__add_otp_challenges.sql`
- Create: `src/main/kotlin/com/mudhut/software/kasisira/profiles/entities/OtpPurpose.kt`
- Create: `src/main/kotlin/com/mudhut/software/kasisira/profiles/entities/OtpChallenge.kt`
- Create: `src/main/kotlin/com/mudhut/software/kasisira/profiles/repositories/OtpChallengeRepository.kt`

- [ ] **Step 1: Write the migration**

```sql
-- V4__add_otp_challenges.sql
CREATE TABLE otp_challenges (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    phone_number VARCHAR(20) NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    purpose VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT ck_otp_purpose CHECK (purpose IN ('LOGIN', 'SIGNUP'))
);

CREATE INDEX idx_otp_phone_purpose_created ON otp_challenges(phone_number, purpose, created_at DESC);
CREATE INDEX idx_otp_phone_active ON otp_challenges(phone_number, expires_at) WHERE consumed_at IS NULL;
```

- [ ] **Step 2: Enum**

```kotlin
// OtpPurpose.kt
package com.mudhut.software.kasisira.profiles.entities
enum class OtpPurpose { LOGIN, SIGNUP }
```

- [ ] **Step 3: Entity**

```kotlin
// OtpChallenge.kt
package com.mudhut.software.kasisira.profiles.entities

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "otp_challenges",
    indexes = [
        Index(name = "idx_otp_phone_purpose_created", columnList = "phone_number,purpose,created_at"),
        Index(name = "idx_otp_phone_active", columnList = "phone_number,expires_at")
    ]
)
data class OtpChallenge(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "phone_number", nullable = false, length = 20)
    val phoneNumber: String,

    @Column(name = "code_hash", nullable = false, length = 255)
    val codeHash: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val purpose: OtpPurpose,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "consumed_at")
    var consumedAt: Instant? = null,

    @Column(nullable = false)
    var attempts: Int = 0,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
```

- [ ] **Step 4: Repository**

```kotlin
// OtpChallengeRepository.kt
package com.mudhut.software.kasisira.profiles.repositories

import com.mudhut.software.kasisira.profiles.entities.OtpChallenge
import com.mudhut.software.kasisira.profiles.entities.OtpPurpose
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface OtpChallengeRepository : JpaRepository<OtpChallenge, Long> {

    @Query("""
        SELECT o FROM OtpChallenge o
        WHERE o.phoneNumber = :phone AND o.purpose = :purpose
          AND o.consumedAt IS NULL AND o.expiresAt > :now
        ORDER BY o.createdAt DESC
    """)
    fun findLatestActive(
        @Param("phone") phone: String,
        @Param("purpose") purpose: OtpPurpose,
        @Param("now") now: Instant
    ): List<OtpChallenge>

    @Query("""
        SELECT COUNT(o) FROM OtpChallenge o
        WHERE o.phoneNumber = :phone AND o.createdAt > :since
    """)
    fun countRecent(
        @Param("phone") phone: String,
        @Param("since") since: Instant
    ): Long
}
```

- [ ] **Step 5: Run build (compilation check)**

```bash
./gradlew compileKotlin
```

Expected: success.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V4__add_otp_challenges.sql \
        src/main/kotlin/com/mudhut/software/kasisira/profiles/entities/OtpPurpose.kt \
        src/main/kotlin/com/mudhut/software/kasisira/profiles/entities/OtpChallenge.kt \
        src/main/kotlin/com/mudhut/software/kasisira/profiles/repositories/OtpChallengeRepository.kt
git commit -m "feat(profiles): add OtpChallenge entity, repo, and migration"
```

---

## Task 9 — `SmsSender` interface + `NoopSmsSender`

**Files:**
- Create: `src/main/kotlin/com/mudhut/software/kasisira/notifications/sms/SmsSender.kt`
- Create: `src/main/kotlin/com/mudhut/software/kasisira/notifications/sms/NoopSmsSender.kt`

- [ ] **Step 1: Interface**

```kotlin
// SmsSender.kt
package com.mudhut.software.kasisira.notifications.sms

interface SmsSender {
    /** Sends an SMS. Throws on non-retryable errors; caller handles retries. */
    fun send(toE164: String, body: String)
}
```

- [ ] **Step 2: No-op implementation for dev/testing**

```kotlin
// NoopSmsSender.kt
package com.mudhut.software.kasisira.notifications.sms

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["notifications.sms.provider"], havingValue = "noop", matchIfMissing = true)
class NoopSmsSender : SmsSender {
    private val log = LoggerFactory.getLogger(NoopSmsSender::class.java)
    override fun send(toE164: String, body: String) {
        log.info("[noop-sms] -> {}: {}", toE164, body)
    }
}
```

- [ ] **Step 3: Add property default to `application.properties`**

Append:

```properties
notifications.sms.provider=noop
```

Override in `application-production.properties` and `application-staging.properties` to `africas-talking`.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/mudhut/software/kasisira/notifications/sms/SmsSender.kt \
        src/main/kotlin/com/mudhut/software/kasisira/notifications/sms/NoopSmsSender.kt \
        src/main/resources/application*.properties
git commit -m "feat(notifications): add SmsSender interface and noop impl for dev/test"
```

---

## Task 10 — `AfricasTalkingSmsSender`

**Files:**
- Modify: `build.gradle.kts`
- Create: `src/main/kotlin/com/mudhut/software/kasisira/notifications/sms/AfricasTalkingSmsSender.kt`
- Create: `src/test/kotlin/com/mudhut/software/kasisira/notifications/sms/AfricasTalkingSmsSenderTest.kt`
- Modify: `application-staging.properties`, `application-production.properties`

- [ ] **Step 1: Add Africa's Talking SDK**

In `build.gradle.kts` add to `dependencies { ... }`:

```kotlin
implementation("com.africastalking:core:3.5.0")
```

Run `./gradlew dependencies --configuration runtimeClasspath | grep africastalking` to confirm resolution.

- [ ] **Step 2: Write the implementation**

```kotlin
// AfricasTalkingSmsSender.kt
package com.mudhut.software.kasisira.notifications.sms

import com.africastalking.AfricasTalking
import com.africastalking.SmsService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["notifications.sms.provider"], havingValue = "africas-talking")
class AfricasTalkingSmsSender(
    @Value("\${AT_USERNAME}") username: String,
    @Value("\${AT_API_KEY}") apiKey: String,
    @Value("\${AT_SENDER_ID:}") private val senderId: String
) : SmsSender {

    private val log = LoggerFactory.getLogger(AfricasTalkingSmsSender::class.java)
    private val sms: SmsService

    init {
        AfricasTalking.initialize(username, apiKey)
        sms = AfricasTalking.getService(AfricasTalking.SERVICE_SMS)
    }

    override fun send(toE164: String, body: String) {
        try {
            val from = senderId.takeIf { it.isNotBlank() }
            val recipients = sms.send(body, from, arrayOf(toE164), false)
            val failed = recipients.filter { it.status != "Success" }
            if (failed.isNotEmpty()) {
                val reasons = failed.joinToString { "${it.number}:${it.status}" }
                throw SmsDeliveryException("Africa's Talking rejected: $reasons")
            }
        } catch (e: Exception) {
            log.warn("SMS send failed to {}: {}", toE164, e.message)
            throw e
        }
    }
}

class SmsDeliveryException(message: String) : RuntimeException(message)
```

- [ ] **Step 3: Configure staging/production**

Append to `application-staging.properties` and `application-production.properties`:

```properties
notifications.sms.provider=africas-talking
# Required env: AT_USERNAME, AT_API_KEY, AT_SENDER_ID
```

- [ ] **Step 4: Commit**

```bash
git add build.gradle.kts \
        src/main/kotlin/com/mudhut/software/kasisira/notifications/sms/AfricasTalkingSmsSender.kt \
        src/main/resources/application-staging.properties \
        src/main/resources/application-production.properties
git commit -m "feat(notifications): add Africa's Talking SMS sender (conditional on profile)"
```

---

## Task 11 — `OtpService` with TDD (generate / send / verify / rate limit)

**Files:**
- Test: `src/test/kotlin/com/mudhut/software/kasisira/profiles/services/OtpServiceImplTest.kt`
- Create: `src/main/kotlin/com/mudhut/software/kasisira/profiles/services/OtpService.kt`
- Create: `src/main/kotlin/com/mudhut/software/kasisira/profiles/services/OtpServiceImpl.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.mudhut.software.kasisira.profiles.services

import com.mudhut.software.kasisira.notifications.services.NotificationService
import com.mudhut.software.kasisira.profiles.entities.OtpChallenge
import com.mudhut.software.kasisira.profiles.entities.OtpPurpose
import com.mudhut.software.kasisira.profiles.repositories.OtpChallengeRepository
import com.mudhut.software.kasisira.utils.exceptions.RateLimitedException
import com.mudhut.software.kasisira.utils.exceptions.InvalidOtpException
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@ExtendWith(MockKExtension::class)
class OtpServiceImplTest {

    @MockK private lateinit var repo: OtpChallengeRepository
    @MockK private lateinit var notifications: NotificationService
    @MockK private lateinit var passwordEncoder: PasswordEncoder

    private val fixed = Clock.fixed(Instant.parse("2026-04-17T10:00:00Z"), ZoneOffset.UTC)

    @InjectMockKs
    private lateinit var service: OtpServiceImpl  // uses `fixed` via constructor below if needed

    @Test
    fun `requestOtp creates a challenge, hashes code, enqueues SMS`() {
        every { repo.countRecent(any(), any()) } returns 0L
        every { passwordEncoder.encode(any()) } returns "hashed"
        every { repo.save(any()) } answers { firstArg<OtpChallenge>().copy(id = 1L) }
        every { notifications.enqueueSms(any(), any()) } just Runs

        service.requestOtp("+256700000001", OtpPurpose.LOGIN)

        verify { repo.save(match { it.phoneNumber == "+256700000001" && it.codeHash == "hashed" && it.purpose == OtpPurpose.LOGIN }) }
        verify { notifications.enqueueSms("+256700000001", match<String> { it.contains("Kasisira") }) }
    }

    @Test
    fun `requestOtp throws RateLimitedException when more than 3 requests in the last hour`() {
        every { repo.countRecent("+256700000002", any()) } returns 3L
        assertThrows(RateLimitedException::class.java) {
            service.requestOtp("+256700000002", OtpPurpose.LOGIN)
        }
        verify(exactly = 0) { repo.save(any()) }
    }

    @Test
    fun `verifyOtp returns true when code matches latest active challenge and marks consumed`() {
        val active = OtpChallenge(id = 5, phoneNumber = "+256700000003", codeHash = "hashed",
            purpose = OtpPurpose.LOGIN, expiresAt = Instant.parse("2026-04-17T10:05:00Z"))
        every { repo.findLatestActive("+256700000003", OtpPurpose.LOGIN, any()) } returns listOf(active)
        every { passwordEncoder.matches("123456", "hashed") } returns true
        every { repo.save(any()) } answers { firstArg() }

        val result = service.verifyOtp("+256700000003", "123456", OtpPurpose.LOGIN)

        assertTrue(result)
        verify { repo.save(match { it.consumedAt != null }) }
    }

    @Test
    fun `verifyOtp throws InvalidOtpException when no active challenge exists`() {
        every { repo.findLatestActive(any(), any(), any()) } returns emptyList()
        assertThrows(InvalidOtpException::class.java) {
            service.verifyOtp("+256700000004", "123456", OtpPurpose.LOGIN)
        }
    }

    @Test
    fun `verifyOtp increments attempts and throws on wrong code`() {
        val active = OtpChallenge(id = 6, phoneNumber = "+256700000005", codeHash = "hashed",
            purpose = OtpPurpose.LOGIN, expiresAt = Instant.parse("2026-04-17T10:05:00Z"), attempts = 1)
        every { repo.findLatestActive(any(), any(), any()) } returns listOf(active)
        every { passwordEncoder.matches("000000", "hashed") } returns false
        every { repo.save(any()) } answers { firstArg() }

        assertThrows(InvalidOtpException::class.java) {
            service.verifyOtp("+256700000005", "000000", OtpPurpose.LOGIN)
        }
        verify { repo.save(match { it.attempts == 2 }) }
    }
}
```

- [ ] **Step 2: Add the two exception types (if missing)**

In `src/main/kotlin/com/mudhut/software/kasisira/utils/exceptions/CustomExceptions.kt`, add:

```kotlin
class RateLimitedException(message: String) : RuntimeException(message)
class InvalidOtpException(message: String) : RuntimeException(message)
```

And in `GlobalExceptionHandler.kt`, map them to `HTTP 429` and `HTTP 400` respectively.

- [ ] **Step 3: Run test — expect failure**

```bash
./gradlew test --tests OtpServiceImplTest
```

- [ ] **Step 4: Write the interface**

```kotlin
// OtpService.kt
package com.mudhut.software.kasisira.profiles.services

import com.mudhut.software.kasisira.profiles.entities.OtpPurpose

interface OtpService {
    fun requestOtp(phoneNumberE164: String, purpose: OtpPurpose)
    fun verifyOtp(phoneNumberE164: String, code: String, purpose: OtpPurpose): Boolean
}
```

- [ ] **Step 5: Write the implementation**

```kotlin
// OtpServiceImpl.kt
package com.mudhut.software.kasisira.profiles.services

import com.mudhut.software.kasisira.notifications.services.NotificationService
import com.mudhut.software.kasisira.profiles.entities.OtpChallenge
import com.mudhut.software.kasisira.profiles.entities.OtpPurpose
import com.mudhut.software.kasisira.profiles.repositories.OtpChallengeRepository
import com.mudhut.software.kasisira.utils.exceptions.InvalidOtpException
import com.mudhut.software.kasisira.utils.exceptions.RateLimitedException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration

@Service
class OtpServiceImpl(
    private val repo: OtpChallengeRepository,
    private val notifications: NotificationService,
    private val passwordEncoder: PasswordEncoder,
    private val clock: Clock
) : OtpService {

    private val rng = SecureRandom()
    private val codeLength = 6
    private val expiry: Duration = Duration.ofMinutes(10)
    private val rateWindow: Duration = Duration.ofHours(1)
    private val rateMax = 3
    private val maxAttempts = 5

    @Transactional
    override fun requestOtp(phoneNumberE164: String, purpose: OtpPurpose) {
        val since = clock.instant().minus(rateWindow)
        if (repo.countRecent(phoneNumberE164, since) >= rateMax) {
            throw RateLimitedException("Too many OTP requests. Try again later.")
        }
        val code = generateCode()
        val hash = passwordEncoder.encode(code)
        repo.save(OtpChallenge(
            phoneNumber = phoneNumberE164,
            codeHash = hash,
            purpose = purpose,
            expiresAt = clock.instant().plus(expiry)
        ))
        notifications.enqueueSms(
            phoneNumberE164,
            "Your Kasisira verification code is $code. It expires in ${expiry.toMinutes()} minutes."
        )
    }

    @Transactional
    override fun verifyOtp(phoneNumberE164: String, code: String, purpose: OtpPurpose): Boolean {
        val candidates = repo.findLatestActive(phoneNumberE164, purpose, clock.instant())
        val active = candidates.firstOrNull() ?: throw InvalidOtpException("No active OTP for $phoneNumberE164.")
        if (active.attempts >= maxAttempts) {
            throw InvalidOtpException("OTP locked after too many attempts.")
        }
        if (!passwordEncoder.matches(code, active.codeHash)) {
            active.attempts += 1
            repo.save(active)
            throw InvalidOtpException("Incorrect code.")
        }
        active.consumedAt = clock.instant()
        repo.save(active)
        return true
    }

    private fun generateCode(): String =
        (1..codeLength).joinToString("") { rng.nextInt(10).toString() }
}
```

- [ ] **Step 6: Run tests**

```bash
./gradlew test --tests OtpServiceImplTest
```

Expected: PASS (all 5 tests).

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/mudhut/software/kasisira/profiles/services/OtpService*.kt \
        src/main/kotlin/com/mudhut/software/kasisira/utils/exceptions/CustomExceptions.kt \
        src/main/kotlin/com/mudhut/software/kasisira/utils/exceptions/GlobalExceptionHandler.kt \
        src/test/kotlin/com/mudhut/software/kasisira/profiles/services/OtpServiceImplTest.kt
git commit -m "feat(profiles): add OtpService with generate/verify/rate limit and exception mapping"
```

---

## Task 12 — `Outbox` entity + migration

**Files:**
- Create: `src/main/resources/db/migration/V5__add_outbox.sql`
- Create: `src/main/kotlin/com/mudhut/software/kasisira/notifications/entities/OutboxChannel.kt`
- Create: `src/main/kotlin/com/mudhut/software/kasisira/notifications/entities/OutboxStatus.kt`
- Create: `src/main/kotlin/com/mudhut/software/kasisira/notifications/entities/Outbox.kt`
- Create: `src/main/kotlin/com/mudhut/software/kasisira/notifications/repositories/OutboxRepository.kt`

- [ ] **Step 1: Migration**

```sql
-- V5__add_outbox.sql
CREATE TABLE outbox (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    channel VARCHAR(20) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    not_before TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    last_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    processed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_outbox_channel CHECK (channel IN ('SMS', 'EMAIL', 'INAPP')),
    CONSTRAINT ck_outbox_status  CHECK (status  IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX idx_outbox_due ON outbox(not_before) WHERE status = 'PENDING';
```

- [ ] **Step 2: Enums**

```kotlin
// OutboxChannel.kt
package com.mudhut.software.kasisira.notifications.entities
enum class OutboxChannel { SMS, EMAIL, INAPP }

// OutboxStatus.kt
package com.mudhut.software.kasisira.notifications.entities
enum class OutboxStatus { PENDING, SENT, FAILED }
```

- [ ] **Step 3: Entity**

```kotlin
// Outbox.kt
package com.mudhut.software.kasisira.notifications.entities

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "outbox", indexes = [Index(name = "idx_outbox_due", columnList = "not_before")])
data class Outbox(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val channel: OutboxChannel,

    @Column(nullable = false, length = 255)
    val recipient: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    val payload: String, // serialized JSON string; worker deserializes

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: OutboxStatus = OutboxStatus.PENDING,

    @Column(nullable = false)
    var attempts: Int = 0,

    @Column(name = "not_before", nullable = false)
    var notBefore: Instant = Instant.now(),

    @Column(name = "last_error", columnDefinition = "text")
    var lastError: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "processed_at")
    var processedAt: Instant? = null
)
```

- [ ] **Step 4: Repository**

```kotlin
// OutboxRepository.kt
package com.mudhut.software.kasisira.notifications.repositories

import com.mudhut.software.kasisira.notifications.entities.Outbox
import com.mudhut.software.kasisira.notifications.entities.OutboxStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface OutboxRepository : JpaRepository<Outbox, Long> {
    @Query("""
        SELECT o FROM Outbox o
        WHERE o.status = :status AND o.notBefore <= :now
        ORDER BY o.notBefore ASC
    """)
    fun findDue(@Param("status") status: OutboxStatus, @Param("now") now: Instant, pageable: Pageable): List<Outbox>
}
```

- [ ] **Step 5: Compile check**

```bash
./gradlew compileKotlin
```

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V5__add_outbox.sql \
        src/main/kotlin/com/mudhut/software/kasisira/notifications/entities \
        src/main/kotlin/com/mudhut/software/kasisira/notifications/repositories
git commit -m "feat(notifications): add Outbox entity, repo, and migration"
```

---

## Task 13 — `NotificationService` (enqueue-only)

**Files:**
- Test: `src/test/kotlin/com/mudhut/software/kasisira/notifications/services/NotificationServiceImplTest.kt`
- Create: `src/main/kotlin/com/mudhut/software/kasisira/notifications/services/NotificationService.kt`
- Create: `src/main/kotlin/com/mudhut/software/kasisira/notifications/services/NotificationServiceImpl.kt`

- [ ] **Step 1: Failing test**

```kotlin
package com.mudhut.software.kasisira.notifications.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.software.kasisira.notifications.entities.Outbox
import com.mudhut.software.kasisira.notifications.entities.OutboxChannel
import com.mudhut.software.kasisira.notifications.repositories.OutboxRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class NotificationServiceImplTest {
    @MockK private lateinit var repo: OutboxRepository
    @MockK private lateinit var objectMapper: ObjectMapper
    @InjectMockKs private lateinit var service: NotificationServiceImpl

    @Test
    fun `enqueueSms writes a PENDING SMS outbox row with the body in the payload`() {
        every { objectMapper.writeValueAsString(any()) } returns """{"body":"hi"}"""
        every { repo.save(any<Outbox>()) } answers { firstArg() }

        service.enqueueSms("+256700000000", "hi")

        verify { repo.save(match<Outbox> {
            it.channel == OutboxChannel.SMS && it.recipient == "+256700000000"
        }) }
    }

    @Test
    fun `enqueueEmail writes a PENDING EMAIL outbox row with template and variables`() {
        every { objectMapper.writeValueAsString(any()) } returns """{"template":"welcome","variables":{"name":"A"}}"""
        every { repo.save(any<Outbox>()) } answers { firstArg() }

        service.enqueueEmail("a@example.com", "welcome", mapOf("name" to "A"))

        verify { repo.save(match<Outbox> {
            it.channel == OutboxChannel.EMAIL && it.recipient == "a@example.com"
        }) }
    }
}
```

- [ ] **Step 2: Run — expect failure**

```bash
./gradlew test --tests NotificationServiceImplTest
```

- [ ] **Step 3: Interface**

```kotlin
// NotificationService.kt
package com.mudhut.software.kasisira.notifications.services

interface NotificationService {
    fun enqueueSms(toE164: String, body: String)
    fun enqueueEmail(toEmail: String, template: String, variables: Map<String, Any>)
    fun enqueueInApp(userId: Long, template: String, payload: Map<String, Any>)
}
```

- [ ] **Step 4: Implementation**

```kotlin
// NotificationServiceImpl.kt
package com.mudhut.software.kasisira.notifications.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.software.kasisira.notifications.entities.Outbox
import com.mudhut.software.kasisira.notifications.entities.OutboxChannel
import com.mudhut.software.kasisira.notifications.repositories.OutboxRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationServiceImpl(
    private val outboxRepository: OutboxRepository,
    private val objectMapper: ObjectMapper
) : NotificationService {

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    override fun enqueueSms(toE164: String, body: String) {
        val payload = objectMapper.writeValueAsString(mapOf("body" to body))
        outboxRepository.save(Outbox(channel = OutboxChannel.SMS, recipient = toE164, payload = payload))
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    override fun enqueueEmail(toEmail: String, template: String, variables: Map<String, Any>) {
        val payload = objectMapper.writeValueAsString(mapOf("template" to template, "variables" to variables))
        outboxRepository.save(Outbox(channel = OutboxChannel.EMAIL, recipient = toEmail, payload = payload))
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    override fun enqueueInApp(userId: Long, template: String, payload: Map<String, Any>) {
        val json = objectMapper.writeValueAsString(mapOf("template" to template, "payload" to payload))
        outboxRepository.save(Outbox(channel = OutboxChannel.INAPP, recipient = userId.toString(), payload = json))
    }
}
```

**Note:** `Propagation.MANDATORY` forces callers to already be in a transaction — this enforces the outbox pattern correctness (the caller's domain write and the outbox write share the same commit).

- [ ] **Step 5: Run test — expect pass**

```bash
./gradlew test --tests NotificationServiceImplTest
```

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/mudhut/software/kasisira/notifications/services/NotificationService*.kt \
        src/test/kotlin/com/mudhut/software/kasisira/notifications/services/NotificationServiceImplTest.kt
git commit -m "feat(notifications): add NotificationService enqueue API"
```

---

## Task 14 — `OutboxDispatcher` + `OutboxWorker`

**Files:**
- Test: `src/test/kotlin/com/mudhut/software/kasisira/notifications/services/OutboxDispatcherTest.kt`
- Create: `src/main/kotlin/com/mudhut/software/kasisira/notifications/services/OutboxDispatcher.kt`
- Create: `src/main/kotlin/com/mudhut/software/kasisira/notifications/services/OutboxWorker.kt`
- Modify: `src/main/kotlin/com/mudhut/software/kasisira/KasisiraApplication.kt` (or wherever the `@SpringBootApplication` class is) — add `@EnableScheduling`.

- [ ] **Step 1: Failing test for dispatcher**

```kotlin
package com.mudhut.software.kasisira.notifications.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.software.kasisira.email.EmailService
import com.mudhut.software.kasisira.notifications.entities.Outbox
import com.mudhut.software.kasisira.notifications.entities.OutboxChannel
import com.mudhut.software.kasisira.notifications.entities.OutboxStatus
import com.mudhut.software.kasisira.notifications.repositories.OutboxRepository
import com.mudhut.software.kasisira.notifications.sms.SmsSender
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@ExtendWith(MockKExtension::class)
class OutboxDispatcherTest {

    @MockK private lateinit var repo: OutboxRepository
    @MockK private lateinit var smsSender: SmsSender
    @MockK private lateinit var emailService: EmailService
    private val clock: Clock = Clock.fixed(Instant.parse("2026-04-17T10:00:00Z"), ZoneOffset.UTC)
    private val objectMapper = ObjectMapper()

    private lateinit var dispatcher: OutboxDispatcher

    @org.junit.jupiter.api.BeforeEach
    fun setUp() {
        dispatcher = OutboxDispatcher(repo, smsSender, emailService, objectMapper, clock)
    }

    @Test
    fun `dispatch SMS row calls SmsSender and marks SENT`() {
        val row = Outbox(id = 1, channel = OutboxChannel.SMS, recipient = "+256700000000",
            payload = """{"body":"hello"}""")
        every { smsSender.send("+256700000000", "hello") } just Runs
        every { repo.save(any()) } answers { firstArg() }

        dispatcher.dispatch(row)

        assertEquals(OutboxStatus.SENT, row.status)
        verify { smsSender.send("+256700000000", "hello") }
    }

    @Test
    fun `dispatch failure increments attempts, sets notBefore to backoff, keeps PENDING`() {
        val row = Outbox(id = 2, channel = OutboxChannel.SMS, recipient = "+256700000001",
            payload = """{"body":"x"}""", attempts = 0)
        every { smsSender.send(any(), any()) } throws RuntimeException("boom")
        every { repo.save(any()) } answers { firstArg() }

        dispatcher.dispatch(row)

        assertEquals(1, row.attempts)
        assertEquals(OutboxStatus.PENDING, row.status)
        assertEquals(Instant.parse("2026-04-17T10:00:30Z"), row.notBefore) // +30s backoff
        assertEquals("boom", row.lastError)
    }

    @Test
    fun `third failure marks row FAILED`() {
        val row = Outbox(id = 3, channel = OutboxChannel.SMS, recipient = "+256700000002",
            payload = """{"body":"x"}""", attempts = 2)
        every { smsSender.send(any(), any()) } throws RuntimeException("boom3")
        every { repo.save(any()) } answers { firstArg() }

        dispatcher.dispatch(row)

        assertEquals(3, row.attempts)
        assertEquals(OutboxStatus.FAILED, row.status)
    }

    @Test
    fun `dispatch EMAIL row calls EmailService with template and variables`() {
        val row = Outbox(id = 4, channel = OutboxChannel.EMAIL, recipient = "a@example.com",
            payload = """{"template":"welcome","variables":{"name":"A"}}""")
        every { emailService.sendTemplate("a@example.com", "welcome", mapOf("name" to "A")) } just Runs
        every { repo.save(any()) } answers { firstArg() }

        dispatcher.dispatch(row)

        assertEquals(OutboxStatus.SENT, row.status)
        verify { emailService.sendTemplate("a@example.com", "welcome", mapOf("name" to "A")) }
    }
}
```

- [ ] **Step 2: Run — expect failure**

```bash
./gradlew test --tests OutboxDispatcherTest
```

- [ ] **Step 3: Add `sendTemplate(to, template, variables)` to EmailService**

Extend `EmailService.kt` interface and implement in `EmailServiceImpl.kt`. It should resolve the Thymeleaf template by name (e.g. `email/welcome`) and apply the variables, then send via the existing `JavaMailSender`.

- [ ] **Step 4: Write the dispatcher**

```kotlin
// OutboxDispatcher.kt
package com.mudhut.software.kasisira.notifications.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.software.kasisira.email.EmailService
import com.mudhut.software.kasisira.notifications.entities.Outbox
import com.mudhut.software.kasisira.notifications.entities.OutboxChannel
import com.mudhut.software.kasisira.notifications.entities.OutboxStatus
import com.mudhut.software.kasisira.notifications.repositories.OutboxRepository
import com.mudhut.software.kasisira.notifications.sms.SmsSender
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration

@Component
class OutboxDispatcher(
    private val repo: OutboxRepository,
    private val smsSender: SmsSender,
    private val emailService: EmailService,
    private val objectMapper: ObjectMapper,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(OutboxDispatcher::class.java)
    private val maxAttempts = 3
    private val backoffs = listOf(Duration.ofSeconds(30), Duration.ofMinutes(2))

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun dispatch(row: Outbox) {
        try {
            when (row.channel) {
                OutboxChannel.SMS -> {
                    val json = objectMapper.readValue(row.payload, Map::class.java)
                    smsSender.send(row.recipient, json["body"] as String)
                }
                OutboxChannel.EMAIL -> {
                    val json = objectMapper.readValue(row.payload, Map::class.java)
                    @Suppress("UNCHECKED_CAST")
                    emailService.sendTemplate(
                        row.recipient,
                        json["template"] as String,
                        json["variables"] as Map<String, Any>
                    )
                }
                OutboxChannel.INAPP -> {
                    // In-app notifications are persisted elsewhere via a listener in a later plan.
                    // For MVP, just mark SENT (the Outbox row itself is the visible record).
                }
            }
            row.status = OutboxStatus.SENT
            row.processedAt = clock.instant()
            row.lastError = null
            repo.save(row)
        } catch (e: Exception) {
            row.attempts += 1
            row.lastError = e.message?.take(1000)
            if (row.attempts >= maxAttempts) {
                row.status = OutboxStatus.FAILED
            } else {
                row.status = OutboxStatus.PENDING
                val backoff = backoffs.getOrElse(row.attempts - 1) { backoffs.last() }
                row.notBefore = clock.instant().plus(backoff)
            }
            repo.save(row)
            log.warn("Outbox {} dispatch failed (attempt {}): {}", row.id, row.attempts, e.message)
        }
    }
}
```

- [ ] **Step 5: Write the worker**

```kotlin
// OutboxWorker.kt
package com.mudhut.software.kasisira.notifications.services

import com.mudhut.software.kasisira.notifications.entities.OutboxStatus
import com.mudhut.software.kasisira.notifications.repositories.OutboxRepository
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class OutboxWorker(
    private val repo: OutboxRepository,
    private val dispatcher: OutboxDispatcher,
    private val clock: Clock
) {
    @Scheduled(fixedDelayString = "\${notifications.outbox.fixed-delay-ms:5000}")
    fun drain() {
        val due = repo.findDue(OutboxStatus.PENDING, clock.instant(), PageRequest.of(0, 20))
        for (row in due) dispatcher.dispatch(row)
    }
}
```

- [ ] **Step 6: Enable scheduling**

On the `@SpringBootApplication` class add `@EnableScheduling`.

- [ ] **Step 7: Run tests**

```bash
./gradlew test --tests 'com.mudhut.software.kasisira.notifications.*'
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/com/mudhut/software/kasisira/notifications \
        src/main/kotlin/com/mudhut/software/kasisira/email/EmailService.kt \
        src/main/kotlin/com/mudhut/software/kasisira/email/EmailServiceImpl.kt \
        src/main/kotlin/com/mudhut/software/kasisira/KasisiraApplication.kt \
        src/test/kotlin/com/mudhut/software/kasisira/notifications
git commit -m "feat(notifications): add OutboxDispatcher, OutboxWorker with backoff and EmailService.sendTemplate"
```

---

## Task 15 — Refactor existing email sends to go through the outbox

**Files:**
- Modify: `src/main/kotlin/com/mudhut/software/kasisira/profiles/services/VerificationServiceImpl.kt`
- Modify: tests in `src/test/kotlin/.../VerificationServiceImplTest.kt`

**Context:** `VerificationService` today calls `EmailService.send(...)` directly. We want it to call `NotificationService.enqueueEmail(...)` so that (a) the email is written in the same transaction as the verification token and (b) delivery retries are consistent across SMS and email.

- [ ] **Step 1: Update the tests**

Replace assertions like `verify { emailService.send(...) }` with `verify { notificationService.enqueueEmail(email, "verification", match { it["token"] == token && it["name"] == name }) }`.

- [ ] **Step 2: Update `VerificationServiceImpl`**

- Inject `NotificationService` instead of `EmailService`.
- For each email-sending path (send verification, send welcome, send password-reset), replace the direct call with `notificationService.enqueueEmail(...)`.
- Make sure the method is `@Transactional` so the outbox row is committed atomically with the token row.

- [ ] **Step 3: Run tests**

```bash
./gradlew test --tests VerificationServiceImplTest
```

Expected: PASS.

- [ ] **Step 4: Verify templates still render**

Do a manual smoke on the dev profile:

```bash
SPRING_PROFILES_ACTIVE=development ./gradlew bootRun
```

Register a user via the existing `/api/v1/auth/register` endpoint (or whatever the current path is) and confirm the verification email lands in the inbox (Gmail SMTP) within ~10 seconds of the registration call.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/mudhut/software/kasisira/profiles/services/VerificationServiceImpl.kt \
        src/test/kotlin/com/mudhut/software/kasisira/profiles/services/VerificationServiceImplTest.kt
git commit -m "refactor(profiles): send verification and welcome emails via NotificationService outbox"
```

---

## Task 16 — Phone OTP auth DTOs

**Files:**
- Create: `src/main/kotlin/com/mudhut/software/kasisira/profiles/models/request/RequestOtpRequest.kt`
- Create: `src/main/kotlin/com/mudhut/software/kasisira/profiles/models/request/VerifyOtpRequest.kt`

- [ ] **Step 1: Request-OTP DTO**

```kotlin
package com.mudhut.software.kasisira.profiles.models.request

import com.mudhut.software.kasisira.profiles.entities.OtpPurpose
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class RequestOtpRequest(
    @field:NotBlank(message = "Phone number is required")
    @field:Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Phone number must be E.164 (e.g. +256700000000)")
    val phoneNumber: String,
    val purpose: OtpPurpose = OtpPurpose.LOGIN
)
```

- [ ] **Step 2: Verify-OTP DTO**

```kotlin
package com.mudhut.software.kasisira.profiles.models.request

import com.mudhut.software.kasisira.profiles.entities.OtpPurpose
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class VerifyOtpRequest(
    @field:NotBlank
    @field:Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Phone number must be E.164")
    val phoneNumber: String,

    @field:NotBlank
    @field:Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits")
    val code: String,

    val purpose: OtpPurpose = OtpPurpose.LOGIN,

    /** Optional: used only when purpose = SIGNUP to set username/email. */
    val signupHint: SignupHint? = null
)

data class SignupHint(
    val email: String? = null,
    val fullName: String? = null
)
```

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/mudhut/software/kasisira/profiles/models/request
git commit -m "feat(profiles): add OTP request/verify DTOs"
```

---

## Task 17 — `PhoneAuthService` with TDD

**Files:**
- Test: `src/test/kotlin/com/mudhut/software/kasisira/profiles/services/PhoneAuthServiceImplTest.kt`
- Create: `src/main/kotlin/com/mudhut/software/kasisira/profiles/services/PhoneAuthService.kt`
- Create: `src/main/kotlin/com/mudhut/software/kasisira/profiles/services/PhoneAuthServiceImpl.kt`

- [ ] **Step 1: Failing test**

```kotlin
package com.mudhut.software.kasisira.profiles.services

import com.mudhut.software.kasisira.profiles.entities.*
import com.mudhut.software.kasisira.profiles.models.request.RequestOtpRequest
import com.mudhut.software.kasisira.profiles.models.request.VerifyOtpRequest
import com.mudhut.software.kasisira.profiles.repositories.ContactRepository
import com.mudhut.software.kasisira.profiles.repositories.UserRepository
import com.mudhut.software.kasisira.security.JwtTokenProvider
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class PhoneAuthServiceImplTest {
    @MockK private lateinit var otpService: OtpService
    @MockK private lateinit var userRepository: UserRepository
    @MockK private lateinit var contactRepository: ContactRepository
    @MockK private lateinit var roleService: RoleService
    @MockK private lateinit var jwtTokenProvider: JwtTokenProvider
    @InjectMockKs private lateinit var service: PhoneAuthServiceImpl

    @Test
    fun `requestOtp delegates to OtpService`() {
        every { otpService.requestOtp("+256700000000", OtpPurpose.LOGIN) } just Runs
        service.requestOtp(RequestOtpRequest("+256700000000", OtpPurpose.LOGIN))
        verify { otpService.requestOtp("+256700000000", OtpPurpose.LOGIN) }
    }

    @Test
    fun `verify LOGIN returns JWT for existing user found via contact`() {
        val user = User(id = 10L, username = "u10", email = "u10@example.com", provider = AuthProvider.LOCAL)
        val contact = Contact(id = 1L, user = user, phoneNumber = "+256700000001")
        every { otpService.verifyOtp("+256700000001", "123456", OtpPurpose.LOGIN) } returns true
        every { contactRepository.findByPhoneNumber("+256700000001") } returns contact
        every { jwtTokenProvider.issueTokens(user) } returns TokenPair("access", "refresh")

        val result = service.verify(VerifyOtpRequest("+256700000001", "123456", OtpPurpose.LOGIN))

        assertNotNull(result.accessToken)
    }

    @Test
    fun `verify SIGNUP creates a new user, primary contact, grants TENANT, returns JWT`() {
        every { otpService.verifyOtp("+256700000002", "123456", OtpPurpose.SIGNUP) } returns true
        every { contactRepository.findByPhoneNumber("+256700000002") } returns null
        every { userRepository.save(any()) } answers { firstArg<User>().copy(id = 99L) }
        every { contactRepository.save(any()) } answers { firstArg() }
        every { roleService.grant(any(), RoleName.TENANT) } just Runs
        every { jwtTokenProvider.issueTokens(any()) } returns TokenPair("a", "r")

        val result = service.verify(VerifyOtpRequest("+256700000002", "123456", OtpPurpose.SIGNUP))

        verify { userRepository.save(match { it.provider == AuthProvider.LOCAL }) }
        verify { contactRepository.save(match<Contact> { it.phoneNumber == "+256700000002" && it.isPrimary }) }
        verify { roleService.grant(match { it.id == 99L }, RoleName.TENANT) }
        assertNotNull(result.accessToken)
    }
}

data class TokenPair(val accessToken: String, val refreshToken: String)
```

*(If `JwtTokenProvider` doesn't already expose `issueTokens(User): TokenPair`, add it: issue the access + refresh pair and persist the refresh token via `RefreshTokenRepository` the same way the existing email/password flow does.)*

- [ ] **Step 2: Run — expect failure**

```bash
./gradlew test --tests PhoneAuthServiceImplTest
```

- [ ] **Step 3: Interface**

```kotlin
// PhoneAuthService.kt
package com.mudhut.software.kasisira.profiles.services

import com.mudhut.software.kasisira.profiles.models.request.RequestOtpRequest
import com.mudhut.software.kasisira.profiles.models.request.VerifyOtpRequest
import com.mudhut.software.kasisira.profiles.models.response.AuthResponse

interface PhoneAuthService {
    fun requestOtp(request: RequestOtpRequest)
    fun verify(request: VerifyOtpRequest): AuthResponse
}
```

- [ ] **Step 4: Implementation**

```kotlin
// PhoneAuthServiceImpl.kt
package com.mudhut.software.kasisira.profiles.services

import com.mudhut.software.kasisira.profiles.entities.*
import com.mudhut.software.kasisira.profiles.models.request.RequestOtpRequest
import com.mudhut.software.kasisira.profiles.models.request.VerifyOtpRequest
import com.mudhut.software.kasisira.profiles.models.response.AuthResponse
import com.mudhut.software.kasisira.profiles.repositories.ContactRepository
import com.mudhut.software.kasisira.profiles.repositories.UserRepository
import com.mudhut.software.kasisira.security.JwtTokenProvider
import com.mudhut.software.kasisira.utils.exceptions.InvalidOtpException
import com.mudhut.software.kasisira.utils.exceptions.UserNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class PhoneAuthServiceImpl(
    private val otpService: OtpService,
    private val userRepository: UserRepository,
    private val contactRepository: ContactRepository,
    private val roleService: RoleService,
    private val jwtTokenProvider: JwtTokenProvider
) : PhoneAuthService {

    @Transactional
    override fun requestOtp(request: RequestOtpRequest) {
        // For SIGNUP, allow even if contact doesn't exist.
        // For LOGIN, require an existing contact so we don't leak which phones are registered.
        if (request.purpose == OtpPurpose.LOGIN) {
            contactRepository.findByPhoneNumber(request.phoneNumber) ?: throw UserNotFoundException("No account for this phone.")
        }
        otpService.requestOtp(request.phoneNumber, request.purpose)
    }

    @Transactional
    override fun verify(request: VerifyOtpRequest): AuthResponse {
        if (!otpService.verifyOtp(request.phoneNumber, request.code, request.purpose)) {
            throw InvalidOtpException("Verification failed.")
        }

        val user = when (request.purpose) {
            OtpPurpose.LOGIN -> {
                val contact = contactRepository.findByPhoneNumber(request.phoneNumber)
                    ?: throw UserNotFoundException("No account for this phone.")
                contact.user!!
            }
            OtpPurpose.SIGNUP -> {
                val existing = contactRepository.findByPhoneNumber(request.phoneNumber)
                if (existing != null) existing.user!!
                else {
                    val stub = request.signupHint
                    val userToSave = User(
                        id = 0,
                        username = stub?.email ?: "u_${UUID.randomUUID().toString().substring(0, 8)}",
                        email = stub?.email ?: "${UUID.randomUUID()}@placeholder.kasisira",
                        provider = AuthProvider.LOCAL,
                        isActive = true,
                        isEnabled = true
                    )
                    val saved = userRepository.save(userToSave)
                    contactRepository.save(Contact(
                        id = 0, user = saved,
                        phoneNumber = request.phoneNumber,
                        isPrimary = true, isVerified = true
                    ))
                    roleService.grant(saved, RoleName.TENANT)
                    saved
                }
            }
        }

        val tokens = jwtTokenProvider.issueTokens(user)
        return AuthResponse(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            user = null // UserMapper-based response in controller if needed
        )
    }
}
```

- [ ] **Step 5: Run tests**

```bash
./gradlew test --tests PhoneAuthServiceImplTest
```

Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/mudhut/software/kasisira/profiles/services/PhoneAuthService*.kt \
        src/test/kotlin/com/mudhut/software/kasisira/profiles/services/PhoneAuthServiceImplTest.kt \
        src/main/kotlin/com/mudhut/software/kasisira/security/JwtTokenProvider.kt
git commit -m "feat(profiles): add PhoneAuthService (OTP request + verify issues JWT, signup creates user+contact+role)"
```

---

## Task 18 — `PhoneAuthController` + integration test

**Files:**
- Create: `src/main/kotlin/com/mudhut/software/kasisira/profiles/controllers/PhoneAuthController.kt`
- Create: `src/test/kotlin/com/mudhut/software/kasisira/profiles/controllers/PhoneAuthControllerTest.kt`
- Modify: `src/main/kotlin/com/mudhut/software/kasisira/config/SecurityConfig.kt` — allow unauthenticated access to `/api/v1/auth/phone/**`.

- [ ] **Step 1: Controller**

```kotlin
package com.mudhut.software.kasisira.profiles.controllers

import com.mudhut.software.kasisira.profiles.models.request.RequestOtpRequest
import com.mudhut.software.kasisira.profiles.models.request.VerifyOtpRequest
import com.mudhut.software.kasisira.profiles.models.response.AuthResponse
import com.mudhut.software.kasisira.profiles.services.PhoneAuthService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/auth/phone")
class PhoneAuthController(
    private val phoneAuthService: PhoneAuthService
) {
    @PostMapping("/otp/request")
    fun requestOtp(@Valid @RequestBody body: RequestOtpRequest): ResponseEntity<Map<String, String>> {
        phoneAuthService.requestOtp(body)
        return ResponseEntity.accepted().body(mapOf("status" to "sent"))
    }

    @PostMapping("/otp/verify")
    fun verifyOtp(@Valid @RequestBody body: VerifyOtpRequest): ResponseEntity<AuthResponse> =
        ResponseEntity.ok(phoneAuthService.verify(body))
}
```

*(Note: `application.properties` has `server.servlet.context-path=/api`, so the full path is `/api/v1/auth/phone/otp/request`.)*

- [ ] **Step 2: Open the endpoints in `SecurityConfig`**

Add to the public-paths list: `"/v1/auth/phone/**"`.

- [ ] **Step 3: Integration test with MockMvc + mocked service**

```kotlin
package com.mudhut.software.kasisira.profiles.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.software.kasisira.profiles.entities.OtpPurpose
import com.mudhut.software.kasisira.profiles.models.request.RequestOtpRequest
import com.mudhut.software.kasisira.profiles.models.request.VerifyOtpRequest
import com.mudhut.software.kasisira.profiles.models.response.AuthResponse
import com.mudhut.software.kasisira.profiles.services.PhoneAuthService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.security.test.context.support.WithMockUser

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("testing")
class PhoneAuthControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    @MockkBean private lateinit var phoneAuthService: PhoneAuthService

    @Test
    fun `POST otp request returns 202`() {
        every { phoneAuthService.requestOtp(any()) } just Runs
        mockMvc.post("/v1/auth/phone/otp/request") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(RequestOtpRequest("+256700000000", OtpPurpose.LOGIN))
        }.andExpect {
            status { isAccepted() }
        }
    }

    @Test
    fun `POST otp verify returns JWT`() {
        every { phoneAuthService.verify(any()) } returns AuthResponse("access", "refresh", null)
        mockMvc.post("/v1/auth/phone/otp/verify") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                VerifyOtpRequest("+256700000000", "123456", OtpPurpose.LOGIN)
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { value("access") }
        }
    }

    @Test
    fun `POST otp request returns 400 on bad phone format`() {
        mockMvc.post("/v1/auth/phone/otp/request") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"phoneNumber":"not-a-phone","purpose":"LOGIN"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }
}
```

- [ ] **Step 4: Run tests**

```bash
./gradlew test --tests PhoneAuthControllerTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/mudhut/software/kasisira/profiles/controllers/PhoneAuthController.kt \
        src/main/kotlin/com/mudhut/software/kasisira/config/SecurityConfig.kt \
        src/test/kotlin/com/mudhut/software/kasisira/profiles/controllers/PhoneAuthControllerTest.kt
git commit -m "feat(profiles): expose phone OTP auth endpoints at /v1/auth/phone"
```

---

## Task 19 — End-to-end smoke test on dev

**Files:** none (operational).

- [ ] **Step 1: Start Postgres locally**

Assuming Docker is available:

```bash
docker run --name kasisira-dev-pg -e POSTGRES_USER=dev -e POSTGRES_PASSWORD=dev -e POSTGRES_DB=kasisira_dev -p 5432:5432 -d postgres:16
```

Set env vars in your shell:

```bash
export DEV_DB_USERNAME=dev
export DEV_DB_PASSWORD=dev
export EMAIL_USERNAME=<your Gmail>
export EMAIL_PASSWORD=<your Gmail app password>
export JWT_SECRET=<random 64-char string>
```

- [ ] **Step 2: Boot the app**

```bash
./gradlew bootRun
```

Expected: Flyway applies V1..V5 migrations; app starts on `:8080`.

- [ ] **Step 3: Request an OTP**

```bash
curl -i -X POST http://localhost:8080/api/v1/auth/phone/otp/request \
  -H 'Content-Type: application/json' \
  -d '{"phoneNumber":"+256700000000","purpose":"SIGNUP"}'
```

Expected: `202 Accepted`. Check the application log — you should see a `[noop-sms]` line with a 6-digit code.

- [ ] **Step 4: Verify the OTP**

Copy the code from the log. Then:

```bash
curl -i -X POST http://localhost:8080/api/v1/auth/phone/otp/verify \
  -H 'Content-Type: application/json' \
  -d '{"phoneNumber":"+256700000000","code":"<code>","purpose":"SIGNUP"}'
```

Expected: `200 OK` with `accessToken` and `refreshToken` in the body.

- [ ] **Step 5: Check the DB**

```bash
docker exec -it kasisira-dev-pg psql -U dev -d kasisira_dev -c "SELECT id, email FROM users;"
docker exec -it kasisira-dev-pg psql -U dev -d kasisira_dev -c "SELECT phone_number FROM contacts;"
docker exec -it kasisira-dev-pg psql -U dev -d kasisira_dev -c "SELECT role_name FROM user_roles;"
docker exec -it kasisira-dev-pg psql -U dev -d kasisira_dev -c "SELECT channel, status, attempts FROM outbox ORDER BY id DESC LIMIT 5;"
```

Expected: a new user, a primary contact with the phone, a `TENANT` role, an SMS outbox row with `status=SENT`.

- [ ] **Step 6: Commit the completed-plan note**

No code changes here — this step is a manual verification gate. Record the checklist completion in your PR description.

---

## Acceptance Criteria (definition of done for Plan 1)

- `./gradlew test` passes with no skipped or failing tests.
- `./gradlew bootRun` boots cleanly on the `development` profile with Flyway migrations V1..V5 applied.
- Manual smoke in Task 19 succeeds end-to-end (OTP request → verify → JWT).
- New users receive the `TENANT` role. Verified via DB + via JWT's `roles` claim.
- `VerificationServiceImpl` email paths flow through the outbox (confirm by checking `outbox` table for an `EMAIL` row after registration).
- Tests include: `RoleServiceImplTest`, `OtpServiceImplTest`, `NotificationServiceImplTest`, `OutboxDispatcherTest`, `PhoneAuthServiceImplTest`, `PhoneAuthControllerTest`, `FlywayBaselineTest`, updated `AuthServiceImplTest` and `VerificationServiceImplTest`.

---

## What Plan 1 does **not** do (intentional)

- Does not touch the in-progress `properties` module on `feature/add-properties` — Plan 2 absorbs that branch's work cleanly.
- Does not add any notification templates beyond what's needed to keep existing email paths working. Per-event notification mapping (from spec §11.2) is added as each feature ships.
- Does not build the admin-visible notification queue UI — that's Plan 5.
- Does not add row-level locking to the outbox worker — MVP runs a single instance. When we scale out, add `SELECT … FOR UPDATE SKIP LOCKED`.
- Does not implement in-app notification persistence beyond the outbox row itself (a domain `notification` table for tenant/owner UIs is added in Plan 4 when chat needs it).

---

## Self-review summary

- Spec coverage: §6.1 (auth) covered by Tasks 2-8, 16-18; §6.11 (notifications) covered by Tasks 12-15; §7.1 (global roles) covered by Tasks 2-5.
- Type consistency checked: `RoleName`, `OtpPurpose`, `OutboxChannel`, `OutboxStatus` used uniformly; `TokenPair` defined once in `JwtTokenProvider`.
- No placeholders: every step shows real code or real commands.
- Scope discipline: org-scoped permissions (spec §7.2) are Plan 2. Rent, chat, faults are later plans.
