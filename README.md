# MacroDeck 1.1.1

Hotfix für die Bedienungshilfe-Erkennung auf HyperOS/Android.

## Änderungen
- Status wird jetzt aus Androids `ENABLED_ACCESSIBILITY_SERVICES` gelesen.
- Unterscheidung zwischen „aktiviert“ und tatsächlich verbundenem AccessibilityService.
- Automatische Rechecks nach Rückkehr aus den Bedienungshilfe-Einstellungen.
- Sauberer Status beim Trennen des Dienstes.
- VersionCode 3 / VersionName 1.1.1.

## Update über bestehendes GitHub-Repo
Den Inhalt des `app`-Ordners über den vorhandenen `app`-Ordner im Repository hochladen/ersetzen. Der bestehende GitHub-Actions-Workflow kann unverändert bleiben.
