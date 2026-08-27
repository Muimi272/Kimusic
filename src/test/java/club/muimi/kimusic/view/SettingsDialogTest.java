package club.muimi.kimusic.view;

import club.muimi.kimusic.AppContext;
import club.muimi.kimusic.KimusicApplication;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Window;
import javafx.util.Duration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsDialogTest {
    @Test
    void settingsUsesCategoryNavigationAndExposesNewPlaybackOptions() throws Exception {
        String originalHome = System.getProperty("user.home");
        Path temporaryHome = Path.of("target", "settings-test-home").toAbsolutePath();
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // The JavaFX toolkit is shared with the other view tests.
        }
        System.setProperty("user.home", temporaryHome.toString());

        Runnable testBody = () -> {
            AppContext context = null;
            try {
                FXMLLoader loader = new FXMLLoader(KimusicApplication.class.getResource("view.fxml"));
                Parent root = loader.load();
                Scene scene = new Scene(root);
                scene.getStylesheets().add(KimusicApplication.class.getResource("kimusic.css").toExternalForm());
                MainController controller = loader.getController();
                AppContext testContext = new AppContext();
                context = testContext;
                controller.initializeContext(testContext);

                Platform.runLater(() -> {
                    try {
                        Window dialog = Window.getWindows().stream()
                                .filter(Window::isShowing)
                                .filter(window -> window.getScene() != null
                                        && window.getScene().lookup(".settings-content") != null)
                                .findFirst().orElseThrow();
                        Set<ToggleButton> navigation = dialog.getScene().getRoot()
                                .lookupAll(".settings-nav-item")
                                .stream().map(ToggleButton.class::cast).collect(java.util.stream.Collectors.toSet());
                        assertEquals(2, navigation.size());
                        assertTrue(navigation.stream().allMatch(button -> button.getAlignment() == Pos.CENTER));
                        assertTrue(navigation.stream().anyMatch(button -> "外观".equals(button.getText())));
                        assertTrue(navigation.stream().anyMatch(button -> "播放".equals(button.getText())));
                        assertEquals(6, dialog.getScene().getRoot().lookupAll(".color-swatch").size());
                        CheckBox autoDecode = dialog.getScene().getRoot().lookupAll(".check-box").stream()
                                .filter(CheckBox.class::isInstance).map(CheckBox.class::cast)
                                .findFirst().orElseThrow();
                        assertTrue(autoDecode.getStyleClass().contains("ncm-auto-decode-check"));
                        Region box = (Region) autoDecode.lookup(".box");
                        Region mark = (Region) autoDecode.lookup(".mark");
                        assertNotNull(box);
                        assertNotNull(mark);
                        assertEquals(22, box.getPrefWidth(), 0.01);
                        assertNotNull(box.getBackground());
                        autoDecode.setSelected(true);
                        assertTrue(testContext.isAutoDecodeNcm(),
                                "settings should apply immediately when the option changes");
                        dialog.getScene().getRoot().applyCss();
                        assertEquals(Color.WHITE, mark.getBackground().getFills().getFirst().getFill());
                        dialog.getScene().getRoot().getStyleClass().remove("theme-light");
                        dialog.getScene().getRoot().getStyleClass().add("theme-dark");
                        dialog.getScene().getRoot().applyCss();
                        assertEquals(Color.web("#102321"),
                                mark.getBackground().getFills().getFirst().getFill(),
                                "the NCM check mark must adapt to the dark theme");
                        dialog.hide();
                    } catch (Throwable throwable) {
                        failure.set(throwable);
                        Window.getWindows().stream().filter(Window::isShowing).forEach(Window::hide);
                    }
                });
                PauseTransition guard = new PauseTransition(Duration.seconds(3));
                guard.setOnFinished(event -> Window.getWindows().stream()
                        .filter(Window::isShowing).forEach(Window::hide));
                guard.play();
                Method openSettings = MainController.class.getDeclaredMethod("openSettings");
                openSettings.setAccessible(true);
                openSettings.invoke(controller);
                guard.stop();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                if (context != null) {
                    context.close();
                }
                completed.countDown();
            }
        };
        Platform.runLater(testBody);

        try {
            assertTrue(completed.await(10, TimeUnit.SECONDS), "settings dialog test timed out");
            if (failure.get() != null) {
                throw new AssertionError("settings dialog validation failed", failure.get());
            }
        } finally {
            System.setProperty("user.home", originalHome);
        }
    }
}
