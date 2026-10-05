# MacroDeck 1.0

Native Android-Makro-App für lokale, vom Nutzer erstellte Automationen.

## Funktionen
- Makros erstellen, bearbeiten, duplizieren und löschen
- Reihenfolge der Schritte ändern
- Tap, Langdruck, Swipe, Warten, Text eingeben, App starten, Zurück, Home
- Bildschirmkoordinaten per transparentem Overlay aufnehmen
- AccessibilityService für Gesten und globale Aktionen
- sichtbarer STOP-Button während der Ausführung
- lokale Speicherung via SharedPreferences/JSON
- keine INTERNET-Berechtigung, kein Konto, keine Cloud

## Zielplattform
- minSdk 28
- targetSdk / compileSdk 36
- besonders für aktuelle Xiaomi/Redmi-/HyperOS-Geräte gedacht

## Einmalige Einrichtung auf dem Redmi
1. APK installieren.
2. MacroDeck öffnen.
3. `Bedienungshilfe` antippen und `MacroDeck Makro-Ausführung` aktivieren.
4. `Overlay erlauben` aktivieren, damit Koordinatenaufnahme und STOP-Button funktionieren.
5. In HyperOS für MacroDeck Akku-Beschränkungen möglichst auf `Keine Einschränkungen` setzen.

## Build
Projekt in Android Studio öffnen und `Build > Build APK(s)` ausführen.
Erwartete Toolchain: Android Gradle Plugin 8.13.2, Gradle 8.13, JDK 17+ und Android SDK 36.
