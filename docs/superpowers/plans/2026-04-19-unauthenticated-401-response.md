# Unauthenticated 401 JSON Response Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Protected endpoints return a consistent JSON `401 Unauthorized` response (and `403 Forbidden` for authenticated-but-unauthorized requests) instead of Spring Security's default HTML/403 fallback.

**Architecture:** Add two Spring beans — `RestAuthenticationEntryPoint` and `RestAccessDeniedHandler` — that write an `ErrorResponse` body using the existing Jackson `ObjectMapper`. Wire them into `SecurityConfig.filterChain` via `.exceptionHandling { ... }`. No controller changes.

**Tech Stack:** Kotlin 2.2, Spring Boot 4.0.0-M2, Spring Security 6, Jackson, JUnit 5, MockK, Spring Security Test.

**Spec:** `docs/superpowers/specs/2026-04-19-unauthenticated-401-response-design.md`

---

## File Structure

**New files:**
- `src/main/kotlin/com/mudhut/software/kasisira/security/RestAuthenticationEntryPoint.kt` — Spring `@Component` that writes a JSON 401 body when Spring Security rejects an unauthenticated request.
- `src/main/kotlin/com/mudhut/software/kasisira/security/RestAccessDeniedHandler.kt` — Spring `@Component` that writes a JSON 403 body when an authenticated request lacks required authorities.
- `src/test/kotlin/com/mudhut/software/kasisira/security/RestAuthenticationEntryPointTest.kt` — Pure unit test; calls `commence(...)` directly against `MockHttpServletResponse`.
- `src/test/kotlin/com/mudhut/software/kasisira/security/RestAccessDeniedHandlerTest.kt` — Pure unit test; calls `handle(...)` directly against `MockHttpServletResponse`.
- `src/test/kotlin/com/mudhut/software/kasisira/security/SecurityChainIntegrationTest.kt` — Full MockMvc integration test that drives requests through `SecurityConfig.filterChain` to verify the wiring.

**Modified files:**
- `src/main/kotlin/com/mudhut/software/kasisira/config/SecurityConfig.kt` — Inject the two new beans and add an `.exceptionHandling { ... }` block to `filterChain(http)`.

**Unchanged:** All controllers, `GlobalExceptionHandler`, `JwtAuthenticationFilter`, `ErrorResponse`, `UserPrincipal`.

---

## Task 1: RestAuthenticationEntryPoint

**Files:**
- Create: `src/main/kotlin/com/mudhut/software/kasisira/security/RestAuthenticationEntryPoint.kt`
- Test: `src/test/kotlin/com/mudhut/software/kasisira/security/RestAuthenticationEntryPointTest.kt`

- [ ] **Step 1.1: Write the failing unit test**

Create `src/test/kotlin/com/mudhut/software/kasisira/security/RestAuthenticationEntryPointTest.kt`:

```kotlin
package com.mudhut.software.kasisira.security

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.mudhut.software.kasisira.utils.exceptions.ErrorResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.InsufficientAuthenticationException

class RestAuthenticationEntryPointTest {

    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    private val entryPoint = RestAuthenticationEntryPoint(objectMapper)

    @Test
    fun `writes 401 with AUTHENTICATION_ERROR JSON body`() {
        val request = MockHttpServletRequest("GET", "/v1/orgs/me")
        val response = MockHttpServletResponse()
        val exception = InsufficientAuthenticationException("no auth")

        entryPoint.commence(request, response, exception)

        assertEquals(401, response.status)
        assertEquals(MediaType.APPLICATION_JSON_VALUE, response.contentType)

        val body = objectMapper.readValue(response.contentAsString, ErrorResponse::class.java)
        assertEquals("AUTHENTICATION_ERROR", body.errorCode)
        assertEquals("Authentication is required to access this resource", body.message)
    }
}
```

- [ ] **Step 1.2: Run the test to verify it fails**

```bash
cd kasisira-backend && ./gradlew test --tests "com.mudhut.software.kasisira.security.RestAuthenticationEntryPointTest"
```

Expected: Compilation failure — `Unresolved reference: RestAuthenticationEntryPoint`.

- [ ] **Step 1.3: Implement RestAuthenticationEntryPoint**

Create `src/main/kotlin/com/mudhut/software/kasisira/security/RestAuthenticationEntryPoint.kt`:

```kotlin
package com.mudhut.software.kasisira.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.software.kasisira.utils.exceptions.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

@Component
class RestAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper
) : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        val body = ErrorResponse(
            errorCode = "AUTHENTICATION_ERROR",
            message = "Authentication is required to access this resource"
        )
        response.writer.write(objectMapper.writeValueAsString(body))
    }
}
```

- [ ] **Step 1.4: Run the test to verify it passes**

```bash
cd kasisira-backend && ./gradlew test --tests "com.mudhut.software.kasisira.security.RestAuthenticationEntryPointTest"
```

Expected: PASS.

- [ ] **Step 1.5: Commit**

```bash
cd kasisira-backend && git add src/main/kotlin/com/mudhut/software/kasisira/security/RestAuthenticationEntryPoint.kt src/test/kotlin/com/mudhut/software/kasisira/security/RestAuthenticationEntryPointTest.kt
git commit -m "feat(security): add RestAuthenticationEntryPoint for JSON 401 responses"
```

---

## Task 2: RestAccessDeniedHandler

**Files:**
- Create: `src/main/kotlin/com/mudhut/software/kasisira/security/RestAccessDeniedHandler.kt`
- Test: `src/test/kotlin/com/mudhut/software/kasisira/security/RestAccessDeniedHandlerTest.kt`

- [ ] **Step 2.1: Write the failing unit test**

Create `src/test/kotlin/com/mudhut/software/kasisira/security/RestAccessDeniedHandlerTest.kt`:

```kotlin
package com.mudhut.software.kasisira.security

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.mudhut.software.kasisira.utils.exceptions.ErrorResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.access.AccessDeniedException

class RestAccessDeniedHandlerTest {

    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    private val handler = RestAccessDeniedHandler(objectMapper)

    @Test
    fun `writes 403 with AUTHORIZATION_ERROR JSON body`() {
        val request = MockHttpServletRequest("POST", "/v1/orgs/create")
        val response = MockHttpServletResponse()
        val exception = AccessDeniedException("forbidden")

        handler.handle(request, response, exception)

        assertEquals(403, response.status)
        assertEquals(MediaType.APPLICATION_JSON_VALUE, response.contentType)

        val body = objectMapper.readValue(response.contentAsString, ErrorResponse::class.java)
        assertEquals("AUTHORIZATION_ERROR", body.errorCode)
        assertEquals("You don't have permission to access this resource", body.message)
    }
}
```

- [ ] **Step 2.2: Run the test to verify it fails**

```bash
cd kasisira-backend && ./gradlew test --tests "com.mudhut.software.kasisira.security.RestAccessDeniedHandlerTest"
```

Expected: Compilation failure — `Unresolved reference: RestAccessDeniedHandler`.

- [ ] **Step 2.3: Implement RestAccessDeniedHandler**

Create `src/main/kotlin/com/mudhut/software/kasisira/security/RestAccessDeniedHandler.kt`:

```kotlin
package com.mudhut.software.kasisira.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.software.kasisira.utils.exceptions.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

@Component
class RestAccessDeniedHandler(
    private val objectMapper: ObjectMapper
) : AccessDeniedHandler {

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException
    ) {
        response.status = HttpServletResponse.SC_FORBIDDEN
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        val body = ErrorResponse(
            errorCode = "AUTHORIZATION_ERROR",
            message = "You don't have permission to access this resource"
        )
        response.writer.write(objectMapper.writeValueAsString(body))
    }
}
```

- [ ] **Step 2.4: Run the test to verify it passes**

```bash
cd kasisira-backend && ./gradlew test --tests "com.mudhut.software.kasisira.security.RestAccessDeniedHandlerTest"
```

Expected: PASS.

- [ ] **Step 2.5: Commit**

```bash
cd kasisira-backend && git add src/main/kotlin/com/mudhut/software/kasisira/security/RestAccessDeniedHandler.kt src/test/kotlin/com/mudhut/software/kasisira/security/RestAccessDeniedHandlerTest.kt
git commit -m "feat(security): add RestAccessDeniedHandler for JSON 403 responses"
```

---

## Task 3: Wire into SecurityConfig (integration test)

**Files:**
- Modify: `src/main/kotlin/com/mudhut/software/kasisira/config/SecurityConfig.kt`
- Test: `src/test/kotlin/com/mudhut/software/kasisira/security/SecurityChainIntegrationTest.kt`

- [ ] **Step 3.1: Write the failing integration test**

Create `src/test/kotlin/com/mudhut/software/kasisira/security/SecurityChainIntegrationTest.kt`:

```kotlin
package com.mudhut.software.kasisira.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.software.kasisira.owner_org.models.request.CreateOrgRequest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("testing")
class SecurityChainIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    @Test
    fun `POST orgs create without auth returns 401 JSON`() {
        mockMvc.post("/v1/orgs/create") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(CreateOrgRequest("My Org"))
        }.andExpect {
            status { isUnauthorized() }
            content { contentType(MediaType.APPLICATION_JSON) }
            jsonPath("$.errorCode") { value("AUTHENTICATION_ERROR") }
            jsonPath("$.message") { value("Authentication is required to access this resource") }
        }
    }
}
```

- [ ] **Step 3.2: Run the test to verify it fails**

```bash
cd kasisira-backend && ./gradlew test --tests "com.mudhut.software.kasisira.security.SecurityChainIntegrationTest"
```

Expected: FAIL. The response will either be 403 (Spring default), have no JSON body, or have a non-`AUTHENTICATION_ERROR` error code — confirming the entry point is not yet wired.

- [ ] **Step 3.3: Wire the entry point and access denied handler into SecurityConfig**

Modify `src/main/kotlin/com/mudhut/software/kasisira/config/SecurityConfig.kt`.

Add two new `@Autowired lateinit var` fields alongside the existing ones (just after the `oauth2AuthenticationSuccessHandler` field, around line 34):

```kotlin
    @Autowired
    private lateinit var restAuthenticationEntryPoint: RestAuthenticationEntryPoint

    @Autowired
    private lateinit var restAccessDeniedHandler: RestAccessDeniedHandler
```

Add the import statements at the top with the other `security` imports:

```kotlin
import com.mudhut.software.kasisira.security.RestAccessDeniedHandler
import com.mudhut.software.kasisira.security.RestAuthenticationEntryPoint
```

In `filterChain(http: HttpSecurity)`, add `.exceptionHandling { ... }` between `.sessionManagement { ... }` and `.authorizeHttpRequests { ... }`. The final block becomes:

```kotlin
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                it.authenticationEntryPoint(restAuthenticationEntryPoint)
                it.accessDeniedHandler(restAccessDeniedHandler)
            }
            .authorizeHttpRequests { authorize ->
                authorize
                    // Public endpoints (paths are relative to context-path, so exclude /api prefix)
                    .requestMatchers(
                        "/v1/auth/register",
                        "/v1/auth/login",
                        "/v1/auth/refresh",
                        "/v1/auth/verify-email",
                        "/v1/auth/resend-verification",
                        "/v1/auth/check-email-verified",
                        "/v1/auth/phone/**",
                        "/oauth2/**",
                        "/error",
                        "/actuator/health"
                    ).permitAll()
                    // Public property endpoints (GET only for browsing)
                    .requestMatchers(HttpMethod.GET, "/v1/properties").permitAll()
                    .requestMatchers(HttpMethod.GET, "/v1/properties/search").permitAll()
                    .requestMatchers(HttpMethod.GET, "/v1/properties/type/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/v1/properties/listing/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/v1/properties/{id}").permitAll()
                    .requestMatchers(HttpMethod.GET, "/v1/properties/{propertyId}/media").permitAll()
                    // All other requests require authentication
                    .anyRequest().authenticated()
            }
            .oauth2Login { oauth2 ->
                oauth2
                    .successHandler(oauth2AuthenticationSuccessHandler)
            }
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }
```

- [ ] **Step 3.4: Run the test to verify it passes**

```bash
cd kasisira-backend && ./gradlew test --tests "com.mudhut.software.kasisira.security.SecurityChainIntegrationTest"
```

Expected: PASS.

- [ ] **Step 3.5: Commit**

```bash
cd kasisira-backend && git add src/main/kotlin/com/mudhut/software/kasisira/config/SecurityConfig.kt src/test/kotlin/com/mudhut/software/kasisira/security/SecurityChainIntegrationTest.kt
git commit -m "feat(security): wire RestAuthenticationEntryPoint and RestAccessDeniedHandler into filter chain"
```

---

## Task 4: Additional integration coverage (malformed JWT, GET endpoint)

**Files:**
- Modify: `src/test/kotlin/com/mudhut/software/kasisira/security/SecurityChainIntegrationTest.kt`

- [ ] **Step 4.1: Add failing tests for malformed JWT and GET endpoint without auth**

Add these two test methods to the existing `SecurityChainIntegrationTest` class. Also add the missing imports at the top of the file:

```kotlin
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.get
```

Test methods to add inside the class:

```kotlin
    @Test
    fun `POST orgs create with malformed JWT returns 401 JSON`() {
        mockMvc.post("/v1/orgs/create") {
            header(HttpHeaders.AUTHORIZATION, "Bearer malformed.jwt.token")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(CreateOrgRequest("My Org"))
        }.andExpect {
            status { isUnauthorized() }
            content { contentType(MediaType.APPLICATION_JSON) }
            jsonPath("$.errorCode") { value("AUTHENTICATION_ERROR") }
        }
    }

    @Test
    fun `GET orgs me without auth returns 401 JSON`() {
        mockMvc.get("/v1/orgs/me").andExpect {
            status { isUnauthorized() }
            content { contentType(MediaType.APPLICATION_JSON) }
            jsonPath("$.errorCode") { value("AUTHENTICATION_ERROR") }
        }
    }
```

- [ ] **Step 4.2: Run the tests to verify they pass**

```bash
cd kasisira-backend && ./gradlew test --tests "com.mudhut.software.kasisira.security.SecurityChainIntegrationTest"
```

Expected: All three tests in the class PASS. Both new tests exercise paths already covered by the entry point wired in Task 3, so they should pass immediately — confirming the fix is chain-level, not endpoint-specific.

- [ ] **Step 4.3: Run the full test suite to confirm no regressions**

```bash
cd kasisira-backend && ./gradlew test
```

Expected: BUILD SUCCESSFUL. Pay particular attention to `AuthControllerTest`, `PhoneAuthControllerTest`, and `OwnerOrgControllerTest` — these exercise Spring Security paths and would surface any accidental regression.

- [ ] **Step 4.4: Commit**

```bash
cd kasisira-backend && git add src/test/kotlin/com/mudhut/software/kasisira/security/SecurityChainIntegrationTest.kt
git commit -m "test(security): cover malformed JWT and protected GET endpoint for 401 JSON response"
```

---

## Done Criteria

- All four tasks committed.
- `./gradlew test` passes cleanly.
- `curl -i -X POST http://localhost:8080/api/v1/orgs/create -H "Content-Type: application/json" -d '{"name":"x"}'` returns `HTTP/1.1 401` with JSON body `{"errorCode":"AUTHENTICATION_ERROR", ...}` instead of the previous HTML/403 fallback.
