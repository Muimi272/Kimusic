module club.muimi.kimusic {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires atlantafx.base;
    requires java.desktop;
    requires java.xml;
    requires dev.mccue.mp3spi;
    requires dev.mccue.vorbisspi;
    requires jflac.codec;
    requires jaudiotagger;

    opens club.muimi.kimusic.view to javafx.fxml;
    exports club.muimi.kimusic;
    exports club.muimi.kimusic.status;
    exports club.muimi.kimusic.view.component;
}
