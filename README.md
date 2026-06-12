# ClashReminders

Android-App, die die Reminder-Funktionalität (ListeningEvents) des lostmanager-Discord-Bots als lokale Notifications aufs Handy bringt — **user-basiert**: Du trägst deine eigenen Accounts ein, die App leitet daraus automatisch ab, welche Clans geprüft werden müssen.

## Konzept

- **Accounts statt Clans**: Du fügst deine Spieler-Tags hinzu. Die Clans deiner Accounts werden automatisch verfolgt — wechselt ein Account den Clan, zieht die App mit.
- **Sighting-Tracking für Clan-Hopping**: Jeder Clan, in dem ein Account innerhalb der letzten 7 Tage gesehen wurde, bleibt getrackt und fliegt danach automatisch raus. Ein leichtgewichtiger Presence-Check (alle 15 min — das WorkManager-Minimum, nur 1 Player-Request pro Account) erfasst die Aufenthalte. So werden Kriege/CWL in Clans erkannt, in die du nur für die Hits rübergehst, auch während der Account im Heimat-Clan sitzt — Lineup-Checks laufen gegen alle Accounts, nicht nur gegen die aktuell im Clan sitzenden.
- **Nur deine Angriffe zählen**: Erinnerungen listen ausschließlich deine Accounts mit offenen Aufgaben. Haben alle Accounts alles erledigt, kommt **keine** Benachrichtigung.
- **Globale Erinnerungen**: Eine Erinnerungs-Konfiguration gilt für alle Accounts und alle ihre Clans (kein Setup pro Clan nötig).

## Features

- **Kriegsstart**: Notification, sobald ein Clankrieg gefunden wird, in dessen Aufstellung einer deiner Accounts steht
- **Kriegsende**: X Stunden vor Kriegsende — nur wenn deine Accounts noch offene Angriffe haben (pro Clan mit laufendem Krieg)
- **CWL-Tag**: X Stunden vor Ende jedes CWL-Kriegstags — nur wenn deine Accounts im Tages-Lineup stehen und ihren Hit noch offen haben
- **Raid-Wochenende**: fester Wochenendtag + Uhrzeit (z.B. Sonntag 18:00) — prüft **alle** getrackten Clans in einer Notification; Accounts, die woanders geraidet und Angriffe offen gelassen haben, werden dort mitgezählt
- **Clan-Spiele**: X Stunden vor Ende (28. 12:00 UTC) — Accounts unter dem Punkteziel, getrackt über das "Games Champion"-Achievement (Snapshot pro Account am 22. 07:00 UTC)

Mehrere Accounts verwaltbar, mehrere Erinnerungen pro Typ, de/en lokalisiert.

## Setup

1. API-Key auf [developer.clashofclans.com](https://developer.clashofclans.com) erstellen und die IP **45.79.218.79** (RoyaleAPI-Proxy) auf die Whitelist setzen. Alle Requests laufen über `cocproxy.royaleapi.dev` — so funktioniert der Key trotz wechselnder Mobilfunk-IPs.
2. App starten → Onboarding: Key eintragen → ersten Account per Spieler-Tag hinzufügen (Standard-Erinnerungen werden automatisch angelegt).
3. In den Einstellungen Benachrichtigungen und exakte Alarme erlauben.

## Architektur

Drei-Stufen-Hintergrundstrategie:

0. **PresenceWorker** (periodisch alle 15 min): holt nur die Player-Profile und schreibt Clan-Sightings (`clan_sightings`, 7 Tage Retention). Taucht ein Account in einem noch nicht getrackten Clan auf, wird sofort ein voller Refresh angestoßen.
1. **RefreshWorker** (periodisch alle 3 h + bei App-Start/Änderungen/Boot): aktualisiert die Account-Profile (Name, Clan-Zugehörigkeit), leitet die zu prüfenden Clans aus den Sightings der letzten Woche ab, erkennt Kriegsstart-Übergänge (nur wenn ein Account in der Aufstellung steht) und armiert exakte AlarmManager-Alarme (`Endzeit − Vorlauf`) — pro Clan mit laufendem Krieg/CWL-Tag ein Alarm. Raid- und Clan-Spiele-Zeitpunkte werden deterministisch ohne API berechnet (ein globaler Alarm).
2. **FireReminderWorker** (expedited, vom Alarm getriggert): holt frische Daten, prüft was **deine** Accounts noch offen haben — alles erledigt heißt stiller Skip. Beim Raid werden alle Clans der Accounts in einer Notification zusammengefasst. Bei Netzwerkfehlern 3 Retries mit Backoff, danach degradierte Notification ohne Account-Liste.

Dedupe über `fired_events` (ein Key pro Reminder + Event-Vorkommen, bei Krieg/CWL pro Clan), Boot-Re-Arming über die `scheduled_alarms`-Tabelle (stabile Request-Codes pro Reminder+Event).

## Build

```
.\gradlew.bat assembleDebug        # Debug-APK
.\gradlew.bat testDebugUnitTest    # Unit-Tests (Domain-Logik)
.\gradlew.bat assembleRelease      # Release (R8/Proguard)
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Debugging

- Im Debug-Build hat jede Erinnerung einen **"Jetzt feuern"**-Button (Glocken-Icon), der den kompletten Fire-Pfad inkl. API-Fetch und Notification durchläuft.
- Logs: `adb logcat -s ClashReminders`
- Armierte Alarme: `adb shell dumpsys alarm | findstr clashreminders`
- Boot-Pfad testen: `adb install -r ...` triggert `MY_PACKAGE_REPLACED`.

## Tech

Kotlin, Jetpack Compose (Material 3, dynamic color), Room, DataStore, WorkManager + AlarmManager, OkHttp + kotlinx.serialization, Coil. minSdk 26, targetSdk 35. Kein DI-Framework — lazy Singletons in `ClashRemindersApp`.
