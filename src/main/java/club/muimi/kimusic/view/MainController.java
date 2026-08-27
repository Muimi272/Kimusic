package club.muimi.kimusic.view;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import club.muimi.kimusic.AppContext;
import club.muimi.kimusic.model.EqualizerSettings;
import club.muimi.kimusic.service.AudioScanService;
import club.muimi.kimusic.service.FileTreeService;
import club.muimi.kimusic.service.LyricsService.LyricLine;
import club.muimi.kimusic.service.NoticeService;
import club.muimi.kimusic.service.Notice;
import club.muimi.kimusic.service.NoticeLevel;
import club.muimi.kimusic.status.PlayMode;
import club.muimi.kimusic.status.Language;
import club.muimi.kimusic.status.VisualizationMode;
import club.muimi.kimusic.util.I18n;
import club.muimi.kimusic.view.component.SvgIcon;
import club.muimi.kimusic.view.component.SmoothScrollSupport;
import club.muimi.kimusic.view.component.WaveformView;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Slider;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
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
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.control.SplitPane;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.event.ActionEvent;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class MainController {
    private static final double SIDEBAR_WIDTH = 292;
    private static final double TRACK_PANE_MIN_WIDTH = 420;
    private static final double TRACK_PANE_PREF_WIDTH = 680;
    private static final double DEFAULT_WORKSPACE_DIVIDER_POSITION = 0.60;
    private static final Color DEFAULT_SONG_BACKGROUND = Color.web("#EEF2F0");
    private static final PseudoClass PLAYING = PseudoClass.getPseudoClass("playing");
    private static final Duration LYRIC_FOLLOW_RESUME_DELAY = Duration.seconds(3);
    private static final String LYRIC_SCROLL_TRACKING_KEY =
            MainController.class.getName() + ".lyricScrollTracking";
    private static final LyricLine LYRIC_TOP_SPACER = new LyricLine(Double.NaN, "");
    private static final LyricLine LYRIC_BOTTOM_SPACER = new LyricLine(Double.NaN, "");

    @FXML
    private BorderPane root;
    @FXML
    private HBox topBar;
    @FXML
    private VBox sidebar;
    @FXML
    private Button importButton;
    @FXML
    private Button themeButton;
    @FXML
    private SvgIcon themeIcon;
    @FXML
    private Button playPauseButton;
    @FXML
    private SvgIcon playPauseIcon;
    @FXML
    private Button lyricsButton;
    @FXML
    private Button queueButton;
    @FXML
    private Button modeButton;
    @FXML
    private Button equalizerButton;
    @FXML
    private Button findAllAudioButton;
    @FXML
    private SvgIcon modeIcon;
    @FXML
    private TreeView<Path> libraryTree;
    @FXML
    private ListView<String> playlistList;
    @FXML
    private TableView<Path> trackTable;
    @FXML
    private TableColumn<Path, String> indexColumn;
    @FXML
    private TableColumn<Path, String> titleColumn;
    @FXML
    private TableColumn<Path, String> folderColumn;
    @FXML
    private TableColumn<Path, String> playlistActionColumn;
    @FXML
    private ListView<LyricLine> lyricsList;
    @FXML
    private ListView<Path> queueList;
    @FXML
    private Label queueCountLabel;
    @FXML
    private Label viewTitleLabel;
    @FXML
    private Label trackCountLabel;
    @FXML
    private Label nowTitleLabel;
    @FXML
    private Label nowPathLabel;
    @FXML
    private Label visualTitleLabel;
    @FXML
    private Label visualPathLabel;
    @FXML
    private Label currentTimeLabel;
    @FXML
    private Label totalTimeLabel;
    @FXML
    private Label noticeLabel;
    @FXML
    private Slider progressSlider;
    @FXML
    private Slider volumeSlider;
    @FXML
    private SplitPane workspaceSplit;
    @FXML
    private VBox trackPane;
    @FXML
    private VBox visualPane;
    @FXML
    private StackPane coverPane;
    @FXML
    private StackPane visualStack;
    @FXML
    private ImageView coverImage;
    @FXML
    private ImageView previousCoverImage;
    @FXML
    private VBox coverPlaceholder;
    @FXML
    private VBox lyricsPane;
    @FXML
    private VBox queueDrawer;
    @FXML
    private VBox playerBar;
    @FXML
    private WaveformView waveformView;

    private final ObservableList<Path> libraryTracks = FXCollections.observableArrayList();
    private final ObservableList<Path> displayedTracks = FXCollections.observableArrayList();
    private final ObservableList<LyricLine> lyricLines = FXCollections.observableArrayList();
    private final ObservableList<LyricLine> displayedLyrics = FXCollections.observableArrayList();
    private AppContext context;
    private boolean sidebarExpanded = true;
    private boolean lyricsVisible;
    private boolean queueVisible;
    private boolean updatingProgress;
    private Timeline sidebarAnimation;
    private TranslateTransition queueAnimation;
    private ParallelTransition lyricsAnimation;
    private SequentialTransition themeAnimation;
    private Map<Node, Double> themeNodeOpacities = Map.of();
    private ParallelTransition artworkAnimation;
    private Timeline artworkToneAnimation;
    private SequentialTransition noticeAnimation;
    private NoticeService noticeService;
    private Language language = Language.CHINESE;
    private Timeline libraryWatch;
    private Timeline lyricScrollAnimation;
    private PauseTransition lyricFollowResumeDelay;
    private SequentialTransition lyricStyleAnimation;
    private Timeline songViewAnimation;
    private int lyricsFontSize = 14;
    private Color artworkTone;
    private Color displayedArtworkTone;
    private boolean libraryLoaded;
    private boolean libraryScanInFlight;
    private Task<List<Path>> audioScanTask;
    private Dialog<ButtonType> audioScanDialog;
    private List<Path> audioScanRoots = List.of();
    private Label audioScanStatusLabel;
    private Label audioScanPercentLabel;
    private ProgressBar audioScanProgressBar;
    private boolean songViewExpanded;
    private boolean lyricAutoFollowSuspended;
    private double previousWorkspaceDividerPosition = DEFAULT_WORKSPACE_DIVIDER_POSITION;
    private Tooltip coverExpansionTooltip;

    @FXML
    private void initialize() {
        configureTable();
        configureLyrics();
        configureModeSelector();
        configureWorkspaceSplit();
        configureCoverSizing();
        trackTable.setItems(displayedTracks);
        lyricsList.setItems(displayedLyrics);
        libraryTree.setShowRoot(false);
        libraryTree.setFixedCellSize(34);
        playlistList.setFixedCellSize(34);
        queueDrawer.setVisible(false);
        queueDrawer.setManaged(false);
        libraryTree.setCellFactory(tree -> new TreeCell<>() {
            {
                setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(Path path, boolean empty) {
                super.updateItem(path, empty);
                setText(empty || path == null ? null : displayName(path));
            }
        });
        playlistList.setCellFactory(list -> new ListCell<>() {
            {
                setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                setText(empty ? null : name);
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
        language = context.getLanguage();
        lyricsFontSize = context.getLyricsFontSize();
        initializeNotices(context.notices());
        applyLanguage(language);
        applyTheme(context.isDarkTheme());
        populateLibraryTree();
        populatePlaylists();
        bindPlayer();
        applyLyricHighlightColor(context.getLyricHighlightColor());
        applyLyricsFontSize();
        configureInteractions();
        configureQueue();
        if (context.startupTrack() != null) {
            Platform.runLater(() -> context.music().open(context.startupTrack()));
        }
        refreshLibrary();
        libraryWatch = new Timeline(new KeyFrame(Duration.seconds(5), event -> refreshLibrary()));
        libraryWatch.setCycleCount(Timeline.INDEFINITE);
        libraryWatch.play();
        Platform.runLater(() -> {
            lyricsPane.prefWidthProperty().bind(visualStack.widthProperty());
            lyricsList.setMaxWidth(Double.MAX_VALUE);
            installButtonMotion();
        });
    }

    @FXML
    private void showImportMenu() {
        MenuItem files = new MenuItem(t("导入音频文件"));
        files.setOnAction(event -> importFiles());
        MenuItem folder = new MenuItem(t("导入音乐文件夹"));
        folder.setOnAction(event -> importFolder());
        ContextMenu menu = new ContextMenu(files, folder);
        menu.show(importButton, Side.BOTTOM, -142, 6);
    }

    private void importFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(t("选择音频文件"));
        chooser.getExtensionFilters().add(new ExtensionFilter(t("音频文件"),
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
            noticeService.addInfo(t("已成功导入音乐文件。"));
        } else {
            noticeService.addError(t("导入音乐文件失败，请检查文件格式或是否重复。"));
        }
    }

    private void importFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(t("选择音乐文件夹"));
        File directory = chooser.showDialog(root.getScene().getWindow());
        if (directory == null) {
            return;
        }
        if (context.library().addRootFile(directory)) {
            libraryChanged();
            noticeService.addInfo(t("已成功导入音乐文件夹。"));
        } else {
            noticeService.addError(t("导入音乐文件夹失败，请检查文件夹是否重复。"));
        }
    }

    @FXML
    private void removeLibraryRoot() {
        TreeItem<Path> selected = libraryTree.getSelectionModel().getSelectedItem();
        if (selected != null && selected.getParent() == libraryTree.getRoot()
                && context.library().removeLibraryEntry(selected.getValue().toFile())) {
            libraryChanged();
        }
    }

    @FXML
    private void createPlaylist() {
        TextInputDialog dialog = new TextInputDialog();
        styleDialog(dialog);
        dialog.initOwner(root.getScene().getWindow());
        dialog.setTitle(t("新建歌单"));
        dialog.setHeaderText(null);
        dialog.setContentText(t("歌单名称"));
        dialog.showAndWait().ifPresent(name -> {
            if (context.playlists().create(name)) {
                populatePlaylists();
                playlistList.getSelectionModel().select(name.strip());
                context.save();
            } else {
                noticeService.addWarning(t("歌单名称为空或已经存在。"));
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
        addTrackToPlaylist(track);
    }

    private void addTrackToPlaylist(Path track) {
        if (track == null) {
            noticeService.addWarning(t("请先选择一首曲目。"));
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        styleDialog(dialog);
        dialog.initOwner(root.getScene().getWindow());
        dialog.setTitle(t("加入歌单"));
        dialog.setHeaderText(null);
        ButtonType confirm = new ButtonType(t("加入歌单"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(t("取消"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(confirm, cancel);
        ComboBox<String> choices = new ComboBox<>(FXCollections.observableArrayList(context.playlists().names()));
        choices.setMaxWidth(Double.MAX_VALUE);
        choices.getStyleClass().add("add-playlist-choices");
        String selected = playlistList.getSelectionModel().getSelectedItem();
        if (selected != null && choices.getItems().contains(selected)) choices.setValue(selected);
        else if (!choices.getItems().isEmpty()) choices.getSelectionModel().selectFirst();
        Button create = new Button(t("新建歌单"));
        create.getStyleClass().addAll("text-action", "add-playlist-create");
        create.setOnAction(event -> {
            TextInputDialog nameDialog = new TextInputDialog();
            styleDialog(nameDialog);
            nameDialog.initOwner(dialog.getDialogPane().getScene().getWindow());
            nameDialog.setTitle(t("新建歌单"));
            nameDialog.setHeaderText(null);
            nameDialog.setContentText(t("歌单名称"));
            nameDialog.showAndWait().ifPresent(name -> {
                if (context.playlists().create(name)) {
                    populatePlaylists();
                    String normalized = name.strip();
                    choices.getItems().setAll(context.playlists().names());
                    choices.setValue(normalized);
                } else {
                    noticeService.addWarning(t("歌单名称为空或已经存在。"));
                }
            });
        });
        String trackTitle = titleOf(track);
        Label trackCaption = new Label(t("曲目"));
        trackCaption.getStyleClass().add("add-playlist-caption");
        Label trackName = new Label(trackTitle);
        trackName.setMaxWidth(480);
        trackName.setMinWidth(0);
        trackName.setWrapText(false);
        trackName.setTextOverrun(OverrunStyle.ELLIPSIS);
        trackName.setEllipsisString("...");
        trackName.setTooltip(new Tooltip(trackTitle));
        trackName.getStyleClass().add("add-playlist-track");
        VBox trackSummary = new VBox(5, trackCaption, trackName);
        Label targetCaption = new Label(t("目标歌单"));
        targetCaption.getStyleClass().add("add-playlist-caption");
        HBox selector = new HBox(10, choices, create);
        selector.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(choices, Priority.ALWAYS);
        VBox content = new VBox(20, trackSummary, new VBox(8, targetCaption, selector));
        content.getStyleClass().add("add-playlist-dialog-content");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(560);
        dialog.getDialogPane().setPrefHeight(260);
        dialog.showAndWait().ifPresent(result -> {
            String playlist = choices.getValue();
            if (result == confirm && playlist != null && context.playlists().addTrack(playlist, track)) {
                noticeService.addInfo(language == Language.ENGLISH
                        ? "Added to \"" + playlist + "\"." : "已加入「" + playlist + "」。");
                if (playlist.equals(playlistList.getSelectionModel().getSelectedItem())) {
                    showPlaylist(playlist);
                }
                context.save();
            } else if (result == confirm && playlist == null) {
                noticeService.addWarning(t("请先新建歌单。"));
            } else if (result == confirm) {
                noticeService.addWarning(t("曲目已经在该歌单中。"));
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
        if (lyricsAnimation != null) lyricsAnimation.stop();
        boolean showLyrics = lyricsVisible;
        coverPane.setManaged(true);
        coverPane.setVisible(true);
        lyricsPane.setManaged(true);
        lyricsPane.setVisible(true);
        FadeTransition coverFade = new FadeTransition(Duration.millis(210), coverPane);
        coverFade.setToValue(showLyrics ? 0 : 1);
        coverFade.setInterpolator(Interpolator.EASE_BOTH);
        FadeTransition lyricsFade = new FadeTransition(Duration.millis(210), lyricsPane);
        lyricsFade.setToValue(showLyrics ? 1 : 0);
        lyricsFade.setInterpolator(Interpolator.EASE_BOTH);
        lyricsAnimation = new ParallelTransition(coverFade, lyricsFade);
        lyricsAnimation.setOnFinished(event -> {
            coverPane.setManaged(!showLyrics);
            coverPane.setVisible(!showLyrics);
            lyricsPane.setManaged(showLyrics);
            lyricsPane.setVisible(showLyrics);
            lyricsAnimation = null;
            if (showLyrics) {
                recenterActiveLyric();
            }
        });
        lyricsAnimation.play();
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
        dialog.setTitle(t("设置"));
        ButtonType close = new ButtonType(t("关闭"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(close);

        ImageView logo = new ImageView(new Image(
                Objects.requireNonNull(MainController.class.getResourceAsStream("/club/muimi/kimusic/logo.png"))));
        logo.setFitWidth(34);
        logo.setFitHeight(34);
        logo.setPreserveRatio(true);
        VBox headingText = new VBox(styledLabel(t("设置"), "settings-title"));
        HBox heading = new HBox(12, logo, headingText);
        heading.setAlignment(Pos.CENTER_LEFT);
        heading.getStyleClass().add("settings-heading");

        ToggleButton lightTheme = segmentedButton(t("浅色"));
        ToggleButton darkTheme = segmentedButton(t("深色"));
        ToggleGroup themeGroup = new ToggleGroup();
        lightTheme.setToggleGroup(themeGroup);
        darkTheme.setToggleGroup(themeGroup);
        (context.isDarkTheme() ? darkTheme : lightTheme).setSelected(true);
        HBox themeSelector = segmentedControl(lightTheme, darkTheme);
        themeGroup.selectedToggleProperty().addListener((observable, oldToggle, selected) -> {
            if (selected != null) {
                boolean dark = selected == darkTheme;
                applyTheme(dark);
                updateDialogTheme(dialog, dark);
            }
        });

        ToggleButton waveformMode = segmentedButton(t("波形"));
        ToggleButton spectrumMode = segmentedButton(t("频谱"));
        ToggleGroup visualizationGroup = new ToggleGroup();
        waveformMode.setToggleGroup(visualizationGroup);
        spectrumMode.setToggleGroup(visualizationGroup);
        (context.getVisualizationMode() == VisualizationMode.SPECTRUM
                ? spectrumMode : waveformMode).setSelected(true);
        HBox visualizationSelector = segmentedControl(waveformMode, spectrumMode);
        visualizationGroup.selectedToggleProperty().addListener((observable, oldToggle, selected) -> {
            if (selected == null) return;
            VisualizationMode mode = selected == spectrumMode
                    ? VisualizationMode.SPECTRUM : VisualizationMode.WAVEFORM;
            context.setVisualizationMode(mode);
            waveformView.setMode(mode);
        });

        Slider settingVolume = new Slider(0, 100, context.music().volumeProperty().get() * 100);
        Label volumeValue = styledLabel(Math.round(settingVolume.getValue()) + "%", "settings-value");
        settingVolume.valueProperty().addListener((observable, oldValue, value) -> {
            volumeValue.setText(Math.round(value.doubleValue()) + "%");
            context.music().volumeProperty().set(value.doubleValue() / 100);
        });
        HBox volumeHeading = settingRow(t("默认音量"), volumeValue);

        ToggleButton chineseLanguage = segmentedButton("中文");
        ToggleButton englishLanguage = segmentedButton("English");
        ToggleGroup languageGroup = new ToggleGroup();
        chineseLanguage.setToggleGroup(languageGroup);
        englishLanguage.setToggleGroup(languageGroup);
        (language == Language.ENGLISH ? englishLanguage : chineseLanguage).setSelected(true);
        HBox languageSelector = segmentedControl(chineseLanguage, englishLanguage);
        languageGroup.selectedToggleProperty().addListener((observable, oldToggle, selected) -> {
            if (selected != null) {
                applyLanguage(selected == englishLanguage ? Language.ENGLISH : Language.CHINESE);
            }
        });

        Slider lyricSize = new Slider(11, 24, lyricsFontSize);
        lyricSize.setBlockIncrement(1);
        Label lyricSizeValue = styledLabel(Math.round(lyricSize.getValue()) + " px", "settings-value");
        lyricSize.valueProperty().addListener((observable, oldValue, value) -> {
            lyricSizeValue.setText(Math.round(value.doubleValue()) + " px");
            lyricsFontSize = (int) Math.round(value.doubleValue());
            context.setLyricsFontSize(lyricsFontSize);
            applyLyricsFontSize();
        });

        ColorPicker lyricColor = new ColorPicker(Color.web(context.getLyricHighlightColor()));
        lyricColor.getStyleClass().add("lyric-color-picker");
        FlowPane presets = new FlowPane(8, 8);
        for (String color : List.of("#1AA79B", "#E5484D", "#E88C30", "#2F6FEB",
                "#8E5BD9", "#D14D8B")) {
            presets.getChildren().add(colorSwatch(color, lyricColor));
        }
        lyricColor.valueProperty().addListener((observable, oldValue, value) -> {
            String selectedColor = colorToHex(value);
            context.setLyricHighlightColor(selectedColor);
            applyLyricHighlightColor(selectedColor);
        });

        CheckBox autoDecodeNcm = new CheckBox();
        autoDecodeNcm.setSelected(context.isAutoDecodeNcm());
        autoDecodeNcm.setAccessibleText(t("自动解码 NCM"));
        autoDecodeNcm.getStyleClass().add("ncm-auto-decode-check");
        autoDecodeNcm.selectedProperty().addListener((observable, oldValue, value) ->
                context.setAutoDecodeNcm(value));

        VBox appearancePage = new VBox(18,
                settingsSection(t("界面"), settingRow(t("界面主题"), themeSelector),
                        settingRow(t("界面语言"), languageSelector)),
                settingsSection(t("歌词"), settingRow(t("歌词高亮颜色"), lyricColor), presets,
                        settingRow(t("歌词字号"), lyricSizeValue), lyricSize));
        appearancePage.getStyleClass().add("settings-page");

        VBox playbackPage = new VBox(18,
                settingsSection(t("播放"), volumeHeading, settingVolume,
                        settingRow(t("播放画面"), visualizationSelector),
                        settingRow(t("自动解码 NCM"), autoDecodeNcm)));
        playbackPage.getStyleClass().add("settings-page");
        playbackPage.setVisible(false);
        playbackPage.setManaged(false);

        ToggleButton appearanceNav = new ToggleButton(t("外观"));
        ToggleButton playbackNav = new ToggleButton(t("播放"));
        ToggleGroup navigation = new ToggleGroup();
        appearanceNav.setToggleGroup(navigation);
        playbackNav.setToggleGroup(navigation);
        appearanceNav.setSelected(true);
        appearanceNav.getStyleClass().add("settings-nav-item");
        playbackNav.getStyleClass().add("settings-nav-item");
        appearanceNav.setMaxWidth(Double.MAX_VALUE);
        playbackNav.setMaxWidth(Double.MAX_VALUE);
        navigation.selectedToggleProperty().addListener((observable, oldToggle, selected) -> {
            if (selected == null) {
                (oldToggle == null ? appearanceNav : (ToggleButton) oldToggle).setSelected(true);
                return;
            }
            boolean showAppearance = selected == appearanceNav;
            appearancePage.setVisible(showAppearance);
            appearancePage.setManaged(showAppearance);
            playbackPage.setVisible(!showAppearance);
            playbackPage.setManaged(!showAppearance);
        });
        VBox nav = new VBox(6, appearanceNav, playbackNav);
        nav.getStyleClass().add("settings-nav");
        StackPane pages = new StackPane(appearancePage, playbackPage);
        HBox.setHgrow(pages, Priority.ALWAYS);
        HBox settingsBody = new HBox(24, nav, pages);
        settingsBody.getStyleClass().add("settings-body");

        VBox content = new VBox(14, heading, settingsBody);
        content.getStyleClass().add("settings-content");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(720);
        dialog.getDialogPane().setPrefHeight(520);
        dialog.setOnHidden(event -> context.save());
        dialog.showAndWait();
    }

    @FXML
    private void openEqualizer() {
        EqualizerSettings draft = context.music().getEqualizerSettings();
        Dialog<ButtonType> dialog = new Dialog<>();
        styleDialog(dialog);
        dialog.initOwner(root.getScene().getWindow());
        dialog.setTitle(t("均衡器"));
        ButtonType close = new ButtonType(t("关闭"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(close);

        Runnable applyDraft = () -> context.music().setEqualizerSettings(draft);

        CheckBox enabled = new CheckBox(t("启用均衡器"));
        enabled.getStyleClass().add("equalizer-enabled");
        enabled.setSelected(draft.isEnabled());
        enabled.selectedProperty().addListener((observable, oldValue, value) -> {
            draft.setEnabled(value);
            applyDraft.run();
        });
        ComboBox<String> presets = new ComboBox<>(FXCollections.observableArrayList(draft.allPresets().keySet()));
        presets.getStyleClass().add("equalizer-presets");
        presets.setPrefWidth(180);
        presets.setCellFactory(list -> localizedPresetCell());
        presets.setButtonCell(localizedPresetCell());
        String active = draft.getActivePreset();
        presets.setValue(draft.allPresets().containsKey(active) ? active : "Flat");
        Button savePreset = new Button(t("保存为预设"));
        savePreset.getStyleClass().addAll("text-action", "equalizer-save-preset");
        HBox bands = new HBox(10);
        ScrollPane bandsViewport = new ScrollPane(bands);
        bandsViewport.setFitToHeight(true);
        bandsViewport.setFitToWidth(true);
        bandsViewport.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        bandsViewport.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        bandsViewport.getStyleClass().add("equalizer-bands-viewport");
        bands.setAlignment(Pos.CENTER);
        bands.getStyleClass().add("equalizer-bands");
        Slider[] sliders = new Slider[EqualizerSettings.BAND_COUNT];
        boolean[] applyingPreset = {false};
        for (int index = 0; index < sliders.length; index++) {
            Label frequency = new Label(formatFrequency(EqualizerSettings.BAND_FREQUENCIES[index]));
            Slider slider = new Slider(-12, 12, draft.getGains()[index]);
            slider.setOrientation(Orientation.VERTICAL);
            slider.setPrefHeight(190);
            slider.setMinHeight(150);
            slider.setMaxHeight(220);
            slider.setPrefWidth(30);
            slider.setBlockIncrement(1);
            slider.setMajorTickUnit(6);
            slider.setMinorTickCount(5);
            slider.setShowTickMarks(true);
            slider.setShowTickLabels(false);
            Label value = new Label(formatGain(slider.getValue()));
            slider.valueProperty().addListener((observable, oldValue, newValue) -> {
                value.setText(formatGain(newValue.doubleValue()));
                draft.setGains(java.util.Arrays.stream(sliders)
                        .mapToDouble(Slider::getValue).toArray());
                if (!applyingPreset[0]) {
                    draft.setActivePreset("Custom");
                    applyDraft.run();
                }
            });
            sliders[index] = slider;
            VBox band = new VBox(6, frequency, slider, value);
            band.setAlignment(Pos.CENTER);
            band.setMinWidth(50);
            bands.getChildren().add(band);
        }
        presets.setOnAction(event -> {
            String name = presets.getValue();
            double[] selected = draft.allPresets().get(name);
            if (selected != null) {
                applyingPreset[0] = true;
                try {
                    draft.setGains(selected);
                    for (int index = 0; index < sliders.length; index++) {
                        sliders[index].setValue(selected[index]);
                    }
                    draft.setActivePreset(name);
                } finally {
                    applyingPreset[0] = false;
                }
                applyDraft.run();
            }
        });
        savePreset.setOnAction(event -> {
            TextInputDialog nameDialog = new TextInputDialog(draft.getActivePreset().equals("Custom") ? "" : draft.getActivePreset());
            styleDialog(nameDialog);
            nameDialog.initOwner(dialog.getDialogPane().getScene().getWindow());
            nameDialog.setTitle(t("保存为预设"));
            nameDialog.setHeaderText(null);
            nameDialog.setContentText(t("预设名称"));
            nameDialog.showAndWait().ifPresent(name -> {
                if (!name.isBlank()) {
                    draft.setCustomPreset(name, draft.getGains());
                    draft.setActivePreset(name.strip());
                    presets.getItems().setAll(draft.allPresets().keySet());
                    presets.setValue(name.strip());
                    applyDraft.run();
                }
            });
        });
        HBox presetSelector = new HBox(10, new Label(t("预设")), presets);
        presetSelector.setAlignment(Pos.CENTER_LEFT);
        BorderPane header = new BorderPane();
        header.setLeft(presetSelector);
        header.setRight(savePreset);
        header.setMaxWidth(Double.MAX_VALUE);
        header.getStyleClass().add("equalizer-header");
        BorderPane footer = new BorderPane();
        footer.setLeft(enabled);
        footer.setMaxWidth(Double.MAX_VALUE);
        footer.getStyleClass().add("equalizer-footer");
        VBox content = new VBox(18, header, bandsViewport, footer);
        content.getStyleClass().add("equalizer-dialog-content");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(650);
        dialog.setOnHidden(event -> context.save());
        dialog.showAndWait();
    }

    private String formatFrequency(double frequency) {
        return frequency >= 1000 ? String.format(Locale.ROOT, "%.0f kHz", frequency / 1000) : String.format(Locale.ROOT, "%.0f Hz", frequency);
    }

    private ListCell<String> localizedPresetCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : equalizerPresetLabel(value));
            }
        };
    }

    private String equalizerPresetLabel(String value) {
        if (language == Language.ENGLISH) return value;
        return switch (value) {
            case "Bass Boost" -> "重低音";
            case "Treble Boost" -> "高音增强";
            case "Vocal" -> "人声";
            case "Rock" -> "摇滚";
            case "Classical" -> "古典";
            case "Flat" -> "平直";
            default -> value;
        };
    }

    private String formatGain(double gain) {
        return String.format(Locale.ROOT, "%+.1f dB", gain);
    }

    @FXML
    private void openAudioScan() {
        if (audioScanTask != null && !audioScanTask.isDone()) {
            showAudioScanProgress();
            return;
        }
        audioScanTask = null;
        audioScanRoots = AudioScanService.defaultRoots();
        Dialog<ButtonType> dialog = new Dialog<>();
        audioScanDialog = dialog;
        styleDialog(dialog);
        dialog.initOwner(root.getScene().getWindow());
        dialog.setTitle(t("快速查找所有可用的音乐文件"));
        ButtonType start = new ButtonType(t("开始扫描"), ButtonBar.ButtonData.OK_DONE);
        ButtonType close = new ButtonType(t("取消"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(start, close);
        Label heading = new Label(t("快速查找所有可用的音乐文件"));
        heading.setWrapText(true);
        heading.getStyleClass().add("scan-dialog-title");
        Label selectedScope = new Label();
        selectedScope.setWrapText(true);
        selectedScope.setMaxWidth(Double.MAX_VALUE);
        selectedScope.setTextOverrun(OverrunStyle.ELLIPSIS);
        selectedScope.setEllipsisString("...");
        selectedScope.setVisible(false);
        selectedScope.setManaged(false);
        selectedScope.getStyleClass().add("scan-dialog-selected-scope");
        VBox scanHeading = new VBox(5, heading, selectedScope);
        Button choose = new Button(t("选择扫描范围"));
        choose.setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(t("选择扫描范围"));
            File selected = chooser.showDialog(root.getScene().getWindow());
            if (selected != null) {
                audioScanRoots = List.of(selected.toPath().toAbsolutePath().normalize());
                selectedScope.setText(audioScanRoots.getFirst().toString());
                selectedScope.setTooltip(new Tooltip(audioScanRoots.getFirst().toString()));
                selectedScope.setVisible(true);
                selectedScope.setManaged(true);
            }
        });
        choose.getStyleClass().addAll("text-action", "scan-scope-button");
        choose.setMinWidth(132);
        Label timeNote = new Label(t("该功能会扫描整个设备中的所有文件，会耗时较久。"));
        Label exclusionNote = new Label(t("常见系统目录、缓存目录和构建目录会自动跳过。"));
        timeNote.setWrapText(true);
        exclusionNote.setWrapText(true);
        timeNote.getStyleClass().add("scan-dialog-note");
        exclusionNote.getStyleClass().add("scan-dialog-note");
        VBox notes = new VBox(3, timeNote, exclusionNote);
        notes.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(notes, Priority.ALWAYS);
        HBox footer = new HBox(16, notes, choose);
        footer.setAlignment(Pos.BOTTOM_RIGHT);
        footer.getStyleClass().add("scan-dialog-footer");
        VBox content = new VBox(28, scanHeading, footer);
        content.getStyleClass().add("scan-dialog-content");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(560);
        Node startButton = dialog.getDialogPane().lookupButton(start);
        startButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            startAudioScan(dialog);
        });
        dialog.show();
    }

    private void startAudioScan(Dialog<ButtonType> dialog) {
        if (audioScanTask != null && !audioScanTask.isDone()) return;
        audioScanStatusLabel = new Label(t("正在扫描中"));
        audioScanStatusLabel.getStyleClass().add("scan-progress-status");
        audioScanPercentLabel = new Label("0%");
        audioScanPercentLabel.getStyleClass().add("scan-progress-percent");
        audioScanProgressBar = new ProgressBar(0);
        audioScanProgressBar.getStyleClass().add("scan-progress-bar");
        audioScanProgressBar.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(audioScanProgressBar, Priority.NEVER);
        BorderPane progressHeader = new BorderPane();
        progressHeader.setLeft(audioScanStatusLabel);
        progressHeader.setRight(audioScanPercentLabel);
        VBox progress = new VBox(12, progressHeader, audioScanProgressBar);
        progress.getStyleClass().add("scan-progress-dialog");
        dialog.getDialogPane().setContent(progress);
        dialog.getDialogPane().getStyleClass().add("scan-progress-dialog-pane");
        ButtonType cancel = new ButtonType(t("取消扫描"), ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType background = new ButtonType(t("后台扫描"), ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().setAll(cancel, background);
        Node cancelButton = dialog.getDialogPane().lookupButton(cancel);
        cancelButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            if (audioScanTask != null) audioScanTask.cancel();
            dialog.hide();
        });
        Node backgroundButton = dialog.getDialogPane().lookupButton(background);
        cancelButton.getStyleClass().add("scan-cancel-button");
        backgroundButton.getStyleClass().add("scan-background-button");
        cancelButton.setStyle("-fx-pref-width: 148px;");
        backgroundButton.setStyle("-fx-pref-width: 148px;");
        backgroundButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            dialog.hide();
        });

        List<Path> roots = List.copyOf(audioScanRoots);
        Task<List<Path>> task = new Task<>() {
            @Override
            protected List<Path> call() {
                return AudioScanService.scan(roots, (visited, total, found) -> {
                    if (total > 0) updateProgress(visited, total);
                    else updateProgress(visited > 0 ? 1 : 0, 1);
                    updateMessage(t("已发现") + " " + found + t(" 首"));
                }, this::isCancelled);
            }
        };
        audioScanTask = task;
        audioScanProgressBar.progressProperty().bind(task.progressProperty());
        task.progressProperty().addListener((observable, oldValue, newValue) -> {
            double value = newValue.doubleValue();
            audioScanPercentLabel.setText(Math.round(Math.max(0, Math.min(1, value)) * 100) + "%");
        });
        task.messageProperty().addListener((observable, oldValue, message) -> {
            if (message != null && !message.isBlank()) audioScanStatusLabel.setText(t("正在扫描中") + " - " + message);
        });
        task.setOnSucceeded(event -> finishAudioScan(task.getValue()));
        task.setOnCancelled(event -> finishAudioScanCancelled());
        task.setOnFailed(event -> finishAudioScanFailed(task.getException()));
        Thread.ofVirtual().name("kimusic-device-audio-scan").start(task);
    }

    private void showAudioScanProgress() {
        if (audioScanDialog == null) return;
        audioScanDialog.show();
    }

    private void finishAudioScan(List<Path> found) {
        int added = context.library().addDiscoveredFiles(found);
        if (added > 0) libraryChanged();
        if (audioScanStatusLabel != null) audioScanStatusLabel.setText(
                t("扫描完成") + " - " + t("新增") + " " + added + t(" 首"));
        if (audioScanPercentLabel != null) audioScanPercentLabel.setText("100%");
        if (audioScanProgressBar != null) {
            if (audioScanProgressBar.progressProperty().isBound()) {
                audioScanProgressBar.progressProperty().unbind();
            }
            audioScanProgressBar.setProgress(1);
        }
        if (audioScanDialog != null) {
            ButtonType confirm = new ButtonType(t("确认"), ButtonBar.ButtonData.OK_DONE);
            audioScanDialog.getDialogPane().getButtonTypes().setAll(confirm);
        }
    }

    private void finishAudioScanCancelled() {
        if (audioScanStatusLabel != null) audioScanStatusLabel.setText(t("扫描已取消"));
        if (audioScanDialog != null) {
            ButtonType close = new ButtonType(t("关闭"), ButtonBar.ButtonData.CANCEL_CLOSE);
            audioScanDialog.getDialogPane().getButtonTypes().setAll(close);
        }
    }

    private void finishAudioScanFailed(Throwable error) {
        if (audioScanStatusLabel != null) audioScanStatusLabel.setText(t("扫描失败"));
        if (error != null) noticeService.addError(t("扫描失败") + ": " + error.getMessage());
        if (audioScanDialog != null) {
            ButtonType close = new ButtonType(t("关闭"), ButtonBar.ButtonData.CANCEL_CLOSE);
            audioScanDialog.getDialogPane().getButtonTypes().setAll(close);
        }
    }

    private Button colorSwatch(String color, ColorPicker target) {
        Button swatch = new Button();
        swatch.getStyleClass().add("color-swatch");
        swatch.setStyle("-swatch-color: " + color + ";");
        swatch.setAccessibleText(color);
        swatch.setTooltip(new Tooltip(color));
        swatch.setOnAction(event -> target.setValue(Color.web(color)));
        return swatch;
    }

    private String colorToHex(Color color) {
        Color value = color == null ? Color.web("#1AA79B") : color;
        return String.format(Locale.ROOT, "#%02X%02X%02X",
                Math.round(value.getRed() * 255), Math.round(value.getGreen() * 255),
                Math.round(value.getBlue() * 255));
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
                Objects.requireNonNull(MainController.class.getResource("/club/muimi/kimusic/kimusic.css")).toExternalForm());
        dialog.getDialogPane().getStyleClass().add("kimusic-dialog");
        updateDialogTheme(dialog, context != null && context.isDarkTheme());
    }

    private void updateDialogTheme(Dialog<?> dialog, boolean dark) {
        dialog.getDialogPane().getStyleClass().removeAll("theme-light", "theme-dark");
        dialog.getDialogPane().getStyleClass().add(dark ? "theme-dark" : "theme-light");
    }

    private void configureTable() {
        trackTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        trackTable.setFixedCellSize(38);
        indexColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                Integer.toString(displayedTracks.indexOf(cell.getValue()) + 1)));
        indexColumn.setStyle("-fx-alignment: CENTER;");
        indexColumn.setCellFactory(column -> alignedTableCell(Pos.CENTER, false));
        titleColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(titleOf(cell.getValue())));
        titleColumn.setCellFactory(column -> alignedTableCell(Pos.CENTER_LEFT, false));
        folderColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                cell.getValue().getParent() == null ? "" : cell.getValue().getParent().toString()));
        folderColumn.setCellFactory(column -> alignedTableCell(Pos.CENTER_LEFT, true));
        playlistActionColumn.setText(t("加入歌单"));
        playlistActionColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(""));
        playlistActionColumn.setCellFactory(column -> new TableCell<>() {
            private final Button add = new Button();
            private final SvgIcon addIcon = new SvgIcon();
            {
                addIcon.setResource("/club/muimi/kimusic/icons/plus.svg");
                addIcon.setSize(15);
                add.setGraphic(addIcon);
                add.setTooltip(new Tooltip(t("加入歌单")));
                add.getStyleClass().add("small-icon-action");
                add.setOnAction(event -> addTrackToPlaylist(
                        getTableView().getItems().get(getIndex())));
                setAlignment(Pos.CENTER);
            }
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty) {
                    setGraphic(null);
                    setTooltip(null);
                } else {
                    add.setTooltip(new Tooltip(t("加入歌单")));
                    setGraphic(add);
                }
            }
        });
        trackTable.setRowFactory(table -> {
            TableRow<Path> row = new TableRow<>() {
                {
                    setAlignment(Pos.CENTER_LEFT);
                }

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
            MenuItem add = new MenuItem(t("加入歌单"));
            add.setOnAction(event -> addToPlaylist());
            MenuItem remove = new MenuItem(t("从当前歌单移除"));
            remove.setOnAction(event -> removeFromPlaylist());
            ContextMenu menu = new ContextMenu(add, remove);
            menu.setOnShowing(event -> {
                add.setText(t("加入歌单"));
                remove.setText(t("从当前歌单移除"));
                remove.setDisable(playlistList.getSelectionModel().getSelectedItem() == null);
            });
            row.setContextMenu(menu);
            return row;
        });
    }

    private TableCell<Path, String> alignedTableCell(Pos alignment, boolean showTooltip) {
        return new TableCell<>() {
            {
                setAlignment(alignment);
            }

            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                setTooltip(showTooltip && !empty && value != null ? new Tooltip(value) : null);
            }
        };
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
            {
                prefWidthProperty().bind(javafx.beans.binding.Bindings.max(
                        0, list.widthProperty().subtract(18)));
                maxWidthProperty().bind(prefWidthProperty());
                list.heightProperty().addListener(observable -> updateLyricCellStyle());
            }

            @Override
            protected void updateItem(LyricLine line, boolean empty) {
                super.updateItem(line, empty);
                setText(empty || line == null ? null : line.text());
                setWrapText(true);
                setTextAlignment(TextAlignment.CENTER);
                setTextOverrun(OverrunStyle.CLIP);
                updateLyricCellStyle();
            }

            @Override
            public void updateSelected(boolean selected) {
                super.updateSelected(selected);
                updateLyricCellStyle();
            }

            private void updateLyricCellStyle() {
                LyricLine line = getItem();
                boolean spacer = line == LYRIC_TOP_SPACER || line == LYRIC_BOTTOM_SPACER;
                if (spacer) {
                    double spacerHeight = Math.max(0, list.getHeight() / 2);
                    setText(null);
                    setMinHeight(spacerHeight);
                    setPrefHeight(spacerHeight);
                    setMaxHeight(spacerHeight);
                    setPadding(Insets.EMPTY);
                    setMouseTransparent(true);
                    setStyle("");
                    return;
                }
                setMinHeight(Region.USE_PREF_SIZE);
                setPrefHeight(Region.USE_COMPUTED_SIZE);
                setMaxHeight(Double.MAX_VALUE);
                setPadding(new Insets(9, 20, 9, 20));
                setMouseTransparent(false);
                setStyle("-fx-font-size: " + (isSelected() ? lyricsFontSize + 2 : lyricsFontSize) + "px;");
            }
        });
        lyricsList.setFixedCellSize(-1);
        lyricsList.skinProperty().addListener((observable, oldSkin, skin) -> Platform.runLater(() -> {
            lyricsList.refresh();
            installLyricScrollTracking();
            recenterActiveLyric();
        }));
        lyricsList.addEventFilter(ScrollEvent.SCROLL, event -> suspendLyricAutoFollow());
        lyricsList.widthProperty().addListener(observable -> recenterActiveLyric());
        lyricsList.heightProperty().addListener(observable -> {
            lyricsList.refresh();
            recenterActiveLyric();
        });
        lyricsList.setOnMouseClicked(event -> {
            Node target = event.getPickResult().getIntersectedNode();
            while (target != null && !(target instanceof ListCell<?>)) {
                target = target.getParent();
            }
            LyricLine line = target instanceof ListCell<?> cell && cell.getItem() instanceof LyricLine lyric
                    ? lyric : null;
            OptionalDouble seekTarget = lyricSeekTarget(line);
            if (seekTarget.isPresent() && context != null
                    && context.music().durationSecondsProperty().get() > 0) {
                context.music().seekToSeconds(seekTarget.getAsDouble());
            }
        });
    }

    private void installLyricScrollTracking() {
        ScrollBar vertical = verticalLyricScrollBar();
        if (vertical == null || vertical.getProperties().putIfAbsent(
                LYRIC_SCROLL_TRACKING_KEY, Boolean.TRUE) != null) {
            return;
        }
        vertical.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> suspendLyricAutoFollow());
        vertical.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> suspendLyricAutoFollow());
    }

    private ScrollBar verticalLyricScrollBar() {
        return lyricsList.lookupAll(".scroll-bar").stream()
                .filter(ScrollBar.class::isInstance)
                .map(ScrollBar.class::cast)
                .filter(bar -> bar.getOrientation() == javafx.geometry.Orientation.VERTICAL)
                .findFirst().orElse(null);
    }

    private void suspendLyricAutoFollow() {
        if (lyricLines.isEmpty()) {
            return;
        }
        lyricAutoFollowSuspended = true;
        if (lyricScrollAnimation != null) {
            lyricScrollAnimation.stop();
            lyricScrollAnimation = null;
        }
        if (lyricFollowResumeDelay == null) {
            lyricFollowResumeDelay = new PauseTransition(LYRIC_FOLLOW_RESUME_DELAY);
            lyricFollowResumeDelay.setOnFinished(event -> {
                lyricAutoFollowSuspended = false;
                recenterActiveLyric();
            });
        }
        lyricFollowResumeDelay.playFromStart();
    }

    private void resetLyricAutoFollow() {
        lyricAutoFollowSuspended = false;
        if (lyricFollowResumeDelay != null) {
            lyricFollowResumeDelay.stop();
        }
        if (lyricScrollAnimation != null) {
            lyricScrollAnimation.stop();
            lyricScrollAnimation = null;
        }
    }

    private void recenterActiveLyric() {
        if (!lyricsVisible || lyricAutoFollowSuspended) {
            return;
        }
        int index = lyricsList.getSelectionModel().getSelectedIndex();
        if (index >= 0) {
            smoothScrollLyrics(index);
        }
    }

    static OptionalDouble lyricSeekTarget(LyricLine line) {
        return line != null && Double.isFinite(line.seconds()) && line.seconds() >= 0
                ? OptionalDouble.of(line.seconds()) : OptionalDouble.empty();
    }

    private void setLyrics(List<LyricLine> lines) {
        lyricLines.setAll(lines == null ? List.of() : lines);
        displayedLyrics.clear();
        if (!lyricLines.isEmpty()) {
            displayedLyrics.add(LYRIC_TOP_SPACER);
            displayedLyrics.addAll(lyricLines);
            displayedLyrics.add(LYRIC_BOTTOM_SPACER);
        }
    }

    private void applyLyricsFontSize() {
        lyricsList.refresh();
    }

    private void configureModeSelector() {
        updateModeIcon(PlayMode.SEQUENTIAL);
    }

    private void configureWorkspaceSplit() {
        workspaceSplit.setDividerPositions(DEFAULT_WORKSPACE_DIVIDER_POSITION);
        workspaceSplit.widthProperty().addListener((observable, oldWidth, width) ->
                clampWorkspaceDivider(width.doubleValue()));
        workspaceSplit.getDividers().getFirst().positionProperty().addListener(
                (observable, oldPosition, position) -> clampWorkspaceDivider(workspaceSplit.getWidth()));

        coverPane.setCursor(Cursor.HAND);
        coverPane.setFocusTraversable(true);
        coverPane.setOnMouseClicked(event -> toggleSongViewExpansion());
        coverPane.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER
                    || event.getCode() == javafx.scene.input.KeyCode.SPACE) {
                toggleSongViewExpansion();
                event.consume();
            }
        });
        coverExpansionTooltip = new Tooltip();
        Tooltip.install(coverPane, coverExpansionTooltip);
        updateSongPresentation(null);
        updateCoverExpansionHint();
    }

    private void toggleSongViewExpansion() {
        setSongViewExpanded(!songViewExpanded, true);
    }

    void setSongViewExpanded(boolean expanded, boolean animate) {
        if (expanded == songViewExpanded && songViewAnimation == null) {
            return;
        }

        boolean animationWasRunning = songViewAnimation != null;
        if (expanded && !songViewExpanded && !animationWasRunning) {
            previousWorkspaceDividerPosition = workspaceSplit.getDividers().getFirst().getPosition();
        }
        if (songViewAnimation != null) {
            songViewAnimation.stop();
            songViewAnimation = null;
        }

        songViewExpanded = expanded;
        trackPane.setMinWidth(0);
        if (!expanded) {
            trackPane.setVisible(true);
            trackPane.setPrefWidth(TRACK_PANE_PREF_WIDTH);
            trackPane.setMaxWidth(Double.MAX_VALUE);
        }
        trackPane.setMouseTransparent(expanded);
        visualPane.getStyleClass().remove("song-view-expanded");
        workspaceSplit.getStyleClass().remove("song-view-expanded");
        if (expanded) {
            visualPane.getStyleClass().add("song-view-expanded");
            workspaceSplit.getStyleClass().add("song-view-expanded");
        }
        updateCoverExpansionHint();

        double target = expanded ? 0 : previousWorkspaceDividerPosition;
        if (!animate) {
            workspaceSplit.setDividerPosition(0, target);
            finishSongViewTransition(expanded);
            return;
        }

        SplitPane.Divider divider = workspaceSplit.getDividers().getFirst();
        Timeline animation = new Timeline(new KeyFrame(Duration.millis(260),
                new KeyValue(divider.positionProperty(), target, Interpolator.EASE_BOTH)));
        songViewAnimation = animation;
        animation.setOnFinished(event -> {
            if (songViewAnimation != animation) {
                return;
            }
            songViewAnimation = null;
            finishSongViewTransition(expanded);
        });
        animation.play();
    }

    private void finishSongViewTransition(boolean expanded) {
        if (expanded) {
            trackPane.setMinWidth(0);
            trackPane.setPrefWidth(0);
            trackPane.setMaxWidth(0);
            trackPane.setVisible(false);
            workspaceSplit.setDividerPosition(0, 0);
        } else {
            trackPane.setMinWidth(TRACK_PANE_MIN_WIDTH);
            trackPane.setPrefWidth(TRACK_PANE_PREF_WIDTH);
            trackPane.setMaxWidth(Double.MAX_VALUE);
            trackPane.setMouseTransparent(false);
            clampWorkspaceDivider(workspaceSplit.getWidth());
        }
    }

    private void updateCoverExpansionHint() {
        String hint = t(songViewExpanded ? "点击封面恢复歌曲视图" : "点击封面展开歌曲视图");
        coverPane.setAccessibleText(hint);
        if (coverExpansionTooltip != null) {
            coverExpansionTooltip.setText(hint);
        }
    }

    private void configureCoverSizing() {
        coverImage.fitWidthProperty().bind(coverPane.widthProperty());
        coverImage.fitHeightProperty().bind(coverPane.heightProperty());
        coverImage.setPreserveRatio(true);
        previousCoverImage.fitWidthProperty().bind(coverPane.widthProperty());
        previousCoverImage.fitHeightProperty().bind(coverPane.heightProperty());
        previousCoverImage.setPreserveRatio(true);
        coverPane.prefWidthProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                () -> clampCoverSize(Math.min(visualStack.getWidth() - 56, visualStack.getHeight() - 8)),
                visualStack.widthProperty(), visualStack.heightProperty()));
        coverPane.prefHeightProperty().bind(coverPane.prefWidthProperty());
        coverPane.minWidthProperty().bind(coverPane.prefWidthProperty());
        coverPane.maxWidthProperty().bind(coverPane.prefWidthProperty());
        coverPane.minHeightProperty().bind(coverPane.prefWidthProperty());
        coverPane.maxHeightProperty().bind(coverPane.prefWidthProperty());
        lyricsPane.minHeightProperty().set(0);
        lyricsPane.prefHeightProperty().bind(coverPane.prefHeightProperty());
        lyricsPane.maxHeightProperty().bind(visualStack.heightProperty());
        lyricsList.minHeightProperty().set(0);
        lyricsList.prefHeightProperty().bind(lyricsPane.prefHeightProperty());
        lyricsList.maxHeightProperty().bind(lyricsPane.maxHeightProperty());
    }

    private void clampWorkspaceDivider(double width) {
        if (width <= 0 || workspaceSplit.getDividers().isEmpty()
                || songViewExpanded || songViewAnimation != null) {
            return;
        }
        double minimum = TRACK_PANE_MIN_WIDTH / width;
        double maximum = 1 - 390 / width;
        if (minimum > maximum) {
            return;
        }
        double current = workspaceSplit.getDividers().getFirst().getPosition();
        double clamped = Math.clamp(current, minimum, maximum);
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
        String localizedLabel = t(label);
        modeButton.setAccessibleText(localizedLabel);
        modeButton.setTooltip(new Tooltip(localizedLabel));
    }

    private void configureInteractions() {
        playlistList.getSelectionModel().selectedItemProperty().addListener((observable, oldName, name) -> {
            if (name != null) {
                showPlaylist(name);
            }
        });
        libraryTree.getSelectionModel().selectedItemProperty().addListener((observable, oldItem, item) -> {
            if (item != null) {
                trackTable.getSelectionModel().clearSelection();
            }
            if (item != null && item.getValue() != null && FileTreeService.isAudioFile(item.getValue())) {
                showLibrary();
            }
        });
        trackTable.getSelectionModel().selectedItemProperty().addListener((observable, oldTrack, track) -> {
            if (track != null) {
                libraryTree.getSelectionModel().clearSelection();
            }
        });
        clearSelectionOnBlank(playlistList, () -> playlistList.getSelectionModel().clearSelection());
        clearSelectionOnBlank(libraryTree, () -> libraryTree.getSelectionModel().clearSelection());
        clearSelectionOnBlank(trackTable, () -> trackTable.getSelectionModel().clearSelection());
        clearSelectionOnBlank(queueList, () -> queueList.getSelectionModel().clearSelection());
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
        MenuItem remove = new MenuItem(t("从当前队列移除"));
        remove.setOnAction(event -> context.music().removeQueueIndex(
                queueList.getSelectionModel().getSelectedIndex()));
        ContextMenu queueMenu = new ContextMenu(remove);
        queueMenu.setOnShowing(event -> remove.setText(t("从当前队列移除")));
        queueList.setContextMenu(queueMenu);
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
            playPauseButton.setAccessibleText(t(isPlaying ? "暂停" : "播放"));
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
        for (File rootFile : context.library().getLibraryEntries()) {
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
            trackCountLabel.setText(t("扫描中…"));
        }
        Task<List<Path>> scanTask = new Task<>() {
            @Override
            protected List<Path> call() {
                return context.library().scanAudioFiles();
            }
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
                trackCountLabel.setText(t("扫描失败"));
            }
            noticeService.addError(t("扫描音乐资料库失败。"));
        });
        Thread.ofPlatform().daemon().name("kimusic-library-scan").start(scanTask);
    }

    private void showLibrary() {
        playlistList.getSelectionModel().clearSelection();
        displayedTracks.setAll(libraryTracks);
        viewTitleLabel.setText(t("音乐资料库"));
        trackCountLabel.setText(formatCount(displayedTracks.size()));
    }

    private void showPlaylist(String name) {
        libraryTree.getSelectionModel().clearSelection();
        trackTable.getSelectionModel().clearSelection();
        displayedTracks.setAll(context.playlists().tracks(name));
        viewTitleLabel.getProperties().remove("i18nKey");
        viewTitleLabel.setText(name);
        trackCountLabel.setText(formatCount(displayedTracks.size()));
    }

    private void playTrack(Path track) {
        context.music().setQueue(new ArrayList<>(displayedTracks), track);
        updateQueueCount();
        context.music().open(track);
        context.save();
    }

    private void updateCurrentTrack(Path track) {
        resetLyricAutoFollow();
        waveformView.clear();
        if (track == null) {
            nowTitleLabel.setText(t("未在播放"));
            nowPathLabel.setText("");
            visualTitleLabel.setText(t("选择一首音乐"));
            visualPathLabel.setText("");
            setLyrics(List.of());
            updateArtwork(null);
            return;
        }
        writeTrackLabels(track);
        setLyrics(context.lyrics().load(track));
        lyricsList.getSelectionModel().clearSelection();
    }

    private void writeTrackLabels(Path track) {
        String title = titleOf(track);
        String location = track.getParent() == null ? "" : track.getParent().toString();
        nowTitleLabel.getProperties().remove("i18nKey");
        visualTitleLabel.getProperties().remove("i18nKey");
        nowTitleLabel.setText(title);
        nowPathLabel.setText(location);
        visualTitleLabel.setText(title);
        visualPathLabel.setText(location);
    }

    private void updateArtwork(Image image) {
        if (artworkAnimation != null) artworkAnimation.stop();
        Image previous = coverImage.getImage();
        double previousOpacity = coverImage.isVisible() ? coverImage.getOpacity() : 0;
        previousCoverImage.setImage(previous);
        previousCoverImage.setOpacity(previousOpacity);
        previousCoverImage.setVisible(previous != null);
        coverImage.setImage(image);
        coverImage.setPreserveRatio(true);
        coverImage.setVisible(image != null);
        coverImage.setOpacity(0);
        coverPlaceholder.setVisible(previous == null && image == null);
        coverPlaceholder.setManaged(previous == null && image == null);
        FadeTransition outgoing = new FadeTransition(Duration.millis(300), previousCoverImage);
        outgoing.setToValue(0);
        outgoing.setInterpolator(Interpolator.EASE_BOTH);
        FadeTransition incoming = new FadeTransition(Duration.millis(300), coverImage);
        incoming.setToValue(image == null ? 0 : 1);
        incoming.setInterpolator(Interpolator.EASE_BOTH);
        artworkAnimation = new ParallelTransition(outgoing, incoming);
        artworkAnimation.setOnFinished(event -> {
            previousCoverImage.setImage(null);
            previousCoverImage.setVisible(false);
            coverImage.setOpacity(image == null ? 0 : 1);
            coverPlaceholder.setVisible(image == null);
            coverPlaceholder.setManaged(image == null);
            artworkAnimation = null;
        });
        artworkAnimation.play();

        animateArtworkTone(image == null ? null : averageArtworkTone(image));
    }

    private void clearSelectionOnBlank(Node control, Runnable clearSelection) {
        control.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, event -> {
            Node target = event.getPickResult().getIntersectedNode();
            while (target != null && target != control) {
                if (target instanceof javafx.scene.control.IndexedCell<?> cell && !cell.isEmpty()) {
                    return;
                }
                target = target.getParent();
            }
            clearSelection.run();
        });
    }

    private Color averageArtworkTone(Image image) {
        PixelReader reader = image.getPixelReader();
        if (reader == null) {
            return artworkTone;
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
        return Color.rgb(
                (int) Math.clamp(red / Math.max(1, count) * 0.72, 0, 255),
                (int) Math.clamp(green / Math.max(1, count) * 0.72, 0, 255),
                (int) Math.clamp(blue / Math.max(1, count) * 0.72, 0, 255));
    }

    private void animateArtworkTone(Color target) {
        if (artworkToneAnimation != null) {
            artworkToneAnimation.stop();
        }
        Color start = displayedArtworkTone == null ? artworkTone : displayedArtworkTone;
        if (target == null) {
            if (start == null) {
                visualPane.setBackground(null);
                displayedArtworkTone = null;
                updateSongPresentation(null);
                return;
            }
            target = Color.color(start.getRed(), start.getGreen(), start.getBlue(), 0);
        }
        if (start == null) {
            start = target;
        }
        visualPane.setBackground(new Background(
                new BackgroundFill(start, CornerRadii.EMPTY, Insets.EMPTY)));
        displayedArtworkTone = start;
        updateSongPresentation(start);
        Color finalTarget = target;
        javafx.beans.property.ObjectProperty<Color> tone =
                new javafx.beans.property.SimpleObjectProperty<>(start);
        tone.addListener((observable, oldColor, color) -> {
            displayedArtworkTone = color;
            visualPane.setBackground(new Background(
                    new BackgroundFill(color, CornerRadii.EMPTY, Insets.EMPTY)));
            updateSongPresentation(color);
        });
        artworkToneAnimation = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(tone, start)),
                new KeyFrame(Duration.millis(340), event -> {
                    if (finalTarget.getOpacity() == 0) {
                        visualPane.setBackground(null);
                        artworkTone = null;
                        displayedArtworkTone = null;
                        updateSongPresentation(null);
                    } else {
                        artworkTone = finalTarget;
                        displayedArtworkTone = finalTarget;
                        updateSongPresentation(finalTarget);
                    }
                    artworkToneAnimation = null;
                }, new KeyValue(tone, finalTarget, Interpolator.EASE_BOTH)));
        artworkToneAnimation.play();
    }

    private void updateSongPresentation(Color background) {
        Color effective = compositeOver(background, DEFAULT_SONG_BACKGROUND);
        Background sharedBackground = new Background(
                new BackgroundFill(effective, CornerRadii.EMPTY, Insets.EMPTY));
        workspaceSplit.setBackground(sharedBackground);
        visualPane.setBackground(sharedBackground);
        double luminance = 0.2126 * effective.getRed() + 0.7152 * effective.getGreen()
                + 0.0722 * effective.getBlue();
        String toneClass = luminance < 0.48 ? "tone-dark" : "tone-light";
        visualPane.getStyleClass().removeAll("tone-light", "tone-dark");
        visualPane.getStyleClass().add(toneClass);

    }

    private Color compositeOver(Color foreground, Color background) {
        if (foreground == null) {
            return background;
        }
        double alpha = foreground.getOpacity();
        return Color.color(
                foreground.getRed() * alpha + background.getRed() * (1 - alpha),
                foreground.getGreen() * alpha + background.getGreen() * (1 - alpha),
                foreground.getBlue() * alpha + background.getBlue() * (1 - alpha));
    }

    private void applyLyricHighlightColor(String color) {
        String value = color == null ? "#1AA79B" : color;
        String style = "-kimusic-lyric-highlight: " + value + ";";
        if (style.equals(lyricsList.getStyle())) {
            return;
        }
        if (lyricsList.getStyle().isBlank() || root.getScene() == null || !lyricsVisible) {
            lyricsList.setStyle(style);
            lyricsList.refresh();
            return;
        }
        if (lyricStyleAnimation != null) {
            lyricStyleAnimation.stop();
            lyricsPane.setOpacity(1);
        }
        FadeTransition fadeOut = new FadeTransition(Duration.millis(110), lyricsPane);
        fadeOut.setToValue(0.62);
        fadeOut.setInterpolator(Interpolator.EASE_IN);
        fadeOut.setOnFinished(event -> {
            lyricsList.setStyle(style);
            lyricsList.refresh();
        });
        FadeTransition fadeIn = new FadeTransition(Duration.millis(180), lyricsPane);
        fadeIn.setToValue(1);
        fadeIn.setInterpolator(Interpolator.EASE_OUT);
        lyricStyleAnimation = new SequentialTransition(fadeOut, fadeIn);
        lyricStyleAnimation.setOnFinished(event -> lyricStyleAnimation = null);
        lyricStyleAnimation.play();
    }

    private void updateActiveLyric(double seconds) {
        int lyricIndex = context.lyrics().activeLineIndex(lyricLines, seconds);
        int displayIndex = lyricIndex < 0 ? -1 : lyricIndex + 1;
        if (displayIndex >= 0 && displayIndex != lyricsList.getSelectionModel().getSelectedIndex()) {
            lyricsList.getSelectionModel().select(displayIndex);
            if (lyricsVisible && !lyricAutoFollowSuspended) {
                smoothScrollLyrics(displayIndex);
            }
        }
    }

    private void smoothScrollLyrics(int index) {
        Platform.runLater(() -> {
            if (lyricAutoFollowSuspended || index < 0 || index >= displayedLyrics.size()) {
                return;
            }
            Node flowNode = lyricsList.lookup(".virtual-flow");
            if (!(flowNode instanceof VirtualFlow<?> flow)) {
                lyricsList.scrollTo(index);
                return;
            }
            flow.scrollTo(index);
            lyricsList.applyCss();
            lyricsList.layout();
            javafx.scene.control.IndexedCell<?> cell = flow.getVisibleCell(index);
            if (cell == null) {
                lyricsList.scrollTo(index);
                return;
            }
            double targetPixels = lyricCenterOffset(flow, cell);
            if (Math.abs(targetPixels) < 0.5) {
                return;
            }
            if (lyricScrollAnimation != null) {
                lyricScrollAnimation.stop();
            }
            SimpleDoubleProperty animatedPixels = new SimpleDoubleProperty();
            double[] previousPixels = {0};
            animatedPixels.addListener((observable, oldValue, newValue) -> {
                double next = newValue.doubleValue();
                flow.scrollPixels(next - previousPixels[0]);
                previousPixels[0] = next;
            });
            lyricScrollAnimation = new Timeline(
                    new KeyFrame(Duration.millis(260),
                            new KeyValue(animatedPixels, targetPixels, Interpolator.EASE_BOTH)));
            lyricScrollAnimation.setOnFinished(event -> {
                lyricScrollAnimation = null;
                lyricsList.layout();
                javafx.scene.control.IndexedCell<?> finalCell = flow.getVisibleCell(index);
                if (finalCell != null) {
                    flow.scrollPixels(lyricCenterOffset(flow, finalCell));
                    lyricsList.layout();
                }
            });
            lyricScrollAnimation.play();
        });
    }

    static double lyricCenterOffset(double viewportCenterY, double lyricCenterY) {
        if (!Double.isFinite(viewportCenterY) || !Double.isFinite(lyricCenterY)) {
            return 0;
        }
        return lyricCenterY - viewportCenterY;
    }

    private static double lyricCenterOffset(VirtualFlow<?> flow,
                                            javafx.scene.control.IndexedCell<?> cell) {
        double contentHeight = Math.max(0,
                cell.getHeight() - cell.getPadding().getTop() - cell.getPadding().getBottom());
        double contentCenter = cell.getPadding().getTop() + contentHeight / 2;
        double lyricCenterY = cell.localToScene(0, contentCenter).getY();
        double viewportCenterY = flow.localToScene(0, flow.getHeight() / 2).getY();
        return lyricCenterOffset(viewportCenterY, lyricCenterY);
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
        queueCountLabel.setText(formatCount(size));
        queueButton.setText(t("播放列表 ") + size);
    }

    private void applyTheme(boolean dark) {
        boolean themeAlreadyApplied = root.getStyleClass().contains(dark ? "theme-dark" : "theme-light");
        boolean hasAppliedTheme = root.getStyleClass().contains("theme-dark")
                || root.getStyleClass().contains("theme-light");
        boolean changed = context.isDarkTheme() != dark;
        context.setDarkTheme(dark);
        if (!changed && themeAlreadyApplied) {
            return;
        }
        if (root.getScene() == null || !hasAppliedTheme) {
            applyThemeStyles(dark);
            return;
        }
        if (themeAnimation != null) {
            themeAnimation.stop();
            themeNodeOpacities.forEach(Node::setOpacity);
            themeNodeOpacities = Map.of();
        }
        List<Node> transitionNodes = themeTransitionNodes();
        Map<Node, Double> originalOpacities = new IdentityHashMap<>();
        transitionNodes.forEach(node -> originalOpacities.put(node, node.getOpacity()));
        themeNodeOpacities = originalOpacities;
        List<FadeTransition> fadeOuts = transitionNodes.stream().map(node -> {
            double originalOpacity = originalOpacities.get(node);
            FadeTransition fade = new FadeTransition(Duration.millis(140), node);
            fade.setFromValue(originalOpacity);
            fade.setToValue(originalOpacity * 0.24);
            fade.setInterpolator(Interpolator.EASE_IN);
            return fade;
        }).toList();
        List<FadeTransition> fadeIns = transitionNodes.stream().map(node -> {
            double originalOpacity = originalOpacities.get(node);
            FadeTransition fade = new FadeTransition(Duration.millis(220), node);
            fade.setFromValue(originalOpacity * 0.24);
            fade.setToValue(originalOpacity);
            fade.setInterpolator(Interpolator.EASE_OUT);
            return fade;
        }).toList();
        ParallelTransition fadeOut = new ParallelTransition(fadeOuts.toArray(FadeTransition[]::new));
        ParallelTransition fadeIn = new ParallelTransition(fadeIns.toArray(FadeTransition[]::new));
        fadeOut.setOnFinished(event -> {
            applyThemeStyles(dark);
            root.applyCss();
        });
        SequentialTransition animation = new SequentialTransition(fadeOut, fadeIn);
        animation.setOnFinished(event -> {
            originalOpacities.forEach(Node::setOpacity);
            if (themeAnimation == animation) {
                themeNodeOpacities = Map.of();
                themeAnimation = null;
            }
        });
        themeAnimation = animation;
        animation.play();
    }

    private List<Node> themeTransitionNodes() {
        List<Node> nodes = new ArrayList<>();
        topBar.getChildren().stream().filter(node -> node != noticeLabel).forEach(nodes::add);
        nodes.addAll(sidebar.getChildren());
        nodes.addAll(trackPane.getChildren());
        nodes.addAll(queueDrawer.getChildren());
        nodes.addAll(playerBar.getChildren());
        return nodes;
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
        themeButton.setTooltip(new Tooltip(t(dark ? "切换浅色" : "切换深色")));
    }

    private String t(String key) {
        return I18n.text(language, key);
    }

    void applyLanguage(Language next) {
        language = next == null ? Language.CHINESE : next;
        if (context != null) context.setLanguage(language);
        for (Node node : root.lookupAll("*")) {
            if (node instanceof Labeled labeled) {
                String key = (String) labeled.getProperties().get("i18nKey");
                if (key == null) {
                    key = I18n.keyFor(labeled.getText());
                    if (key != null) labeled.getProperties().put("i18nKey", key);
                }
                if (key != null) labeled.setText(t(key));
            }
            String accessibleKey = (String) node.getProperties().get("accessibleI18nKey");
            if (accessibleKey == null) {
                accessibleKey = I18n.keyFor(node.getAccessibleText());
                if (accessibleKey != null) node.getProperties().put("accessibleI18nKey", accessibleKey);
            }
            if (accessibleKey != null) node.setAccessibleText(t(accessibleKey));

        }
        titleColumn.setText(t("曲目"));
        folderColumn.setText(t("位置"));
        playlistActionColumn.setText(t("加入歌单"));
        equalizerButton.setTooltip(new Tooltip(t("均衡器")));
        updateModeIcon(context == null ? PlayMode.SEQUENTIAL : context.music().playModeProperty().get());
        updateCoverExpansionHint();
        if (context != null) {
            boolean dark = context.isDarkTheme();
            themeIcon.setResource(dark
                    ? "/club/muimi/kimusic/icons/sun.svg"
                    : "/club/muimi/kimusic/icons/moon.svg");
            themeButton.setTooltip(new Tooltip(t(dark ? "切换浅色" : "切换深色")));
        }
        refreshCurrentViewLabels();
        refreshPlayerLabels();
    }

    private void refreshCurrentViewLabels() {
        if (playlistList.getSelectionModel().getSelectedItem() == null) {
            viewTitleLabel.setText(t("音乐资料库"));
        }
        trackCountLabel.setText(formatCount(displayedTracks.size()));
    }

    private void refreshPlayerLabels() {
        if (context == null) return;
        Path currentTrack = context.music().currentTrackProperty().get();
        if (currentTrack == null) {
            nowTitleLabel.setText(t("未在播放"));
            visualTitleLabel.setText(t("选择一首音乐"));
        } else {
            writeTrackLabels(currentTrack);
        }
        queueButton.setText(t("播放列表 ") + context.music().getQueue().size());
        queueCountLabel.setText(formatCount(context.music().getQueue().size()));
    }

    private String formatCount(int size) {
        return language == Language.ENGLISH ? size + (size == 1 ? " track" : " tracks") : size + " 首";
    }

    private double clampCoverSize(double value) {
        if (!Double.isFinite(value) || value <= 0) return 120;
        return Math.max(120, Math.min(440, value));
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
        Object active = node.getProperties().get("scaleTransition");
        if (active instanceof ScaleTransition transition) transition.stop();
        ScaleTransition transition = new ScaleTransition(Duration.millis(millis), node);
        transition.setInterpolator(Interpolator.EASE_BOTH);
        transition.setToX(scale);
        transition.setToY(scale);
        transition.setOnFinished(event -> node.getProperties().remove("scaleTransition"));
        node.getProperties().put("scaleTransition", transition);
        transition.play();
    }

    private void showNextNotice() {
        if (noticeAnimation != null || noticeService == null) {
            return;
        }
        Notice notice = noticeService.takeNoticeEntry();
        if (notice == null) {
            return;
        }

        String noticeKey = I18n.keyFor(notice.message());
        noticeLabel.setText(noticeKey == null ? notice.message() : t(noticeKey));
        noticeLabel.getStyleClass().removeAll("notice-info", "notice-warning", "notice-error");
        noticeLabel.getStyleClass().add(switch (notice.level()) {
            case INFO -> "notice-info";
            case WARNING -> "notice-warning";
            case ERROR -> "notice-error";
        });
        noticeLabel.setOpacity(0);
        noticeLabel.setVisible(true);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(160), noticeLabel);
        fadeIn.setToValue(1);
        PauseTransition delay = new PauseTransition(Duration.seconds(2.5));
        FadeTransition fadeOut = new FadeTransition(Duration.millis(180), noticeLabel);
        fadeOut.setToValue(0);

        noticeAnimation = new SequentialTransition(fadeIn, delay, fadeOut);
        noticeAnimation.setOnFinished(event -> {
            noticeLabel.setVisible(false);
            noticeAnimation = null;
            showNextNotice();
        });
        noticeAnimation.play();
    }

    void initializeNotices(NoticeService notices) {
        noticeService = Objects.requireNonNull(notices, "notices");
        noticeService.setOnNoticesAvailable(() -> Platform.runLater(this::showNextNotice));
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
