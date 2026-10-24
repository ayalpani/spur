# Umgebungen

Spur hat **kein Staging**, nur Production.

## Production

- **App:** die Spur-Version auf dem registrierten Testhandy
  (`app.spur`). Sie wird vom Mac aus mit `scripts/spurctl start` installiert;
  das ersetzt die installierte App per `adb install -r` und behält die
  App-Daten. Production ist der Stand von `main`.
- **Server:** Spur hat derzeit keinen eigenen Server. Der frühere
  Items-Dienst wurde am 20.10.2026 abgeschaltet.

## Regeln

- Ein Branch-Build darf zum Prüfen aufs Testhandy, solange die App-Daten erhalten
  bleiben (siehe `AGENTS.md`). Als „live“ gilt eine Änderung erst nach dem
  Merge nach `main` und einer Installation von `main` aufs Testhandy.
- Live-Gang nur nach ausdrücklichem Ja des Inhabers.
- Das Testhandy wird von allen Threads geteilt: Es installiert immer nur ein Thread
  zur gleichen Zeit.
