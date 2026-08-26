package club.muimi.kimusic.view;

import club.muimi.kimusic.KimusicApplication;
import club.muimi.kimusic.status.Language;
import club.muimi.kimusic.service.LyricsService.LyricLine;
import club.muimi.kimusic.view.component.WaveformView;
import javafx.application.Platform;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.geometry.Pos;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Background;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.control.SplitPane;
import javafx.scene.text.Font;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainViewTest {
    @Test
    void fxmlLoadsAndInjectsController() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Runnable testBody = () -> {
            try {
                try (var font = KimusicApplication.class.getResourceAsStream("fonts/SourceHanSansSC-Bold.ttf")) {
                    Font.loadFont(font, 13);
                }
                try (var font = KimusicApplication.class.getResourceAsStream("fonts/JetBrainsMono-SemiBold.ttf")) {
                    Font.loadFont(font, 13);
                }
                FXMLLoader loader = new FXMLLoader(KimusicApplication.class.getResource("view.fxml"));
                Parent root = loader.load();
                Scene scene = new Scene(root, 1440, 560);
                scene.getStylesheets().add(KimusicApplication.class.getResource("kimusic.css").toExternalForm());
                root.applyCss();
                assertTrue(loader.getController() instanceof MainController);
                MainController controller = loader.getController();
                Label bodyLabel = (Label) root.lookup("#trackCountLabel");
                assertEquals("Source Han Sans SC", bodyLabel.getFont().getFamily());
                assertTrue(bodyLabel.getFont().getStyle().contains("Bold"), bodyLabel.getFont()::toString);
                assertNull(root.lookup(".brand"));
                assertNull(root.lookup(".brand-subtitle"));
                assertNull(root.lookup(".eyebrow"), "secondary subtitles should be removed from the main view");
                ImageView headerLogo = (ImageView) root.lookup("#headerLogo");
                assertNotNull(headerLogo);
                assertNotNull(headerLogo.getImage());
                assertEquals(headerLogo, ((HBox) headerLogo.getParent()).getChildren().getLast());

                @SuppressWarnings("unchecked")
                TableView<Path> trackTable = (TableView<Path>) root.lookup("#trackTable");
                @SuppressWarnings("unchecked")
                TreeView<Path> libraryTree = (TreeView<Path>) root.lookup("#libraryTree");
                @SuppressWarnings("unchecked")
                ListView<String> playlistList = (ListView<String>) root.lookup("#playlistList");
                @SuppressWarnings("unchecked")
                TableColumn<Path, String> titleColumn =
                        (TableColumn<Path, String>) trackTable.getColumns().get(1);
                TableCell<Path, String> titleCell = titleColumn.getCellFactory().call(titleColumn);
                TreeCell<Path> treeCell = libraryTree.getCellFactory().call(libraryTree);
                ListCell<String> playlistCell = playlistList.getCellFactory().call(playlistList);
                assertEquals(Pos.CENTER_LEFT, titleCell.getAlignment());
                assertEquals(Pos.CENTER_LEFT, treeCell.getAlignment());
                assertEquals(Pos.CENTER_LEFT, playlistCell.getAlignment());
                assertEquals(38, trackTable.getFixedCellSize(), 0.01);
                assertEquals(34, libraryTree.getFixedCellSize(), 0.01);
                assertEquals(34, playlistList.getFixedCellSize(), 0.01);

                ImageView coverImage = (ImageView) root.lookup("#coverImage");
                assertTrue(coverImage.isPreserveRatio(), "cover art must keep its aspect ratio while resizing");
                ImageView previousCoverImage = (ImageView) root.lookup("#previousCoverImage");
                assertNotNull(previousCoverImage, "cover transitions require a second artwork layer");
                assertTrue(previousCoverImage.isPreserveRatio());
                SplitPane split = (SplitPane) root.lookup("#workspaceSplit");
                StackPane coverPane = (StackPane) root.lookup("#coverPane");
                assertEquals(Cursor.HAND, coverPane.getCursor());
                assertNotNull(coverPane.getOnMouseClicked());
                assertNotNull(coverPane.getOnKeyPressed());
                split.setDividerPositions(0.52);
                root.layout();
                assertEquals(coverPane.getWidth(), coverPane.getHeight(), 0.01,
                        "cover container must stay square when the divider moves");
                split.setDividerPositions(0.38);
                root.layout();
                assertEquals(coverPane.getWidth(), coverPane.getHeight(), 0.01);
                split.setDividerPositions(0.68);
                root.layout();
                assertEquals(coverPane.getWidth(), coverPane.getHeight(), 0.01);

                VBox trackPane = (VBox) root.lookup("#trackPane");
                VBox visualPane = (VBox) root.lookup("#visualPane");
                double regularDividerPosition = split.getDividers().getFirst().getPosition();
                controller.setSongViewExpanded(true, false);
                root.layout();
                assertEquals(0, split.getDividers().getFirst().getPosition(), 0.01,
                        "expanded song view should hide the middle track preview");
                assertEquals(0, trackPane.getMinWidth(), 0.01);
                assertTrue(trackPane.isMouseTransparent());
                assertTrue(!trackPane.isVisible());
                assertTrue(visualPane.getStyleClass().contains("song-view-expanded"));
                assertTrue(split.getStyleClass().contains("song-view-expanded"));
                double expandedSplitWidth = split.getWidth();
                split.resize(Math.max(800, expandedSplitWidth - 120), split.getHeight());
                split.layout();
                assertEquals(0, trackPane.getWidth(), 0.01,
                        "expanded track preview must remain fully collapsed after a window resize");
                assertEquals(0, split.getDividers().getFirst().getPosition(), 0.01);
                split.resize(expandedSplitWidth, split.getHeight());
                controller.setSongViewExpanded(false, false);
                root.layout();
                assertEquals(regularDividerPosition, split.getDividers().getFirst().getPosition(), 0.01,
                        "restoring the song view should recover the previous divider position");
                assertEquals(420, trackPane.getMinWidth(), 0.01);
                assertTrue(!trackPane.isMouseTransparent());
                assertTrue(trackPane.isVisible());
                assertTrue(!visualPane.getStyleClass().contains("song-view-expanded"));
                assertTrue(!split.getStyleClass().contains("song-view-expanded"));

                WaveformView waveform = (WaveformView) root.lookup("#waveformView");
                double coverModeWaveformHeight = waveform.getHeight();
                Method toggleLyrics = MainController.class.getDeclaredMethod("toggleLyrics");
                toggleLyrics.setAccessible(true);
                toggleLyrics.invoke(controller);
                root.applyCss();
                root.layout();
                assertTrue(waveform.getHeight() >= waveform.minHeight(-1),
                        "lyrics mode must not compress the spectrum below its minimum height");
                assertEquals(coverModeWaveformHeight, waveform.getHeight(), 0.01,
                        "cover and lyrics modes should reserve the same spectrum height");

                @SuppressWarnings("unchecked")
                ListView<LyricLine> lyrics = (ListView<LyricLine>) root.lookup("#lyricsList");
                lyrics.getItems().setAll(new LyricLine(1,
                        "这是一行用于验证歌词自动换行能力的超长歌词文本，它应当始终限制在歌词组件的可用宽度以内并自然显示为多行内容。".repeat(3)));
                root.applyCss();
                root.layout();
                ListCell<?> lyricCell = lyrics.lookupAll(".list-cell").stream()
                        .filter(ListCell.class::isInstance)
                        .map(ListCell.class::cast)
                        .filter(cell -> cell.getItem() != null)
                        .findFirst().orElseThrow();
                assertTrue(lyricCell.getWidth() <= lyrics.getWidth(),
                        "a lyric cell must remain within the list viewport");
                assertTrue(lyricCell.getHeight() > lyricCell.getFont().getSize() * 2,
                        "long lyrics should grow vertically and wrap to multiple lines");

                WritableImage redArtwork = solidImage(Color.CRIMSON);
                WritableImage blueArtwork = solidImage(Color.DODGERBLUE);
                Method updateArtwork = MainController.class.getDeclaredMethod(
                        "updateArtwork", javafx.scene.image.Image.class);
                updateArtwork.setAccessible(true);
                updateArtwork.invoke(controller, redArtwork);
                updateArtwork.invoke(controller, blueArtwork);
                assertEquals(redArtwork, previousCoverImage.getImage(),
                        "the outgoing artwork must remain in the transition layer");
                assertEquals(blueArtwork, coverImage.getImage());
                assertNotNull(((VBox) root.lookup("#visualPane")).getBackground(),
                        "artwork changes should establish an animated color background");

                Field artworkToneAnimation = MainController.class.getDeclaredField("artworkToneAnimation");
                artworkToneAnimation.setAccessible(true);
                Timeline toneAnimation = (Timeline) artworkToneAnimation.get(controller);
                if (toneAnimation != null) {
                    toneAnimation.stop();
                }
                Method applyThemeStyles = MainController.class.getDeclaredMethod("applyThemeStyles", boolean.class);
                applyThemeStyles.setAccessible(true);
                Method updateSongPresentation = MainController.class.getDeclaredMethod(
                        "updateSongPresentation", Color.class);
                updateSongPresentation.setAccessible(true);
                updateSongPresentation.invoke(controller, Color.BLACK);
                Background songBackground = visualPane.getBackground();
                Color songBackgroundColor = (Color) songBackground.getFills().getFirst().getFill();
                applyThemeStyles.invoke(controller, true);
                root.applyCss();
                assertEquals(songBackgroundColor,
                        visualPane.getBackground().getFills().getFirst().getFill(),
                        "theme changes must not replace the song page background");
                assertEquals(1, visualPane.getOpacity(), 0.01,
                        "the song page must not participate in the theme fade");
                applyThemeStyles.invoke(controller, false);
                root.applyCss();
                assertEquals(songBackgroundColor,
                        visualPane.getBackground().getFills().getFirst().getFill());
                Label visualTitle = (Label) root.lookup("#visualTitleLabel");
                Label visualPath = (Label) root.lookup("#visualPathLabel");
                assertEquals(Color.web("#F4FAF8"), visualTitle.getTextFill(),
                        "dark song backgrounds must use light title text regardless of app theme");
                assertEquals(Color.web("#F4FAF8"), visualPath.getTextFill());
                assertEquals(Color.web("#F4FAF8"), lyricCell.getTextFill());
                Background darkSharedBackground = split.getBackground();
                assertEquals(darkSharedBackground, visualPane.getBackground(),
                        "the divider carrier and song page must share the same background object");
                updateSongPresentation.invoke(controller, Color.WHITE);
                root.applyCss();
                assertEquals(Color.web("#16201E"), visualTitle.getTextFill(),
                        "light song backgrounds must use dark title text regardless of app theme");
                assertEquals(Color.web("#16201E"), visualPath.getTextFill());
                assertEquals(Color.web("#16201E"), lyricCell.getTextFill());
                assertNotEquals(darkSharedBackground, split.getBackground(),
                        "the divider color must follow the song page background tone");
                assertEquals(split.getBackground(), visualPane.getBackground(),
                        "the updated divider and song page must remain pixel-identical");
                Method themeTransitionNodes = MainController.class.getDeclaredMethod("themeTransitionNodes");
                themeTransitionNodes.setAccessible(true);
                @SuppressWarnings("unchecked")
                List<Node> transitionNodes = (List<Node>) themeTransitionNodes.invoke(controller);
                assertTrue(!transitionNodes.contains(visualPane),
                        "the song page must be excluded from theme transitions");
                assertTrue(!transitionNodes.contains(trackPane),
                        "theme fades must keep the track pane background opaque");
                assertTrue(transitionNodes.containsAll(trackPane.getChildren()),
                        "theme fades should target track content instead of its background");
                assertTrue(!transitionNodes.contains(root.lookup("#noticeLabel")),
                        "theme fades must not interfere with notice animations");
                Field themeAnimation = MainController.class.getDeclaredField("themeAnimation");
                assertEquals(SequentialTransition.class, themeAnimation.getType(),
                        "theme changes must use separate fade-out and fade-in phases");

                controller.applyLanguage(Language.ENGLISH);
                Label viewTitle = (Label) root.lookup("#viewTitleLabel");
                assertEquals("Music Library", viewTitle.getText());
                controller.applyLanguage(Language.CHINESE);
                assertEquals("音乐资料库", viewTitle.getText());

                Method writeTrackLabels = MainController.class.getDeclaredMethod("writeTrackLabels", Path.class);
                writeTrackLabels.setAccessible(true);
                writeTrackLabels.invoke(controller, Path.of("C:/Music/My Song.flac"));
                controller.applyLanguage(Language.ENGLISH);
                Label nowTitle = (Label) root.lookup("#nowTitleLabel");
                assertEquals("My Song", nowTitle.getText(),
                        "changing language must not replace a current track title with the idle placeholder");
                assertEquals(12.5, MainController.lyricSeekTarget(new LyricLine(12.5, "line"))
                        .orElseThrow());
                assertTrue(MainController.lyricSeekTarget(
                        new LyricLine(Double.POSITIVE_INFINITY, "untimed")).isEmpty());

                TreeItem<Path> disclosureRoot = new TreeItem<>(Path.of("C:/Music"));
                disclosureRoot.setExpanded(true);
                disclosureRoot.getChildren().add(new TreeItem<>(Path.of("C:/Music/Song.flac")));
                libraryTree.setShowRoot(true);
                libraryTree.setRoot(disclosureRoot);
                root.applyCss();
                root.layout();
                Region disclosure = (Region) libraryTree.lookup(".tree-disclosure-node");
                assertNotNull(disclosure);
                assertEquals(34, disclosure.getHeight(), 0.01,
                        "folder disclosure controls must use the full centered row height");
                Node arrow = disclosure.lookup(".arrow");
                assertNotNull(arrow);
                double arrowCenterY = arrow.getBoundsInParent().getMinY()
                        + arrow.getBoundsInParent().getHeight() / 2;
                assertEquals(disclosure.getHeight() / 2, arrowCenterY, 0.51,
                        "folder arrows must be vertically centered in their row");
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                completed.countDown();
            }
        };
        try {
            Platform.startup(testBody);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(testBody);
        }

        assertTrue(completed.await(10, TimeUnit.SECONDS), "FXML loading timed out");
        if (failure.get() != null) {
            throw new AssertionError("FXML loading failed", failure.get());
        }
    }

    private static WritableImage solidImage(Color color) {
        WritableImage image = new WritableImage(4, 4);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                image.getPixelWriter().setColor(x, y, color);
            }
        }
        return image;
    }
}
