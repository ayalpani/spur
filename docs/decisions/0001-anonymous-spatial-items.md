# ADR 0001: Anonyme räumliche Items

## Entscheidung

Spur behandelt Karte und Augmented Reality als zwei Ansichten derselben Welt.
Ein lokaler `WorldMode` schaltet jederzeit zwischen beiden um. Die MapView bleibt
dabei genau einmal montiert; eine AR-Session existiert höchstens einmal und nur,
solange AR sichtbar ist. Die 50-m-Grenze steuert ausschließlich, welche
öffentlichen Items in AR gerendert werden.

Ein Item wird relativ zu einer lokalen ARCore-Bodenfläche platziert. Spur
projiziert den horizontalen Offset mit Gerätestandort und Nordausrichtung in
eine geografische Koordinate. Die Frucht schwebt 1,60 m über dem gewählten
Bodenpunkt. Diese Übertragung ist absichtlich näherungsweise: GPS- und
Kompassgenauigkeit bleiben sichtbar, Cloud Anchors, VPS und Kamera-Uploads gibt
es nicht.

Besitz ist eine rotierende Capability, keine Nutzeridentität. Das besitzende
Gerät verwahrt ein zufälliges 256-Bit-Geheimnis und die Provenance-Kapsel lokal
AES-GCM-verschlüsselt unter einem Android-Keystore-Schlüssel. Der Server kennt
nur SHA-256-Prüfwerte. Beim Claim liefert der Finder vorab ausschließlich den
Prüfwert seines neuen Geheimnisses. Eine SQLite-Transaktion erlaubt den Wechsel
innerhalb von zehn Metern genau einmal. `recover` und `ack` machen die Ausgabe
nach Unterbrechungen wiederholbar; erst nach `ack` entfernt der Server die
temporäre Kapsel.

Die serverseitige Provenance enthält Generation, UTC-Kalendertag, auf zwei
Dezimalstellen gerundete Koordinaten, Vorgänger-Hash und eine ECDSA-P-256-
Signatur. Der aktuelle öffentliche Fundort ist dagegen absichtlich exakt. Nach
dem Aufheben werden dessen Koordinaten gelöscht. Es gibt keine Nutzer-, Geräte-,
Besitzer- oder Übergabetabelle und keine Request-, IP- oder User-Agent-Logs.

V1 akzeptiert Standort-Spoofing durch eine veränderte offizielle App als
bekannte Grenze. Play Integrity, Standortattestierung, Geospatial/VPS und
Konten würden das Privacy- und Betriebsmodell wesentlich vergrößern und gehören
nicht zu diesem Schnitt.

## Konsequenzen

- Wer das lokale Geheimnis verliert, verliert auch das Besitzrecht; der Server
  kann es keiner Person zuordnen oder wiederherstellen.
- Die Provenance beweist die vom Server signierte Item-Kette, nicht die Identität
  früherer Besitzer und keine zentimetergenaue historische Position.
- Ein `CLAIMING`-Datensatz hält die Kapsel bis zum bestätigten `ack`, damit ein
  Prozessabbruch kein Item vernichtet.
- Die API und Betriebsdiagnostik dürfen keine zusätzliche Identität ableiten.
