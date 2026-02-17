# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**IDVerse API Client Library** - A reusable Java library that handles all HTTP communication with the IDVerse identity verification API, including OAuth authentication, webhook JWT signing, and SMS verification requests.

- **GroupId:** `org.itnaf`
- **ArtifactId:** `idverse-api`
- **Version:** `1.0-SNAPSHOT`
- **Java Version:** 21
- **Build Tool:** Maven
- **Packaging:** `jar` (library, not executable)
- **GitHub:** https://github.com/marcofanti/idverse-api

### Relationship to `idverse` App

This library is consumed by the `idverse` web application:

| Repo | Path | Role |
|------|------|------|
| `idverse-api` | `/Users/mfanti/Documents/BehavioSec/IDVerse/idverse-api` | This library |
| `idverse` | `/Users/mfanti/Documents/BehavioSec/IDVerse/idverse` | Web app consumer |

The library must be installed to the local Maven repository before the app can build:
```bash
mvn clean install -DskipTests
```

## Technology Stack

- **Spring Boot Starter WebFlux** - `WebClient` for non-blocking HTTP calls
- **Spring Boot Starter Validation** - Jakarta validation annotations on DTOs
- **JWT (jjwt 0.12.3)** - JWT generation and validation for webhook authentication
- **Lombok** - Boilerplate reduction (`@Data`, `@Service`, `@Slf4j`, etc.)

> **No Spring Boot repackaging.** The `spring-boot-maven-plugin` is configured with `<skip>true</skip>` so the artifact is a plain library JAR, not an executable fat JAR.

## Package Structure

All classes live under `org.itnaf.idverse.client` — a sub-package of the consuming app's base package (`org.itnaf.idverse`). This means Spring Boot's `@ComponentScan` in the `idverse` app automatically discovers the library's `@Service` beans without any extra configuration.

```
src/main/java/org/itnaf/idverse/client/
├── IdVerseApiClient.java            # Main entry point — sends verification requests
├── model/
│   ├── VerificationRequest.java     # API request DTO (phone, referenceId, transactionId, etc.)
│   ├── OAuthTokenResponse.java      # OAuth 2.0 token response DTO
│   └── WebhookPayload.java          # Incoming webhook payload DTO (transactionId + event)
└── service/
    ├── OAuthTokenService.java       # OAuth token management with 800-second cache
    └── JwtService.java              # JWT generation/validation for webhook auth
```

## Build Commands

```bash
# Install library to local Maven repo (required before building idverse app)
mvn clean install -DskipTests

# Compile only
mvn compile

# Run tests
mvn test

# Run a single test class
mvn test -Dtest=ClassName

# Package (produces library JAR, not executable)
mvn package
```

## Class Reference

### `IdVerseApiClient`

The single entry point for calling the IDVerse SMS verification API.

**Method:** `sendVerification(VerificationRequest request) → String`
- Obtains an OAuth token via `OAuthTokenService`
- Builds the JSON request body (required + optional fields, webhook URLs with JWT auth)
- POSTs to `idverseApiUrl` with `Authorization: Bearer` header
- Returns raw JSON response body
- Throws `RuntimeException` on HTTP errors, HTML responses, timeouts

**Constructor dependencies** (injected by name from the app's `ApiConfig.java`):
| Parameter | Bean name | Source |
|-----------|-----------|--------|
| `WebClient webClient` | `webClient` | `ApiConfig.webClient()` |
| `OAuthTokenService oAuthTokenService` | auto | component scan |
| `JwtService jwtService` | auto | component scan |
| `String idverseApiUrl` | `idverseApiUrl` | `ApiConfig.idverseApiUrl()` |
| `String verboseMode` | `verboseMode` | `ApiConfig.verboseMode()` |
| `String notifyUrlComplete` | `notifyUrlComplete` | `ApiConfig.notifyUrlComplete()` |
| `String notifyUrlEvent` | `notifyUrlEvent` | `ApiConfig.notifyUrlEvent()` |

---

### `OAuthTokenService`

Manages OAuth 2.0 client-credentials tokens with in-memory caching.

**Key behaviour:**
- Token cached for **800 seconds** (not the `expires_in` value from the response)
- Thread-safe via `synchronized` on `getAccessToken()` and `clearToken()`
- `testConnection()` — makes a live OAuth call, returns `OAuthTokenResponse`
- `testConnectionVerbose(boolean debug)` — returns `Map<String, Object>` with request/response detail; used by `OAuthTestController` in the app

**Constructor dependencies:**
| Parameter | Bean name |
|-----------|-----------|
| `WebClient webClient` | `webClient` |
| `String idverseClientId` | `idverseClientId` |
| `String idverseClientSecret` | `idverseClientSecret` |
| `String idverseOAuthUrl` | `idverseOAuthUrl` |
| `String verboseMode` | `verboseMode` |

---

### `JwtService`

Generates and validates HS256 JWT tokens for webhook authentication.

**Key behaviour:**
- Secret key padded to minimum 32 bytes if shorter
- Default expiration: 24 hours
- `generateToken(String subject)` — subjects are `"webhook-complete"` or `"webhook-event"`
- `validateToken(String token)` — throws `JwtException` if invalid or expired
- `isTokenValid(String token)` — boolean wrapper, never throws

**Constructor dependency:**
| Parameter | Bean name |
|-----------|-----------|
| `String jwtSecretKey` | `jwtSecretKey` |

---

### `model/VerificationRequest`

Request DTO for the IDVerse SMS verification API.

| Field | Required | Validation |
|-------|----------|-----------|
| `phoneCode` | Yes | `^\\+?[1-9]\\d{0,3}$` (e.g. `+1`) |
| `phoneNumber` | Yes | `^\\d{4,15}$` |
| `referenceId` | Yes | non-blank |
| `transactionId` | No* | 10–128 chars, `[a-zA-Z0-9 _-]` |
| `name` | No | — |
| `suppliedFirstName` | No | — |

\* Auto-generated by `IdVerificationService` in the app if not provided.

---

### `model/OAuthTokenResponse`

Maps the OAuth 2.0 token endpoint response.

- `isSuccess()` — true when `accessToken` is non-null/non-empty
- `hasError()` — true when `error` or `message` fields are set

---

### `model/WebhookPayload`

Incoming webhook payload from IDVerse. Fields: `transactionId`, `event`.

**Event values** (from IDVerse docs):
- Event webhook: `pending`, `termsAndConditions`, `idSelection`, `personalDetails`, `liveness`, `expired`, `cancelled`, `completedPass`, `completedFlagged`
- Completion webhook: `completedPass`, `completedFlagged`, `expired`, `cancelled`

## Architectural Decisions

### Why a Separate Library?

The HTTP client code (OAuth, JWT, API calls, DTOs) has no dependency on the database or web framework. Separating it into a library:
1. Makes it independently testable
2. Allows reuse in other projects without pulling in JPA, Thymeleaf, etc.
3. Creates a clear boundary between "IDVerse API communication" and "application logic"

### Why Sub-Package (`org.itnaf.idverse.client`)?

Using a sub-package of the app's base package means Spring Boot's component scan picks up `@Service` beans automatically — no `@SpringBootApplication(scanBasePackages=...)` change needed in the consuming app.

### No Spring Boot Auto-Configuration

The library relies on the consuming app's `ApiConfig.java` to provide the required `@Bean` definitions (`idverseClientId`, `idverseOAuthUrl`, `webClient`, etc.). This keeps the library simple and avoids the complexity of a custom auto-configuration module.

### Plain Library JAR

The `spring-boot-maven-plugin` repackaging is skipped. This produces a standard library JAR that can be added as a `<dependency>` in any Maven project.

## Common Development Tasks

### Modify a Request Field

1. Edit `VerificationRequest.java`
2. Update `IdVerseApiClient.sendVerification()` to include the field in `requestBody`
3. `mvn clean install -DskipTests`
4. Update callers and tests in the `idverse` app

### Add a New API Operation

1. Add a new method to `IdVerseApiClient` (or create a new client class)
2. Add any new model DTOs in `model/`
3. `mvn clean install -DskipTests`
4. Call the new method from a service in the `idverse` app

### Change the Token Cache Duration

In `OAuthTokenService.fetchNewToken()`:
```java
int expiresIn = 800;  // seconds — change this value
```

### Change JWT Expiration

In `JwtService`:
```java
private final long expirationMs = 86400000; // 24 hours in ms — change this value
```

### Troubleshooting

**`idverse` app fails to find library classes:**
- Run `mvn clean install -DskipTests` in this repo first
- Verify the JAR exists: `ls ~/.m2/repository/org/itnaf/idverse-api/1.0-SNAPSHOT/`

**Spring can't inject `OAuthTokenService` or `JwtService`:**
- Confirm the consuming app's `@SpringBootApplication` class is in `org.itnaf.idverse` (parent of `org.itnaf.idverse.client`)
- Confirm `ApiConfig.java` defines all required `@Bean` String methods

**OAuth token not refreshing:**
- Call `POST /test/oauth/clear` in the running app to force a new token fetch
