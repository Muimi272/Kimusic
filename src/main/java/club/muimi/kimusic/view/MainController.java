package club.muimi.kimusic.view;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import club.muimi.kimusic.AppContext;
import club.muimi.kimusic.service.FileTreeService;
import club.muimi.kimusic.service.LyricsService.LyricLine;
import club.muimi.kimusic.status.PlayMode;
import club.muimi.kimusic.status.VisualizationMode;
import club.muimi.kimusic.view.component.SvgIcon;
import club.muimi.kimusic.view.component.SmoothScrollSupport;
import club.muimi.kimusic.view.component.WaveformView;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.control.SplitPane;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class MainController {
    private static final double SIDEBAR_WIDTH = 292;
    private static final PseudoClass PLAYING = PseudoClass.getPseudoClass("playing");

    @FXML private BorderPane root;
    @FXML private VBox sidebar;
    @FXML private Button importButton;
    @FXML private Button themeButton;
    @FXML private SvgIcon themeIcon;
    @FXML private Button playPauseButton;
    @FXML private SvgIcon playPauseIcon;
    @FXML private Button lyricsButton;
    @FXML private Button queueButton;
    @FXML private Button modeButton;
    @FXML private SvgIcon modeIcon;
    @FXML private TreeView<Path> libraryTree;
    @FXML private ListView<String> playlistList;
    @FXML private TableView<Path> trackTable;
    @FXML private TableColumn<Path, String> indexColumn;
    @FXML private TableColumn<Path, String> titleColumn;
    @FXML private TableColumn<Path, String> folderColumn;
    @FXML private ListView<LyricLine> lyricsList;
    @FXML private ListView<Path> queueList;
    @FXML private Label queueCountLabel;
    @FXML private Label viewTitleLabel;
    @FXML private Label trackCountLabel;
    @FXML private Label nowTitleLabel;
    @FXML private Label nowPathLabel;
    @FXML private Label visualTitleLabel;
    @FXML private Label visualPathLabel;
    @FXML private Label currentTimeLabel;
    @FXML private Label totalTimeLabel;
    @FXML private Label noticeLabel;
    @FXML private Slider progressSlider;
    @FXML private Slider volumeSlider;
    @FXML private SplitPane workspaceSplit;
    @FXML private VBox visualPane;
    @FXML private StackPane coverPane;
    @FXML private StackPane visualStack;
    @FXML private ImageView coverImage;
    @FXML private VBox coverPlaceholder;
    @FXML private VBox lyricsPane;
    @FXML private VBox queueDrawer;
    @FXML private WaveformView waveformView;

    private final ObservableList<Path> libraryTracks = FXCollections.observableArrayList();
    private final ObservableList<Path> displayedTracks = FXCollections.observableArrayList();
    private final ObservableList<LyricLine> displayedLyrics = FXCollections.observableArrayList();
    private AppContext context;
    private boolean sidebarExpanded = true;
    private boolean lyricsVisible;
    private boolean queueVisible;
    private boolean updatingProgress;
    private Timeline sidebarAnimation;
    private TranslateTransition queueAnimation;
    private FadeTransition themeAnimation;
    private Timeline libraryWatch;
    private Timeline lyricScrollAnimation;
    private int lyricsFontSize = 14;
    private boolean libraryLoaded;
    private boolean libraryScanInFlight;

    @FXML
    private void initialize() {
        configureTable();
        configureLyrics();
        configureModeSelector();
        configureWorkspaceSplit();
        trackTable.setItems(displayedTracks);
        lyricsList.setItems(displayedLyrics);
        libraryTree.setShowRoot(false);
        queueDrawer.setVisible(false);
        queueDrawer.setManaged(false);
        libraryTree.setCellFactory(tree -> new TreeCell<>() {
            @Override
            protected void updateItem(Path path, boolean empty) {
                super.updateItem(path, empty);
                setText(empty || path == null ? null : displayName(path));
            }
        });
        SmoothScrollSupport.install(libraryTree);
        SmoothScrollSupport.install(playlistList);
        SmoothScrollSupport.install(trackTable);
        SmoothScrollSupport.install(lyricsList);
        SmoothScrollSupport.install(queueList);
    }

    public void initializeContext(AppContext appContext) {
        context = appContext;
        applyTheme(context.isDarkTheme());
        populateLibraryTree();
        populatePlaylists();
        bindPlayer();
        configureInteractions();
        configureQueue();
        if (context.startupTrack() != null) {
            Platform.runLater(() -> context.music().open(context.startupTrack()));
        }
        refreshLibrary();
        libraryWatch = new Timeline(new KeyFrame(Duration.seconds(5), event -> refreshLibrary()));
        libraryWatch.setCycleCount(Timeline.INDEFINITE);
        libraryWatch.play();
        showNextNotice();
        Platform.runLater(() -> {
            coverImage.fitWidthProperty().bind(coverPane.widthProperty());
            coverImage.fitHeightProperty().bind(coverPane.heightProperty());
            lyricsPane.prefWidthProperty().bind(visualStack.widthProperty());
            lyricsList.prefWidthProperty().bind(visualStack.widthProperty());
            lyricsList.setMaxWidth(Double.MAX_VALUE);
            installButtonMotion();
        });
    }

    @FXML
    private void showImportMenu() {
        MenuItem files = new MenuItem("导入音频文件");
        files.setOnAction(event -> importFiles());
        MenuItem folder = new MenuItem("导入音乐文件夹");
        folder.setOnAction(event -> importFolder());
        ContextMenu menu = new ContextMenu(files, folder);
        menu.show(importButton, Side.BOTTOM, -142, 6);
    }

    private void importFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择音频文件");
        chooser.getExtensionFilters().add(new ExtensionFilter("音频文件",
                FileTreeService.supportedExtensions().stream().sorted()
                        .map(extension -> "*." + extension).toArray(String[]::new)));
        List<File> files = chooser.showOpenMultipleDialog(root.getScene().getWindow());
        if (files == null) {
            return;
        }
        boolean changed = false;
        for (File file : files) {
            changed |= context.library().addRootFile(file);
        }
        if (changed) {
            libraryChanged();
        }
    }

    private void importFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择音乐文件夹");
        File directory = chooser.showDialog(root.getScene().getWindow());
        if (directory != null && context.library().addRootFile(directory)) {
            libraryChanged();
        }
    }

    @FXML
    private void removeLibraryRoot() {
        TreeItem<Path> selected = libraryTree.getSelectionModel().getSelectedItem();
        if (selected != null && selected.getParent() == libraryTree.getRoot()
                && context.library().removeRootFile(selected.getValue().toFile())) {
            libraryChanged();
        }
    }

    @FXML
    private void createPlaylist() {
        TextInputDialog dialog = new TextInputDialog();
        styleDialog(dialog);
        dialog.initOwner(root.getScene().getWindow());
        dialog.setTitle("新建歌单");
        dialog.setHeaderText(null);
        dialog.setContentText("歌单名称");
        dialog.showAndWait().ifPresent(name -> {
            if (context.playlists().create(name)) {
                populatePlaylists();
                playlistList.getSelectionModel().select(name.strip());
                context.save();
            } else {
                context.notices().addNotice("歌单名称为空或已经存在。");
            }
        });
    }

    @FXML
    private void deletePlaylist() {
        String selected = playlistList.getSelectionModel().getSelectedItem();
        if (selected != null && context.playlists().remove(selected)) {
            populatePlaylists();
            showLibrary();
            context.save();
        }
    }

    @FXML
    private void addToPlaylist() {
        Path track = trackTable.getSelectionModel().getSelectedItem();
        if (track == null) {
            context.notices().addNotice("请先选择一首曲目。");
            return;
        }
        List<String> names = context.playlists().names();
        if (names.isEmpty()) {
            context.notices().addNotice("请先新建歌单。");
            return;
        }
        String selected = playlistList.getSelectionModel().getSelectedItem();
        ChoiceDialog<String> dialog = new ChoiceDialog<>(selected == null ? names.getFirst() : selected, names);
        styleDialog(dialog);
        // ChoiceDialog supplies a generic question-mark graphic; Kimusic uses
        // icon-only controls, so keep this prompt visually quiet.
        dialog.setGraphic(null);
        dialog.getDialogPane().setGraphic(null);
        dialog.initOwner(root.getScene().getWindow());
        dialog.setTitle("加入歌单");
        dialog.setHeaderText(titleOf(track));
        dialog.setContentText("目标歌单");
        dialog.showAndWait().ifPresent(playlist -> {
            if (context.playlists().addTrack(playlist, track)) {
                context.notices().addNotice("已加入「" + playlist + "」。");
                if (playlist.equals(playlistList.getSelectionModel().getSelectedItem())) {
                    showPlaylist(playlist);
                }
                context.save();
            } else {
                context.notices().addNotice("曲目已经在该歌单中。");
            }
        });
    }

    @FXML
    private void showLibraryView() {
        showLibrary();
    }

    @FXML
    private void toggleMenu() {
        sidebarExpanded = !sidebarExpanded;
        if (sidebarAnimation != null) {
            sidebarAnimation.stop();
        }
        if (sidebarExpanded) {
            sidebar.setManaged(true);
            sidebar.setVisible(true);
        }
        double target = sidebarExpanded ? SIDEBAR_WIDTH : 0;
        sidebarAnimation = new Timeline(new KeyFrame(Duration.millis(230), event -> {
            if (!sidebarExpanded) {
                sidebar.setVisible(false);
                sidebar.setManaged(false);
            }
        },
                new KeyValue(sidebar.minWidthProperty(), target, Interpolator.EASE_BOTH),
                new KeyValue(sidebar.prefWidthProperty(), target, Interpolator.EASE_BOTH),
                new KeyValue(sidebar.maxWidthProperty(), target, Interpolator.EASE_BOTH),
                new KeyValue(sidebar.opacityProperty(), sidebarExpanded ? 1 : 0, Interpolator.EASE_BOTH)));
        sidebarAnimation.play();
    }

    @FXML
    private void toggleTheme() {
        applyTheme(!context.isDarkTheme());
        context.save();
    }

    @FXML
    private void toggleLyrics() {
        lyricsVisible = !lyricsVisible;
        lyricsButton.getStyleClass().remove("active");
        if (lyricsVisible) {
            lyricsButton.getStyleClass().add("active");
        }
        Node outgoing = lyricsVisible ? coverPane : lyricsPane;
        Node incoming = lyricsVisible ? lyricsPane : coverPane;
        FadeTransition fadeOut = new FadeTransition(Duration.millis(150), outgoing);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(event -> {
            outgoing.setVisible(false);
            outgoing.setManaged(false);
            incoming.setManaged(true);
            incoming.setVisible(true);
            incoming.setOpacity(0);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(220), incoming);
            fadeIn.setToValue(1);
            fadeIn.play();
        });
        fadeOut.play();
    }

    @FXML
    private void toggleQueue() {
        queueVisible = !queueVisible;
        queueButton.getStyleClass().remove("active");
        if (queueVisible) {
            queueButton.getStyleClass().add("active");
            queueDrawer.setManaged(true);
            queueDrawer.setVisible(true);
        }
        if (queueAnimation != null) {
            queueAnimation.stop();
        }
        queueAnimation = new TranslateTransition(Duration.millis(260), queueDrawer);
        queueAnimation.setInterpolator(Interpolator.EASE_BOTH);
        queueAnimation.setFromY(queueDrawer.getTranslateY());
        queueAnimation.setToY(queueVisible ? 0 : Math.max(420, queueDrawer.getHeight() + 40));
        queueAnimation.setOnFinished(event -> {
            if (!queueVisible) {
                queueDrawer.setVisible(false);
                queueDrawer.setManaged(false);
            }
        });
        queueAnimation.play();
    }

    @FXML
    private void togglePlayback() {
        context.music().togglePlayback();
    }

    @FXML
    private void previousTrack() {
        context.music().previous();
    }

    @FXML
    private void nextTrack() {
        context.music().next();
    }

    @FXML
    private void openSettings() {
        Dialog<ButtonType> dialog = new Dialog<>();
        styleDialog(dialog);
        dialog.initOwner(root.getScene().getWindow());
        dialog.setTitle("设置");
        ButtonType apply = new ButtonType("应用", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(apply, ButtonType.CANCEL);

        ImageView logo = new ImageView(new Image(
                MainController.class.getResourceAsStream("/club/muimi/kimusic/logo.png")));
        logo.setFitWidth(34);
        logo.setFitHeight(34);
        logo.setPreserveRatio(true);
        VBox headingText = new VBox(1, styledLabel("设置", "settings-title"),
                styledLabel("KIMUSIC PREFERENCES", "eyebrow"));
        HBox heading = new HBox(12, logo, headingText);
        heading.setAlignment(Pos.CENTER_LEFT);
        heading.getStyleClass().add("settings-heading");

        ToggleButton lightTheme = segmentedButton("浅色");
        ToggleButton darkTheme = segmentedButton("深色");
        ToggleGroup themeGroup = new ToggleGroup();
        lightTheme.setToggleGroup(themeGroup);
        darkTheme.setToggleGroup(themeGroup);
        (context.isDarkTheme() ? darkTheme : lightTheme).setSelected(true);
        HBox themeSelector = segmentedControl(lightTheme, darkTheme);

        ToggleButton waveformMode = segmentedButton("波形");
        ToggleButton spectrumMode = segmentedButton("频谱");
        ToggleGroup visualizationGroup = new ToggleGroup();
        waveformMode.setToggleGroup(visualizationGroup);
        spectrumMode.setToggleGroup(visualizationGroup);
        (context.getVisualizationMode() == VisualizationMode.SPECTRUM
                ? spectrumMode : waveformMode).setSelected(true);
        HBox visualizationSelector = segmentedControl(waveformMode, spectrumMode);

        Slider settingVolume = new Slider(0, 100, context.music().volumeProperty().get() * 100);
        Label volumeValue = styledLabel(Math.round(settingVolume.getValue()) + "%", "settings-value");
        settingVolume.valueProperty().addListener((observable, oldValue, value) ->
                volumeValue.setText(Math.round(value.doubleValue()) + "%"));
        HBox volumeHeading = settingRow("默认音量", volumeValue);

        VBox appearance = settingsSection("外观", settingRow("界面主题", themeSelector));
        VBox visualization = settingsSection("可视化", settingRow("播放画面", visualizationSelector));
        VBox playback = settingsSection("播放", volumeHeading, settingVolume);
        Slider lyricSize = new Slider(11, 24, lyricsFontSize);
        lyricSize.setBlockIncrement(1);
        Label lyricSizeValue = styledLabel(Math.round(lyricSize.getValue()) + " px", "settings-value");
        lyricSize.valueProperty().addListener((observable, oldValue, value) ->
                lyricSizeValue.setText(Math.round(value.doubleValue()) + " px"));
        VBox lyrics = settingsSection("歌词", settingRow("歌词字号", lyricSizeValue), lyricSize);
        VBox content = new VBox(14, heading, appearance, visualization, playback, lyrics);
        content.getStyleClass().add("settings-content");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(520);
        dialog.showAndWait().filter(apply::equals).ifPresent(result -> {
            applyTheme(darkTheme.isSelected());
            VisualizationMode mode = spectrumMode.isSelected()
                    ? VisualizationMode.SPECTRUM : VisualizationMode.WAVEFORM;
            context.setVisualizationMode(mode);
            waveformView.setMode(mode);
            context.music().volumeProperty().set(settingVolume.getValue() / 100);
            lyricsFontSize = (int) Math.round(lyricSize.getValue());
            applyLyricsFontSize();
            context.save();
        });
    }

    private Label styledLabel(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private ToggleButton segmentedButton(String text) {
        ToggleButton button = new ToggleButton(text);
        button.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(button, Priority.ALWAYS);
        button.getStyleClass().add("segment-button");
        return button;
    }

    private HBox segmentedControl(ToggleButton... buttons) {
        HBox control = new HBox(buttons);
        control.setPrefWidth(210);
        control.getStyleClass().add("segmented-control");
        return control;
    }

    private HBox settingRow(String title, Node control) {
        Label label = styledLabel(title, "settings-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(16, label, spacer, control);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("settings-row");
        return row;
    }

    private VBox settingsSection(String title, Node... children) {
        VBox section = new VBox(11);
        section.getStyleClass().add("settings-section");
        section.getChildren().add(styledLabel(title, "settings-section-title"));
        section.getChildren().addAll(children);
        return section;
    }

    private void styleDialog(Dialog<?> dialog) {
        dialog.getDialogPane().getStylesheets().add(
                MainController.class.getResource("/club/muimi/kimusic/kimusic.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().addAll("kimusic-dialog",
                context != null && context.isDarkTheme() ? "theme-dark" : "theme-light");
    }

    private void configureTable() {
        trackTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        indexColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                Integer.toString(displayedTracks.indexOf(cell.getValue()) + 1)));
        indexColumn.setStyle("-fx-alignment: CENTER;");
        titleColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(titleOf(cell.getValue())));
        folderColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                cell.getValue().getParent() == null ? "" : cell.getValue().getParent().toString()));
        folderColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                setTooltip(empty || value == null ? null : new Tooltip(value));
            }
        });
        trackTable.setRowFactory(table -> {
            TableRow<Path> row = new TableRow<>() {
                @Override
                protected void updateItem(Path path, boolean empty) {
                    super.updateItem(path, empty);
                    pseudoClassStateChanged(PLAYING, !empty && context != null
                            && path.equals(context.music().currentTrackProperty().get()));
                }
            };
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    playTrack(row.getItem());
                }
            });
            MenuItem add = new MenuItem("加入歌单");
            add.setOnAction(event -> addToPlaylist());
            MenuItem remove = new MenuItem("从当前歌单移除");
            remove.setOnAction(event -> removeFromPlaylist());
            ContextMenu menu = new ContextMenu(add, remove);
            menu.setOnShowing(event -> remove.setDisable(playlistList.getSelectionModel().getSelectedItem() == null));
            row.setContextMenu(menu);
            return row;
        });
    }

    private void removeFromPlaylist() {
        Path track = trackTable.getSelectionModel().getSelectedItem();
        String playlist = playlistList.getSelectionModel().getSelectedItem();
        if (track != null && playlist != null && context.playlists().removeTrack(playlist, track)) {
            showPlaylist(playlist);
            context.save();
        }
    }

    private void configureLyrics() {
        lyricsList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(LyricLine line, boolean empty) {
                super.updateItem(line, empty);
                setText(empty || line == null ? null : line.text());
                setWrapText(true);
                setMaxWidth(Double.MAX_VALUE);
                setStyle("-fx-font-size: " + (isSelected() ? lyricsFontSize + 2 : lyricsFontSize) + "px;");
                setMouseTransparent(true);
            }
        });
    }

    private void applyLyricsFontSize() {
        lyricsList.refresh();
    }

    private void configureModeSelector() {
        updateModeIcon(PlayMode.SEQUENTIAL);
    }

    private void configureWorkspaceSplit() {
        workspaceSplit.setDividerPositions(0.60);
        workspaceSplit.widthProperty().addListener((observable, oldWidth, width) ->
                clampWorkspaceDivider(width.doubleValue()));
        workspaceSplit.getDividers().getFirst().positionProperty().addListener(
                (observable, oldPosition, position) -> clampWorkspaceDivider(workspaceSplit.getWidth()));
    }

    private void clampWorkspaceDivider(double width) {
        if (width <= 0 || workspaceSplit.getDividers().isEmpty()) {
            return;
        }
        double minimum = 420 / width;
        double maximum = 1 - 390 / width;
        if (minimum > maximum) {
            return;
        }
        double current = workspaceSplit.getDividers().getFirst().getPosition();
        double clamped = Math.max(minimum, Math.min(maximum, current));
        if (Math.abs(clamped - current) > 0.0001) {
            workspaceSplit.setDividerPosition(0, clamped);
        }
    }

    @FXML
    private void cyclePlayMode() {
        PlayMode current = context.music().playModeProperty().get();
        PlayMode[] modes = PlayMode.values();
        PlayMode next = modes[(current.ordinal() + 1) % modes.length];
        context.music().playModeProperty().set(next);
        context.save();
    }

    private void updateModeIcon(PlayMode mode) {
        if (mode == null || modeIcon == null) {
            return;
        }
        String resource = switch (mode) {
            case SEQUENTIAL -> "/club/muimi/kimusic/icons/mode-sequential.svg";
            case REPEAT_ALL -> "/club/muimi/kimusic/icons/mode-repeat.svg";
            case REPEAT_ONE -> "/club/muimi/kimusic/icons/mode-repeat-one.svg";
            case SHUFFLE -> "/club/muimi/kimusic/icons/mode-shuffle.svg";
        };
        String label = modeLabel(mode);
        modeIcon.setResource(resource);
        modeButton.setAccessibleText(label + "播放");
        modeButton.setTooltip(new Tooltip(label + "播放"));
    }

    private void configureInteractions() {
        playlistList.getSelectionModel().selectedItemProperty().addListener((observable, oldName, name) -> {
            if (name != null) {
                showPlaylist(name);
            }
        });
        libraryTree.getSelectionModel().selectedItemProperty().addListener((observable, oldItem, item) -> {
            if (item != null && item.getValue() != null && FileTreeService.isAudioFile(item.getValue())) {
                showLibrary();
                trackTable.getSelectionModel().select(item.getValue());
            }
        });
        libraryTree.setOnMouseClicked(event -> {
            TreeItem<Path> item = libraryTree.getSelectionModel().getSelectedItem();
            if (event.getClickCount() == 2 && item != null && FileTreeService.isAudioFile(item.getValue())) {
                showLibrary();
                playTrack(item.getValue());
            }
        });
        progressSlider.setCursor(Cursor.OPEN_HAND);
        progressSlider.setOnMousePressed(event -> progressSlider.setCursor(Cursor.CLOSED_HAND));
        progressSlider.setOnMouseReleased(event -> {
            progressSlider.setCursor(Cursor.OPEN_HAND);
            seekFromSlider();
        });
        progressSlider.setOnMouseExited(event -> {
            if (!event.isPrimaryButtonDown()) {
                progressSlider.setCursor(Cursor.OPEN_HAND);
            }
        });
        progressSlider.valueChangingProperty().addListener((observable, wasChanging, changing) -> {
            if (!changing) {
                seekFromSlider();
            }
        });
    }

    private void configureQueue() {
        queueList.setItems(context.music().getQueue());
        queueList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Path path, boolean empty) {
                super.updateItem(path, empty);
                setText(empty || path == null ? null : titleOf(path));
                pseudoClassStateChanged(PLAYING, !empty && path != null
                        && path.equals(context.music().currentTrackProperty().get()));
            }
        });
        queueList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                int index = queueList.getSelectionModel().getSelectedIndex();
                context.music().playQueueIndex(index);
            }
        });
        MenuItem remove = new MenuItem("从当前队列移除");
        remove.setOnAction(event -> context.music().removeQueueIndex(
                queueList.getSelectionModel().getSelectedIndex()));
        queueList.setContextMenu(new ContextMenu(remove));
        context.music().getQueue().addListener((ListChangeListener<Path>) change -> updateQueueCount());
        updateQueueCount();
    }

    private void bindPlayer() {
        var music = context.music();
        music.playModeProperty().addListener((observable, oldMode, mode) -> updateModeIcon(mode));
        updateModeIcon(music.playModeProperty().get());
        volumeSlider.valueProperty().bindBidirectional(music.volumeProperty());
        music.playingProperty().addListener((observable, oldValue, isPlaying) -> {
            playPauseIcon.setResource(isPlaying
                    ? "/club/muimi/kimusic/icons/pause.svg"
                    : "/club/muimi/kimusic/icons/play.svg");
            playPauseButton.setAccessibleText(isPlaying ? "暂停" : "播放");
        });
        music.currentTrackProperty().addListener((observable, oldTrack, track) -> {
            updateCurrentTrack(track);
            updateQueueCount();
            trackTable.refresh();
            queueList.refresh();
        });
        music.currentSecondsProperty().addListener((observable, oldValue, seconds) -> {
            currentTimeLabel.setText(formatTime(seconds.doubleValue()));
            updateProgress(seconds.doubleValue(), music.durationSecondsProperty().get());
            updateActiveLyric(seconds.doubleValue());
        });
        music.durationSecondsProperty().addListener((observable, oldValue, seconds) -> {
            totalTimeLabel.setText(formatTime(seconds.doubleValue()));
            updateProgress(music.currentSecondsProperty().get(), seconds.doubleValue());
        });
        music.artworkProperty().addListener((observable, oldImage, image) -> updateArtwork(image));
        music.visualizationProperty().addListener((observable, oldFrame, frame) -> waveformView.push(frame));
        waveformView.setMode(context.getVisualizationMode());
        if (music.currentTrackProperty().get() != null) {
            updateCurrentTrack(music.currentTrackProperty().get());
        }
        volumeSlider.setValue(music.volumeProperty().get());
    }

    private void libraryChanged() {
        populateLibraryTree();
        refreshLibrary();
        context.save();
    }

    private void populateLibraryTree() {
        TreeItem<Path> hiddenRoot = new TreeItem<>();
        for (File rootFile : context.library().getRootFiles()) {
            hiddenRoot.getChildren().add(new LazyPathTreeItem(rootFile.toPath()));
        }
        libraryTree.setRoot(hiddenRoot);
    }

    private void populatePlaylists() {
        playlistList.getItems().setAll(context.playlists().names());
    }

    private void refreshLibrary() {
        if (libraryScanInFlight) {
            return;
        }
        libraryScanInFlight = true;
        if (!libraryLoaded) {
            trackCountLabel.setText("扫描中…");
        }
        Task<List<Path>> scanTask = new Task<>() {
            @Override protected List<Path> call() { return context.library().scanAudioFiles(); }
        };
        scanTask.setOnSucceeded(event -> {
            libraryScanInFlight = false;
            List<Path> scanned = scanTask.getValue();
            boolean changed = !scanned.equals(libraryTracks);
            libraryTracks.setAll(scanned);
            libraryLoaded = true;
            if (changed) {
                populateLibraryTree();
            }
            if (playlistList.getSelectionModel().getSelectedItem() == null) {
                showLibrary();
            }
        });
        scanTask.setOnFailed(event -> {
            libraryScanInFlight = false;
            if (!libraryLoaded) {
                trackCountLabel.setText("扫描失败");
            }
            context.notices().addNotice("扫描音乐资料库失败。");
        });
        Thread.ofPlatform().daemon().name("kimusic-library-scan").start(scanTask);
    }

    private void showLibrary() {
        playlistList.getSelectionModel().clearSelection();
        displayedTracks.setAll(libraryTracks);
        viewTitleLabel.setText("音乐资料库");
        trackCountLabel.setText(displayedTracks.size() + " 首");
    }

    private void showPlaylist(String name) {
        displayedTracks.setAll(context.playlists().tracks(name));
        viewTitleLabel.setText(name);
        trackCountLabel.setText(displayedTracks.size() + " 首");
    }

    private void playTrack(Path track) {
        context.music().setQueue(new ArrayList<>(displayedTracks), track);
        updateQueueCount();
        context.music().open(track);
        context.save();
    }

    private void updateCurrentTrack(Path track) {
        waveformView.clear();
        if (track == null) {
            nowTitleLabel.setText("未在播放");
            nowPathLabel.setText("");
            visualTitleLabel.setText("选择一首音乐");
            visualPathLabel.setText("");
            displayedLyrics.clear();
            updateArtwork(null);
            return;
        }
        String title = titleOf(track);
        String location = track.getParent() == null ? "" : track.getParent().toString();
        nowTitleLabel.setText(title);
        nowPathLabel.setText(location);
        visualTitleLabel.setText(title);
        visualPathLabel.setText(location);
        displayedLyrics.setAll(context.lyrics().load(track));
        lyricsList.getSelectionModel().clearSelection();
    }

    private void updateArtwork(Image image) {
        coverImage.setImage(image);
        coverImage.setPreserveRatio(false);
        coverImage.setVisible(image != null);
        coverPlaceholder.setVisible(image == null);
        coverPlaceholder.setManaged(image == null);
        if (image == null) {
            visualPane.setStyle("");
            visualPane.getStyleClass().removeAll("tone-light", "tone-dark");
            return;
        }
        PixelReader reader = image.getPixelReader();
        if (reader == null) {
            return;
        }
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        long red = 0, green = 0, blue = 0, count = 0;
        int step = Math.max(1, Math.min(width, height) / 32);
        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                Color pixel = reader.getColor(x, y);
                red += Math.round(pixel.getRed() * 255);
                green += Math.round(pixel.getGreen() * 255);
                blue += Math.round(pixel.getBlue() * 255);
                count++;
            }
        }
        int r = (int) Math.max(0, Math.min(255, red / Math.max(1, count) * 0.72));
        int g = (int) Math.max(0, Math.min(255, green / Math.max(1, count) * 0.72));
        int b = (int) Math.max(0, Math.min(255, blue / Math.max(1, count) * 0.72));
        visualPane.setStyle("-fx-background-color: rgb(" + r + ", " + g + ", " + b + ");");
        double luminance = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0;
        visualPane.getStyleClass().removeAll("tone-light", "tone-dark");
        visualPane.getStyleClass().add(luminance < 0.48 ? "tone-dark" : "tone-light");
    }

    private void updateActiveLyric(double seconds) {
        int index = context.lyrics().activeLineIndex(displayedLyrics, seconds);
        if (index >= 0 && index != lyricsList.getSelectionModel().getSelectedIndex()) {
            lyricsList.getSelectionModel().select(index);
            smoothScrollLyrics(index);
        }
    }

    private void smoothScrollLyrics(int index) {
        Platform.runLater(() -> {
            ScrollBar vertical = lyricsList.lookupAll(".scroll-bar").stream()
                    .filter(ScrollBar.class::isInstance)
                    .map(ScrollBar.class::cast)
                    .filter(bar -> bar.getOrientation() == javafx.geometry.Orientation.VERTICAL)
                    .findFirst().orElse(null);
            if (vertical == null || vertical.getMax() <= 0) {
                lyricsList.scrollTo(Math.max(0, index - 3));
                return;
            }
            double target = Math.max(0, Math.min(1,
                    (index - 2.0) / Math.max(1, displayedLyrics.size() - 1)));
            if (lyricScrollAnimation != null) {
                lyricScrollAnimation.stop();
            }
            lyricScrollAnimation = new Timeline(
                    new KeyFrame(Duration.millis(260),
                            new KeyValue(vertical.valueProperty(), target, Interpolator.EASE_BOTH)));
            lyricScrollAnimation.setOnFinished(event -> lyricScrollAnimation = null);
            lyricScrollAnimation.play();
        });
    }

    private void updateProgress(double current, double duration) {
        double progress = duration > 0 ? current / duration : 0;
        waveformView.progressProperty().set(progress);
        if (!progressSlider.isValueChanging() && duration > 0) {
            updatingProgress = true;
            progressSlider.setValue(progress);
            updatingProgress = false;
        }
    }

    private void seekFromSlider() {
        if (!updatingProgress) {
            context.music().seekToFraction(progressSlider.getValue());
        }
    }

    private void updateQueueCount() {
        int size = context.music().getQueue().size();
        queueCountLabel.setText(size + " 首");
        queueButton.setText("播放列表 " + size);
    }

    private void applyTheme(boolean dark) {
        context.setDarkTheme(dark);
        if (root.getScene() == null) {
            applyThemeStyles(dark);
            return;
        }
        if (themeAnimation != null) {
            themeAnimation.stop();
        }
        themeAnimation = new FadeTransition(Duration.millis(150), root);
        themeAnimation.setToValue(0.72);
        themeAnimation.setInterpolator(Interpolator.EASE_IN);
        themeAnimation.setOnFinished(event -> {
            applyThemeStyles(dark);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(220), root);
            fadeIn.setToValue(1.0);
            fadeIn.setInterpolator(Interpolator.EASE_OUT);
            fadeIn.setOnFinished(ignored -> themeAnimation = null);
            themeAnimation = fadeIn;
            fadeIn.play();
        });
        themeAnimation.play();
    }

    private void applyThemeStyles(boolean dark) {
        Application.setUserAgentStylesheet(dark
                ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());
        root.getStyleClass().removeAll("theme-light", "theme-dark");
        root.getStyleClass().add(dark ? "theme-dark" : "theme-light");
        themeIcon.setResource(dark
                ? "/club/muimi/kimusic/icons/sun.svg"
                : "/club/muimi/kimusic/icons/moon.svg");
        themeButton.setTooltip(new Tooltip(dark ? "切换浅色" : "切换深色"));
    }

    private void installButtonMotion() {
        for (Node node : root.lookupAll(".button")) {
            node.setOnMouseEntered(event -> animateScale(node, 1.04, 90));
            node.setOnMouseExited(event -> animateScale(node, 1, 110));
            node.setOnMousePressed(event -> animateScale(node, 0.96, 55));
            node.setOnMouseReleased(event -> animateScale(node, 1.04, 70));
        }
    }

    private void animateScale(Node node, double scale, double millis) {
        ScaleTransition transition = new ScaleTransition(Duration.millis(millis), node);
        transition.setToX(scale);
        transition.setToY(scale);
        transition.play();
    }

    private void showNextNotice() {
        String notice = context.notices().takeNotice();
        if (notice != null) {
            noticeLabel.setText(notice);
            noticeLabel.setOpacity(0);
            noticeLabel.setVisible(true);
            FadeTransition fade = new FadeTransition(Duration.millis(160), noticeLabel);
            fade.setToValue(1);
            fade.play();
        }
        PauseTransition delay = new PauseTransition(Duration.seconds(2.5));
        delay.setOnFinished(event -> {
            FadeTransition fade = new FadeTransition(Duration.millis(180), noticeLabel);
            fade.setToValue(0);
            fade.setOnFinished(done -> noticeLabel.setVisible(false));
            fade.play();
            Platform.runLater(this::showNextNotice);
        });
        delay.play();
    }

    private String titleOf(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String displayName(Path path) {
        Path fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
    }

    private String formatTime(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0) {
            return "0:00";
        }
        long total = Math.round(seconds);
        return "%d:%02d".formatted(total / 60, total % 60);
    }

    private String modeLabel(PlayMode mode) {
        return switch (mode) {
            case SEQUENTIAL -> "顺序";
            case REPEAT_ONE -> "单曲循环";
            case REPEAT_ALL -> "列表循环";
            case SHUFFLE -> "随机";
        };
    }

    private static final class LazyPathTreeItem extends TreeItem<Path> {
        private boolean loaded;

        private LazyPathTreeItem(Path path) {
            super(path.toAbsolutePath().normalize());
        }

        @Override
        public ObservableList<TreeItem<Path>> getChildren() {
            if (!loaded) {
                loaded = true;
                super.getChildren().setAll(loadChildren(getValue()));
            }
            return super.getChildren();
        }

        @Override
        public boolean isLeaf() {
            return Files.isRegularFile(getValue());
        }

        private List<TreeItem<Path>> loadChildren(Path directory) {
            if (!Files.isDirectory(directory)) {
                return List.of();
            }
            try (var paths = Files.list(directory)) {
                return paths.filter(path -> Files.isDirectory(path) || FileTreeService.isAudioFile(path))
                        .sorted(Comparator.comparing((Path path) -> !Files.isDirectory(path))
                                .thenComparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                        .map(LazyPathTreeItem::new)
                        .map(item -> (TreeItem<Path>) item)
                        .toList();
            } catch (IOException exception) {
                return List.of();
            }
        }
    }
}
