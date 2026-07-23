# Produktkern

## Produktname

**Spur** vermittelt Weg, Erinnerung und Aufzeichnung in einem kurzen,
reduzierten Namen. Er klingt nach einem privaten Werkzeug statt nach Fitness
oder Social Media.

Claim: **Wege, die dir gehören.**

Marken-, Store- und Domainverfügbarkeit sind noch nicht geprüft.

## MVP in einem Satz

Eine Person startet unten mit einem Daumentipp eine Tour, zeichnet ihren Weg
zuverlässig im Hintergrund auf, setzt unterwegs Text-, Emoji- oder Fotomarker
und findet später alles ausschließlich lokal wieder.

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
Standortzentrierung übereinander. Unten bleiben der Tour-Button und der Zugang
zur History. Der Kompass richtet die aktuelle Ansicht nach Norden aus; die
Standorttaste zentriert und aktiviert wieder die bewegungsabhängige
Kartenausrichtung. „Tour starten“ ist eine schwarze, groß beschriftete Pille.
Während der Aufzeichnung wird daraus eine rote Stop-Aktion; vor dem Beenden
muss die Person ausdrücklich bestätigen. Ein zusätzlicher Status-Chip ist
nicht nötig. Alle Icon-Buttons sind gleich groß und reinweiß. Als primäres
Werkzeug behält die Karte sämtliche Wischgesten; das Hauptmenü öffnet
ausschließlich über seinen Button.

## Erste vertikale Schnitte

1. **Tracking:** Tour starten, im Hintergrund aufzeichnen, pausieren,
   fortsetzen und beenden.
2. **Wiederfinden:** vergangene Touren und ihre Route lokal anzeigen und
   löschen.
3. **Momente:** Text- und Emojimarker, danach Fotomarker.
4. **Robustheit:** Onboarding, Berechtigungsfälle, Prozessneustart und große
   Displays.

## Bewusst später

Video, Audio, Bewegungserkennung, Export, Offline-Karten und Backup. Es gibt
im MVP keine Accounts, Cloud, Social-Funktionen, Fitnessmetriken oder
Routenplanung.
