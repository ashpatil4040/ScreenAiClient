import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import controller.MainController;
import controller.DualModeMainController;
import org.springframework.context.ConfigurableApplicationContext;
import java.io.IOException;
import java.util.List;

/**
 * ScreenAI Client - JavaFX + Spring Application
 * Default: new split-panel UI (main.fxml).  --classic: tabbed dual-mode UI.
 */
public class App extends Application {
    private ConfigurableApplicationContext springContext;
    private static boolean useClassicMode = false; // Default to new UI

    @Override
    public void start(Stage stage) throws IOException {
        // Check command line arguments for mode selection
        List<String> args = getParameters().getRaw();
        if (args.contains("--classic")) {
            useClassicMode = true;
            System.out.println("🔧 Running in CLASSIC mode (tabbed dual-mode UI)");
        } else {
            System.out.println("🔧 Running in DEFAULT mode (new split-panel UI)");
        }

        // Initialize Spring context for dependency injection
        System.out.println("🚀 Initializing Spring context...");
        ScreenAIClientApplication.startSpringContext();
        springContext = ScreenAIClientApplication.getSpringContext();
        System.out.println("✅ Spring context initialized");

        // Load appropriate FXML based on mode
        FXMLLoader fxmlLoader;
        if (useClassicMode) {
            System.out.println("📺 Loading Classic (Dual-Mode) UI...");
            fxmlLoader = new FXMLLoader(App.class.getResource("/ui/dual-mode.fxml"));
            fxmlLoader.setControllerFactory(controllerClass -> {
                if (controllerClass == DualModeMainController.class) {
                    return new DualModeMainController();
                }
                return springContext.getBean(controllerClass);
            });
        } else {
            System.out.println("🚀 Loading New UI...");
            fxmlLoader = new FXMLLoader(App.class.getResource("/ui/main.fxml"));
            fxmlLoader.setControllerFactory(controllerClass -> {
                if (controllerClass == MainController.class) {
                    return new MainController();
                }
                return springContext.getBean(controllerClass);
            });
        }

        Scene scene = new Scene(fxmlLoader.load(), 1200, 800);
        stage.setTitle("ScreenAI - Secure Screen Sharing");
        stage.setScene(scene);

        // Handle window close
        stage.setOnCloseRequest(e -> {
            System.out.println("🔌 Closing application...");
            ScreenAIClientApplication.stopSpringContext();
            System.exit(0);
        });

        stage.show();
        System.out.println("✅ JavaFX UI loaded successfully");
    }

    public static void main(String[] args) {
        launch(args);
    }
}

