# MacroDeck 1.2.4

Android-App zum Aufnehmen, Bearbeiten und Ausführen lokaler Makros. Die App verwendet einen Android-Bedienungshilfedienst, um Eingaben in anderen Apps zu simulieren und das ⚡-Schnellmenü einzublenden.

## Änderungen in 1.2.4

- **Ausführungseinstellungen bleiben gespeichert:** Wiederholungen (`1×`, `5×`, `10×`, `25×`, `∞`), Pause (`0–30 s` in 0,5-s-Schritten) und „Ziel-App nicht neu öffnen“ werden **pro Makro** gespeichert.
- Die Einstellungen werden im ⚡-Schnellmenü bei jeder Änderung unmittelbar und dauerhaft gespeichert. In der Makro-Liste kannst du sie auch unter `⋯ → ⚙ Ausführung einstellen` ändern und mit „Speichern“ übernehmen.
- `▶ Start` auf der Hauptseite verwendet dieselben gespeicherten Optionen. Eine Endlosschleife erfordert dort eine Bestätigung.
- Das ⚡-Schnellmenü respektiert die zuletzt gewählte Sichtbarkeit auch nach einem erneuten Verbinden des Bedienungshilfediensts: bewusstes Ausblenden wird bei einem Update nicht mehr überschrieben.
- Die Speicherung bestehender Makros wird synchron abgeschlossen, bevor „Gespeichert“ angezeigt wird. Beim Laden werden fehlerhafte Einträge isoliert; zusätzlich bleibt eine vorherige Makro-Version als Fallback erhalten.
- Für bearbeitete Makros warnt die App jetzt vor dem Verwerfen ungespeicherter Änderungen. Ein Android-Konfigurationswechsel (z. B. Displaydrehung) stellt den offenen Editor-Entwurf wieder her. Beim Duplizieren werden die Ausführungsoptionen übernommen, beim Löschen mit entfernt.
- Bei vielen gespeicherten Makros kann das ⚡-Schnellmenü jetzt vertikal gescrollt werden. Das Aktivieren/Deaktivieren eines Makros aktualisiert sofort den Start-Button.
- Alte, nicht mehr verwendete `OverlayService`-Implementierung entfernt; die App nutzt weiterhin ausschließlich `TYPE_ACCESSIBILITY_OVERLAY`.
- Bestehende `applicationId`/Paketkennung beibehalten; Versionscode auf `8` erhöht.

## Upgrade ohne Datenverlust

1. Sichere deine Daten (am besten zusätzlich vor größeren Android-Updates).
2. **Nicht deinstallieren und nicht „App-Daten löschen“**: Beides entfernt lokale Makros und Einstellungen.
3. In GitHub dieses Projekt einschließlich `.github/workflows/build-apk.yml` aktualisieren. Verwende **dieselben vier bestehenden Signatur-Secrets** und den **gleichen Keystore** wie bei deiner bisher installierten APK.
4. In GitHub Actions `Build signed MacroDeck APK` starten oder auf `main`/`master` pushen und das Artefakt `MacroDeck-1.2.4-signed-apk` herunterladen.
5. APK über der bestehenden Version installieren. Mit einer **anderen** Signatur verweigert Android das Update. Keine Deinstallation als schnelle Lösung wählen, wenn du Makros behalten möchtest.

**Hinweis:** Die im alten Repository vorhandene Signing-Konfiguration wird hier nicht durch einen neuen Keystore ersetzt. Ein hier erzeugtes Debug-APK wäre nicht als Update einer anders signierten Release-APK geeignet.

## Android-Systemmeldung „Zugriffseinstellungen prüfen“

Android kann diese Sicherheitsmeldung anzeigen, wenn ein Bedienungshilfedienst aktiv ist. Die App benötigt diesen sensiblen Zugriff, um Gesten über anderen Apps auszuführen. Es handelt sich um eine Android-Systemmeldung, **nicht** um einen MacroDeck-Absturz. Die App kann und sollte die Sicherheitsprüfung nicht selbst umgehen oder unterdrücken. Prüfe die Berechtigung unter `Einstellungen → Bedienungshilfen`; du kannst sie dort jederzeit widerrufen. Dann sind Aufnahme und Wiedergabe von Gesten nicht mehr möglich.

Makros werden lokal in privaten App-Einstellungen gespeichert. Beachte, dass aufgezeichnete `Text eingeben`-Schritte ihren Inhalt enthalten können. **Keine Passwörter, TANs oder andere Geheimnisse in Makros speichern.** Die bestehende Android-App-Datensicherung ist unverändert geblieben.

## Tests und Build

Der isolierte Persistenztest kann ohne Android SDK mit JDK 17+ ausgeführt werden:

```bash
bash tests/test_run_settings.sh
```

Für den vollständigen Android-Build benötigt das Projekt Android SDK 36, Android Gradle Plugin 8.13.2 und Gradle 8.13. Die GitHub-Actions-Konfiguration enthält einen signierten Release-Build. Funktion, Zugriffsberechtigung und Gerät-Neustart müssen zusätzlich auf einem echten Android-Gerät geprüft werden.

### Empfohlener Funktionstest auf dem Gerät

1. Makro anlegen, unter `⋯ → ⚙ Ausführung einstellen` beispielsweise 10×, 3,5 s, Smart-Launch aus wählen und speichern.
2. App ganz schließen, wieder öffnen; unter `⋯` die gespeicherten Werte prüfen.
3. Werte auch im ⚡-Schnellmenü ändern, Overlay schließen und erneut öffnen; die Auswahl muss erhalten bleiben.
4. Das Gerät neu starten, den Accessibility-Dienst erneut verbinden lassen und die Einstellungen prüfen.
5. ⚡ manuell ausblenden, die App neu öffnen und sicherstellen, dass der Button ausgeblendet bleibt. Er kann über die Hauptseite wieder eingeblendet werden.
6. Makro duplizieren; die Ausführungsoptionen sollen übernommen sein. Ein anderes Makro darf nicht mitverändert werden.
