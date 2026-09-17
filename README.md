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

Die startfähige Compose-App nutzt MapLibre mit OpenStreetMap-Vektorkacheln für
ihre primäre Kartenansicht. Ein kurzes Standort-Onboarding führt zur
Android-Freigabe; danach folgt die Karte Position und Gerätekompass. Touren
werden über einen Foreground Service aufgezeichnet, lokal in SQLite gespeichert
und als Route auf der Karte dargestellt. Foto- und Videomomente nutzen CameraX.

## Starten

1. Projekt in Android Studio öffnen.
2. Gradle-Synchronisierung abwarten.
3. `app` auf einem Emulator oder Android-Gerät starten.

Die ausführliche Produktskizze liegt in [PRODUCT.md](PRODUCT.md).

## Schutz vor versehentlich veröffentlichten Zugangsdaten

Lokale `.env`-Dateien, private Schlüssel und Signing-Keystores werden durch
`.gitignore` ausgeschlossen. `.env.example`, `.env.sample` und `.env.template`
dürfen ausschließlich Platzhalter enthalten. Bereits versionierte Dateien
werden durch Ignore-Regeln nicht nachträglich entfernt.

Der GitHub-Workflow **Secret scan** prüft bei Pushes und Pull Requests die
vollständig abgerufene Git-Historie mit Gitleaks. Treffer lassen den Check
fehlschlagen; Geheimniswerte werden in der Ausgabe geschwärzt. Der Check läuft
nach dem Upload und verhindert selbst keinen Push. Für eine verpflichtende
Merge-Sperre muss `gitleaks` als erforderlicher Statuscheck eingerichtet werden.

Vor einem Push lässt sich derselbe Scan mit installiertem Gitleaks lokal starten:

```sh
gitleaks git . --log-opts="--all --full-history" --redact=100 --ignore-gitleaks-allow
```

## Architektur und Wartung

- [Architektur und Zustandsbesitz](docs/architecture.md)
- [Protokoll der MainActivity-Zerlegung](docs/main-activity-refactor.md)
- [Verbindliche Änderungs- und Prüfregeln](AGENTS.md)
