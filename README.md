# Kimusic

Kimusic 是一个基于 JavaFX 的本地音乐播放器，面向希望管理本地音乐、歌单和歌词的桌面用户。应用采用简洁的双栏布局，支持浅色和深色主题，并提供可调整的音乐库与播放视图。

## 功能

- 导入音频文件或文件夹，使用文件树管理本地音乐库
- 支持 MP3、FLAC、WAV、AAC、AIFF、M4A、OGG 和 NCM 文件
- NCM 文件通过 ncmdump 解码后播放
- 自定义歌单、当前播放列表和四种播放模式：顺序、列表循环、单曲循环、随机
- 自动读取音频内嵌封面或同目录封面图片
- 自动查找同目录歌词文件并滚动显示
- 波形图和频谱可视化，可在设置中切换
- 可拖动的音乐库/播放视图分隔栏
- 浅色/深色主题、平滑歌词滚动、播放淡入淡出和封面色彩背景
- 音乐库目录自动扫描新增音频文件
- 库、歌单、播放队列、主题和上次播放歌曲状态持久化保存

## 环境要求

- JDK 21 或更高版本
- Maven 3.9 或使用仓库中的 Maven Wrapper
- Windows 下播放 NCM 文件需要随应用提供的 `ncmdump.exe`

## 本地开发

```bash
mvn test
mvn javafx:run
```

Windows PowerShell：

```powershell
.\mvnw.cmd test
.\mvnw.cmd javafx:run
```

## 构建

构建普通 JAR：

```bash
mvn package
```

Windows 安装包使用 JDK 自带的 `jpackage`。完整发行包和校验文件会发布在 GitHub Releases 中。

## 数据和隐私

Kimusic 只读取用户主动导入的本地音乐目录。应用状态默认保存在用户目录下的 `.kimusic/state.bin`，不上传音乐文件、歌词或播放记录。

## 许可证

本项目使用 MIT 许可证，详见 [LICENSE](LICENSE)。
