package controller;

import config.EnvConfig;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import service.AuthenticationService.AuthResult;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.UUID;

/**
 * Main UI Controller — wires the new split-panel FXML to DualModeController.
 * Server connection is auto-configured from .env (EnvConfig).
 * Supports simultaneous hosting + viewing.
 */
public class MainController {

    // ── Left Panel ──
    @FXML private VBox authButtonsSection;
    @FXML private Button signInButton;
    @FXML private Button googleSignUpButton;
    @FXML private Hyperlink createAccountLink;
    @FXML private VBox userProfileSection;
    @FXML private Label userAvatarLabel;
    @FXML private Label userDisplayName;
    @FXML private Label userRoleLabel;
    @FXML private Label sessionStatusLabel;
    @FXML private Button signOutButton;
    @FXML private Label securityStatusDot;
    @FXML private Label securityStatusLabel;

    // ── Connection status (auto-connect) ──
    @FXML private Label connectionStatusLabel;

    // ── Host Section ──
    @FXML private VBox hostSection;
    @FXML private Label hostConnectionStatusLabel;
    @FXML private TextField roomIdInput;
    @FXML private Button startButton;
    @FXML private Button stopButton;

    // ── Host Room Info (hidden until room created) ──
    @FXML private VBox roomInfoSection;
    @FXML private Label activeRoomLabel;
    @FXML private Button copyRoomIdButton;
    @FXML private Label viewerCountLabel;
    @FXML private HBox accessCodeRow;
    @FXML private Label accessCodeLabel;
    @FXML private Button copyAccessCodeButton;
    @FXML private Label hostFpsLabel;

    // ── Viewer Section ──
    @FXML private VBox viewerSection;
    @FXML private Label viewerStatusLabel;
    @FXML private VBox viewerConnectedPane;
    @FXML private VBox viewerStreamPane;
    @FXML private TextField joinRoomIdInput;
    @FXML private Button joinRoomButton;
    @FXML private ImageView videoImageView;
    @FXML private VBox videoPlaceholder;
    @FXML private Label videoDisplayLabel;
    @FXML private Label roomStatusLabel;
    @FXML private Label viewerFpsLabel;
    @FXML private Label viewerDataLabel;
    @FXML private Label latencyLabel;
    @FXML private Label qualityLabel;
    @FXML private Button disconnectViewerButton;

    // ── Viewer Fullscreen Screen ──
    @FXML private ScrollPane homeScreen;
    @FXML private VBox viewerScreen;
    @FXML private StackPane viewerVideoContainer;
    @FXML private ImageView viewerFullscreenVideo;
    @FXML private VBox viewerFullscreenPlaceholder;
    @FXML private Label viewerRoomCodeLabel;
    @FXML private Button viewerBackButton;
    @FXML private Label fsRoomLabel;
    @FXML private Label fsFpsLabel;
    @FXML private Label fsDataLabel;
    @FXML private Label fsLatencyLabel;
    @FXML private Label fsQualityLabel;

    // ── Settings ──
    @FXML private Button settingsButton;

    // ── Backend ──
    private DualModeController controller;
    private volatile boolean loginDialogShowing = false;

    // ═══════════════════════════════════════════════════════════
    //  INITIALIZATION
    // ═══════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        System.out.println("🚀 MainController initializing (new UI)...");

        // ── DualModeController backend ──
        controller = new DualModeController();
        controller.initialize(
                this::onStatusUpdate,
                this::onConnectionStatusChanged,
                this::onHostPerformanceUpdate,
                this::onViewerCountUpdate,
                this::onHostingStateChanged,
                this::onRoomCreated,
                this::onFrameReceived,
                this::onViewerPerformanceUpdate,
                this::onViewingStateChanged,
                this::onAuthenticationRequired,
                this::onAuthenticationSuccess
        );
        controller.setOnAccessCodeReceived(this::onAccessCodeReceived);

        // ── Generate default room ID ──
        roomIdInput.setText("room-" + UUID.randomUUID().toString().substring(0, 8));

        // ── Set initial disabled state ──
        setDisconnectedState();

        // ── Try auto-login from saved credentials ──
        controller.tryAutoLogin();

        // ── Auto-connect to server from .env config ──
        autoConnect();

        // ── Bind fullscreen video to fill container ──
        viewerFullscreenVideo.fitWidthProperty().bind(viewerVideoContainer.widthProperty().subtract(20));
        viewerFullscreenVideo.fitHeightProperty().bind(viewerVideoContainer.heightProperty().subtract(20));

        System.out.println("✅ MainController initialized");
    }

    /**
     * Auto-connect to the server using host/port from EnvConfig (.env file).
     */
    private void autoConnect() {
        EnvConfig config = EnvConfig.getInstance();
        String host = config.getServerHost();
        int port = config.getServerPort();

        System.out.println("🔌 Auto-connecting to " + host + ":" + port + "...");
        connectionStatusLabel.setText("⏳ Connecting to " + host + ":" + port + "...");
        connectionStatusLabel.setStyle("-fx-font-weight: 800; -fx-text-fill: #1570ef; -fx-font-size: 12;");

        controller.connect(host, port);
    }

    // ═══════════════════════════════════════════════════════════
    //  LEFT PANEL — AUTH HANDLERS
    // ═══════════════════════════════════════════════════════════

    @FXML
    private void handleSignIn() {
        showLoginDialog();
    }

    @FXML
    private void handleGoogleSignUp() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Google Sign-In");
        alert.setHeaderText("Coming Soon");
        alert.setContentText("Google OAuth sign-in will be available in a future update.");
        alert.showAndWait();
    }

    @FXML
    private void handleCreateAccount() {
        showLoginDialog();
    }

    @FXML
    private void handleSignOut() {
        controller.getAuthService().logout();
        controller.setAuthenticated(false);
        showAuthButtons();
        updateSecurityStatus(false, "Signed out");
        setDisconnectedState();
        autoConnect();
    }

    private void showLoginDialog() {
        // Prevent duplicate dialogs from concurrent auth-required callbacks
        if (loginDialogShowing) {
            System.out.println("⚠️ Login dialog already showing, ignoring duplicate request");
            return;
        }
        loginDialogShowing = true;

        Stage owner = (Stage) connectionStatusLabel.getScene().getWindow();
        LoginDialog loginDialog = new LoginDialog(controller.getAuthService(), owner);
        loginDialog.showAndWait().ifPresent(result -> {
            if (result instanceof AuthResult authResult) {
                if (authResult.success()) {
                    controller.setAuthenticated(true);
                    String user = controller.getAuthService().getCurrentUsername().orElse("User");
                    updateSecurityStatus(true, "Authenticated as " + user);
                    showUserProfile(user);
                    // Re-connect if not already connected
                    autoConnect();
                } else if (!"Cancelled".equals(authResult.message())) {
                    updateSecurityStatus(false, "Authentication failed");
                }
            }
        });

        loginDialogShowing = false;
    }

    // ═══════════════════════════════════════════════════════════
    //  HOST HANDLERS
    // ═══════════════════════════════════════════════════════════

    @FXML
    private void handleStartHosting() {
        String roomId = roomIdInput.getText().trim();
        controller.startHosting(roomId.isEmpty() ? null : roomId);
    }

    @FXML
    private void handleStopHosting() {
        controller.stopHosting();
    }

    @FXML
    private void handleCopyRoomId() {
        String roomId = activeRoomLabel.getText();
        if (roomId != null && !roomId.equals("-")) {
            copyToClipboard(roomId);
            connectionStatusLabel.setText("📋 Room ID copied!");
        }
    }

    @FXML
    private void handleCopyAccessCode() {
        String code = accessCodeLabel.getText();
        if (code != null && !code.isEmpty()) {
            copyToClipboard(code);
            connectionStatusLabel.setText("📋 Access code copied!");
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  VIEWER HANDLERS
    // ═══════════════════════════════════════════════════════════

    @FXML
    private void handleStartViewing() {
        String roomId = joinRoomIdInput.getText().trim();
        if (roomId.isEmpty()) {
            viewerStatusLabel.setText("⚠️ Enter a room ID");
            viewerStatusLabel.setStyle("-fx-font-weight: 900; -fx-text-fill: #ff9800;");
            return;
        }
        controller.startViewing(roomId);
    }

    @FXML
    private void handleStopViewing() {
        controller.stopViewing();
    }

    // ═══════════════════════════════════════════════════════════
    //  CALLBACKS FROM DualModeController
    // ═══════════════════════════════════════════════════════════

    private void onStatusUpdate(String status) {
        Platform.runLater(() -> connectionStatusLabel.setText(status));
    }

    private void onConnectionStatusChanged(Boolean connected) {
        Platform.runLater(() -> {
            if (connected) {
                setConnectedState();
            } else {
                setDisconnectedState();
            }
        });
    }

    private void onHostPerformanceUpdate(String performance) {
        Platform.runLater(() -> {
            if (hostFpsLabel != null) {
                hostFpsLabel.setText(performance);
            }
        });
    }

    private void onViewerCountUpdate(Integer count) {
        Platform.runLater(() -> {
            if (viewerCountLabel != null) {
                viewerCountLabel.setText("👁 " + count + " viewer" + (count != 1 ? "s" : ""));
            }
        });
    }

    private void onHostingStateChanged(Boolean isHosting) {
        Platform.runLater(() -> {
            startButton.setDisable(isHosting);
            stopButton.setDisable(!isHosting);
            roomIdInput.setDisable(isHosting);

            if (isHosting) {
                hostConnectionStatusLabel.setText("🟢 Hosting");
                hostConnectionStatusLabel.setStyle("-fx-text-fill: #12b76a; -fx-font-weight: 800;");
            } else {
                hostConnectionStatusLabel.setText("⚫ Not Hosting");
                hostConnectionStatusLabel.setStyle("-fx-text-fill: #667085; -fx-font-weight: 800;");

                // Hide room info
                roomInfoSection.setVisible(false);
                roomInfoSection.setManaged(false);
                accessCodeRow.setVisible(false);
                accessCodeRow.setManaged(false);
                accessCodeLabel.setText("");
                activeRoomLabel.setText("-");
                if (hostFpsLabel != null) hostFpsLabel.setText("");
            }
        });
    }

    private void onRoomCreated(String roomId) {
        Platform.runLater(() -> {
            activeRoomLabel.setText(roomId);
            roomInfoSection.setVisible(true);
            roomInfoSection.setManaged(true);
        });
    }

    private void onAccessCodeReceived(String accessCode) {
        Platform.runLater(() -> {
            if (accessCode != null && !accessCode.isEmpty()) {
                accessCodeLabel.setText(accessCode);
                accessCodeRow.setVisible(true);
                accessCodeRow.setManaged(true);
            } else {
                accessCodeRow.setVisible(false);
                accessCodeRow.setManaged(false);
                accessCodeLabel.setText("");
            }
        });
    }

    private void onFrameReceived(Image frame) {
        Platform.runLater(() -> {
            videoImageView.setImage(frame);
            if (videoPlaceholder != null) {
                videoPlaceholder.setVisible(false);
            }
            // Update fullscreen viewer video
            viewerFullscreenVideo.setImage(frame);
            if (viewerFullscreenPlaceholder != null) {
                viewerFullscreenPlaceholder.setVisible(false);
            }
        });
    }

    private void onViewerPerformanceUpdate(String performance) {
        Platform.runLater(() -> {
            // Parse performance string and update individual labels
            // Format from DualModeController: "📥 FPS: X | Data: Y MB"
            if (performance.contains("FPS:")) {
                try {
                    String[] parts = performance.split("\\|");
                    for (String part : parts) {
                        String trimmed = part.trim();
                        if (trimmed.contains("FPS:")) {
                            String fps = trimmed.replaceAll("[^0-9.]", "").trim();
                            viewerFpsLabel.setText(fps + " FPS");
                            fsFpsLabel.setText(fps);
                        } else if (trimmed.contains("Data:") || trimmed.contains("MB")) {
                            String data = trimmed.replaceAll("[^0-9.]", "").trim();
                            viewerDataLabel.setText(data + " MB");
                            fsDataLabel.setText(data + " MB");
                        }
                    }
                } catch (Exception e) {
                    viewerFpsLabel.setText(performance);
                    fsFpsLabel.setText(performance);
                }
            } else {
                viewerFpsLabel.setText(performance);
                fsFpsLabel.setText(performance);
            }
        });
    }

    private void onViewingStateChanged(Boolean isViewing) {
        Platform.runLater(() -> {
            joinRoomButton.setDisable(isViewing);
            joinRoomIdInput.setDisable(isViewing);
            disconnectViewerButton.setDisable(!isViewing);

            if (isViewing) {
                String roomCode = joinRoomIdInput.getText().trim();
                viewerStatusLabel.setText("🟢 Viewing");
                viewerStatusLabel.setStyle("-fx-font-weight: 900; -fx-text-fill: #12b76a;");
                roomStatusLabel.setText(roomCode);

                // ── Switch to viewer fullscreen screen ──
                homeScreen.setVisible(false);
                homeScreen.setManaged(false);
                viewerScreen.setVisible(true);
                viewerScreen.setManaged(true);

                // Set room labels on fullscreen view
                viewerRoomCodeLabel.setText("Room: " + roomCode);
                fsRoomLabel.setText(roomCode);
            } else {
                viewerStatusLabel.setText("🟡 Waiting");
                viewerStatusLabel.setStyle("-fx-font-weight: 900; -fx-text-fill: #ff9800;");

                // ── Switch back to home screen ──
                viewerScreen.setVisible(false);
                viewerScreen.setManaged(false);
                homeScreen.setVisible(true);
                homeScreen.setManaged(true);

                // Reset home screen viewer state
                if (videoPlaceholder != null) videoPlaceholder.setVisible(true);
                videoImageView.setImage(null);
                roomStatusLabel.setText("None");
                viewerFpsLabel.setText("0 FPS");
                viewerDataLabel.setText("0 MB");
                latencyLabel.setText("0 ms");
                qualityLabel.setText("N/A");
                viewerStreamPane.setVisible(false);
                viewerStreamPane.setManaged(false);

                // Reset fullscreen viewer state
                viewerFullscreenVideo.setImage(null);
                if (viewerFullscreenPlaceholder != null) viewerFullscreenPlaceholder.setVisible(true);
                viewerRoomCodeLabel.setText("Room: ---");
                fsRoomLabel.setText("---");
                fsFpsLabel.setText("0");
                fsDataLabel.setText("0 MB");
                fsLatencyLabel.setText("0 ms");
                fsQualityLabel.setText("N/A");
            }
        });
    }

    // ── Authentication callbacks ──

    private void onAuthenticationRequired(String message) {
        Platform.runLater(() -> {
            updateSecurityStatus(false, message);
            showLoginDialog();
        });
    }

    private void onAuthenticationSuccess(String username) {
        Platform.runLater(() -> {
            updateSecurityStatus(true, "Logged in as " + username);
            showUserProfile(username);
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  LEFT PANEL — AUTH / PROFILE TOGGLE
    // ═══════════════════════════════════════════════════════════

    private void showUserProfile(String username) {
        // Set avatar initial
        String initial = (username != null && !username.isEmpty())
                ? username.substring(0, 1).toUpperCase() : "U";
        userAvatarLabel.setText(initial);
        userDisplayName.setText(username);
        userRoleLabel.setText("Authenticated");
        sessionStatusLabel.setText("Session active");

        // Hide auth buttons, show profile
        authButtonsSection.setVisible(false);
        authButtonsSection.setManaged(false);
        userProfileSection.setVisible(true);
        userProfileSection.setManaged(true);
    }

    private void showAuthButtons() {
        // Show auth buttons, hide profile
        authButtonsSection.setVisible(true);
        authButtonsSection.setManaged(true);
        userProfileSection.setVisible(false);
        userProfileSection.setManaged(false);
    }

    // ═══════════════════════════════════════════════════════════
    //  UI STATE MANAGEMENT
    // ═══════════════════════════════════════════════════════════

    private void setConnectedState() {
        connectionStatusLabel.setText("✅ Connected");
        connectionStatusLabel.setStyle("-fx-font-weight: 800; -fx-text-fill: #12b76a; -fx-font-size: 12;");

        // Enable host/viewer controls
        startButton.setDisable(false);
        roomIdInput.setDisable(false);

        // Show viewer connected pane (join controls)
        viewerConnectedPane.setVisible(true);
        viewerConnectedPane.setManaged(true);
        joinRoomButton.setDisable(false);
        joinRoomIdInput.setDisable(false);
        disconnectViewerButton.setDisable(true);

        hostConnectionStatusLabel.setText("⚫ Not Hosting");
        hostConnectionStatusLabel.setStyle("-fx-text-fill: #667085; -fx-font-weight: 800;");

        updateSecurityStatus(true, "Connected securely");
    }

    private void setDisconnectedState() {
        connectionStatusLabel.setText("❌ Not Connected");
        connectionStatusLabel.setStyle("-fx-font-weight: 800; -fx-text-fill: #d92d20; -fx-font-size: 12;");

        // Disable all host/viewer controls
        startButton.setDisable(true);
        stopButton.setDisable(true);
        roomIdInput.setDisable(true);

        // Hide viewer connected pane
        viewerConnectedPane.setVisible(false);
        viewerConnectedPane.setManaged(false);

        // Reset host info
        hostConnectionStatusLabel.setText("🔴 Disconnected");
        hostConnectionStatusLabel.setStyle("-fx-text-fill: #d92d20; -fx-font-weight: 800;");
        roomInfoSection.setVisible(false);
        roomInfoSection.setManaged(false);

        // Reset viewer
        viewerStatusLabel.setText("🟡 Waiting");
        viewerStatusLabel.setStyle("-fx-font-weight: 900;");
        if (videoPlaceholder != null) videoPlaceholder.setVisible(true);
        videoImageView.setImage(null);
    }

    private void updateSecurityStatus(boolean secure, String message) {
        Platform.runLater(() -> {
            if (secure) {
                securityStatusDot.setText("●");
                securityStatusDot.setStyle("-fx-text-fill: #39d98a; -fx-font-size: 14;");
            } else {
                securityStatusDot.setText("●");
                securityStatusDot.setStyle("-fx-text-fill: #ff9800; -fx-font-size: 14;");
            }
            securityStatusLabel.setText(message);
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  UTILITIES
    // ═══════════════════════════════════════════════════════════

    private void copyToClipboard(String text) {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
    }
}
