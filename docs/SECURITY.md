# ScreenAI Client — Security

Detailed documentation of client-side security features.

> **For the full security architecture (server + client), see** [Server SECURITY.md](../../ScreenAi-security-server/docs/SECURITY.md)

---

## Overview

The client implements security at three levels:

1. **Authentication** — JWT-based login with auto-refresh and retry
2. **Encrypted Storage** — AES-256-GCM for persisted credentials
3. **Room Security UI** — Password and access code dialogs

---

## Authentication Flow

```
  App Launch
      │
      ▼
  ┌────────────────────┐
  │ Check stored creds  │
  │ (credentials.enc)   │
  └─────────┬──────────┘
            │
      ┌─────▼─────┐
      │ Has saved  │──── No ───► Show LoginDialog
      │ refresh    │                    │
      │ token?     │              ┌─────▼─────┐
      └─────┬──────┘              │ Login or   │
            │ Yes                 │ Sign Up    │
            ▼                     └─────┬──────┘
  ┌────────────────────┐                │
  │ tryAutoLogin()     │                ▼
  │ POST /api/auth/    │        POST /api/auth/login
  │     refresh        │        POST /api/auth/register
  └─────────┬──────────┘                │
            │                           │
      ┌─────▼─────┐              ┌──────▼──────┐
      │ Success?   │──── No ───► │ LoginDialog  │
      └─────┬──────┘              └─────────────┘
            │ Yes
            ▼
  ┌────────────────────┐
  │ Auto-connect to    │
  │ server from .env   │
  │ (SERVER_HOST:PORT) │
  └─────────┬──────────┘
            │
  ┌─────────▼──────────┐
  │ Schedule auto-      │
  │ refresh (expiry     │
  │ - 60 seconds)       │
  └─────────────────────┘
```

### Implementation: `AuthenticationService.java`

| Method | Purpose |
|--------|---------|
| `login(username, password, rememberMe)` | POST to `/api/auth/login`, stores tokens, schedules refresh |
| `register(username, password)` | POST to `/api/auth/register` |
| `refreshToken()` | POST refresh token to `/api/auth/refresh`, updates stored tokens |
| `logout()` | Cancels refresh scheduler, clears local tokens, POST to `/api/auth/logout` |
| `tryAutoLogin()` | Loads persisted refresh token → calls `refreshToken()` |
| `validateToken()` | GET `/api/auth/validate` with Bearer header |
| `getValidAccessToken()` | Returns current token or triggers refresh |
| `scheduleTokenRefresh(expiresInMs)` | Schedules refresh at `expiresIn - 60s` (min 30s) |
| `performScheduledRefreshWithRetry(attempt)` | Retries refresh up to 3 times with exponential backoff |

### Token Auto-Refresh with Retry

A `ScheduledExecutorService` (daemon thread) refreshes the access token **1 minute before expiry** with built-in retry logic:

```
Token received (expiresIn = 900s)
    │
    ▼
Schedule refresh at 900 - 60 = 840s
    │
    ▼ (after 840 seconds)
Attempt 1: POST /api/auth/refresh
    │
    ├── Success → Store new tokens, schedule next refresh
    │
    └── Transient failure → Wait 5s → Attempt 2
                                         │
                                         ├── Success → Store new tokens, schedule next refresh
                                         │
                                         └── Transient failure → Wait 10s → Attempt 3
                                                                               │
                                                                               ├── Success → Store new tokens
                                                                               └── Failure → LoginDialog appears
```

**Retry classification:**
- **Retryable:** Connection errors, timeouts, server 500 errors
- **Non-retryable (immediate re-login):** "invalid token", "expired", "revoked", "unauthorized"

**Smart token clearing:** Tokens are only cleared from storage on definitive server rejection (HTTP 401/403). Transient errors (500, network timeout) preserve the refresh token so retries can succeed.

### Duplicate Login Dialog Prevention

A `loginDialogShowing` flag in `MainController` prevents multiple login dialogs from appearing if concurrent auth-required callbacks fire (e.g., refresh and reconnect both failing).

---

## Encrypted Token Storage

### Implementation: `TokenStorageService.java`

All credentials are encrypted at rest using authenticated encryption.

| Property | Value |
|----------|-------|
| **Cipher** | AES-256-GCM (Galois/Counter Mode) |
| **Key Derivation** | PBKDF2WithHmacSHA256 |
| **Iterations** | 65,536 |
| **Key Size** | 256 bits |
| **IV** | 12 bytes, randomly generated per encryption |
| **Auth Tag** | 128 bits (GCM provides integrity verification) |
| **Storage Format** | JSON → Encrypt → Base64 → File |
| **File Location** | `~/.screenai/credentials.enc` |

### What's Stored

| Field | Stored | Notes |
|-------|:------:|-------|
| Access Token | ✅ | Not used directly on restart (always refreshed) |
| Refresh Token | ✅ | Used for auto-login on restart |
| Token Expiry | ✅ | With 1-minute buffer subtracted |
| Username | ✅ | For display purposes |
| Remember Me Flag | ✅ | Controls persistence behavior |

### Security Considerations

- **GCM auth tag** prevents tampering — modified files are rejected
- **Random IV** ensures identical tokens produce different ciphertext
- **PBKDF2 with 65K iterations** makes brute-force key derivation expensive
- **Corrupted files are deleted** — app falls back to login dialog
- `clearTokens()` deletes the file from disk and clears memory

---

## Encryption Key Configuration

The encryption key is configured via the `TOKEN_ENCRYPTION_KEY` environment variable in `.env`:

```env
TOKEN_ENCRYPTION_KEY=your-32-character-encryption-key!
```

| Scenario | Behavior |
|----------|----------|
| Key not set | A **per-user fallback key** is generated from the username (`"ScreenAI-" + username + "-default-key-pad!"`). A security warning is printed to the console |
| Key set | Used for PBKDF2 derivation, no warning |
| Key changed | Previously stored credentials become unreadable, user must re-login |

---

## WebSocket Authentication

The client authenticates WebSocket connections by appending the JWT to the URL:

```
ws://localhost:8080/screenshare?token=eyJhbGciOiJIUzI1NiJ9...
```

### Implementation: `ServerConnectionService.java`

1. `setAuthToken(token)` stores the JWT
2. `connect()` appends `?token=<jwt>` to the WebSocket URL
3. Server's `WebSocketAuthHandler` validates the token on connect
4. If token is invalid/expired, connection is rejected

---

## Login Dialog

### Implementation: `LoginDialog.java`

A modal JavaFX dialog with separate **Login** and **Sign Up** pages.

**Design:**
- White card with drop shadow on semi-transparent overlay
- Blue gradient header stripe with ScreenAI branding
- Login page and Sign Up page as separate VBoxes in a StackPane
- Toggle links to switch between pages

**Client-Side Validation:**

| Field | Login Rules | Sign Up Rules |
|-------|------------|----------------|
| Username | Required | Required, 3+ chars |
| Password | Required | Required, ≥ 8 chars |
| Confirm Password | — | Must match password |

**Features:**
- Separate Login and Sign Up pages with smooth toggle
- Remember Me checkbox
- Progress indicator during async operations
- Error message display
- Shared fields move between pages

---

## Room Password Dialog

### Implementation: `RoomPasswordDialog.java`

A modal dialog for room creation and room joining with security.

**Create Mode:**

| Field | Validation |
|-------|-----------|
| Room ID | Required, 3-50 chars, alphanumeric + hyphens/underscores |
| Password | Optional, min 4 chars |
| Confirm Password | Must match password |
| Require Approval | Checkbox (auto-enabled when password is set) |

**Join Mode:**

| Field | Notes |
|-------|-------|
| Password | Enter room password |
| Access Code | OR enter 8-char access code |

Either password or access code can be used — they're alternatives.

---

## Security File Map

```
src/main/java/
├── config/
│   └── EnvConfig.java                 # TOKEN_ENCRYPTION_KEY, CREDENTIALS_STORAGE_DIR,
│                                      # SERVER_HOST, SERVER_PORT
├── service/
│   ├── AuthenticationService.java     # JWT login/register/refresh/auto-login
│   │   ├── scheduleTokenRefresh()     # Auto-refresh 1 min before expiry
│   │   ├── performScheduledRefreshWithRetry()  # 3 retries with exponential backoff
│   │   └── isRetryableFailure()       # Smart retry classification
│   ├── TokenStorageService.java       # AES-256-GCM encrypt/decrypt/persist
│   │   ├── encrypt()/decrypt()        # PBKDF2 + AES-256-GCM
│   │   ├── storeTokens()             # Cache + persist if remember-me
│   │   ├── loadPersistedTokens()     # Decrypt on startup
│   │   └── clearTokens()             # Memory + disk cleanup
│   └── ServerConnectionService.java   # WebSocket ?token= auth
└── controller/
    ├── MainController.java            # StackPane root (homeScreen + viewerScreen),
    │                                   # user profile toggle, auto-connect, loginDialogShowing guard
    ├── LoginDialog.java               # Login/Sign Up pages with white card design
    └── RoomPasswordDialog.java        # Room security UI (create/join)
```
