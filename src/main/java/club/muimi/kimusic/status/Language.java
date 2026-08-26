package club.muimi.kimusic.status;

public enum Language {
    CHINESE, ENGLISH;

    public Language toggle() {
        return this == CHINESE ? ENGLISH : CHINESE;
    }
}
