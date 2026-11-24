# FishClient

Ein Client-Mod für Minecraft mit Ozean-Look: eigenes Menü, HUDs, Freelook, Zoom, eigener Himmel, Cosmetics und Auktions-Werkzeuge. Er läuft nur auf dem Client, der Server braucht nichts.

## Voraussetzungen

| | Version |
| --- | --- |
| Minecraft | 26.1.2 |
| Fabric Loader | 0.19.1 oder neuer |
| Fabric API | 0.145.4+26.1.2 oder neuer |
| Java | 25 |

## Bauen

Im Projektordner:

```bash
./gradlew build
```

Unter Windows geht in PowerShell auch `.\gradlew.bat build`. Gradle läuft dabei immer auf Java 25, auch wenn `JAVA_HOME` auf eine ältere Version zeigt. Java 25 muss nur installiert sein.

Die fertige Datei liegt danach unter `build/libs/fishclient-1.0.0.jar`.

## Installieren

1. Fabric Loader für Minecraft 26.1.2 installieren.
2. `fishclient-1.0.0.jar` und die passende Fabric API in den Ordner `.minecraft/mods` legen.
3. Das Fabric-Profil im Launcher starten.

## Ausprobieren ohne Installation

```bash
./gradlew runClient
```

Das startet das Spiel direkt aus dem Projekt, mit eigenem Spielordner unter `run/`.

## Tasten

Alle Tasten lassen sich in den Minecraft-Einstellungen oder im Menü ändern.

| Taste | Funktion |
| --- | --- |
| Rechte Umschalttaste | Menü öffnen, auch auf dem Titelbildschirm |
| Linke Alt-Taste | Freelook |
| C | Zoom, dabei ändert das Mausrad die Stufe |
| nicht belegt | Wegpunkt an der aktuellen Position setzen |

## Was drin ist

- **Profit-HUD und Transaktionen:** liest Zahlungen aus dem Chat, zeigt Empfangen, Gesendet, Session-Profit und den Top-Zahler. Filter nach Mindestbetrag und Spieler, dauerhafter Verlauf.
- **Live-Auktion für den Stream:** Du wählst Items aus der Schnellleiste, Mindestgebot, Dauer und eine Notiz und startest. Jede Zahlung, die währenddessen per `/pay` reinkommt, zählt als Gebot. Eine Karte auf dem Bildschirm zeigt Timer, Höchstgebot und alle Gebote, am Ende erscheint der Gewinner. Danach gibt es eine Rückzahlungsliste für alle anderen, auch für verspätete Zahlungen. Jede Rückzahlung geht erst nach einem zweiten Klick raus. Der Verlauf liegt unter „Letzte Auktionen“, dort startet „Wiederholen“ dieselbe Auktion neu.
- **Auktionshaus-Helfer:** Flip-Tracker, letzte Verkäufe und eine Merkliste, die passende Items im Auktionsfenster grün markiert.
- **Ansicht:** Freelook, sanfter Zoom, Fullbright, eigenes Fadenkreuz, gestaltetes Scoreboard.
- **Himmel:** acht Presets, nur auf dem eigenen Bildschirm.
- **Portale:** Reist du durch ein Netherportal, nimmt FishClient auf beiden Seiten ein Rundumbild auf. Danach zeigt jedes Portal die andere Seite wie ein Fenster, mit Blickwinkel und Tiefe, auch auf Servern. Statt des Ladebildschirms fliegt das Bild ins Portal, färbt sich lila und zeigt das Ziel, dann blendet es weich in die Welt über. Im Tab „Portale“ lassen sich Blick und Übergang abschalten, die Bildqualität und Sichtweite einstellen und gespeicherte Ansichten löschen.
- **HUDs:** FPS und Ping, Keystrokes, CPS, Rüstung, Angeln, Musik, Minimap. Alle verschiebbar und skalierbar über "HUD bearbeiten".
- **Wegpunkte und Minimap:** pro Server und Dimension gespeichert.
- **Cosmetics:** Cape-Upload mit Physik, vier Hüte, Kopfhörer, Akzentfarbe, Skin-Bibliothek.
- **Angeln:** Fang-Statistiken, Blasen-Effekt, Angel-Skin.
- **Musik:** Titel, Cover, App und Laufzeit aus der Windows-Wiedergabe, ohne Login.
- **Songtexte um dich herum:** Die aktuelle Zeile schwebt synchron zum Song in der Welt um deine Figur. Die Texte kommen zur Laufzeit von [lrclib.net](https://lrclib.net), dafür gehen Titel, Interpret und Länge des Songs dorthin. Das passiert nur, wenn das Music-Modul an ist. Ein Zeitversatz lässt sich einstellen, falls die App ihre Position verzögert meldet.
- **Screenshots:** Vorschau nach der Aufnahme und Galerie im Menü.
- **Profile:** eigene Einstellungen und Tastenbelegungen pro Profil, Import und Export als JSON.

## Bewusste Grenzen

- **Keine Automatik:** FishClient kauft, bietet und angelt nie von selbst. Befehle gehen nur auf deinen Klick raus, Rückzahlungen erst nach Bestätigung und mit Abstand zueinander. Die Merkliste markiert nur.
- **Cosmetics sieht vorerst nur du.** Für andere Spieler bräuchte es einen eigenen Server.
- **Musik und Zwischenablage nur unter Windows.** Beides läuft über PowerShell.
- **Portale sind ein Standbild.** Der Server schickt nur die Dimension, in der du bist. Das Portal zeigt deshalb die andere Seite vom letzten Besuch, ohne Mobs, Spieler und Änderungen seitdem. Reisen und Teleport macht weiter der Server. Das Endportal bekommt nur den Übergang, keinen Blick.
- **Serverregeln:** Prüf vor dem Spielen die Regeln deines Servers. Fullbright, Freelook und Minimap sind nicht überall erlaubt.

## Chat-Erkennung anpassen

Die Muster für DonutSMP, HugoSMP und FishSMP sind Vorlagen, keine geprüften Formate. Unter **Einstellungen → Chat-Muster pro Server** lassen sie sich ändern und mit echten Chat-Zeilen testen. Es sind reguläre Ausdrücke mit den benannten Gruppen `player`, `amount` und `item`, zum Beispiel:

```text
^(?<player>[A-Za-z0-9_.]{2,17}) paid you \$?(?<amount>[0-9][0-9.,]*\s?[kKmMbBtT]?)[.!]?$
```

Der Server wird über einen Teil seiner Adresse erkannt. Es zählen nur Systemnachrichten, damit niemand im normalen Chat eine Zahlung vortäuschen kann. Dort steht auch der Bezahlbefehl für Rückzahlungen, Standard ist `/pay {player} {amount}`.

## Wo die Daten liegen

Alles liegt in `.minecraft/config/fishclient/`:

| Datei oder Ordner | Inhalt |
| --- | --- |
| `profiles/` | Einstellungsprofile |
| `transactions.json` | Zahlungsverlauf |
| `live_auctions.json` | Live-Auktionen mit Geboten und Rückzahlungen |
| `auctions.json` | Auktionshaus-Verkäufe und Merkliste |
| `waypoints.json` | Wegpunkte |
| `fishing.json` | Fang-Statistiken |
| `skins/`, `capes/` | hochgeladene Bilder |
| `portal_views/<server>/` | Rundumbilder der Portale (`index.json` und PNGs) |

## Entwicklung

```bash
./gradlew runAutotest
```

Startet das echte Spiel, legt eine Testwelt an, geht die Funktionen durch und klickt die Knöpfe an. Der Bericht landet in `run-test/autotest-report.txt`, die Bilder in `run-test/screenshots/autotest/`. Der Testlauf hat einen eigenen Spielordner, weil er den Ton stummschaltet und Testdaten hinterlässt, und er ist ein eigener Mod unter `src/testmod`, der nicht in die fertige JAR kommt.

```bash
./gradlew prodAutotest
```

Macht dasselbe mit der fertigen JAR auf dem echten Client samt Fabric Loader, also so, wie der Launcher eines Spielers es startet. Bericht und Bilder liegen dann unter `run-prod/`. `./gradlew prodClient` startet die JAR auf demselben Weg ohne Test.

```bash
python tools/check_lang.py
```

Prüft, ob `en_us.json` und `de_de.json` alle im Code verwendeten Texte enthalten.

```bash
python tools/generate_assets.py
```

Erzeugt Icons, Logo, Cosmetic-Texturen und Sounds neu. Braucht Pillow und ffmpeg.

## Herkunft der Inhalte

Code und Assets sind eigens für dieses Projekt entstanden. Einzige Ausnahme ist die Schrift [Inter](https://rsms.me/inter/) von Rasmus Andersson unter der SIL Open Font License, deren Text unter `src/main/resources/assets/fishclient/font/inter-license.txt` liegt.

FishClient steht in keiner Verbindung zu Mojang oder Microsoft.

## Lizenz

MIT – siehe [LICENSE](LICENSE). Inoffizielle Mod, nicht mit Mojang oder Microsoft verbunden.
