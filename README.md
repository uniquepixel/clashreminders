# ClashReminders

Android-App, die die Reminder-Funktionalität (ListeningEvents) des lostmanager-Discord-Bots als lokale Notifications aufs Handy bringt.

## Features

- **Kriegsstart**: Notification, sobald ein neuer Clankrieg gefunden wird (Vorbereitungs-/Kampftag)
- **Kriegsende**: X Stunden vor Kriegsende mit Liste der Mitglieder mit offenen Angriffen
- **CWL-Tag**: X Stunden vor Ende jedes CWL-Kriegstags mit offenen Hits (1 Angriff/Tag)
- **Raid-Wochenende**: fester Wochenendtag + Uhrzeit (z.B. Sonntag 18:00) — wer nicht/unvollständig geraidet hat
- **Clan-Spiele**: X Stunden vor Ende (28. 12:00 UTC) — Mitglieder unter der Punkte-Schwelle, getrackt über das "Games Champion"-Achievement (Snapshot am 22. 07:00 UTC). Sind alle fertig, kommt keine Notification.

Mehrere Clans verwaltbar, mehrere Reminder pro Typ und Clan, de/en lokalisiert.

## Setup

1. API-Key auf [developer.clashofclans.com](https://developer.clashofclans.com) erstellen und die IP **45.79.218.79** (RoyaleAPI-Proxy) auf die Whitelist setzen. Alle Requests laufen über `cocproxy.royaleapi.dev` — so funktioniert der Key trotz wechselnder Mobilfunk-IPs.
2. App starten → Onboarding: Key eintragen → ersten Clan per Tag hinzufügen.
3. In den Einstellungen Benachrichtigungen und exakte Alarme erlauben.

## Architektur

Zwei-Stufen-Hintergrundstrategie:

1. **RefreshWorker** (periodisch alle 3 h + bei App-Start/Änderungen/Boot): holt Kriegs-/CWL-Endzeiten pro Clan, erkennt Kriegsstart-Übergänge und armiert exakte AlarmManager-Alarme (`Endzeit − Vorlauf`). Raid- und Clan-Spiele-Zeitpunkte werden deterministisch ohne API berechnet.
2. **FireReminderWorker** (expedited, vom Alarm getriggert): holt frische Daten, validiert dass das Event noch läuft (sonst stiller Skip), baut die Notification. Bei Netzwerkfehlern 3 Retries mit Backoff, danach degradierte Notification ohne Mitgliederliste.

Dedupe über `fired_events` (ein Key pro Reminder + Event-Vorkommen), Boot-Re-Arming über die `scheduled_alarms`-Tabelle.

## Build

```
.\gradlew.bat assembleDebug        # Debug-APK
.\gradlew.bat testDebugUnitTest    # Unit-Tests (Domain-Logik)
.\gradlew.bat assembleRelease      # Release (R8/Proguard)
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Debugging

- Im Debug-Build hat jeder Reminder einen **"Jetzt feuern"**-Button (Glocken-Icon), der den kompletten Fire-Pfad inkl. API-Fetch und Notification durchläuft.
- Logs: `adb logcat -s ClashReminders`
- Armierte Alarme: `adb shell dumpsys alarm | findstr clashreminders`
- Boot-Pfad testen: `adb install -r ...` triggert `MY_PACKAGE_REPLACED`.

## Tech

Kotlin, Jetpack Compose (Material 3, dynamic color), Room, DataStore, WorkManager + AlarmManager, OkHttp + kotlinx.serialization, Coil. minSdk 26, targetSdk 35. Kein DI-Framework — lazy Singletons in `ClashRemindersApp`.
