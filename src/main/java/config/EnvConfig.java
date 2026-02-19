package config;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * Environment configuration loader for the ScreenAI Client.
 * Loads configuration from .env file and provides type-safe access to settings.
 */
public class EnvConfig {

    private static final Logger log = LoggerFactory.getLogger(EnvConfig.class);
    
    private static EnvConfig instance;
    private final Dotenv dotenv;

    // Server connection
    private final String serverHost;
    private final int serverPort;
    private final String serverUrl;
    private final String httpUrl;
    
    // Reconnection settings
    private final int reconnectAttempts;
    private final int reconnectDelayMs;
    private final int connectionTimeoutMs;
    
    // Video settings
    private final int videoFrameRate;
    private final int videoBitrateKbps;
    private final String videoQuality;
    
    // Buffer sizes
    private final int maxTextMessageBufferSize;
    private final int maxBinaryMessageBufferSize;
    private final int decoderMaxBufferSize;
    private final int decoderMaxBufferTimeMs;
    
    // Performance monitoring
    private final boolean monitorCpu;
    private final boolean monitorMemory;
    private final boolean monitorNetwork;
    
    // HTTP client settings
    private final int httpConnectTimeoutSeconds;
    private final int httpRequestTimeoutSeconds;
    
    // Security
    private final String tokenEncryptionKey;
    private final String credentialsStorageDir;
    private final boolean usingDefaultEncryptionKey;
    private final boolean allowInsecureTransport;
    private final String webSocketScheme;
    private final String httpScheme;

    private EnvConfig() {
        log.info("Loading environment configuration from .env file...");
        
        // Load .env file, ignore if missing (use defaults)
        this.dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .load();
        
        // Server connection
        this.serverHost = getEnv("SERVER_HOST", "localhost");
        this.serverPort = getEnvInt("SERVER_PORT", 8080);
        this.serverUrl = getEnv("SCREENAI_SERVER_URL", "ws://localhost:8080/screenshare");
        this.httpUrl = getEnv("SCREENAI_HTTP_URL", "http://localhost:8080");
        this.webSocketScheme = extractScheme(this.serverUrl, "ws");
        this.httpScheme = extractScheme(this.httpUrl, "http");
        this.allowInsecureTransport = getEnvBoolean("ALLOW_INSECURE_TRANSPORT", false);
        
        // Reconnection settings
        this.reconnectAttempts = getEnvInt("RECONNECT_ATTEMPTS", 3);
        this.reconnectDelayMs = getEnvInt("RECONNECT_DELAY_MS", 5000);
        this.connectionTimeoutMs = getEnvInt("CONNECTION_TIMEOUT_MS", 10000);
        
        // Video settings
        this.videoFrameRate = getEnvInt("VIDEO_FRAME_RATE", 30);
        this.videoBitrateKbps = getEnvInt("VIDEO_BITRATE_KBPS", 2500);
        this.videoQuality = getEnv("VIDEO_QUALITY", "high");
        
        // Buffer sizes
        this.maxTextMessageBufferSize = getEnvInt("MAX_TEXT_MESSAGE_BUFFER_SIZE", 131072);
        this.maxBinaryMessageBufferSize = getEnvInt("MAX_BINARY_MESSAGE_BUFFER_SIZE", 10485760);
        this.decoderMaxBufferSize = getEnvInt("DECODER_MAX_BUFFER_SIZE", 500000);
        this.decoderMaxBufferTimeMs = getEnvInt("DECODER_MAX_BUFFER_TIME_MS", 200);
        
        // Performance monitoring
        this.monitorCpu = getEnvBoolean("MONITOR_CPU", true);
        this.monitorMemory = getEnvBoolean("MONITOR_MEMORY", true);
        this.monitorNetwork = getEnvBoolean("MONITOR_NETWORK", true);
        
        // HTTP client settings
        this.httpConnectTimeoutSeconds = getEnvInt("HTTP_CONNECT_TIMEOUT_SECONDS", 10);
        this.httpRequestTimeoutSeconds = getEnvInt("HTTP_REQUEST_TIMEOUT_SECONDS", 30);
        
        // Security
        String encKey = getEnv("TOKEN_ENCRYPTION_KEY", "").trim();
        if (encKey.isEmpty()) {
            this.usingDefaultEncryptionKey = true;
            this.tokenEncryptionKey = null;
        } else if (encKey.length() < 32) {
            this.usingDefaultEncryptionKey = true;
            this.tokenEncryptionKey = null;
            log.warn("⚠️  TOKEN_ENCRYPTION_KEY is too short ({} chars). Minimum recommended length is 32.", encKey.length());
        } else {
            this.usingDefaultEncryptionKey = false;
            this.tokenEncryptionKey = encKey;
        }
        this.credentialsStorageDir = getEnv("CREDENTIALS_STORAGE_DIR", "~/.screenai")
            .replace("~", System.getProperty("user.home"));
        
        log.info("Environment configuration loaded successfully");
        log.info("Server Host: {}:{}", serverHost, serverPort);
        log.info("Server URL: {}", serverUrl);
        log.info("HTTP URL: {}", httpUrl);
        log.info("Transport policy: {} insecure transport for non-local hosts", allowInsecureTransport ? "allowing" : "blocking");
        
        // Security warnings
        if (usingDefaultEncryptionKey) {
            log.warn("⚠️  TOKEN_ENCRYPTION_KEY is not configured securely.");
            log.warn("⚠️  Remember-me credential persistence is disabled until a secure key is set.");
        }
        if (requiresSecureTransport(serverHost) &&
                ("ws".equalsIgnoreCase(webSocketScheme) || "http".equalsIgnoreCase(httpScheme))) {
            log.warn("⚠️  Non-local host detected with insecure default schemes (ws/http).");
            log.warn("⚠️  Client will enforce wss/https unless ALLOW_INSECURE_TRANSPORT=true.");
        }
    }

    /**
     * Get the singleton instance of EnvConfig
     */
    public static synchronized EnvConfig getInstance() {
        if (instance == null) {
            instance = new EnvConfig();
        }
        return instance;
    }

    /**
     * Reload configuration from .env file
     */
    public static synchronized void reload() {
        instance = null;
        getInstance();
    }

    // Helper methods for type-safe environment variable access
    private String getEnv(String key, String defaultValue) {
        String value = dotenv.get(key);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    private int getEnvInt(String key, int defaultValue) {
        try {
            String value = dotenv.get(key);
            return (value != null && !value.isEmpty()) ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            log.warn("Invalid integer value for {}, using default: {}", key, defaultValue);
            return defaultValue;
        }
    }

    private boolean getEnvBoolean(String key, boolean defaultValue) {
        String value = dotenv.get(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    private String extractScheme(String url, String defaultScheme) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            return (scheme == null || scheme.isBlank()) ? defaultScheme : scheme.toLowerCase();
        } catch (Exception e) {
            log.warn("Invalid URL format for '{}', using default scheme '{}'", url, defaultScheme);
            return defaultScheme;
        }
    }

    private boolean isLoopbackHost(String host) {
        if (host == null) {
            return false;
        }
        String normalized = host.trim().toLowerCase();
        return "localhost".equals(normalized) ||
                "127.0.0.1".equals(normalized) ||
                "::1".equals(normalized) ||
                "[::1]".equals(normalized) ||
                "0:0:0:0:0:0:0:1".equals(normalized);
    }

    private boolean requiresSecureTransport(String host) {
        return !allowInsecureTransport && !isLoopbackHost(host);
    }

    // ==================== Getters ====================

    // Server connection
    public String getServerHost() {
        return serverHost;
    }

    public int getServerPort() {
        return serverPort;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public String getHttpUrl() {
        return httpUrl;
    }
    
    public String getAuthBaseUrl() {
        return httpUrl + "/api/auth";
    }

    public String buildWebSocketUrl(String host, int port, String path) {
        String normalizedPath = (path == null || path.isBlank()) ? "/" : path;
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }
        String scheme = requiresSecureTransport(host) ? "wss" : webSocketScheme;
        return String.format("%s://%s:%d%s", scheme, host, port, normalizedPath);
    }

    public String buildHttpBaseUrl(String host, int port) {
        String scheme = requiresSecureTransport(host) ? "https" : httpScheme;
        return String.format("%s://%s:%d", scheme, host, port);
    }

    // Reconnection settings
    public int getReconnectAttempts() {
        return reconnectAttempts;
    }

    public int getReconnectDelayMs() {
        return reconnectDelayMs;
    }

    public int getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    // Video settings
    public int getVideoFrameRate() {
        return videoFrameRate;
    }

    public int getVideoBitrateKbps() {
        return videoBitrateKbps;
    }

    public String getVideoQuality() {
        return videoQuality;
    }

    // Buffer sizes
    public int getMaxTextMessageBufferSize() {
        return maxTextMessageBufferSize;
    }

    public int getMaxBinaryMessageBufferSize() {
        return maxBinaryMessageBufferSize;
    }

    public int getDecoderMaxBufferSize() {
        return decoderMaxBufferSize;
    }

    public int getDecoderMaxBufferTimeMs() {
        return decoderMaxBufferTimeMs;
    }

    // Performance monitoring
    public boolean isMonitorCpu() {
        return monitorCpu;
    }

    public boolean isMonitorMemory() {
        return monitorMemory;
    }

    public boolean isMonitorNetwork() {
        return monitorNetwork;
    }

    // HTTP client settings
    public int getHttpConnectTimeoutSeconds() {
        return httpConnectTimeoutSeconds;
    }

    public int getHttpRequestTimeoutSeconds() {
        return httpRequestTimeoutSeconds;
    }

    // Security
    public String getTokenEncryptionKey() {
        return tokenEncryptionKey;
    }

    public String getCredentialsStorageDir() {
        return credentialsStorageDir;
    }
    
    /**
     * Check if using the default (insecure) encryption key.
     * This should return false in production environments.
     */
    public boolean isUsingDefaultEncryptionKey() {
        return usingDefaultEncryptionKey;
    }

    public boolean isAllowInsecureTransport() {
        return allowInsecureTransport;
    }
}
