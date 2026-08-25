package club.muimi.kimusic.service;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LyricsService {
    private static final Pattern TIMESTAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");

    public Optional<Path> findForTrack(Path track) {
        if (track == null || track.getParent() == null) {
            return Optional.empty();
        }
        String fileName = track.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String baseName = (dot < 0 ? fileName : fileName.substring(0, dot)).toLowerCase(Locale.ROOT);
        try (var files = Files.list(track.getParent())) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> {
                        String candidate = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return candidate.equals(baseName + ".lrc") || candidate.equals(baseName + ".txt");
                    })
                    .sorted(Comparator.comparing(path -> path.toString().endsWith(".lrc") ? 0 : 1))
                    .findFirst();
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    public List<LyricLine> load(Path track) {
        Optional<Path> lyricFile = findForTrack(track);
        if (lyricFile.isEmpty()) {
            return List.of();
        }
        try {
            return parse(readLines(lyricFile.get()));
        } catch (IOException exception) {
            return List.of();
        }
    }

    public List<LyricLine> parse(List<String> rawLines) {
        List<LyricLine> result = new ArrayList<>();
        for (String rawLine : rawLines) {
            Matcher matcher = TIMESTAMP.matcher(rawLine);
            List<Double> timestamps = new ArrayList<>();
            int textStart = 0;
            while (matcher.find()) {
                double fraction = parseFraction(matcher.group(3));
                timestamps.add(Integer.parseInt(matcher.group(1)) * 60.0
                        + Integer.parseInt(matcher.group(2)) + fraction);
                textStart = matcher.end();
            }
            String text = rawLine.substring(textStart).strip();
            if (!timestamps.isEmpty()) {
                for (double timestamp : timestamps) {
                    result.add(new LyricLine(timestamp, text));
                }
            } else if (!text.isEmpty() && !rawLine.startsWith("[")) {
                result.add(new LyricLine(Double.POSITIVE_INFINITY, text));
            }
        }
        result.sort(Comparator.comparingDouble(LyricLine::seconds));
        return List.copyOf(result);
    }

    public int activeLineIndex(List<LyricLine> lines, double seconds) {
        int active = -1;
        for (int index = 0; index < lines.size(); index++) {
            if (!Double.isFinite(lines.get(index).seconds()) || lines.get(index).seconds() > seconds) {
                break;
            }
            active = index;
        }
        return active;
    }

    private List<String> readLines(Path path) throws IOException {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            Charset fallback = Charset.defaultCharset();
            if (fallback.equals(StandardCharsets.UTF_8)) {
                throw exception;
            }
            return Files.readAllLines(path, fallback);
        }
    }

    private double parseFraction(String value) {
        if (value == null) {
            return 0;
        }
        return Integer.parseInt(value) / Math.pow(10, value.length());
    }

    public record LyricLine(double seconds, String text) {
    }
}
