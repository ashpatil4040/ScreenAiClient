# ScreenAI Client — Secure Cross-Platform Screen Sharing

> A **secure JavaFX desktop client** for real-time screen sharing with **TeamViewer-style guest access**, room password protection, and hardware-accelerated encoding. **No login required** — launch and start sharing instantly. Supports macOS, Windows, and Linux.

> **For detailed security documentation, see the server's [SECURITY.md](../ScreenAi-security-server/docs/SECURITY.md)**

## Documentation

| Document | Description |
|----------|-------------|
| [Architecture](docs/ARCHITECTURE.md) | Client architecture, tech stack, data flow, thread model, encoder strategy |
| [User Guide](docs/USER_GUIDE.md) | How to host, view, and manage streams with room security features |
| [Setup Guide](docs/SETUP.md) | Prerequisites, .env configuration, platform-specific notes, all env variables |
| [Security](docs/SECURITY.md) | Client-side security: AES-256-GCM storage, JWT auth flow, PBKDF2, dialogs |

![JavaFX 21](https://img.shields.io/badge/UI-JavaFX_21-blue) ![JavaCV 1.5.9](https://img.shields.io/badge/Video-JavaCV_1.5.9-orange) ![Spring Framework](https://img.shields.io/badge/Framework-Spring_6.x-green) ![WebSocket](https://img.shields.io/badge/Protocol-WebSocket-brightgreen) ![Java 21](https://img.shields.io/badge/Java-21-red) ![Cross Platform](https://img.shields.io/badge/Platform-macOS%20%7C%20Windows%20%7C%20Linux-purple)

---

## Overview

**ScreenAI Client** is a secure desktop application enabling real-time screen sharing with a **TeamViewer-style workflow**. It auto-connects to the server as a guest on launch — no registration or login required. Rooms are protected by auto-generated passwords shared out-of-band. The app uses a **split-panel home screen** with host and viewer controls, and a **dedicated fullscreen viewer screen** with live video and real-time stats.

### Host (Presenter)
- **No login required** — connect as guest and start hosting immediately
- Room password auto-generated and displayed in UI
- Access codes generated server-side for password-protected rooms
- Viewer management — approve, kick, or ban viewers
- H.264/MPEG-TS encoding using FFmpeg with hardware acceleration (VideoToolbox, NVENC, libx264)
- ~28-30 FPS with ultrafast/zerolatency preset

### Viewer (Watcher)
- Enter Room ID + password/access code to join
- Password dialog appears automatically when room requires authentication
- **Fullscreen viewer screen** replaces the home screen on join — dark themed with top bar, live video, and bottom stats bar
- H.264 decoding via FFmpegFrameGrabber with batch processing
- One-click disconnect returns to home screen

### Split-Panel Home Screen
- Host and viewer controls visible simultaneously (no tabs to switch)
- Left panel: Blue gradient brand panel with optional sign-in buttons or **user profile with avatar, username, role, session status, and Sign Out** (after login)
- Right panel: White card with host section (Room ID + Password + Start), viewer section (Room ID + Join), and room info
- Auto-connects from `.env` — no manual server input needed

---

## Security Features

### Guest Access (Default)
- **No login required** — app connects as a guest automatically on launch
- Server assigns a guest session ID (e.g., `guest_6d618b43`)
- Guests can create and join password-protected rooms
- Optional Sign In / Create Account for persistent identity

### Room Security
- **Password Protection** — Every room gets an auto-generated password displayed in the UI
- **Access Codes** — 8-char alphanumeric codes for password-protected rooms (24-hour expiry)
- **Password Dialog** — Automatically prompted when joining a protected room (ROOM_003)
- **Viewer Approval** — Optional manual approve/deny workflow for incoming viewers
- **Kick/Ban** — Remove or permanently block unwanted viewers

### Authentication (Optional)
- **JWT Token Auth** — Login/register via secure dialog for persistent identity
- **Access + Refresh Tokens** — 15 min access tokens with automatic refresh scheduled 1 min before expiry
- **Token Refresh Retry** — 3 retries with exponential backoff (5s → 10s → 20s) on transient failures
- **Encrypted Storage** — AES-256-GCM encryption with PBKDF2-HMAC-SHA256 key derivation, stored at `~/.screenai/credentials.enc`
- **Auto-Login** — Persisted refresh token used for automatic authentication on launch
- **Token Rotation** — New refresh token issued on every refresh; old one invalidated

### Login Dialog
- White card design with blue gradient header
- Separate **Login** and **Sign Up** pages (toggle between them)
- Client-side validation: username required, password min 8 chars, confirmation match on sign-up
- Server address auto-configured from `.env` — no manual URL input
- Duplicate dialog prevention — `loginDialogShowing` flag prevents concurrent popups

> **Full security details →** [SECURITY.md](docs/SECURITY.md) and server [SECURITY.md](../ScreenAi-security-server/docs/SECURITY.md)

---

## Quick Start

### Prerequisites
- **Java 21+** (`java -version`)
- **Maven 3.x+** (`mvn -version`)
- **ScreenAI Server** running on `localhost:8080`

### 1. Configure Environment

Create `.env` in the project root:

```env
# Server Connection (auto-connect on launch)
SERVER_HOST=localhost
SERVER_PORT=8080

# Server URLs
SCREENAI_SERVER_URL=ws://localhost:8080/screenshare
SCREENAI_HTTP_URL=http://localhost:8080

# Security
TOKEN_ENCRYPTION_KEY=your-32-character-encryption-key!
CREDENTIALS_STORAGE_DIR=~/.screenai
```

### 2. Run

```bash
cd ScreenAiClient-security-client
mvn org.openjfx:javafx-maven-plugin:0.0.8:run
```

### 3. First Launch

1. App auto-connects to `SERVER_HOST:SERVER_PORT` as a guest
2. Room ID and password are auto-generated — ready to host immediately
3. Optionally sign in for persistent identity (left panel)
4. Start hosting or viewing — both sections are visible
5. As a viewer, joining a room transitions to a fullscreen viewer screen

---

## Application Modes

| Mode | Launch | Description |
|------|--------|-------------|
| **Default** (split-panel) | `mvn org.openjfx:javafx-maven-plugin:0.0.8:run` | Split-panel UI with simultaneous host + viewer controls |
| **Classic** (tabbed) | Pass `--classic` flag | Tabbed interface with Share Screen / Watch Stream tabs |

---

## Using the Application

### As a Host

```
1. Launch app → auto-connects to server as guest
2. Room ID and Password are auto-generated
3. Click [Start Sharing]
4. Room is created with password protection
5. Access code appears in room info section
6. Share Room ID + Password (or Access Code) with viewers
```

### As a Viewer

```
1. Launch app → auto-connects to server as guest
2. Enter Room ID from host
3. Click [Join session]
4. Password dialog appears automatically
5. Enter password or access code from host
6. UI transitions to fullscreen viewer screen:
   - Dark top bar with LIVE indicator and room code
   - Full-size video player area
   - Bottom stats bar (Room, FPS, Data, Latency, Quality)
7. Click [✕ Disconnect] to return to home screen
```

---

## System Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                       ScreenAI Client                                 │
├──────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  ┌── UI Layer ──────────────────────────────────────────────────────┐ │
│  │  Root: StackPane with two layers:                                │ │
│  │    ├── homeScreen (ScrollPane) — split-panel (DEFAULT)           │ │
│  │    │     ├── Left: Blue gradient brand panel                     │ │
│  │    │     │     ├── Auth buttons (before login)                   │ │
│  │    │     │     └── User profile: avatar, name, role, sign out    │ │
│  │    │     └── Right: White card (host + viewer controls)          │ │
│  │    └── viewerScreen (VBox) — fullscreen viewer (on join)         │ │
│  │          ├── Top bar: LIVE indicator, room code, disconnect      │ │
│  │          ├── Video area: full-height with ImageView              │ │
│  │          └── Bottom stats: Room, FPS, Data, Latency, Quality    │ │
│  │  LoginDialog — white card, separate Login/Sign Up pages          │ │
│  │  RoomPasswordDialog — access code / password entry               │ │
│  └──────────────────────────────────────────────────────────────────┘ │
│                                                                       │
│  ┌── Services ──────────────────────────────────────────────────────┐ │
│  │  AuthenticationService  — JWT auth + refresh retry (3x backoff)  │ │
│  │  TokenStorageService    — AES-256-GCM encrypted persistence     │ │
│  │  ServerConnectionService — WebSocket client (Tyrus)              │ │
│  │  ScreenCaptureService   — FFmpeg capture + H.264 encode         │ │
│  │  H264DecoderService     — FFmpeg decode + JavaFX display        │ │
│  │  FrameBufferService     — Video frame queue (90 frames)         │ │
│  │  PerformanceMonitorService — FPS, CPU, memory metrics           │ │
│  └──────────────────────────────────────────────────────────────────┘ │
│                                                                       │
│  ┌── Encoders ──────────────────────────────────────────────────────┐ │
│  │  VideoToolbox (macOS GPU) → NVENC (NVIDIA GPU) → libx264 (CPU)  │ │
│  └──────────────────────────────────────────────────────────────────┘ │
│                                                                       │
│  ┌── Config ────────────────────────────────────────────────────────┐ │
│  │  EnvConfig (singleton) — .env loader with SERVER_HOST/PORT      │ │
│  └──────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────┘
                              │
                              │ WebSocket (ws://) + JWT Auth
                              │ Binary H.264/MPEG-TS + JSON Control
                              ▼
                    ScreenAI Server (port 8080)
```

---

## Project Structure

```
src/main/java/
├── App.java                          # JavaFX Application entry point
├── ScreenAIClientApplication.java    # Spring context (NOT Spring Boot)
├── config/
│   └── EnvConfig.java                # Singleton .env loader (SERVER_HOST, SERVER_PORT, etc.)
├── controller/
│   ├── MainController.java           # Default split-panel controller (auto-connect)
│   ├── DualModeMainController.java   # Classic tabbed controller (--classic mode)
│   ├── DualModeController.java       # Bidirectional business logic
│   ├── HostController.java           # Host streaming logic
│   ├── ViewerController.java         # Viewer streaming logic
│   ├── LoginDialog.java              # White card login/sign-up dialog
│   └── RoomPasswordDialog.java       # Room security dialog
├── encoder/
│   ├── VideoEncoderFactory.java      # Selects best available encoder
│   ├── VideoEncoderStrategy.java     # Encoder interface
│   ├── H264VideoToolboxEncoder.java  # macOS GPU encoder
│   ├── NvencEncoder.java             # NVIDIA GPU encoder
│   └── LibX264Encoder.java           # CPU fallback encoder
├── model/
│   ├── ScreenSource.java             # Screen capture source (builder)
│   └── PerformanceMetrics.java       # Streaming metrics (builder)
└── service/
    ├── AuthenticationService.java    # JWT auth + 3-retry refresh with backoff
    ├── TokenStorageService.java      # AES-256-GCM encrypted credential storage
    ├── ServerConnectionService.java  # WebSocket client (Tyrus)
    ├── ScreenCaptureService.java     # Two-thread capture + encode
    ├── H264DecoderService.java       # Accumulated chunk decoder
    ├── FrameBufferService.java       # Frame queue (90 capacity)
    ├── ScreenSourceDetector.java     # AWT display detection
    └── PerformanceMonitorService.java # FPS, CPU, memory tracking

src/main/resources/
├── application.yml                   # Spring config (fallback values)
└── ui/
    ├── main.fxml                     # Split-panel UI (DEFAULT)
    ├── dual-mode.fxml                # Tabbed UI (--classic)
    └── styles.css                    # Modern light theme
```

---

## Configuration

### .env Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_HOST` | `localhost` | Server hostname for auto-connect |
| `SERVER_PORT` | `8080` | Server port for auto-connect |
| `SCREENAI_SERVER_URL` | `ws://localhost:8080/screenshare` | WebSocket endpoint |
| `SCREENAI_HTTP_URL` | `http://localhost:8080` | HTTP API endpoint |
| `TOKEN_ENCRYPTION_KEY` | (required) | 32-char key for AES-256-GCM |
| `CREDENTIALS_STORAGE_DIR` | `~/.screenai` | Encrypted credential location |
| `DEBUG_FFMPEG` | `false` | FFmpeg debug logging |

---

## Hardware-Accelerated Encoding

| Platform | GPU Encoder | CPU Reduction | Fallback |
|----------|------------|---------------|----------|
| macOS | VideoToolbox | ~70% | libopenh264 |
| Windows | NVENC (NVIDIA) | ~80% | libopenh264 |
| Linux | NVENC (NVIDIA) | ~80% | libopenh264 |

---

## Troubleshooting

### "Connection Failed" Error
1. Ensure server is running: `cd ../ScreenAi-security-server && mvn spring-boot:run`
2. Check `SERVER_HOST` and `SERVER_PORT` in `.env`
3. Verify firewall allows port 8080

### Token Refresh Issues
- App retries refresh 3 times with exponential backoff (5s → 10s → 20s)
- If all retries fail, login dialog appears
- Transient errors (timeout, 500) are retried; definitive rejections (401, 403) are not

### No Video Output (Viewer)
- Ensure host is actively streaming
- Check Room ID matches exactly
- Verify access code if room is password-protected

### Login Dialog Appears Unexpectedly
- This occurs when token refresh exhausts all 3 retries
- Check server availability and network connectivity
- Only one dialog appears at a time (duplicate prevention)

---
