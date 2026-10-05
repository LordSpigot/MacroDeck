# MacroDeck 1.1

MacroDeck ist eine lokale Android-Makro-App. Version 1.1 ergänzt einen Bildschirm-Recorder, der über den Android-Bedienungshilfedienst als **Accessibility Overlay** läuft. Dadurch ist keine separate Berechtigung „Über anderen Apps anzeigen“ mehr erforderlich.

## Neu in 1.1

- Recorder-Bubble `● REC`, die über anderen Apps sichtbar bleibt
- Aufnahme direkt in der Ziel-App
- Taps, lange Taps und Swipes werden erfasst und sofort nachgespielt
- reale Pausen zwischen Aktionen werden automatisch als Warte-Schritte gespeichert
- `←` nimmt eine Zurück-Aktion auf
- `⌨` fügt einen Text-Schritt ein und versucht den Text in das fokussierte Feld der Ziel-App einzusetzen
- `■ STOP` beendet die Aufnahme und speichert sie automatisch
- Ziel-App wird beim Start der Aufnahme erkannt und als erster `App starten`-Schritt gespeichert
- Macro-Playback verwendet ebenfalls ein Accessibility-Overlay für den STOP-Button
- keine `SYSTEM_ALERT_WINDOW`-Berechtigung und kein Overlay-Foreground-Service mehr

## Verwendung

1. MacroDeck installieren und öffnen.
2. Unter **Bedienungshilfe** den Dienst **MacroDeck Recorder & Makro-Ausführung** aktivieren.
3. In MacroDeck auf **● Recorder einblenden** tippen.
4. Die Ziel-App öffnen, z. B. Tinder, LOVOO oder Badoo.
5. Dort auf die schwebende Bubble **● REC** tippen.
6. Gewünschte Taps, Long-Presses und Swipes durchführen. MacroDeck spielt jede aufgenommene Geste sofort in der Ziel-App nach.
7. Mit **■ STOP** beenden. Das Makro wird automatisch gespeichert und kann danach in MacroDeck bearbeitet/umbenannt werden.

Hinweis: Ein langer Druck wird ab etwa 550 ms erkannt. Bewegungen über ca. 28 dp werden als Swipe gespeichert. Sehr kurze Pausen unter 180 ms werden nicht als eigener Warte-Schritt angelegt.

## GitHub Actions Build

Der Workflow liegt unter `.github/workflows/build-apk.yml` und baut mit Android SDK 36 / Build Tools 36.0.0 / Java 17 / Gradle 8.13.

Nach einem Push: **Actions → Build MacroDeck APK → Artifacts → MacroDeck-1.1-debug-apk**.
