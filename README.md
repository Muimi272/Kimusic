<p align="center">
  <img src="src/main/resources/club/muimi/kimusic/logo.png" width="128" height="128" alt="Kimusic logo">
</p>

<h1 align="center">Kimusic</h1>

<p align="center">A local-first desktop music player built with JavaFX.</p>

<p align="center"><strong>English</strong> | <a href="README%20-%20ZH.md">简体中文</a></p>

## Introduction

Kimusic is a cross-platform desktop player for organizing and playing a personal local music library. It combines a compact two-pane library and playback layout with playlists, synchronized lyrics, artwork, waveform and spectrum visualizations, and light and dark themes.

Kimusic works with local files and does not require an account or cloud service. NCM containers are handled by a built-in pure Java decoder, so NCM playback does not depend on a separately installed platform executable.

## Core Highlights

- **Local-first:** music, lyrics, playlists, settings, and decoded files stay on the local computer.
- **Cross-platform:** release packages are built for 64-bit Windows and x86-64 Linux.
- **Broad format support:** MP3, FLAC, WAV, AAC, AIFF, M4A, OGG, and NCM.
- **Built-in NCM support:** the same pure Java decoder is used on Windows and Linux; an external decoder is only an optional fallback.
- **Playback-focused interface:** library management, queue controls, lyrics, artwork, and visualization remain available without leaving the main window.

## Features

- Import individual audio files or folders and browse them in a file tree.
- Automatically discover new supported files under imported library folders.
- Create playlists, add or remove tracks, and restore them between sessions.
- Manage the queue with sequential, repeat-all, repeat-one, and shuffle modes.
- Read embedded metadata and artwork, with support for companion cover images.
- Find companion lyric files, highlight and center the current line, and temporarily pause following during manual scrolling.
- Switch between waveform and frequency-spectrum visualizations.
- Use light and dark themes, configurable lyric styling, smooth scrolling, playback fades, and artwork-derived backgrounds.
- Switch the interface between English and Simplified Chinese.
- Decode NCM files into a private local cache before playback.

## Installation (Releases)

Download a package from the [GitHub Releases page](https://github.com/Muimi272/Kimusic/releases). Review the release notes before installing because early releases may be marked as pre-release builds.

### Windows

Download either the <code>.exe</code> installer or the <code>.msi</code> package, then run it normally. The EXE installer can create Start Menu and desktop shortcuts.

### Linux

Use the Debian package on Debian, Ubuntu, or compatible distributions:

~~~bash
sudo apt install ./kimusic_*.deb
~~~

Alternatively, download the x86-64 AppImage:

~~~bash
chmod +x Kimusic-x86_64.AppImage
./Kimusic-x86_64.AppImage
~~~

If the system does not provide FUSE support, run the AppImage with <code>--appimage-extract-and-run</code>.


## Development Requirements

- JDK 21 or newer, including <code>jpackage</code> for client packaging.
- Git.
- Maven 3.9 or the included Maven Wrapper. The wrapper and dependencies require internet access on their first run.
- A 64-bit Windows or x86-64 Linux environment for reproducing the official release packages.
- WiX Toolset 3.x for Windows EXE/MSI packaging.
- <code>fakeroot</code> and Debian packaging tools for Linux DEB packaging.

Run the test suite and start the application from source with:

~~~bash
./mvnw test
./mvnw javafx:run
~~~

On Windows PowerShell, use <code>.\mvnw.cmd</code> instead of <code>./mvnw</code>.

NCM decoding works without external software. To provide an optional fallback for files rejected by the built-in implementation, set <code>KIMUSIC_NCMDUMP</code> to the absolute path of an executable compatible with the ncmdump command-line interface.

## Data and Privacy

Kimusic has no account system, analytics, telemetry, cloud synchronization, or runtime upload feature. It reads the folders and files selected by the user, plus matching local lyric and artwork files.

Application data is stored under <code>.kimusic</code> in the current user's home directory:

- <code>~/.kimusic/state.bin</code> stores imported library folder paths, playlists and their track paths, the playback queue, the last selected track, volume, theme, language, visualization mode, lyric styling, and the automatic NCM decoding preference.
- <code>~/.kimusic/cache/ncm/</code> stores decoded audio produced from NCM files, including metadata and embedded artwork where available. These files remain until the cache is deleted manually.

On Windows, <code>~</code> normally resolves to <code>%USERPROFILE%</code>. On Linux, it normally resolves to <code>$HOME</code>. Deleting the <code>.kimusic</code> directory resets saved state and removes the decoded NCM cache; original music, lyric, and artwork files are not modified or deleted.

## Contributing

Issues and pull requests are welcome:

1. Search existing issues before opening a new one.
2. Fork the repository and create a focused branch.
3. Keep changes scoped and add or update tests for behavior changes.
4. Run <code>./mvnw clean test</code> before submitting a pull request.
5. Describe the motivation, user-visible behavior, and platforms tested.

Do not commit copyrighted music, decrypted audio, account data, or other private test material. Use synthetic or freely redistributable fixtures.

## Open Source License

Kimusic is distributed under the [MIT License](LICENSE), copyright 2026 Muimi272.

Third-party libraries, fonts, and adapted source remain subject to their own licenses. The built-in NCM decoder is adapted from the MIT-licensed [qaralotte/ncmdump](https://github.com/qaralotte/ncmdump); its license text is included in the application resources.

## Disclaimer

Kimusic does not provide, host, sell, or distribute music. Copyright in music, sound recordings, lyrics, album artwork, and related material belongs to the respective record companies, creators, performers, publishers, and other rights holders.

The NCM parsing and decoding feature is intended solely for personal format conversion and playback of music that the user has lawfully purchased or is otherwise authorized to access. It must not be used to infringe copyright, circumvent access restrictions without authorization, redistribute decoded files, or violate applicable law or service agreements. Users are responsible for ensuring that their use is lawful.

Kimusic is an independent open-source project and is not affiliated with, endorsed by, or sponsored by NetEase Cloud Music or any record company.

## Acknowledgements

Kimusic is built with or informed by the following open-source projects:

- [OpenJFX](https://openjfx.io/) - desktop UI and media support.
- [AtlantaFX](https://github.com/mkpaz/atlantafx) - JavaFX design system.
- [qaralotte/ncmdump](https://github.com/qaralotte/ncmdump) - reference for NCM container parsing and decryption.
- [jaudiotagger](https://www.jthink.net/jaudiotagger/) - audio metadata and artwork handling.
- [MP3SPI](https://github.com/umjammer/mp3spi), [VorbisSPI](https://github.com/umjammer/vorbisspi), and [jFLAC](https://github.com/jflac/jflac-codec) - Java audio format support.
- [Gson](https://github.com/google/gson) - metadata JSON parsing.
- [Source Han Sans](https://github.com/adobe-fonts/source-han-sans), [Noto CJK](https://github.com/notofonts/noto-cjk), and [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono) - bundled typefaces.
