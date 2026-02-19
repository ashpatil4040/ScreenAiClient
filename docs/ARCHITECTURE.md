# ScreenAI Client — Architecture

## Tech Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| Language | Java 21 | Runtime |
| UI | JavaFX 21.0.2 | Desktop GUI (FXML + CSS) |
| DI | Spring Context 6.0.13 | Dependency injection (no embedded server) |
| Video Capture | JavaCV 1.5.9 / FFmpeg 6.0 | Screen capture + H.264 encoding |
| WebSocket | Spring WebSocket + Tyrus 2.1.3 | Server communication |
| JSON | Jackson 2.16.0 | Message serialization |
| Config | dotenv-java 3.0.0 | `.env` file loading |
| Logging | SLF4J 2.0.9 + Logback 1.4.11 | Structured logging |
| Build | Maven 3.x | Dependency management |

> **Note:** This is NOT a Spring Boot application. It uses raw `AnnotationConfigApplicationContext` for dependency injection without an embedded server.

---

## High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                        ScreenAI Client                                │
├──────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  ┌── UI Layer (JavaFX FXML) ────────────────────────────────────────┐ │
│  │  Root: StackPane with two layers:                                │ │
│  │    ├── homeScreen (ScrollPane) — split-panel home (DEFAULT)       │ │
│  │    │     ├── Left: Blue gradient brand panel                     │ │
│  │    │     │     ├── authButtonsSection (before login)             │ │
│  │    │     │     └── userProfileSection (after login): avatar,     │ │
│  │    │     │           username, role, session status, sign out  │ │
│  │    │     └── Right: White card (host + viewer controls)          │ │
│  │    └── viewerScreen (VBox) — fullscreen viewer (on room join)     │ │
│  │          ├── Top bar: LIVE indicator, room code, disconnect      │ │
│  │          ├── Video area: full-height ImageView                   │ │
│  │          └── Bottom stats: FPS, Data, Latency, Quality           │ │
│  │  DualModeMainController (dual-mode.fxml) — classic tabbed mode   │ │
│  │  LoginDialog — white card with separate Login/Sign Up pages      │ │
│  │  RoomPasswordDialog — room security popup                        │ │
│  └──────────────────────────────────────────────────────────────────┘ │
│           │                                 │                         │
│  ┌── Business Logic ──┐         ┌── Business Logic ──┐               │
│  │  DualModeController │         │  HostController    │               │
│  │  (bidirectional)    │         │  ViewerController  │               │
│  └─────────┬──────────┘         └────────┬──────────┘               │
│            │                              │                           │
│  ┌── Services ──────────────────────────────────────────────────────┐ │
│  │                                                                   │ │
│  │  AuthenticationService  ← JWT login/register/refresh with retry  │ │
│  │  TokenStorageService    ← AES-256-GCM encrypted persistence     │ │
│  │  ServerConnectionService ← WebSocket client (Tyrus)              │ │
│  │  ScreenCaptureService   ← FFmpeg screen grab + H.264 encode     │ │
│  │  H264DecoderService     ← FFmpeg frame decode + JavaFX display  │ │
│  │  FrameBufferService     ← Video frame buffering (90 frames)     │ │
│  │  ScreenSourceDetector   ← AWT display detection                  │ │
│  │  PerformanceMonitorService ← FPS, CPU, memory tracking          │ │
│  │                                                                   │ │
│  └──────────────────────────────────────────────────────────────────┘ │
│           │                                                           │
│  ┌── Encoders ──────────────────────────────────────────────────────┐ │
│  │  VideoEncoderFactory (selects best available)                     │ │
│  │    ├── H264VideoToolboxEncoder (macOS GPU)                        │ │
│  │    ├── NvencEncoder (NVIDIA GPU)                                  │ │
│  │    └── LibX264Encoder (CPU fallback)                              │ │
│  └──────────────────────────────────────────────────────────────────┘ │
│           │                                                           │
│  ┌── Config ────────────────────────────────────────────────────────┐ │
│  │  EnvConfig (singleton) — loads .env file, provides all settings  │ │
│  │    includes SERVER_HOST, SERVER_PORT for auto-connect            │ │
│  └──────────────────────────────────────────────────────────────────┘ │
│                                                                       │
└──────────────────────────────────────────────────────────────────────┘
                              │
                              │ WebSocket (ws://) + JWT Auth
                              │ Binary H.264/MPEG-TS + JSON Control
                              ▼
                    ScreenAI Server (port 8080)
```

---

## Application Modes

| Mode | Entry | Controller | Description |
|------|-------|-----------|-------------|
| **Default** (split-panel) | `App.java` | `MainController` + `DualModeController` | Split-panel UI with auto-connect, simultaneous host + viewer |
| **Classic Mode** | `App.java --classic` | `DualModeMainController` + `DualModeController` | Tabbed interface (Share Screen / Watch Stream) |

---

## Project Structure

```
src/main/java/
├── App.java                          # JavaFX Application entry point
│                                     # Loads main.fxml (default) or dual-mode.fxml (--classic)
├── ScreenAIClientApplication.java    # Spring context manager (@Configuration, @ComponentScan)
│                                     # NOT Spring Boot — raw AnnotationConfigApplicationContext
├── config/
│   └── EnvConfig.java                # Singleton. Loads .env via dotenv-java.
│                                     # All config: SERVER_HOST, SERVER_PORT, server URLs,
│                                     # video, buffers, security, timeouts
├── controller/
│   ├── MainController.java           # Default split-panel FXML controller
│   │                                 # StackPane root: homeScreen + viewerScreen
│   │                                 # Auto-connects from EnvConfig, loginDialogShowing guard
│   │                                 # Left panel toggles auth buttons / user profile
│   │                                 # Viewer join switches to fullscreen viewer screen
│   ├── DualModeMainController.java   # Classic tabbed FXML controller (--classic mode)
│   ├── DualModeController.java       # Bidirectional business logic (841 lines)
│   │                                 # Auth, connect, host, view — all in one
│   ├── HostController.java           # Classic host logic (screen capture + room management)
│   ├── ViewerController.java         # Classic viewer logic (decode + display)
│   ├── LoginDialog.java              # JavaFX Dialog — white card, separate Login/Sign Up pages
│   └── RoomPasswordDialog.java       # JavaFX Dialog — create/join room with password/access code
│
├── encoder/
│   ├── VideoEncoderStrategy.java     # Interface: configure(), getCodecName(), isHardwareAccelerated()
│   ├── VideoEncoderFactory.java      # Selects best encoder by OS (tests availability)
│   ├── H264VideoToolboxEncoder.java  # macOS GPU (h264_videotoolbox), 70% CPU reduction
│   ├── NvencEncoder.java             # NVIDIA GPU (h264_nvenc), 80% CPU reduction, CUDA check
│   └── LibX264Encoder.java           # CPU fallback (libopenh264), bundled with JavaCV
│
├── model/
│   ├── PerformanceMetrics.java       # Builder pattern: fps, latency, dropped, CPU, memory
│   └── ScreenSource.java             # Builder pattern: id, type (SCREEN/WINDOW), dimensions
│
└── service/
    ├── AuthenticationService.java    # HTTP client for /api/auth/*. Auto-refresh with retry (3x).
    │                                 # tryAutoLogin(), remember-me, token rotation
    │                                 # Smart token clearing: only on 401/403, not transient errors
    ├── TokenStorageService.java      # AES-256-GCM encryption. PBKDF2 key derivation.
    │                                 # Persists to ~/.screenai/credentials.enc
    ├── ServerConnectionService.java  # WebSocket client (Tyrus). JWT via ?token= query param.
    │                                 # 128KB text / 10MB binary buffers. Thread-safe.
    ├── ScreenCaptureService.java     # Two-thread architecture: capture thread + sender thread.
    │                                 # Platform-specific: AVFoundation/GDI/X11.
    │                                 # Half-resolution encoding, 30 FPS, 2 Mbps.
    ├── H264DecoderService.java       # Accumulated chunk decoder. Background thread.
    │                                 # FFmpegFrameGrabber → Java2DFrameConverter → WritableImage.
    ├── FrameBufferService.java       # LinkedBlockingQueue (90 frames). Init segment detection.
    ├── ScreenSourceDetector.java     # AWT GraphicsEnvironment screen enumeration
    └── PerformanceMonitorService.java # FPS, CPU (OperatingSystemMXBean), memory tracking

src/main/resources/
├── application.yml                   # Spring config (fallback to EnvConfig .env)
└── ui/
    ├── main.fxml                     # StackPane root: homeScreen (split-panel) + viewerScreen (fullscreen)
    ├── dual-mode.fxml                # Tabbed UI: Share Screen / Watch Stream (--classic)
    └── styles.css                    # Modern light theme with blue gradients
```

---

## Data Flow: Host Streaming

```
Screen → ScreenCaptureService → VideoEncoder → MPEG-TS bytes → Send Queue → WebSocket → Server
                                                                    │
          captureThread                                        senderThread
          (grab + encode)                                     (poll + send)
                                  ArrayBlockingQueue<byte[]>
                                     (60 frame capacity)
```

1. **Capture Thread** grabs screen via platform API (AVFoundation/GDI/X11)
2. **FFmpegFrameRecorder** encodes to H.264/MPEG-TS at half resolution
3. Encoded bytes offered to `ArrayBlockingQueue` (non-blocking, drops if full)
4. **Sender Thread** polls queue and sends via `ServerConnectionService.sendBinary()`
5. Server relays to all room viewers

## Data Flow: Viewer Decoding

```
WebSocket → binary callback → H264DecoderService → FFmpegFrameGrabber → JavaFX Image → ImageView
                                      │
                              decoderThread (MAX_PRIORITY)
                              accumulates chunks until threshold:
                                - 80KB (~1 GOP)
                                - or 500KB max
                                - or 200ms time-based
```

1. Binary data arrives via WebSocket callback
2. Chunks accumulated in `ByteArrayOutputStream`
3. When threshold reached, `FFmpegFrameGrabber` decodes the batch
4. Frames converted via `Java2DFrameConverter` → `WritableImage`
5. `Platform.runLater()` updates JavaFX `ImageView`

---

## Auto-Connect Flow

On startup, `MainController.initialize()` performs:

```
1. Create DualModeController → wire 11 callbacks
2. Generate default room ID
3. Set UI to disconnected state
4. tryAutoLogin() → load saved credentials
5. autoConnect() → read SERVER_HOST/SERVER_PORT from EnvConfig
   → controller.connect(host, port)
6. Bind viewerFullscreenVideo to container size
```

After login, the left panel switches from auth buttons to a **user profile** showing avatar initial, username, role, and session status with a Sign Out button.

No manual connect button or server input fields exist in the default UI. Server address is configured entirely through `.env`.

When a viewer joins a room, `onViewingStateChanged(true)` hides the `homeScreen` and shows the `viewerScreen` (fullscreen viewer). Disconnecting reverses this.

---

## Hardware-Accelerated Encoding

`VideoEncoderFactory.getBestEncoder()` tests encoders in order by OS:

| OS | Priority 1 | Fallback |
|----|-----------|----------|
| macOS | VideoToolbox (GPU, -70% CPU) | libopenh264 (CPU) |
| Windows | NVENC (NVIDIA GPU, -80% CPU) | libopenh264 (CPU) |
| Linux | NVENC (NVIDIA GPU, -80% CPU) | libopenh264 (CPU) |

### Encoding Parameters

| Parameter | Value |
|-----------|-------|
| Format | MPEG-TS |
| Pixel format | YUV420P |
| Frame rate | 30 FPS |
| Bitrate | 2 Mbps |
| GOP | 15 (keyframe every 0.5s) |
| Resolution | Half of screen |
| Preset | Ultrafast / zerolatency |

---

## Thread Model

| Thread | Purpose | Priority |
|--------|---------|----------|
| JavaFX Application Thread | UI rendering, FXML event handlers | Normal |
| Capture Thread | Screen grab + encode loop | Normal |
| Sender Thread | Send encoded frames via WebSocket | Normal |
| Decoder Thread | Decode incoming H.264 chunks | MAX |
| Token Refresh Scheduler | Auto-refresh JWT with retry (daemon) | Daemon |
| WebSocket IO | Tyrus/Spring WebSocket internal threads | Normal |
| Metrics Scheduler | 1-second FPS/performance updates | Daemon |

---

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| **Auto-connect from .env** | Removes UI clutter; server address rarely changes per deployment |
| **Split-panel home + fullscreen viewer** | Home screen shows both host + viewer controls; joining a room replaces home with a dedicated dark-themed viewer screen for immersive viewing |
| **User profile in left panel** | After login, auth buttons are replaced with avatar, username, role, and session status — provides at-a-glance identity confirmation |
| **Token refresh retry (3x)** | Prevents unnecessary re-login on transient network issues |
| **Smart token clearing** | Only clears on 401/403, not on 500/timeout — preserves refresh token for retry |
| **Two-thread capture** | Decouples encoding from network I/O; sender can drop frames without blocking capture |
| **Accumulated chunk decoding** | MPEG-TS arrives in fragments; accumulating to ~1 GOP gives decoder complete frames |
| **Half resolution** | Reduces bandwidth by 75% while maintaining visual clarity |
| **Not Spring Boot** | Desktop app doesn't need embedded server; raw Spring Context for DI only |
| **Singleton EnvConfig** | Loaded once, accessed globally; `.env` pattern familiar to developers |
| **AES-256-GCM for credentials** | Authenticated encryption prevents both reading and tampering |
| **Duplicate dialog guard** | `loginDialogShowing` flag prevents multiple login popups from concurrent callbacks |
