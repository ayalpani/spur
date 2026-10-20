# Umgebungen

Spur hat **kein Staging**, nur Production.

## Production

- **App:** die Spur-Version auf Arashs persönlichem Galaxy A54
  (`app.spur`). Sie wird vom Mac aus mit `scripts/spurctl start` installiert;
  das ersetzt die installierte App per `adb install -r` und behält die
  App-Daten. Production ist der Stand von `main`.
- **Server:** Spur hat derzeit keinen eigenen Server. Der frühere
  Items-Dienst (spur.yalpani.com auf dem sites-Server) wurde am 20.10.2026
  abgeschaltet.

## Regeln

- Ein Branch-Build darf zum Prüfen aufs A54, solange die App-Daten erhalten
  bleiben (siehe `AGENTS.md`). Als „live“ gilt eine Änderung erst nach dem
  Merge nach `main` und einer Installation von `main` aufs A54.
- Live-Gang nur nach Arashs ausdrücklichem Ja.
- Das A54 wird von allen Threads geteilt: Es installiert immer nur ein Thread
  zur gleichen Zeit.
