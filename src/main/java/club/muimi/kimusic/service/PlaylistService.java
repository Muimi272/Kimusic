package club.muimi.kimusic.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PlaylistService {
    private final Map<String, List<Path>> playlists = new LinkedHashMap<>();

    public boolean create(String rawName) {
        String name = normalizeName(rawName);
        if (name.isEmpty() || playlists.containsKey(name)) {
            return false;
        }
        playlists.put(name, new ArrayList<>());
        return true;
    }

    public boolean remove(String name) {
        return playlists.remove(name) != null;
    }

    public boolean addTrack(String name, Path path) {
        List<Path> tracks = playlists.get(name);
        if (tracks == null || path == null) {
            return false;
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (tracks.contains(normalized)) {
            return false;
        }
        tracks.add(normalized);
        return true;
    }

    public boolean removeTrack(String name, Path path) {
        List<Path> tracks = playlists.get(name);
        return tracks != null && tracks.remove(path.toAbsolutePath().normalize());
    }

    public List<String> names() {
        return List.copyOf(playlists.keySet());
    }

    public List<Path> tracks(String name) {
        return List.copyOf(playlists.getOrDefault(name, List.of()));
    }

    public Map<String, List<String>> snapshot() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        playlists.forEach((name, tracks) ->
                result.put(name, tracks.stream().map(Path::toString).toList()));
        return result;
    }

    public void restore(Map<String, List<String>> data) {
        playlists.clear();
        data.forEach((rawName, values) -> {
            String name = normalizeName(rawName);
            if (!name.isEmpty()) {
                List<Path> tracks = values.stream()
                        .map(Path::of)
                        .map(path -> path.toAbsolutePath().normalize())
                        .filter(Files::isRegularFile)
                        .distinct()
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
                playlists.put(name, tracks);
            }
        });
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.strip();
    }
}
