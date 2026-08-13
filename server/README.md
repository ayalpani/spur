# Spur Items Service

Der Dienst veröffentlicht aktuell abgelegte Items, ohne Konten oder eine
Besitzerhistorie zu führen. Er verwendet ausschließlich `net/http`, SQLite und
eine pure-Go-SQLite-Implementierung.

## Lokal starten

```sh
go test -race ./...
SPUR_DATA_DIR="$(mktemp -d)" go run .
```

Die API liegt unter `/v1/items`; der Healthcheck ist `/healthz`. Es gibt keine
Request-Logs. Fehlermeldungen und Betriebslogs enthalten weder Koordinaten noch
IP-Adressen oder User-Agents.

## Betrieb

`deploy/compose.yaml` betreibt den Prozess als nicht privilegierten,
schreibgeschützten Distroless-Container. Nur `/opt/spur/data` ist beschreibbar.
Der Nginx-VHost deaktiviert Access-Logs vollständig. Der Provenance-Schlüssel
wird beim ersten Start mit Modus `0600` im Datenvolume erzeugt.

`deploy/backup.sh` erzeugt mit einem separaten lokalen 256-Bit-Schlüssel eine
AES-GCM-verschlüsselte, konsistente SQLite-Sicherung. Der Dienst hält automatisch
nur die letzten sieben Kalendertage. Auf dem Host wird das Skript täglich per
systemd-Timer ausgeführt; der Schlüssel bleibt ausschließlich im Datenvolume.
