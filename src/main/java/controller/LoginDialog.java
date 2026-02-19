package controller;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AuthenticationService;
import service.AuthenticationService.AuthResult;

import java.util.Optional;

/**
 * Login / Sign-Up dialog for ScreenAI client.
 * Clean white-card design that matches the landing page aesthetic.
 */
public class LoginDialog extends Dialog<AuthResult> {

    private static final Logger log = LoggerFactory.getLogger(LoginDialog.class);
    private static final String FONT = "-fx-font-family: 'Segoe UI', 'Inter', Arial;";

    private final AuthenticationService authService;

    // Shared fields
    private TextField usernameField;
    private PasswordField passwordField;
    private PasswordField confirmPasswordField;
    private CheckBox rememberMeCheckbox;
    private Label statusLabel;
    private ProgressIndicator progressIndicator;
    private Button actionButton;

    // Page containers
    private VBox loginPage;
    private VBox signUpPage;
    private StackPane pageContainer;

    // Confirm-password row (sign-up only)
    private Label confirmLabel;

    private boolean isSignUpMode = false;

    public LoginDialog(AuthenticationService authService, Stage owner) {
        this.authService = authService;
        setTitle("ScreenAI");
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.UNDECORATED);
        setResizable(false);
        buildUI();
    }

    // ═══════════════════════════════════════════════════════════
    //  BUILD UI
    // ═══════════════════════════════════════════════════════════

    private void buildUI() {
        // ── Outer root: transparent so the card shadow is visible ──
        VBox root = new VBox();
        root.setStyle("-fx-background-color: transparent;");
        root.setPadding(new Insets(16));

        // ── White card ──
        VBox card = new VBox(0);
        card.setStyle(
                "-fx-background-color: white;" +
                "-fx-background-radius: 16;" + FONT
        );
        card.setPrefWidth(380);
        card.setEffect(new DropShadow(24, 0, 4, Color.rgb(0, 0, 0, 0.18)));

        // ── Blue header stripe ──
        VBox header = new VBox(4);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(28, 0, 20, 0));
        header.setStyle(
                "-fx-background-color: linear-gradient(to right, #071a67, #1570ef);" +
                "-fx-background-radius: 16 16 0 0;"
        );

        HBox brandRow = new HBox(8);
        brandRow.setAlignment(Pos.CENTER);
        Label logo = new Label("◀▶");
        logo.setStyle("-fx-font-size: 18; -fx-text-fill: white; -fx-font-weight: 800;" + FONT);
        Label brand = new Label("ScreenAI");
        brand.setStyle("-fx-font-size: 24; -fx-text-fill: white; -fx-font-weight: 800;" + FONT);
        brandRow.getChildren().addAll(logo, brand);
        header.getChildren().add(brandRow);

        // ── Form body ──
        VBox body = new VBox(0);
        body.setPadding(new Insets(24, 32, 24, 32));

        // Build both pages
        loginPage = buildLoginPage();
        signUpPage = buildSignUpPage();
        signUpPage.setVisible(false);
        signUpPage.setManaged(false);

        pageContainer = new StackPane(loginPage, signUpPage);

        body.getChildren().add(pageContainer);

        card.getChildren().addAll(header, body);
        root.getChildren().add(card);

        // ── Dialog pane ──
        getDialogPane().setContent(root);
        getDialogPane().setStyle("-fx-background-color: transparent;" + FONT);
        getDialogPane().setPadding(Insets.EMPTY);

        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        getDialogPane().lookupButton(ButtonType.CLOSE).setVisible(false);
        getDialogPane().lookupButton(ButtonType.CLOSE).setManaged(false);

        setResultConverter(bt -> new AuthResult(false, "Cancelled", null));

        Platform.runLater(() -> usernameField.requestFocus());
    }

    // ─────────────── LOGIN PAGE ───────────────

    private VBox buildLoginPage() {
        VBox page = new VBox(6);

        Label heading = new Label("Welcome back");
        heading.setStyle("-fx-font-size: 18; -fx-font-weight: 800; -fx-text-fill: #101828;" + FONT);
        Label sub = new Label("Sign in to your account");
        sub.setStyle("-fx-font-size: 12; -fx-text-fill: #667085;" + FONT);

        Region gap1 = new Region();
        gap1.setPrefHeight(12);

        // Username
        usernameField = new TextField();
        usernameField.setPromptText("Username");
        styleField(usernameField);

        // Password
        passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        styleField(passwordField);

        // Remember me
        rememberMeCheckbox = new CheckBox("Remember me");
        rememberMeCheckbox.setStyle("-fx-text-fill: #344054; -fx-font-size: 12;" + FONT);

        // Status + progress
        statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: #d92d20; -fx-font-size: 11; -fx-font-weight: 700;" + FONT);
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(300);

        progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(22, 22);
        progressIndicator.setVisible(false);

        // Login button
        actionButton = new Button("Sign In");
        actionButton.setMaxWidth(Double.MAX_VALUE);
        stylePrimaryBtn(actionButton);
        actionButton.setOnAction(e -> handleLogin());

        // Cancel button
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setMaxWidth(Double.MAX_VALUE);
        styleGhostBtn(cancelBtn);
        cancelBtn.setOnAction(e -> { setResult(new AuthResult(false, "Cancelled", null)); close(); });

        Region gap2 = new Region();
        gap2.setPrefHeight(6);

        // Toggle to sign-up
        HBox toggleRow = new HBox(4);
        toggleRow.setAlignment(Pos.CENTER);
        Label noAccount = new Label("Don't have an account?");
        noAccount.setStyle("-fx-text-fill: #667085; -fx-font-size: 12;" + FONT);
        Hyperlink signUpLink = new Hyperlink("Sign up");
        signUpLink.setStyle("-fx-text-fill: #1570ef; -fx-font-size: 12; -fx-font-weight: 700; -fx-border-color: transparent;" + FONT);
        signUpLink.setOnAction(e -> switchToSignUp());
        toggleRow.getChildren().addAll(noAccount, signUpLink);

        passwordField.setOnAction(e -> handleLogin());

        page.getChildren().addAll(
                heading, sub, gap1,
                usernameField, passwordField,
                rememberMeCheckbox,
                statusLabel, progressIndicator,
                actionButton, cancelBtn,
                gap2, toggleRow
        );
        return page;
    }

    // ─────────────── SIGN-UP PAGE ───────────────

    private VBox buildSignUpPage() {
        VBox page = new VBox(6);

        Label heading = new Label("Create account");
        heading.setStyle("-fx-font-size: 18; -fx-font-weight: 800; -fx-text-fill: #101828;" + FONT);
        Label sub = new Label("Sign up to get started");
        sub.setStyle("-fx-font-size: 12; -fx-text-fill: #667085;" + FONT);

        Region gap1 = new Region();
        gap1.setPrefHeight(12);

        // We reuse the shared fields — they move between pages.
        // But for sign-up we need a confirm-password field.
        confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Confirm password");
        styleField(confirmPasswordField);

        // Sign-up button
        Button signUpBtn = new Button("Create Account");
        signUpBtn.setMaxWidth(Double.MAX_VALUE);
        stylePrimaryBtn(signUpBtn);
        signUpBtn.setOnAction(e -> handleRegister());

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setMaxWidth(Double.MAX_VALUE);
        styleGhostBtn(cancelBtn);
        cancelBtn.setOnAction(e -> { setResult(new AuthResult(false, "Cancelled", null)); close(); });

        Region gap2 = new Region();
        gap2.setPrefHeight(6);

        // Toggle to login
        HBox toggleRow = new HBox(4);
        toggleRow.setAlignment(Pos.CENTER);
        Label hasAccount = new Label("Already have an account?");
        hasAccount.setStyle("-fx-text-fill: #667085; -fx-font-size: 12;" + FONT);
        Hyperlink loginLink = new Hyperlink("Sign in");
        loginLink.setStyle("-fx-text-fill: #1570ef; -fx-font-size: 12; -fx-font-weight: 700; -fx-border-color: transparent;" + FONT);
        loginLink.setOnAction(e -> switchToLogin());
        toggleRow.getChildren().addAll(hasAccount, loginLink);

        confirmPasswordField.setOnAction(e -> handleRegister());

        page.getChildren().addAll(
                heading, sub, gap1,
                confirmPasswordField,
                signUpBtn, cancelBtn,
                gap2, toggleRow
        );
        return page;
    }

    // ═══════════════════════════════════════════════════════════
    //  PAGE SWITCHING
    // ═══════════════════════════════════════════════════════════

    private void switchToSignUp() {
        isSignUpMode = true;
        statusLabel.setText("");

        // Move shared fields (username, password) into sign-up page, before confirm
        VBox sp = signUpPage;
        // Insert shared fields at index 3 (after gap)
        if (!sp.getChildren().contains(usernameField)) {
            sp.getChildren().add(3, usernameField);
            sp.getChildren().add(4, passwordField);
            // Also move status + progress
            sp.getChildren().add(6, statusLabel);
            sp.getChildren().add(7, progressIndicator);
        }

        loginPage.setVisible(false);
        loginPage.setManaged(false);
        signUpPage.setVisible(true);
        signUpPage.setManaged(true);

        Platform.runLater(() -> usernameField.requestFocus());
    }

    private void switchToLogin() {
        isSignUpMode = false;
        statusLabel.setText("");

        // Move shared fields back to login page
        VBox lp = loginPage;
        if (!lp.getChildren().contains(usernameField)) {
            // Insert at index 3 (after heading, sub, gap)
            lp.getChildren().add(3, usernameField);
            lp.getChildren().add(4, passwordField);
            // rememberMe at 5, status at 6, progress at 7
            lp.getChildren().add(6, statusLabel);
            lp.getChildren().add(7, progressIndicator);
        }

        signUpPage.setVisible(false);
        signUpPage.setManaged(false);
        loginPage.setVisible(true);
        loginPage.setManaged(true);

        Platform.runLater(() -> usernameField.requestFocus());
    }

    // ═══════════════════════════════════════════════════════════
    //  HANDLERS
    // ═══════════════════════════════════════════════════════════

    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        boolean rememberMe = rememberMeCheckbox.isSelected();

        if (username.isEmpty()) { showError("Username is required"); return; }
        if (password.isEmpty()) { showError("Password is required"); return; }

        setLoading(true);

        authService.login(username, password, rememberMe)
                .thenAccept(result -> Platform.runLater(() -> {
                    setLoading(false);
                    if (result.success()) {
                        log.info("Login successful (remember-me: {})", rememberMe);
                        setResult(result);
                        close();
                    } else {
                        showError(result.message());
                    }
                }));
    }

    private void handleRegister() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        if (username.isEmpty()) { showError("Username is required"); return; }
        if (username.length() < 3 || username.length() > 50) { showError("Username must be 3-50 characters"); return; }
        if (password.isEmpty()) { showError("Password is required"); return; }
        if (password.length() < 8) { showError("Password must be at least 8 characters"); return; }
        if (!password.equals(confirmPassword)) { showError("Passwords do not match"); return; }

        setLoading(true);

        authService.register(username, password)
                .thenAccept(result -> Platform.runLater(() -> {
                    setLoading(false);
                    if (result.success()) {
                        log.info("Registration successful");
                        setResult(result);
                        close();
                    } else {
                        showError(result.message());
                    }
                }));
    }

    private void setLoading(boolean loading) {
        progressIndicator.setVisible(loading);
        actionButton.setDisable(loading);
        usernameField.setDisable(loading);
        passwordField.setDisable(loading);
        confirmPasswordField.setDisable(loading);
        rememberMeCheckbox.setDisable(loading);
        if (loading) {
            statusLabel.setText("Please wait...");
            statusLabel.setStyle("-fx-text-fill: #667085; -fx-font-size: 11;" + FONT);
        }
    }

    private void showError(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: #d92d20; -fx-font-size: 11; -fx-font-weight: 700;" + FONT);
    }

    // ═══════════════════════════════════════════════════════════
    //  STYLES
    // ═══════════════════════════════════════════════════════════

    private void styleField(TextField field) {
        String base =
                "-fx-background-color: #f9fafb; " +
                "-fx-text-fill: #101828; " +
                "-fx-prompt-text-fill: #98a2b3; " +
                "-fx-border-color: #d0d5dd; " +
                "-fx-border-radius: 10; " +
                "-fx-background-radius: 10; " +
                "-fx-padding: 10 14; " +
                "-fx-font-size: 13;" + FONT;
        String focused =
                "-fx-background-color: white; " +
                "-fx-text-fill: #101828; " +
                "-fx-prompt-text-fill: #98a2b3; " +
                "-fx-border-color: #1570ef; " +
                "-fx-border-radius: 10; " +
                "-fx-background-radius: 10; " +
                "-fx-padding: 10 14; " +
                "-fx-font-size: 13;" + FONT;
        field.setStyle(base);
        field.setMaxWidth(Double.MAX_VALUE);
        field.focusedProperty().addListener((o, was, is) -> field.setStyle(is ? focused : base));
    }

    private void stylePrimaryBtn(Button btn) {
        btn.setStyle(
                "-fx-background-color: linear-gradient(to right, #071a67, #1570ef); " +
                "-fx-text-fill: white; " +
                "-fx-font-weight: 800; " +
                "-fx-font-size: 13; " +
                "-fx-background-radius: 10; " +
                "-fx-padding: 11 0; " +
                "-fx-cursor: hand;" + FONT
        );
    }

    private void styleGhostBtn(Button btn) {
        btn.setStyle(
                "-fx-background-color: transparent; " +
                "-fx-text-fill: #667085; " +
                "-fx-font-weight: 600; " +
                "-fx-font-size: 12; " +
                "-fx-border-color: #d0d5dd; " +
                "-fx-border-radius: 10; " +
                "-fx-background-radius: 10; " +
                "-fx-padding: 9 0; " +
                "-fx-cursor: hand;" + FONT
        );
    }

    /**
     * Show the login dialog and return the result.
     */
    public static Optional<AuthResult> showAndWait(AuthenticationService authService, Stage owner) {
        LoginDialog dialog = new LoginDialog(authService, owner);
        return dialog.showAndWait();
    }
}
