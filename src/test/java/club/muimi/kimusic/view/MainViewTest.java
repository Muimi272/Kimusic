package club.muimi.kimusic.view;

import club.muimi.kimusic.KimusicApplication;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.text.Font;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainViewTest {
    @Test
    void fxmlLoadsAndInjectsController() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.startup(() -> {
            try {
                try (var font = KimusicApplication.class.getResourceAsStream("fonts/SourceHanSansSC-Bold.ttf")) {
                    Font.loadFont(font, 13);
                }
                try (var font = KimusicApplication.class.getResourceAsStream("fonts/JetBrainsMono-SemiBold.ttf")) {
                    Font.loadFont(font, 13);
                }
                FXMLLoader loader = new FXMLLoader(KimusicApplication.class.getResource("view.fxml"));
                Parent root = loader.load();
                Scene scene = new Scene(root);
                scene.getStylesheets().add(KimusicApplication.class.getResource("kimusic.css").toExternalForm());
                root.applyCss();
                assertTrue(loader.getController() instanceof MainController);
                Label bodyLabel = (Label) root.lookup("#trackCountLabel");
                assertEquals("Source Han Sans SC", bodyLabel.getFont().getFamily());
                assertTrue(bodyLabel.getFont().getStyle().contains("Bold"), bodyLabel.getFont()::toString);
                assertNull(root.lookup(".brand"));
                assertNull(root.lookup(".brand-subtitle"));
                ImageView headerLogo = (ImageView) root.lookup("#headerLogo");
                assertNotNull(headerLogo);
                assertNotNull(headerLogo.getImage());
                assertEquals(headerLogo, ((HBox) headerLogo.getParent()).getChildren().getLast());
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                completed.countDown();
            }
        });

        assertTrue(completed.await(10, TimeUnit.SECONDS), "FXML loading timed out");
        if (failure.get() != null) {
            throw new AssertionError("FXML loading failed", failure.get());
        }
    }
}
