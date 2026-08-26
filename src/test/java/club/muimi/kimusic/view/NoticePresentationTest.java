package club.muimi.kimusic.view;

import club.muimi.kimusic.KimusicApplication;
import club.muimi.kimusic.service.NoticeService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoticePresentationTest {
    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(10, TimeUnit.SECONDS), "JavaFX startup timed out");
    }

    @Test
    void noticesRemainVisibleAndArePresentedSequentially() throws Exception {
        AtomicReference<Label> label = new AtomicReference<>();
        AtomicReference<NoticeService> service = new AtomicReference<>();

        runOnFxAndWait(() -> {
            FXMLLoader loader = new FXMLLoader(KimusicApplication.class.getResource("view.fxml"));
            Parent root;
            try {
                root = loader.load();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
            NoticeService notices = NoticeService.init();
            ((MainController) loader.getController()).initializeNotices(notices);
            label.set((Label) root.lookup("#noticeLabel"));
            service.set(notices);
        });

        Thread producer = Thread.ofVirtual().start(() -> {
            service.get().addInfo("第一条通知");
            service.get().addError("第二条通知");
        });
        producer.join();
        awaitCondition(() -> label.get().isVisible()
                && "第一条通知".equals(label.get().getText())
                && label.get().getOpacity() >= 0.99, 2_000);
        Thread.sleep(400);
        runOnFxAndWait(() -> {
            assertTrue(label.get().isVisible(), "notice disappeared before its display delay elapsed");
            assertEquals("第一条通知", label.get().getText());
            assertTrue(label.get().getStyleClass().contains("notice-info"));
            assertEquals(1, label.get().getOpacity(), 0.01, "notice became visually transparent");
        });
        awaitCondition(() -> "第二条通知".equals(label.get().getText())
                && label.get().getOpacity() >= 0.99, 4_000);
        Thread.sleep(400);
        runOnFxAndWait(() -> {
            assertTrue(label.get().isVisible(), "second notice did not receive its own display delay");
            assertTrue(label.get().getStyleClass().contains("notice-error"));
            assertEquals(1, label.get().getOpacity(), 0.01, "second notice became visually transparent");
        });
        awaitCondition(() -> !label.get().isVisible(), 4_000);
        runOnFxAndWait(() -> assertFalse(label.get().isVisible()));
    }

    private static void awaitCondition(CheckedBoolean condition, long timeoutMillis) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            AtomicReference<Boolean> result = new AtomicReference<>(false);
            runOnFxAndWait(() -> {
                try {
                    result.set(condition.get());
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });
            if (result.get()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("condition was not met before timeout");
    }

    private static void runOnFxAndWait(Runnable action) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(10, TimeUnit.SECONDS), "JavaFX action timed out");
        if (failure.get() != null) {
            throw new AssertionError("JavaFX action failed", failure.get());
        }
    }

    @FunctionalInterface
    private interface CheckedBoolean {
        boolean get() throws Exception;
    }
}
