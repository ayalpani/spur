# Produktkern

## Produktname

**Spur** vermittelt Weg, Erinnerung und Aufzeichnung in einem kurzen,
reduzierten Namen. Er klingt nach einem privaten Werkzeug statt nach Fitness
oder Social Media.

Claim: **Wege, die dir gehören.**

Marken-, Store- und Domainverfügbarkeit sind noch nicht geprüft.

## MVP in einem Satz

Eine Person startet unten mit einem Daumentipp eine Tour, zeichnet ihren Weg
zuverlässig im Hintergrund auf, setzt unterwegs Sprach-, Emoji-, Video- oder
Fotomarker und findet später alles ausschließlich lokal wieder.

## Primäre Navigation

Spur startet immer auf der Karte. Dort liegen unten der primäre
**„Tour starten“**-Button und direkt daneben ein kompakter History-Button.
Beim Start einer Tour bleibt die Karte sichtbar. Die History ist eine
sekundäre Ebene und gleitet von rechts über die Karte herein.

Beim ersten Start erklärt Spur die lokale Standortnutzung und fragt danach die
Android-Freigabe für den Standort während der Nutzung an. Erst anschließend
öffnet sich die Karte am tatsächlichen Gerätestandort. Die zusätzliche
Hintergrundfreigabe wird separat beim ersten Tourstart erklärt und angefragt,
sobald die Hintergrundaufzeichnung implementiert ist.

Die Kartensteuerung folgt einer festen Ordnung: oben stehen Hauptmenü und –
nur bei einer laufenden Tour – Teilen. Rechts auf der Karte liegen Kompass und
Standortzentrierung übereinander. Unten stehen der Plus-Button zum Ablegen
eines Moments, der Tour-Button und der Zugang zur History. Der Plus-Button
öffnet ein Bottom Sheet in der Reihenfolge Sprachnachricht, Emoji, Video und
Foto. „Foto“ öffnet eine bildschirmfüllende Kamera mit Auslöser und Wechsel
zwischen Front- und Rückkamera. Nach dem Knipsen kann das Bild verworfen oder
lokal verwendet werden. Ein verwendetes Bild erhält die aktuelle Position,
erscheint sofort als Foto-Marker auf der Karte und bleibt nach einem Neustart
erhalten. Alle benachbarten Kartenbuttons verwenden denselben Abstand. Der
eigene Kompass reagiert mit seiner Nadel auf die Geräteausrichtung. Ein Tipp
richtet ausschließlich die bestehende Ansicht nach Norden aus und verändert
weder Mittelpunkt noch Zoom. Eine separate 100-%-Taste stellt die normale
Zoomstufe wieder her. Die Standorttaste verwendet das etablierte Fadenkreuz
und zentriert die Karte einmalig auf den aktuellen Standort mit dieser
normalen Zoomstufe. Die Karte selbst folgt weder beim Start noch durch diese
Aktionen automatisch dem Kompass oder Gyroskop. „Tour starten“ ist eine
schwarze, groß beschriftete Pille.
Während der Aufzeichnung wird daraus eine rote Stop-Aktion; vor dem Beenden
muss die Person ausdrücklich bestätigen. Ein zusätzlicher Status-Chip ist
nicht nötig. Alle Icon-Buttons sind gleich groß und reinweiß. Als primäres
Werkzeug behält die Karte sämtliche Wischgesten; das Hauptmenü öffnet
ausschließlich über seinen Button.

Alle abgelegten Momente nutzen dasselbe erweiterbare Marker-Modell aus
Position, Typ und Inhalt. Die gemeinsame Markerform bleibt konsistent; Foto,
Video, Sprachnachricht und Emoji unterscheiden sich durch ihr inneres Symbol.
Ein Marker besteht aus einem kleinen Ankerpunkt auf der exakten Koordinate,
einer dünnen senkrechten Linie und einer etwa 40 × 40 dp großen Flagge. Fotos
erscheinen als Vorschaubild in der Flagge. Marker sind antippbar; die gewählte
Flagge erhält einen klaren dunklen Auswahlhintergrund.

## Erste vertikale Schnitte

1. **Tracking:** Tour starten, im Hintergrund aufzeichnen, pausieren,
   fortsetzen und beenden.
2. **Wiederfinden:** vergangene Touren und ihre Route lokal anzeigen und
   löschen.
3. **Momente:** Sprachnachricht, Emoji, Video und Foto lokal an einem Ort
   ablegen.
4. **Robustheit:** Onboarding, Berechtigungsfälle, Prozessneustart und große
   Displays.

## Bewusst später

Bewegungserkennung, Export, Offline-Karten und Backup. Es gibt im MVP keine
Accounts, Cloud, Social-Funktionen, Fitnessmetriken oder Routenplanung.
