package service;

import com.fasterxml.jackson.databind.ObjectMapper;
import config.EnvConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Secure token storage for client-side authentication.
 * Stores tokens in user's app data directory with AES-256-GCM encryption.
 * 
 * Features:
 * - AES-256-GCM encryption for stored credentials
 * - Remember-me functionality with persistent refresh tokens
 * - Token expiration tracking for automatic refresh
 */
public class TokenStorageService {

    private static final Logger log = LoggerFactory.getLogger(TokenStorageService.class);
    private static final String TOKEN_FILE = "credentials.enc";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // AES-GCM encryption parameters
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;
    private static final int ITERATION_COUNT = 65536;
    private static final String ENCRYPTED_FILE_PREFIX = "v2:";
    // Legacy static salt for backward-compatible decryption of older credential files.
    private static final byte[] LEGACY_SALT = "ScreenAI-Salt-2026".getBytes(StandardCharsets.UTF_8);

    private final Path storagePath;
    private final String encryptionPassword;
    private final SecureRandom secureRandom;
    
    // Cached tokens
    private volatile String accessToken;
    private volatile String refreshToken;
    private volatile Instant accessTokenExpiresAt;
    private volatile String username;
    private volatile boolean rememberMe = false;

    public TokenStorageService() {
        EnvConfig config = EnvConfig.getInstance();
        
        // Use storage directory from config
        Path baseDir = Path.of(config.getCredentialsStorageDir());
        
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            log.error("Failed to create storage directory: {}", e.getMessage());
        }
        
        this.storagePath = baseDir.resolve(TOKEN_FILE);
        this.secureRandom = new SecureRandom();
        this.encryptionPassword = config.getTokenEncryptionKey();
        if (config.isUsingDefaultEncryptionKey()) {
            log.warn("Remember-me persistence is disabled until TOKEN_ENCRYPTION_KEY is securely configured.");
        }
        
        // Load stored tokens on startup
        loadPersistedTokens();
    }

    // ==================== Token Management ====================

    /**
     * Store authentication tokens after successful login.
     * @param rememberMe if true, persists refresh token to disk for auto-login
     */
    public void storeTokens(String accessToken, String refreshToken, long expiresInMs, String username, boolean rememberMe) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.accessTokenExpiresAt = Instant.now().plusMillis(expiresInMs - 60000); // Subtract 1 minute buffer
        this.username = username;
        this.rememberMe = rememberMe;
        
        if (rememberMe) {
            persistTokens();
        }
        log.info("Tokens stored for user: {} (remember-me: {})", username, rememberMe);
    }
    
    /**
     * Store authentication tokens after successful login (no remember-me).
     */
    public void storeTokens(String accessToken, String refreshToken, long expiresInMs, String username) {
        storeTokens(accessToken, refreshToken, expiresInMs, username, false);
    }

    /**
     * Update access token after refresh.
     */
    public void updateAccessToken(String newAccessToken, long expiresInMs) {
        this.accessToken = newAccessToken;
        this.accessTokenExpiresAt = Instant.now().plusMillis(expiresInMs - 60000);
        
        if (rememberMe) {
            persistTokens();
        }
        log.debug("Access token refreshed");
    }

    /**
     * Update both access and refresh tokens after server-side token rotation.
     * The server generates a new refresh token on each refresh call, invalidating the old one.
     */
    public void updateTokens(String newAccessToken, String newRefreshToken, long expiresInMs) {
        this.accessToken = newAccessToken;
        this.accessTokenExpiresAt = Instant.now().plusMillis(expiresInMs - 60000);
        if (newRefreshToken != null && !newRefreshToken.isBlank()) {
            this.refreshToken = newRefreshToken;
        }
        
        if (rememberMe) {
            persistTokens();
        }
        log.debug("Access token and refresh token updated (rotation)");
    }

    /**
     * Clear all stored tokens (logout).
     */
    public void clearTokens() {
        this.accessToken = null;
        this.refreshToken = null;
        this.accessTokenExpiresAt = null;
        this.username = null;
        this.rememberMe = false;
        
        try {
            Files.deleteIfExists(storagePath);
            log.info("Tokens cleared and credentials file deleted");
        } catch (IOException e) {
            log.error("Failed to delete credentials file: {}", e.getMessage());
        }
    }
    
    /**
     * Check if remember-me is enabled
     */
    public boolean isRememberMeEnabled() {
        return rememberMe;
    }

    // ==================== Token Retrieval ====================

    /**
     * Get current access token if still valid.
     */
    public Optional<String> getAccessToken() {
        if (accessToken == null || isAccessTokenExpired()) {
            return Optional.empty();
        }
        return Optional.of(accessToken);
    }

    /**
     * Get refresh token for token renewal.
     */
    public Optional<String> getRefreshToken() {
        return Optional.ofNullable(refreshToken);
    }

    /**
     * Get the authenticated username.
     */
    public Optional<String> getUsername() {
        return Optional.ofNullable(username);
    }

    /**
     * Check if access token is expired.
     */
    public boolean isAccessTokenExpired() {
        return accessTokenExpiresAt == null || Instant.now().isAfter(accessTokenExpiresAt);
    }

    /**
     * Check if user is logged in (has valid or refreshable token).
     */
    public boolean isLoggedIn() {
        return (accessToken != null && !isAccessTokenExpired()) || refreshToken != null;
    }

    /**
     * Check if we have tokens that might need refresh.
     */
    public boolean needsRefresh() {
        return accessToken != null && isAccessTokenExpired() && refreshToken != null;
    }

    // ==================== Persistence with AES Encryption ====================

    /**
     * Derive AES-256 key from encryption password using PBKDF2.
     */
    private SecretKey deriveKey(String password, byte[] salt) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH);
            SecretKey tmp = factory.generateSecret(spec);
            return new SecretKeySpec(tmp.getEncoded(), "AES");
        } catch (Exception e) {
            log.error("Failed to derive encryption key: {}", e.getMessage());
            throw new RuntimeException("Failed to initialize encryption", e);
        }
    }

    private boolean hasEncryptionKey() {
        return encryptionPassword != null && !encryptionPassword.isBlank();
    }

    /**
     * Encrypt data using AES-256-GCM.
     */
    private String encrypt(String plaintext, byte[] salt) throws Exception {
        SecretKey encryptionKey = deriveKey(encryptionPassword, salt);
        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);
        
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, parameterSpec);
        
        byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        
        // Prepend IV to ciphertext
        byte[] combined = new byte[iv.length + cipherText.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
        
        return Base64.getEncoder().encodeToString(combined);
    }

    /**
     * Decrypt data using AES-256-GCM.
     */
    private String decrypt(String encrypted, byte[] salt) throws Exception {
        SecretKey encryptionKey = deriveKey(encryptionPassword, salt);
        byte[] combined = Base64.getDecoder().decode(encrypted);
        if (combined.length <= GCM_IV_LENGTH) {
            throw new IllegalArgumentException("Encrypted payload is too short");
        }
        
        // Extract IV and ciphertext
        byte[] iv = new byte[GCM_IV_LENGTH];
        byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, iv.length);
        System.arraycopy(combined, iv.length, cipherText, 0, cipherText.length);
        
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, parameterSpec);
        
        byte[] plainText = cipher.doFinal(cipherText);
        return new String(plainText, StandardCharsets.UTF_8);
    }

    /**
     * Backward-compatible decryption for legacy file format that used a static salt.
     */
    private String decryptLegacy(String encrypted) throws Exception {
        return decrypt(encrypted, LEGACY_SALT);
    }

    /**
     * Persist tokens to encrypted file (for remember-me).
     */
    private void persistTokens() {
        if (!hasEncryptionKey()) {
            log.warn("Skipping credential persistence: TOKEN_ENCRYPTION_KEY is missing or invalid.");
            return;
        }

        try {
            Map<String, Object> data = new HashMap<>();
            data.put("accessToken", accessToken != null ? accessToken : "");
            data.put("refreshToken", refreshToken != null ? refreshToken : "");
            data.put("expiresAt", accessTokenExpiresAt != null ? accessTokenExpiresAt.toEpochMilli() : 0);
            data.put("username", username != null ? username : "");
            data.put("rememberMe", rememberMe);
            
            String json = objectMapper.writeValueAsString(data);
            byte[] salt = new byte[SALT_LENGTH];
            secureRandom.nextBytes(salt);

            String encrypted = encrypt(json, salt);
            String payload = ENCRYPTED_FILE_PREFIX +
                    Base64.getEncoder().encodeToString(salt) +
                    ":" + encrypted;
            
            Files.writeString(storagePath, payload,
                    StandardOpenOption.CREATE, 
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            
            log.debug("Credentials persisted to encrypted file");
            
        } catch (Exception e) {
            log.error("Failed to persist credentials: {}", e.getMessage());
        }
    }

    /**
     * Load persisted tokens from encrypted file.
     */
    @SuppressWarnings("unchecked")
    private void loadPersistedTokens() {
        if (!Files.exists(storagePath)) {
            log.debug("No persisted credentials found");
            return;
        }
        
        try {
            String payload = Files.readString(storagePath).trim();
            if (!hasEncryptionKey()) {
                log.warn("Encrypted credentials found but TOKEN_ENCRYPTION_KEY is unavailable; auto-login disabled.");
                return;
            }

            String json;
            if (payload.startsWith(ENCRYPTED_FILE_PREFIX)) {
                String[] parts = payload.split(":", 3);
                if (parts.length != 3) {
                    throw new IllegalArgumentException("Invalid encrypted credential format");
                }
                byte[] salt = Base64.getDecoder().decode(parts[1]);
                json = decrypt(parts[2], salt);
            } else {
                // Legacy format fallback (single base64 blob with static salt).
                json = decryptLegacy(payload);
            }
            
            Map<String, Object> data = objectMapper.readValue(json, Map.class);
            
            String loadedRefreshToken = (String) data.get("refreshToken");
            String loadedUsername = (String) data.get("username");
            Boolean loadedRememberMe = (Boolean) data.get("rememberMe");
            
            // Only load refresh token - access token will need to be refreshed
            if (loadedRefreshToken != null && !loadedRefreshToken.isEmpty()) {
                this.refreshToken = loadedRefreshToken;
                this.username = loadedUsername;
                this.rememberMe = loadedRememberMe != null && loadedRememberMe;
                
                log.info("Loaded persisted credentials for user: {}", username);
            }
            
        } catch (Exception e) {
            log.error("Failed to load persisted credentials (may be corrupted or key changed): {}", e.getMessage());
            // Delete corrupted file
            try {
                Files.deleteIfExists(storagePath);
            } catch (IOException ignored) {}
        }
    }

    /**
     * Check if there are persisted credentials available for auto-login.
     */
    public boolean hasPersistedCredentials() {
        return Files.exists(storagePath) && refreshToken != null;
    }

    // ==================== Utility ====================

    /**
     * Parse expiration time from JWT token (without validation).
     * This is for display purposes only.
     */
    public static long parseExpirationFromJwt(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return 0;
            }
            
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Map<?, ?> claims = new ObjectMapper().readValue(payload, Map.class);
            
            Object exp = claims.get("exp");
            if (exp instanceof Number) {
                return ((Number) exp).longValue() * 1000; // Convert seconds to milliseconds
            }
        } catch (Exception e) {
            log.debug("Failed to parse JWT: {}", e.getMessage());
        }
        return 0;
    }
}
