# Trutho

A private music and voice player for Android, with notes on every song and a Kaizen system for the way you listen.

Trutho plays the audio files that are already on your phone. There are no accounts and no network access: your notes, progress and history stay on the device.

## Features

- **Player.** Full-screen player with album art (or a generated cover when a file has none), seek bar, background playback with notification and lock-screen controls, and it remembers where you stopped.
- **Notes on every song.** Write notes while you listen. A note can be attached to the exact moment of the song, and tapping it jumps back there. A Notes tab lists all notes, and each note can be opened, edited and deleted.
- **Kaizen board.** Track every song as Must listen, Listening, Listened or Repeat. Songs set to Repeat have a target of 2 to 4 listens that is counted automatically when the song plays to the end.
- **Folders.** Playlists with sorting, Play all, and multi-select to move, copy or remove songs.
- **Library.** Search, sorting, multi-select, bulk Kaizen status, and deleting files from the phone.
- **Stats.** Day streak, listens in the last 7 days and your most listened songs.
- **Backup and restore.** Export notes, Kaizen progress, folders and history to a JSON file and restore it on this or another phone. Songs are matched by title, artist and length.

## Tech stack

Kotlin, Jetpack Compose with Material 3, Navigation Compose, Media3 (ExoPlayer and MediaSession), Room with KSP, Coroutines and Flow. Only AndroidX libraries are used.

## Requirements

Android 11 (API 24) or newer, and a recent Android Studio.

## Build and run

1. Clone the repository.
2. Open it in Android Studio and let Gradle sync.
3. Run the `app` configuration on a phone or emulator that has music files.
4. Allow access to audio files when the app asks.

## Privacy

Everything is stored locally. A backup file contains your personal notes, so keep it private and never commit it to a repository.

## Project structure

```
app/src/main/java/com/dawutdeveloper/trutho/
├── data/
│   ├── backup/      JSON backup and restore
│   ├── local/       Room database, entities and DAOs
│   ├── media/       song scanning and artwork loading
│   └── player/      Media3 playback service and player controller
├── domain/          Song model
└── ui/
    ├── components/  shared pieces (song row, mini player, covers)
    ├── navigation/  screens and routes
    ├── theme/       colors, typography, theme
    └── folders, home, kaizen, library, notes, player, settings, stats
```

## License

MIT. See [LICENSE](LICENSE).
