package club.muimi.kimusic;

import club.muimi.kimusic.view.MainController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.IOException;

public class KimusicApplication extends Application {
    private AppContext context;

    @Override
    public void start(Stage stage) throws IOException {
        loadBundledFonts();
        context = new AppContext();
        FXMLLoader loader = new FXMLLoader(KimusicApplication.class.getResource("view.fxml"));
        Scene scene = new Scene(loader.load(), 1320, 820);
        scene.getStylesheets().add(KimusicApplication.class.getResource("kimusic.css").toExternalForm());

        MainController controller = loader.getController();
        controller.initializeContext(context);

        stage.setTitle("Kimusic");
        stage.setMinWidth(1120);
        stage.setMinHeight(640);
        stage.setScene(scene);
        try (var icon = KimusicApplication.class.getResourceAsStream("logo.png")) {
            if (icon != null) {
                stage.getIcons().add(new Image(icon));
            }
        }
        stage.show();
    }

    private void loadBundledFonts() {
        loadFont("fonts/JetBrainsMono-Regular.ttf");
        loadFont("fonts/JetBrainsMono-SemiBold.ttf");
        loadFont("fonts/SourceHanSansSC-Bold.ttf");
        loadFont("fonts/SourceHanSansJP-Bold.ttf");
    }

    private void loadFont(String resource) {
        try (var input = KimusicApplication.class.getResourceAsStream(resource)) {
            if (input != null) {
                Font.loadFont(input, 13);
            }
        } catch (IOException ignored) {
            // CSS still contains explicit installed-font fallbacks.
        }
    }

    @Override
    public void stop() {
        if (context != null) {
            context.close();
        }
    }
}
