# Unauthenticated Requests Return 401 JSON Response

**Date:** 2026-04-19
**Status:** Approved — ready for implementation planning

## Problem

Protected endpoints (e.g. `POST /api/v1/orgs/create`) do not return a clean `401 Unauthorized` JSON response when a request arrives without — or with an invalid — JWT.

Spring Security's `.anyRequest().authenticated()` rule in `SecurityConfig.filterChain` correctly blocks these requests before they reach the controller, but because no `AuthenticationEntryPoint` is configured, Spring falls back to default behavior: an HTML error page or a `403 Forbidden`. The existing `GlobalExceptionHandler.handleAuthenticationException` (`GlobalExceptionHandler.kt:218`) is never invoked, because `@ControllerAdvice` only catches exceptions thrown inside the `DispatcherServlet` — authentication rejection happens earlier in the filter chain.

## Goal

Every request to a protected endpoint that lacks valid authentication returns:

- HTTP status `401 Unauthorized`
- `Content-Type: application/json`
- Body conforming to the existing `ErrorResponse` shape

Consistently, across all 17 `@AuthenticationPrincipal`-annotated endpoints, without per-controller changes.

Additionally, authenticated-but-unauthorized requests (role-based rejection) return `403 Forbidden` in the same JSON shape, for consistency.

## Approach

Configure Spring Security's exception-handling chain with two custom handlers:

1. A custom `AuthenticationEntryPoint` that writes a 401 JSON response.
2. A custom `AccessDeniedHandler` that writes a 403 JSON response.

Both handlers serialize an `ErrorResponse` using the application's existing `ObjectMapper` so the response matches the format produced by `GlobalExceptionHandler`.

## Design

### New files

**`src/main/kotlin/com/mudhut/software/kasisira/security/RestAuthenticationEntryPoint.kt`**

Spring bean (`@Component`) implementing `org.springframework.security.web.AuthenticationEntryPoint`. On `commence(...)`:

- Set `response.status = 401`
- Set `response.contentType = MediaType.APPLICATION_JSON_VALUE`
- Write `ErrorResponse(errorCode = "AUTHENTICATION_ERROR", message = "Authentication is required to access this resource")` using the injected `ObjectMapper`

Covers all unauthenticated rejection cases: missing `Authorization` header, malformed JWT, expired JWT, JWT for a non-existent user. All three return the same 401 response — a future iteration may introduce distinct `TOKEN_EXPIRED` codes if the frontend needs them.

**`src/main/kotlin/com/mudhut/software/kasisira/security/RestAccessDeniedHandler.kt`**

Spring bean (`@Component`) implementing `org.springframework.security.web.access.AccessDeniedHandler`. On `handle(...)`:

- Set `response.status = 403`
- Set `response.contentType = MediaType.APPLICATION_JSON_VALUE`
- Write `ErrorResponse(errorCode = "AUTHORIZATION_ERROR", message = "You don't have permission to access this resource")`

Mirrors the existing `GlobalExceptionHandler.handleAccessDeniedException` (`GlobalExceptionHandler.kt:210`), ensuring a uniform 403 body whether the failure originates in the filter chain or inside a controller.

### Modified files

**`src/main/kotlin/com/mudhut/software/kasisira/config/SecurityConfig.kt`**

- Inject the two new beans as class fields (following the existing `@Autowired lateinit var` pattern used for `customUserDetailsService` and `oauth2AuthenticationSuccessHandler`)
- In `filterChain(http)`, add:

  ```kotlin
  .exceptionHandling {
      it.authenticationEntryPoint(restAuthenticationEntryPoint)
      it.accessDeniedHandler(restAccessDeniedHandler)
  }
  ```

  Placed after `.sessionManagement { ... }` and before `.authorizeHttpRequests { ... }`.

### Unchanged

- No controller changes. All `@AuthenticationPrincipal UserPrincipal` parameter signatures stay non-nullable because Spring Security blocks unauthenticated requests before they reach the controller layer.
- `GlobalExceptionHandler` is untouched. `handleAuthenticationException` continues to serve exceptions thrown *inside* controllers (e.g., `BadCredentialsException` from `AuthController.login`).
- `JwtAuthenticationFilter` is untouched. It already swallows invalid-token exceptions silently so the request proceeds with no authentication set — the new entry point handles the downstream rejection cleanly.
- The public endpoint list in `SecurityConfig.filterChain.authorizeHttpRequests` is untouched.

## Response Contract

### Unauthenticated (401)

```json
{
  "errorCode": "AUTHENTICATION_ERROR",
  "message": "Authentication is required to access this resource",
  "timestamp": "2026-04-19T12:34:56.789Z"
}
```

### Authenticated but Forbidden (403)

```json
{
  "errorCode": "AUTHORIZATION_ERROR",
  "message": "You don't have permission to access this resource",
  "timestamp": "2026-04-19T12:34:56.789Z"
}
```

## Testing

Integration tests using the existing MockMvc setup.

1. **`POST /v1/orgs/create` with no `Authorization` header** → expect `status = 401`, body `errorCode = "AUTHENTICATION_ERROR"`, `Content-Type = application/json`.
2. **`POST /v1/orgs/create` with `Authorization: Bearer malformed.jwt.token`** → expect `status = 401`, `errorCode = "AUTHENTICATION_ERROR"`.
3. **`GET /v1/orgs/me` with no `Authorization` header** → expect `status = 401`. Confirms the fix is filter-chain-level, not endpoint-specific.
4. **Optional spot-check** on one `@PreAuthorize`-protected endpoint with a valid token for a user lacking the required role → expect `status = 403`, `errorCode = "AUTHORIZATION_ERROR"`. Verifies the `AccessDeniedHandler` is wired correctly. Skip if no such endpoint currently exists.

## Out of Scope (YAGNI)

- Distinct `TOKEN_EXPIRED` / `TOKEN_INVALID` / `TOKEN_MISSING` error codes
- Retry/refresh hints (e.g., `WWW-Authenticate` challenge details) in the response body
- Changes to controller method signatures
- Any per-endpoint null-checks or defensive guards
- Refactoring `JwtAuthenticationFilter` error handling
