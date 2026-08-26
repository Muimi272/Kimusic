package club.muimi.kimusic.service;

import java.util.Objects;

public record Notice(NoticeLevel level, String message) {
    public Notice {
        level = Objects.requireNonNullElse(level, NoticeLevel.INFO);
        message = Objects.requireNonNullElse(message, "");
    }
}
