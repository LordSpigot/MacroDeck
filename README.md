# MacroDeck 1.2.2

## Neu
- ⚡ ausblenden trennt nur das Overlay, niemals den Accessibility-Dienst.
- Overlay-Wunsch wird lokal gespeichert und nach einem Android/HyperOS-Rebind automatisch wiederhergestellt.
- Temporär getrennter, aber weiterhin aktivierter Dienst schickt dich nicht mehr unnötig zurück in die Bedienungshilfe-Einstellungen.
- `onRebind()` wird unterstützt, damit HyperOS den Dienst sauber wieder anbinden kann.
- AMOLED-Schwarz und alle Funktionen aus 1.2.1 bleiben erhalten.

## HyperOS
Wenn du MacroDeck über **App-Info → Beenden erzwingen** stoppst oder in Android selbst den Accessibility-Schalter deaktivierst, darf eine normale App ihn aus Sicherheitsgründen nicht selbst wieder einschalten. Normales Schließen/Wegwischen und das Ausblenden des ⚡-Buttons sollen den Dienst dagegen nicht deaktivieren.
