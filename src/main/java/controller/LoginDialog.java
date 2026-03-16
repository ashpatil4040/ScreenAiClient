package controller;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
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
 * TeamViewer-style clean white card design with social auth buttons.
 */
public class LoginDialog extends Dialog<AuthResult> {

    private static final Logger log = LoggerFactory.getLogger(LoginDialog.class);
    private static final String FONT = "-fx-font-family: 'Segoe UI', 'Inter', Arial;";

    private final AuthenticationService authService;

    // Shared fields
    private TextField usernameField;
    private PasswordField passwordField;
    private PasswordField confirmPasswordField;
    private TextField nameField;
    private CheckBox rememberMeCheckbox;
    private Label statusLabel;
    private ProgressIndicator progressIndicator;
    private Button actionButton;

    // Page containers
    private VBox loginPage;
    private VBox signUpPage;
    private StackPane pageContainer;

    private boolean isSignUpMode = false;

    public LoginDialog(AuthenticationService authService, Stage owner) {
        this.authService = authService;
        setTitle("ScreenAI");
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.UNDECORATED);
        setResizable(false);
        buildUI();

        // Ensure dialog appears on top
        setOnShowing(e -> {
            Stage stage = (Stage) getDialogPane().getScene().getWindow();
            stage.setAlwaysOnTop(true);
            Platform.runLater(() -> stage.setAlwaysOnTop(false));
        });
    }

    // ═══════════════════════════════════════════════════════════
    // BUILD UI
    // ═══════════════════════════════════════════════════════════

    private void buildUI() {
        VBox root = new VBox();
        root.setStyle("-fx-background-color: transparent;");
        root.setPadding(new Insets(24));

        VBox card = new VBox(0);
        card.setStyle(
                "-fx-background-color: white;" +
                        "-fx-background-radius: 16;" +
                        "-fx-border-color: #1570ef transparent transparent transparent;" +
                        "-fx-border-width: 4 0 0 0;" +
                        "-fx-border-radius: 16 16 0 0;" + FONT);
        card.setPrefWidth(460);
        card.setEffect(new DropShadow(24, 0, 4, Color.rgb(0, 0, 0, 0.18)));

        VBox body = new VBox(0);
        body.setPadding(new Insets(28, 32, 28, 32));

        loginPage = buildLoginPage();
        signUpPage = buildSignUpPage();
        signUpPage.setVisible(false);
        signUpPage.setManaged(false);

        pageContainer = new StackPane(loginPage, signUpPage);
        body.getChildren().add(pageContainer);

        // Wrap in ScrollPane so content doesn't overflow on small screens
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setStyle("-fx-background: white; -fx-background-color: white; -fx-border-color: transparent;");
        scroll.setMaxHeight(javafx.stage.Screen.getPrimary().getVisualBounds().getHeight() * 0.85);

        card.getChildren().add(scroll);
        root.getChildren().add(card);

        getDialogPane().setContent(root);
        getDialogPane().setStyle("-fx-background-color: transparent;" + FONT);
        getDialogPane().setPadding(Insets.EMPTY);

        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        getDialogPane().lookupButton(ButtonType.CLOSE).setVisible(false);
        getDialogPane().lookupButton(ButtonType.CLOSE).setManaged(false);

        setResultConverter(bt -> new AuthResult(false, "Cancelled", null));

        Platform.runLater(() -> usernameField.requestFocus());
    }

    // ─────────────── LOGIN PAGE (TeamViewer Sign-In style) ───────────────

    private VBox buildLoginPage() {
        VBox page = new VBox(0);

        // Back arrow + "Sign in" header
        HBox headerRow = new HBox(12);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        Label backArrow = new Label("\u2190");
        backArrow.setStyle("-fx-font-size: 22; -fx-text-fill: #101828; -fx-cursor: hand;" + FONT);
        backArrow.setOnMouseClicked(e -> {
            setResult(new AuthResult(false, "Cancelled", null));
            close();
        });
        Label heading = new Label("Sign in");
        heading.setStyle("-fx-font-size: 22; -fx-font-weight: 800; -fx-text-fill: #101828;" + FONT);
        headerRow.getChildren().addAll(backArrow, heading);

        Region gap0 = new Region();
        gap0.setPrefHeight(8);

        Label sub = new Label("Welcome back! It's nice to see you again.");
        sub.setStyle("-fx-font-size: 13; -fx-text-fill: #667085;" + FONT);

        Region gap1 = new Region();
        gap1.setPrefHeight(20);

        // Username field (mapped to "Email" visually)
        usernameField = new TextField();
        usernameField.setPromptText("Email");
        styleField(usernameField);

        Region gap1b = new Region();
        gap1b.setPrefHeight(10);

        // Password field
        passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        styleField(passwordField);

        Region gap1bx = new Region();
        gap1bx.setPrefHeight(4);

        // Remember me
        rememberMeCheckbox = new CheckBox("Remember me");
        rememberMeCheckbox.setStyle("-fx-text-fill: #344054; -fx-font-size: 12;" + FONT);

        Region gap1c = new Region();
        gap1c.setPrefHeight(4);

        // Status + progress
        statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: #d92d20; -fx-font-size: 11; -fx-font-weight: 700;" + FONT);
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(340);

        progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(22, 22);
        progressIndicator.setVisible(false);

        // Continue button
        actionButton = new Button("Continue");
        actionButton.setMaxWidth(Double.MAX_VALUE);
        stylePrimaryBtn(actionButton);
        actionButton.setOnAction(e -> handleLogin());

        Region gap2 = new Region();
        gap2.setPrefHeight(12);

        // "Or" divider
        HBox orDivider = buildOrDivider();

        Region gap3 = new Region();
        gap3.setPrefHeight(12);

        // Social auth button
        Button googleBtn = buildGoogleButton();
        googleBtn.setOnAction(e -> showComingSoon("Google"));

        Region gap4 = new Region();
        gap4.setPrefHeight(16);

        // "New to ScreenAI? Create an account"
        HBox toggleRow = new HBox(4);
        toggleRow.setAlignment(Pos.CENTER);
        Label noAccount = new Label("New to ScreenAI?");
        noAccount.setStyle("-fx-text-fill: #667085; -fx-font-size: 12;" + FONT);
        Hyperlink signUpLink = new Hyperlink("Create an account");
        signUpLink.setStyle(
                "-fx-text-fill: #1570ef; -fx-font-size: 12; -fx-font-weight: 700; -fx-border-color: transparent;"
                        + FONT);
        signUpLink.setOnAction(e -> switchToSignUp());
        toggleRow.getChildren().addAll(noAccount, signUpLink);

        passwordField.setOnAction(e -> handleLogin());

        page.getChildren().addAll(
                headerRow, gap0, sub, gap1,
                usernameField, gap1b, passwordField,
                gap1bx, rememberMeCheckbox, gap1c,
                statusLabel, progressIndicator,
                actionButton,
                gap2, orDivider, gap3,
                googleBtn,
                gap4, toggleRow);
        return page;
    }

    // ─────────────── SIGN-UP PAGE (TeamViewer Create Account style)
    // ───────────────

    private VBox buildSignUpPage() {
        VBox page = new VBox(0);

        // ScreenAI logo header
        HBox brandRow = new HBox(8);
        brandRow.setAlignment(Pos.CENTER);
        brandRow.setPadding(new Insets(0, 0, 16, 0));
        Label logo = new Label("\u25C0\u25B6");
        logo.setStyle("-fx-font-size: 18; -fx-text-fill: #071a67; -fx-font-weight: 800;" + FONT);
        Label brand = new Label("ScreenAI");
        brand.setStyle("-fx-font-size: 22; -fx-text-fill: #071a67; -fx-font-weight: 800;" + FONT);
        brandRow.getChildren().addAll(logo, brand);

        // "Create an account"
        Label heading = new Label("Create an account");
        heading.setStyle("-fx-font-size: 20; -fx-font-weight: 800; -fx-text-fill: #101828;" + FONT);

        Region gap0 = new Region();
        gap0.setPrefHeight(4);

        Label sub = new Label("Welcome! Please enter your details.");
        sub.setStyle("-fx-font-size: 13; -fx-text-fill: #667085;" + FONT);

        Region gap1 = new Region();
        gap1.setPrefHeight(16);

        // Name field (visual only for now)
        nameField = new TextField();
        nameField.setPromptText("First and last name");
        styleField(nameField);

        Region gap1a = new Region();
        gap1a.setPrefHeight(10);

        // Password
        confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Password");
        styleField(confirmPasswordField);

        Region gap1b = new Region();
        gap1b.setPrefHeight(10);

        // Status + progress (sign-up shares them)
        Label signUpStatusLabel = new Label();
        signUpStatusLabel.setStyle("-fx-text-fill: #d92d20; -fx-font-size: 11; -fx-font-weight: 700;" + FONT);
        signUpStatusLabel.setWrapText(true);
        signUpStatusLabel.setMaxWidth(340);

        ProgressIndicator signUpProgress = new ProgressIndicator();
        signUpProgress.setMaxSize(22, 22);
        signUpProgress.setVisible(false);

        // Continue button
        Button signUpBtn = new Button("Continue");
        signUpBtn.setMaxWidth(Double.MAX_VALUE);
        stylePrimaryBtn(signUpBtn);
        signUpBtn.setOnAction(e -> handleRegister());

        Region gap2 = new Region();
        gap2.setPrefHeight(12);

        // "Or" divider
        HBox orDivider = buildOrDivider();

        Region gap3 = new Region();
        gap3.setPrefHeight(12);

        // Social auth button
        Button googleBtnSignUp = buildGoogleButton();
        googleBtnSignUp.setOnAction(e -> showComingSoon("Google"));

        Region gap3a = new Region();
        gap3a.setPrefHeight(8);

        // Disclaimer
        Label disclaimer = new Label(
                "By clicking \u201CContinue with Google\u201D, you acknowledge "
                        + "that your data may be processed outside of the European Union.");
        disclaimer.setStyle("-fx-text-fill: #98a2b3; -fx-font-size: 10; -fx-wrap-text: true;" + FONT);
        disclaimer.setWrapText(true);
        disclaimer.setMaxWidth(340);

        Region gap4 = new Region();
        gap4.setPrefHeight(16);

        // "Already have an account? Sign in"
        HBox toggleRow = new HBox(4);
        toggleRow.setAlignment(Pos.CENTER);
        Label hasAccount = new Label("Already have an account?");
        hasAccount.setStyle("-fx-text-fill: #667085; -fx-font-size: 12;" + FONT);
        Hyperlink loginLink = new Hyperlink("Sign in");
        loginLink.setStyle(
                "-fx-text-fill: #1570ef; -fx-font-size: 12; -fx-font-weight: 700; -fx-border-color: transparent;"
                        + FONT);
        loginLink.setOnAction(e -> switchToLogin());
        toggleRow.getChildren().addAll(hasAccount, loginLink);

        confirmPasswordField.setOnAction(e -> handleRegister());

        page.getChildren().addAll(
                brandRow, heading, gap0, sub, gap1,
                nameField, gap1a,
                confirmPasswordField, gap1b,
                signUpStatusLabel, signUpProgress,
                signUpBtn,
                gap2, orDivider, gap3,
                googleBtnSignUp,
                gap3a, disclaimer,
                gap4, toggleRow);
        return page;
    }

    // ═══════════════════════════════════════════════════════════
    // SHARED UI COMPONENTS
    // ═══════════════════════════════════════════════════════════

    private HBox buildOrDivider() {
        HBox divider = new HBox(12);
        divider.setAlignment(Pos.CENTER);
        Separator leftLine = new Separator();
        HBox.setHgrow(leftLine, Priority.ALWAYS);
        Label orLabel = new Label("Or");
        orLabel.setStyle("-fx-text-fill: #98a2b3; -fx-font-size: 12;" + FONT);
        Separator rightLine = new Separator();
        HBox.setHgrow(rightLine, Priority.ALWAYS);
        divider.getChildren().addAll(leftLine, orLabel, rightLine);
        return divider;
    }

    private Button buildGoogleButton() {
        // Actual Google G logo using SVG paths (4-color)
        Node googleLogo = buildGoogleGLogo();

        Label btnText = new Label("  Continue with Google");
        btnText.setStyle("-fx-text-fill: #344054; -fx-font-weight: 600; -fx-font-size: 13;" + FONT);

        HBox content = new HBox(6, googleLogo, btnText);
        content.setAlignment(Pos.CENTER);

        Button btn = new Button();
        btn.setGraphic(content);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setStyle(
                "-fx-background-color: white; " +
                        "-fx-border-color: #d0d5dd; " +
                        "-fx-border-radius: 10; " +
                        "-fx-background-radius: 10; " +
                        "-fx-padding: 10 16; " +
                        "-fx-cursor: hand;" + FONT);
        return btn;
    }

    private Node buildGoogleGLogo() {
        // Standard Google G logo SVG paths (24x24 viewBox)
        SVGPath blue = new SVGPath();
        blue.setContent(
                "M23.745 12.27c0-.79-.07-1.54-.19-2.27h-11.3v4.51h6.47c-.29 1.48-1.14 2.73-2.4 3.58v3h3.86c2.26-2.09 3.56-5.17 3.56-8.82z");
        blue.setFill(Color.web("#4285F4"));

        SVGPath green = new SVGPath();
        green.setContent(
                "M12.255 24c3.24 0 5.95-1.08 7.93-2.91l-3.86-3c-1.08.72-2.45 1.16-4.07 1.16-3.13 0-5.78-2.11-6.73-4.96h-3.98v3.09C3.515 21.3 7.565 24 12.255 24z");
        green.setFill(Color.web("#34A853"));

        SVGPath yellow = new SVGPath();
        yellow.setContent(
                "M5.525 14.29c-.25-.72-.38-1.49-.38-2.29s.14-1.57.38-2.29V6.62h-3.98a11.86 11.86 0 000 10.76l3.98-3.09z");
        yellow.setFill(Color.web("#FBBC05"));

        SVGPath red = new SVGPath();
        red.setContent(
                "M12.255 4.75c1.77 0 3.35.61 4.6 1.8l3.42-3.42C18.205 1.19 15.495 0 12.255 0c-4.69 0-8.74 2.7-10.71 6.62l3.98 3.09c.95-2.85 3.6-4.96 6.73-4.96z");
        red.setFill(Color.web("#EA4335"));

        Group g = new Group(blue, green, yellow, red);
        // Scale from 24x24 native to ~18px display
        g.setScaleX(0.75);
        g.setScaleY(0.75);

        StackPane container = new StackPane(g);
        container.setPrefSize(20, 20);
        container.setMaxSize(20, 20);
        container.setMinSize(20, 20);
        return container;
    }

    private Button buildSocialButton(String text) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setStyle(
                "-fx-background-color: white; " +
                        "-fx-text-fill: #344054; " +
                        "-fx-font-weight: 600; " +
                        "-fx-font-size: 13; " +
                        "-fx-border-color: #d0d5dd; " +
                        "-fx-border-radius: 10; " +
                        "-fx-background-radius: 10; " +
                        "-fx-padding: 10 16; " +
                        "-fx-cursor: hand; " +
                        "-fx-alignment: CENTER;" + FONT);
        return btn;
    }

    private void showComingSoon(String provider) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(provider + " Sign-In");
        alert.setHeaderText("Coming Soon");
        alert.setContentText(provider + " OAuth sign-in will be available in a future update.");
        alert.showAndWait();
    }

    // ═══════════════════════════════════════════════════════════
    // PAGE SWITCHING
    // ═══════════════════════════════════════════════════════════

    private void switchToSignUp() {
        isSignUpMode = true;
        statusLabel.setText("");

        // Move username field to sign-up page (after gap1, index 5)
        VBox sp = signUpPage;
        if (!sp.getChildren().contains(usernameField)) {
            // Insert username after nameField gap (index 7 = after nameField + gap1a)
            sp.getChildren().add(7, usernameField);
        }

        loginPage.setVisible(false);
        loginPage.setManaged(false);
        signUpPage.setVisible(true);
        signUpPage.setManaged(true);

        Platform.runLater(() -> nameField.requestFocus());
    }

    private void switchToLogin() {
        isSignUpMode = false;
        statusLabel.setText("");

        // Move username field back to login page
        VBox lp = loginPage;
        if (!lp.getChildren().contains(usernameField)) {
            lp.getChildren().add(4, usernameField);
        }

        signUpPage.setVisible(false);
        signUpPage.setManaged(false);
        loginPage.setVisible(true);
        loginPage.setManaged(true);

        Platform.runLater(() -> usernameField.requestFocus());
    }

    // ═══════════════════════════════════════════════════════════
    // HANDLERS
    // ═══════════════════════════════════════════════════════════

    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        boolean rememberMe = rememberMeCheckbox.isSelected();

        if (username.isEmpty()) {
            showError("Email is required");
            return;
        }
        if (password.isEmpty()) {
            showError("Password is required");
            return;
        }

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
        String password = confirmPasswordField.getText();

        if (username.isEmpty()) {
            showError("Email is required");
            return;
        }
        if (username.length() < 3 || username.length() > 50) {
            showError("Email must be 3-50 characters");
            return;
        }
        if (password.isEmpty()) {
            showError("Password is required");
            return;
        }
        if (password.length() < 8) {
            showError("Password must be at least 8 characters");
            return;
        }

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
    // STYLES
    // ═══════════════════════════════════════════════════════════

    private void styleField(TextField field) {
        String base = "-fx-background-color: #f9fafb; " +
                "-fx-text-fill: #101828; " +
                "-fx-prompt-text-fill: #98a2b3; " +
                "-fx-border-color: #d0d5dd; " +
                "-fx-border-radius: 10; " +
                "-fx-background-radius: 10; " +
                "-fx-padding: 10 14; " +
                "-fx-font-size: 13;" + FONT;
        String focused = "-fx-background-color: white; " +
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
                "-fx-background-color: #1570ef; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-weight: 700; " +
                        "-fx-font-size: 13; " +
                        "-fx-background-radius: 10; " +
                        "-fx-padding: 11 0; " +
                        "-fx-cursor: hand;" + FONT);
    }

    private void styleSecondaryBtn(Button btn) {
        btn.setStyle(
                "-fx-background-color: #e8ecf2; " +
                        "-fx-text-fill: #344054; " +
                        "-fx-font-weight: 700; " +
                        "-fx-font-size: 13; " +
                        "-fx-background-radius: 10; " +
                        "-fx-padding: 11 0; " +
                        "-fx-cursor: hand;" + FONT);
    }

    /**
     * Show the login dialog and return the result.
     */
    public static Optional<AuthResult> showAndWait(AuthenticationService authService, Stage owner) {
        LoginDialog dialog = new LoginDialog(authService, owner);
        return dialog.showAndWait();
    }
}
