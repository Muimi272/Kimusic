<p align="center">
  <img src="src/main/resources/club/muimi/kimusic/logo.png" width="128" height="128" alt="Kimusic 应用 Logo">
</p>

<h1 align="center">Kimusic</h1>

<p align="center">一款基于 JavaFX 构建、本地优先的桌面音乐播放器。</p>

<p align="center"><a href="README.md">English</a> | <strong>简体中文</strong></p>

## 介绍

Kimusic 是一款跨平台桌面音乐播放器，用于整理和播放个人本地音乐库。应用采用紧凑的双栏音乐库与播放界面，并集成歌单、同步歌词、专辑封面、波形和频谱可视化，以及浅色和深色主题。

Kimusic 直接处理本地文件，不需要账号或云服务。NCM 容器由内置的纯 Java 解码器处理，因此播放 NCM 文件不依赖用户另外安装特定平台的可执行程序。

## 核心特点

- **本地优先：**音乐、歌词、歌单、设置及解码结果均保留在本机。
- **跨平台：**Release 提供 64 位 Windows 和 x86-64 Linux 客户端。
- **广泛的格式支持：**支持 MP3、FLAC、WAV、AAC、AIFF、M4A、OGG 和 NCM。
- **内置 NCM 支持：**Windows 和 Linux 使用同一套纯 Java 解码器，外部解码器仅作为可选兜底。
- **专注播放的界面：**无需离开主窗口即可管理音乐库、播放队列、歌词、封面和可视化效果。

## 功能

- 导入单个音频文件或文件夹，并通过文件树浏览本地音乐库。
- 自动发现已导入音乐库目录中新增加的受支持音频文件。
- 创建歌单、添加或移除歌曲，并在下次启动时恢复。
- 管理当前播放队列，支持顺序播放、列表循环、单曲循环和随机播放。
- 读取内嵌音频元数据和封面，也可使用同目录的封面图片。
- 查找同目录歌词文件、高亮并居中当前歌词；用户手动滚动后会暂时停止跟随。
- 在波形图和频率频谱两种可视化模式之间切换。
- 支持浅色/深色主题、歌词样式设置、平滑滚动、播放淡入淡出和封面取色背景。
- 支持英文和简体中文界面。
- 播放 NCM 前将其解码到本机私有缓存。

## 安装方式（Release）

请从 [GitHub Releases 页面](https://github.com/Muimi272/Kimusic/releases)下载适合当前系统的安装包。早期版本可能标记为预发布版本，安装前请先阅读对应的 Release Notes。

### Windows

下载 <code>.exe</code> 安装程序或 <code>.msi</code> 安装包并正常运行。EXE 安装程序支持创建开始菜单和桌面快捷方式。

### Linux

Debian、Ubuntu 及兼容发行版可安装 DEB 包：

~~~bash
sudo apt install ./kimusic_*.deb
~~~

也可以下载 x86-64 AppImage：

~~~bash
chmod +x Kimusic-x86_64.AppImage
./Kimusic-x86_64.AppImage
~~~

如果系统没有提供 FUSE 支持，可使用 <code>--appimage-extract-and-run</code> 参数运行 AppImage。


## 开发环境要求

- JDK 21 或更高版本；打包客户端需要 JDK 中的 <code>jpackage</code>。
- Git。
- Maven 3.9，或使用仓库内置的 Maven Wrapper。Wrapper 和项目依赖在首次运行时需要网络连接。
- 如需复现官方 Release，需要 64 位 Windows 或 x86-64 Linux 环境。
- Windows EXE/MSI 打包需要 WiX Toolset 3.x。
- Linux DEB 打包需要 <code>fakeroot</code> 和 Debian 打包工具。

运行测试并从源码启动应用：

~~~bash
./mvnw test
./mvnw javafx:run
~~~

在 Windows PowerShell 中，请将 <code>./mvnw</code> 替换为 <code>.\mvnw.cmd</code>。

NCM 解码不需要外部软件。如果需要为内置解码器无法处理的文件配置可选兜底程序，可将 <code>KIMUSIC_NCMDUMP</code> 设置为兼容 ncmdump 命令行接口的可执行文件绝对路径。

## 数据和隐私

Kimusic 不提供账号系统、行为分析、遥测、云同步或运行时上传功能。应用仅读取用户主动选择的目录和文件，以及匹配的本地歌词和封面文件。

应用数据保存在当前用户主目录下的 <code>.kimusic</code> 目录：

- <code>~/.kimusic/state.bin</code> 保存已导入音乐库目录路径、歌单及歌曲路径、播放队列、最后选择的歌曲、音量、主题、语言、可视化模式、歌词样式和自动解码 NCM 设置。
- <code>~/.kimusic/cache/ncm/</code> 保存 NCM 文件解码后生成的音频；在可用时还会包含写入的元数据和内嵌封面。这些文件会一直保留，直至用户手动删除缓存。

在 Windows 中，<code>~</code> 通常对应 <code>%USERPROFILE%</code>；在 Linux 中通常对应 <code>$HOME</code>。删除 <code>.kimusic</code> 目录会重置应用保存的状态并移除 NCM 解码缓存，但不会修改或删除原始音乐、歌词和封面文件。

## 参与贡献

欢迎提交 Issue 和 Pull Request：

1. 新建 Issue 前请先搜索是否已有相同问题。
2. Fork 仓库并创建目标明确的分支。
3. 保持修改范围集中，行为发生变化时请添加或更新测试。
4. 提交 Pull Request 前运行 <code>./mvnw clean test</code>。
5. 在 Pull Request 中说明修改动机、用户可见行为及已测试的平台。

请勿提交受版权保护的音乐、解密后的音频、账号数据或其他私有测试材料。测试应使用合成数据或允许再分发的素材。

## 开源许可

Kimusic 采用 [MIT License](LICENSE) 开源，Copyright 2026 Muimi272。

第三方库、字体和改编源码仍遵循各自的许可证。内置 NCM 解码器改编自采用 MIT 许可证的 [qaralotte/ncmdump](https://github.com/qaralotte/ncmdump)，其许可证文本已包含在应用资源中。

## 免责声明

Kimusic 不提供、托管、销售或分发音乐。音乐作品、录音制品、歌词、专辑封面及相关内容的版权归相应唱片公司、创作者、表演者、出版方及其他权利人所有。

NCM 解析和解码功能仅用于用户已合法购买或以其他合法方式获得访问授权的音乐进行个人格式转换和播放。不得使用该功能侵犯版权、在未经授权的情况下规避访问限制、传播解码后的文件，或违反适用法律及服务协议。用户应自行确保其使用行为合法合规。

Kimusic 是独立开源项目，与网易云音乐及任何唱片公司均无隶属、认可或赞助关系。

## 感谢

Kimusic 使用或参考了以下开源项目：

- [OpenJFX](https://openjfx.io/)：桌面界面和媒体支持。
- [AtlantaFX](https://github.com/mkpaz/atlantafx)：JavaFX 设计系统。
- [qaralotte/ncmdump](https://github.com/qaralotte/ncmdump)：NCM 容器解析和解密流程参考。
- [jaudiotagger](https://www.jthink.net/jaudiotagger/)：音频元数据和封面处理。
- [MP3SPI](https://github.com/umjammer/mp3spi)、[VorbisSPI](https://github.com/umjammer/vorbisspi) 和 [jFLAC](https://github.com/jflac/jflac-codec)：Java 音频格式支持。
- [Gson](https://github.com/google/gson)：元数据 JSON 解析。
- [Source Han Sans](https://github.com/adobe-fonts/source-han-sans)、[Noto CJK](https://github.com/notofonts/noto-cjk) 和 [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono)：应用内置字体。
