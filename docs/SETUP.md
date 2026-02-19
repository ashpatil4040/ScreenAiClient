# ScreenAI Client — Setup Guide

## Prerequisites

- **Java 21+** (JavaFX requires Java 21)

```bash
java -version
# Should show: openjdk version "21" or higher
```

- **Maven 3.8+**
- **ScreenAI Server** running on `localhost:8080`

---

## Quick Start

### 1. Configure Environment

Create a `.env` file in the project root (copy from `.env.example`):

```env
# Server auto-connect settings
SERVER_HOST=localhost
SERVER_PORT=8080

# WebSocket and HTTP URLs
SCREENAI_SERVER_URL=ws://localhost:8080/screenshare
SCREENAI_HTTP_URL=http://localhost:8080

# Security (IMPORTANT: Change for production!)
TOKEN_ENCRYPTION_KEY=your-32-character-encryption-key!
CREDENTIALS_STORAGE_DIR=~/.screenai
```

> **Warning:** If `TOKEN_ENCRYPTION_KEY` is not set, a **per-user fallback key** is generated from the username. This is convenient for development but not suitable for production.

### 2. Start the Server First

```bash
cd ScreenAi-security-server
mvn spring-boot:run
```

### 3. Run the Client

```bash
cd ScreenAiClient-security-client
mvn org.openjfx:javafx-maven-plugin:0.0.8:run
```

> **Note:** The fully-qualified plugin goal is required. `mvn javafx:run` will not work without a Maven wrapper.

The client auto-connects to the server using `SERVER_HOST` and `SERVER_PORT` from `.env` — no manual connect step.

---

## Configuration Reference

All settings are loaded from `.env` via `EnvConfig.java` (singleton).

### Server Connection

| Env Variable | Default | Description |
|-------------|---------|-------------|
| `SERVER_HOST` | `localhost` | Server host for auto-connect |
| `SERVER_PORT` | `8080` | Server port for auto-connect |
| `SCREENAI_SERVER_URL` | `ws://localhost:8080/screenshare` | WebSocket server URL |
| `SCREENAI_HTTP_URL` | `http://localhost:8080` | HTTP base URL for auth API |

### Reconnection

| Env Variable | Default | Description |
|-------------|---------|-------------|
| `RECONNECT_ATTEMPTS` | `3` | Max reconnection attempts |
| `RECONNECT_DELAY_MS` | `5000` | Delay between reconnection attempts (ms) |
| `CONNECTION_TIMEOUT_MS` | `10000` | WebSocket connection timeout (ms) |

### Video

| Env Variable | Default | Description |
|-------------|---------|-------------|
| `VIDEO_FRAME_RATE` | `30` | Target capture FPS |
| `VIDEO_BITRATE_KBPS` | `2500` | Encoding bitrate (Kbps) |
| `VIDEO_QUALITY` | `high` | Quality preset |

### Buffers

| Env Variable | Default | Description |
|-------------|---------|-------------|
| `MAX_TEXT_MESSAGE_BUFFER_SIZE` | `131072` (128 KB) | Max WebSocket text message size |
| `MAX_BINARY_MESSAGE_BUFFER_SIZE` | `10485760` (10 MB) | Max WebSocket binary message size |
| `DECODER_MAX_BUFFER_SIZE` | `500000` | Max decoder accumulation buffer (bytes) |
| `DECODER_MAX_BUFFER_TIME_MS` | `200` | Max decoder accumulation time (ms) |

### Security

| Env Variable | Default | Description |
|-------------|---------|-------------|
| `TOKEN_ENCRYPTION_KEY` | Per-user fallback | AES-256-GCM encryption key (32 chars). If not set, a key is derived from the username |
| `CREDENTIALS_STORAGE_DIR` | `~/.screenai` | Directory for encrypted credentials file |

### HTTP Timeouts

| Env Variable | Default | Description |
|-------------|---------|-------------|
| `HTTP_CONNECT_TIMEOUT_SECONDS` | `10` | HTTP client connection timeout |
| `HTTP_REQUEST_TIMEOUT_SECONDS` | `30` | HTTP request timeout |

### Monitoring

| Env Variable | Default | Description |
|-------------|---------|-------------|
| `MONITOR_CPU` | `true` | Enable CPU usage tracking |
| `MONITOR_MEMORY` | `true` | Enable memory usage tracking |
| `MONITOR_NETWORK` | `true` | Enable network metrics |

---

## Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Spring WebSocket/Context/Web | 6.0.13 | DI + WebSocket client |
| Jakarta WebSocket API | 2.1.1 | WebSocket standard |
| Tyrus Standalone Client | 2.1.3 | WebSocket implementation |
| JavaFX Controls/FXML/Graphics | 21.0.2 | Desktop UI |
| JavaCV | 1.5.9 | Screen capture + encoding |
| FFmpeg Platform | 6.0-1.5.9 | Native FFmpeg binaries (all platforms) |
| Jackson Databind | 2.16.0 | JSON serialization |
| SLF4J + Logback | 2.0.9 / 1.4.11 | Logging |
| dotenv-java | 3.0.0 | `.env` file loading |

---

## Platform-Specific Notes

### macOS

- **VideoToolbox** hardware encoder is used automatically (70% CPU reduction)
- Screen recording permission may be required — grant it in System Settings → Privacy & Security → Screen Recording
- AVFoundation is used for screen capture

### Windows

- **NVENC** hardware encoder used if NVIDIA GPU is available (80% CPU reduction)
- Falls back to `libopenh264` (CPU) if no NVIDIA GPU
- GDI (`gdigrab`) used for screen capture with DirectShow fallback

### Linux

- **NVENC** hardware encoder used if NVIDIA GPU + CUDA libraries are installed
- Falls back to `libopenh264` (CPU)
- **X11 only** — screen capture uses `x11grab`
- **Wayland:** Screen capture does not work natively. Use X11 session or XWayland.

---

## Building

### Compile Only

```bash
mvn compile
```

### Package as JAR

```bash
mvn clean package
```

### Run Tests

```bash
mvn test
```

---

## File Storage

| File | Location | Purpose |
|------|----------|---------|
| Encrypted credentials | `~/.screenai/credentials.enc` | AES-256-GCM encrypted tokens (if Remember Me enabled) |
| `.env` config | Project root | Environment configuration |
| Logs | Console only | Application logs |
