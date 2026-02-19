# ScreenAI Client — User Guide

## Getting Started

### 1. Start the Server

The ScreenAI Server must be running first:

```bash
cd ScreenAi-security-server
mvn spring-boot:run
```

### 2. Configure the Client

Ensure your `.env` file has the correct server address:

```env
SERVER_HOST=localhost
SERVER_PORT=8080
```

> The client **auto-connects** to the server on launch — no manual connection step needed.

### 3. Launch the Client

```bash
cd ScreenAiClient-security-client
mvn org.openjfx:javafx-maven-plugin:0.0.8:run
```

### 4. Login or Register

On first launch, a **Login Dialog** appears with a modern white card design:

**Login Page:**
1. Enter your **username** and **password**
2. Check **"Remember Me"** to stay logged in across restarts
3. Click **Sign In**

**Sign Up Page:**
1. Click **"Don't have an account? Sign up"** at the bottom of the login page
2. Enter a username (3+ chars) and password (8+ chars with uppercase, lowercase, digit, special char)
3. Confirm your password
4. Click **Create Account**
5. Click **"Already have an account? Sign in"** to switch back to login

---

## Main Interface

The client uses a **split-panel home screen**:

- **Left Panel** — Blue gradient brand panel. Before login: sign-in button, Google sign-up (coming soon), account creation link, and security status indicator. **After login:** a user profile section showing your avatar initial, username, role, and session status, with a **Sign Out** button.
- **Right Panel** — White card containing connection status, host controls, and viewer controls

### Connection Status

The app **auto-connects to the server** using `SERVER_HOST` and `SERVER_PORT` from the `.env` file. A status indicator at the top of the right panel shows:

| Status | Meaning |
|--------|---------|
| ⏳ Connecting... | Auto-connect in progress |
| ✅ Connected | Successfully connected to server |
| ❌ Not Connected | Connection failed (will retry on login) |

---

## Sharing Your Screen (Host)

Both Host and Viewer sections are visible simultaneously — no mode switching needed.

1. (Optional) Enter a custom **Room ID** or use the auto-generated one
2. Click **Start**

**For a password-protected room:**
1. A **Room Password Dialog** appears
2. Enter a Room ID (3-50 chars)
3. Set a password (optional, min 4 chars)
4. Check "Require viewer approval" (auto-enabled with password)
5. Click **Create**

**After room creation:**
- Your Room ID is displayed in a green info box with a **📋 Copy** button
- If password-protected, an **Access Code** row appears — share this with viewers
- Viewer count and FPS metrics update in real-time
- Host status shows **🟢 Hosting**

### Stopping

Click **Stop** to end the streaming session. The room is closed and all viewers are disconnected.

---

## Watching a Stream (Viewer)

The viewer section appears below the host section (both visible at all times).

1. After connecting, the viewer **join controls** appear automatically
2. Enter the host's **Room ID** in the Session Code field
3. Click **Join session**

**For a password-protected room:**
- A password dialog appears
- Enter either the **room password** or the **access code** (received from host)
- Click **Join**

**If approval is required:**
- You'll see "Waiting for approval..."
- The host must approve your request

**While watching:**
- Live video displays in a dark player area
- Stats panel shows: Room, Frame Rate, Data Received, Latency, and Quality
- Viewer status shows **🟢 Viewing**

### Disconnecting

Click **Disconnect** below the viewer to stop watching.

---

## Room Security Features

### Creating a Protected Room

When you create a room with a password:
1. Server generates a **SHA-256 hashed** password (never stored in plain text)
2. An **8-character access code** is auto-generated (valid for 24 hours)
3. The access code displays in the room info section with a **📋 Copy** button
4. Share the access code with trusted viewers via any communication channel

### Joining a Protected Room

Viewers can join using either:
- The **room password** (if shared directly)
- The **access code** (preferred — expires after 24 hours)

### Viewer Approval

When enabled:
- Viewers see "Waiting for approval..." after joining
- The host receives a viewer request notification
- The host can **approve** or **deny** each viewer

### Viewer Management (Host)

During a streaming session, the host can:
- **Kick** a viewer — removes them (they can rejoin)
- **Ban** a viewer — removes and permanently blocks them from the room

---

## Authentication Details

### Token Lifecycle

1. **Login** → Receive access token (15 min) + refresh token (7 days)
2. **Auto-refresh** → Client automatically refreshes 1 minute before expiry
3. **Retry on failure** → If refresh fails (e.g., network blip), retries up to 3 times with exponential backoff (5s → 10s → 20s) before prompting re-login
4. **Smart token handling** → Tokens are only cleared on definitive server rejection (401/403), not on transient network errors
5. **Remember Me** → Tokens encrypted (AES-256-GCM) and saved to `~/.screenai/credentials.enc`
6. **Auto-login** → On next launch, saved refresh token is used to get a new access token

### Logout

To sign out, click the **Sign Out** button in the user profile section on the left panel. If "Remember Me" was not checked, tokens are cleared automatically when the app closes.

---

## Performance Metrics

Real-time metrics are displayed during streaming:

### Host Metrics
- **Host Status** — 🟢 Hosting / ⚫ Not Hosting / 🔴 Disconnected
- **FPS** — Frames captured and sent per second
- **Viewer Count** — Number of connected viewers

### Viewer Metrics
- **Room** — Currently connected room ID
- **Frame Rate** — Frames decoded and displayed per second
- **Data Received** — MB of incoming video data
- **Latency** — Round-trip time in ms
- **Quality** — Stream quality indicator

---

## Screen Capture Details

### Platform-Specific Capture

| Platform | Method | Notes |
|----------|--------|-------|
| **macOS** | AVFoundation | Tries devices `1:none` through `Capture screen 1:none` |
| **Windows** | GDI (`gdigrab`) | Falls back to DirectShow (`dshow`) |
| **Linux** | X11 (`x11grab`) | Tries multiple `$DISPLAY` variants; warns on Wayland |

### Encoding

- Video is captured at **half resolution** to reduce bandwidth
- Encoded as **H.264/MPEG-TS** at 30 FPS, 2 Mbps
- Hardware acceleration used when available (see Architecture docs)
- Keyframe every 0.5 seconds (GOP 15)

---

## Troubleshooting

### "Connection Failed" / ❌ Not Connected

1. Verify the server is running: `mvn spring-boot:run` in the server directory
2. Check `SERVER_HOST` and `SERVER_PORT` in your `.env` file match the server address
3. Ensure firewall allows the configured port (default: 8080)
4. Check the terminal for connection error details

### "Authentication Required" / Login Dialog Appears

1. You must log in before using the app — the login dialog appears automatically
2. Register a new account if you don't have one
3. If the login dialog keeps appearing after being logged in, check:
   - The server is still running (token refresh may have failed)
   - Your network connection is stable
   - The refresh retries (3 attempts with backoff) should handle transient issues

### Access Code Not Showing

Access codes only appear for **password-protected rooms**:
1. When creating a room, set a password
2. The server generates and returns the access code
3. It displays in the green room info section with a copy button

### Token Expired / "Please log in again"

- Access tokens expire after 15 minutes
- The app auto-refreshes with retry logic (3 attempts with exponential backoff)
- Only if all retries fail will you be prompted to log in again
- If using "Remember Me", the app will try auto-login on next launch

### Black Screen / No Video

1. Check that the host is actually streaming (host status shows 🟢 Hosting)
2. Try disconnecting and rejoining the room
3. Check the terminal for decoder errors
4. On Linux/Wayland: screen capture may not work natively — see terminal warnings

### Linux Wayland Warning

X11 screen capture (`x11grab`) doesn't work natively on Wayland. Options:
- Run your desktop session in X11 mode
- Use XWayland compatibility layer

### High CPU Usage

- If using the CPU encoder (`libopenh264`), this is expected
- Install NVIDIA drivers for NVENC support (Windows/Linux)
- On macOS, VideoToolbox should be automatic
