package club.muimi.kimusic.util;

import club.muimi.kimusic.status.Language;

import java.util.Map;
import java.util.Map.Entry;

public final class I18n {
    private static final Map<String, String> ENGLISH = Map.ofEntries(
            Map.entry("本地音乐", "Local Music"), Map.entry("我的歌单", "Playlists"),
            Map.entry("音乐资料库", "Music Library"), Map.entry("全部曲目", "All Tracks"),
            Map.entry("加入歌单", "Add to Playlist"), Map.entry("曲目", "Track"),
            Map.entry("位置", "Location"), Map.entry("从左侧导入你的第一首音乐", "Import your first track from the left"),
            Map.entry("歌词", "Lyrics"), Map.entry("当前曲目没有歌词", "No lyrics for this track"),
            Map.entry("选择一首音乐", "Choose a track"), Map.entry("当前播放列表", "Queue"),
            Map.entry("未在播放", "Not playing"), Map.entry("播放列表", "Queue"),
            Map.entry("设置", "Settings"), Map.entry("应用", "Apply"), Map.entry("取消", "Cancel"),
            Map.entry("浅色", "Light"), Map.entry("深色", "Dark"), Map.entry("波形", "Waveform"),
            Map.entry("频谱", "Spectrum"), Map.entry("外观", "Appearance"), Map.entry("界面主题", "Theme"),
            Map.entry("界面语言", "Language"),
            Map.entry("界面", "Interface"), Map.entry("歌词高亮颜色", "Lyric highlight color"),
            Map.entry("自动解码 NCM", "Automatically decode NCM"),
            Map.entry("点击封面展开歌曲视图", "Click the cover to expand the song view"),
            Map.entry("点击封面恢复歌曲视图", "Click the cover to restore the song view"),
            Map.entry("可视化", "Visualization"), Map.entry("播放画面", "Playback view"), Map.entry("播放", "Playback"),
            Map.entry("默认音量", "Default volume"), Map.entry("歌词字号", "Lyric size"), Map.entry("歌单名称", "Playlist name"),
            Map.entry("目标歌单", "Target playlist"), Map.entry("新建歌单", "New Playlist"),
            Map.entry("选择音频文件", "Choose audio files"), Map.entry("选择音乐文件夹", "Choose music folder"),
            Map.entry("音频文件", "Audio files"),
            Map.entry("导入音频文件", "Import audio files"), Map.entry("导入音乐文件夹", "Import music folder"),
            Map.entry("播放列表 ", "Queue "), Map.entry("扫描中…", "Scanning…"), Map.entry("扫描失败", "Scan failed"),
            Map.entry("首", " tracks"), Map.entry("移除根目录", "Remove folder"), Map.entry("关闭播放列表", "Close queue"),
            Map.entry("上一首", "Previous track"), Map.entry("下一首", "Next track"), Map.entry("顺序播放", "Sequential playback"),
            Map.entry("切换主题", "Toggle theme"), Map.entry("折叠菜单", "Toggle menu"), Map.entry("导入音乐", "Import music"),
            Map.entry("已成功导入音乐文件。", "Audio files imported successfully."),
            Map.entry("导入音乐文件失败，请检查文件格式或是否重复。", "Audio files could not be imported. Check their format or whether they are duplicates."),
            Map.entry("已成功导入音乐文件夹。", "Music folder imported successfully."),
            Map.entry("导入音乐文件夹失败，请检查文件夹是否重复。", "Music folder could not be imported. Check whether it is already in the library."),
            Map.entry("歌单名称为空或已经存在。", "The playlist name is empty or already exists."),
            Map.entry("请先选择一首曲目。", "Select a track first."), Map.entry("请先新建歌单。", "Create a playlist first."),
            Map.entry("曲目已经在该歌单中。", "This track is already in the playlist."),
            Map.entry("扫描音乐资料库失败。", "The music library scan failed."),
            Map.entry("从当前歌单移除", "Remove from playlist"), Map.entry("从当前队列移除", "Remove from queue"),
            Map.entry("暂停", "Pause"), Map.entry("切换浅色", "Switch to light theme"),
            Map.entry("切换深色", "Switch to dark theme"), Map.entry("顺序", "Sequential"),
            Map.entry("单曲循环", "Repeat one"), Map.entry("列表循环", "Repeat all"), Map.entry("随机", "Shuffle"),
            Map.entry("删除歌单", "Delete playlist"),
            Map.entry("保存的音乐资料库格式不受支持。", "The saved library uses an unsupported format."),
            Map.entry("无法恢复保存的音乐资料库。", "The saved library could not be restored."),
            Map.entry("无法保存音乐资料库状态。", "The library state could not be saved."),
            Map.entry("LOCAL SOUND", "LOCAL SOUND"), Map.entry("KIMUSIC PREFERENCES", "KIMUSIC PREFERENCES")
    );

    private I18n() {}

    public static String text(Language language, String chinese) {
        return language == Language.ENGLISH ? ENGLISH.getOrDefault(chinese, chinese) : chinese;
    }

    public static String keyFor(String value) {
        if (value == null) return null;
        if (ENGLISH.containsKey(value)) return value;
        return ENGLISH.entrySet().stream().filter(entry -> entry.getValue().equals(value))
                .map(Entry::getKey).findFirst().orElse(null);
    }
}
