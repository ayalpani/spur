# Spur

**Wege, die dir gehören.**

Spur ist eine private Android-App zum Aufzeichnen von Stadttouren und
ortsgebundenen Erinnerungen. Touren, Standortpunkte, Fotos und Notizen bleiben
standardmäßig auf dem Gerät.

## Produktprinzipien

- local first, ohne Account und eigenes Backend
- zuverlässiges GPS-Tracking, auch bei gesperrtem Bildschirm
- konsequente Einhandbedienung mit Bottom Gravity
- ruhiges Werkzeug statt Fitness- oder Social-App
- Daten verlassen das Gerät nur nach einer bewussten Nutzeraktion

## Technischer Start

- Kotlin
- Jetpack Compose und Material 3
- Android 8.0+ (API 26)

Der erste vertikale Produktschnitt zeichnet Touren über einen Foreground
Service auf, speichert sie lokal in SQLite und stellt die zurückgelegte Spur
dar. Kartenhintergrund, Marker und CameraX kommen in späteren Schnitten hinzu.

## Starten

1. Projekt in Android Studio öffnen.
2. Gradle-Synchronisierung abwarten.
3. `app` auf einem Emulator oder Android-Gerät starten.

Die ausführliche Produktskizze liegt in [PRODUCT.md](PRODUCT.md).
