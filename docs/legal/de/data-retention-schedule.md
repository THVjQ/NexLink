# Aufbewahrungsplan — NexLink

**Version 0.3.0-draft** · Noch nicht veröffentlicht · §32.7

> **Geltungsbereich.** Gilt für Daten auf den Servern des Betreibers, heute also nur für
NexLink Social. NexLink, Wear OS und der Webclient speichern ihre Daten auf Ihrem
eigenen Gerät oder Ihrem eigenen Server.
>
> **Entwurf.** Nicht juristisch geprüft. **Massgeblich ist die englische Fassung.**

Bestandteil der Datenschutzerklärung. Jede Kategorie, die der Dienst hält, wie
lange sie aufbewahrt wird und wodurch sie verschwindet.

---

## 1. Kontodaten

| Daten | Aufbewahrung | Gelöscht durch |
|---|---|---|
| Benutzername (MXID) | **Dauerhaft, nach Löschung gesperrt** | Nie — siehe §4 |
| Passwort-Hash | Bis zur Kontolöschung | Kontolöschung |
| Anzeigename | Bis zur Kontolöschung | Kontolöschung oder eigenes Löschen |
| Profilbild | Bis zur Kontolöschung | Kontolöschung oder eigenes Löschen |
| E-Mail-Adresse (falls angegeben) | Bis zur Kontolöschung oder Entfernung | Kontolöschung |
| Altersbestätigung (Ja/Nein) | Kontodauer + Verjährungsfrist | Wird nicht mit dem Konto gelöscht |
| Nachweis der Annahme der Bedingungen | Kontodauer + Verjährungsfrist | Wird nicht mit dem Konto gelöscht |

## 2. Nachrichten- und Mediendaten

| Daten | Aufbewahrung | Gelöscht durch |
|---|---|---|
| Verschlüsselte Nachrichtenereignisse | Unbefristet oder nach Ihrer Einstellung | Kontolöschung; Raumlöschung |
| Verschlüsselte Medien | Bis zur Kontolöschung oder Bereinigung | Kontolöschung; stündlicher Aufräumlauf |
| Originale vor Redaktion | **7 Tage** | Automatisch |
| Raumzustand (Mitgliedschaft, Namen) | Lebensdauer des Raums | Verlassen und Löschung |
| Reaktionen (unverschlüsselt) | Bis zur Löschung | Kontolöschung; Redaktion |
| Lesebestätigungen, Tippanzeigen | Vorübergehend — nicht dauerhaft gespeichert | — |

## 3. Technische und Sicherheitsdaten

| Daten | Aufbewahrung | Gelöscht durch |
|---|---|---|
| IP-Adressen | **28 Tage** | Automatischer Ablauf; **und Entfernung bei Kontolöschung** |
| User-Agent-Zeichenfolgen | 28 Tage | Wie vor |
| Geräteliste und öffentliche Schlüssel | Bis zur Entfernung des Geräts | Abmeldung; Entfernen des Geräts |
| Push-Token | Bis Abmeldung oder Geräteentfernung | Abmeldung |
| Serverprotokolle | 28 Tage | Automatische Rotation |

**IP-Aufzeichnungen werden bei der Löschung entfernt, nicht bloss dem Ablauf
überlassen.** Ein gelöschtes Konto hinterlässt keine 28 Tage Verbindungshistorie.

## 4. Was die Kontolöschung überdauert

| Daten | Weshalb | Aufbewahrung |
|---|---|---|
| Gesperrter Benutzername | Verhindert Neuvergabe und Identitätstäuschung | Dauerhaft |
| Annahmenachweis | Nachweis von Annahme und Altersbestätigung | Verjährungsfrist |
| Einladungsdatensatz — Einweg-Hash, Zeitstempel, pseudonymisiert | Missbrauchsverfolgung; Integrität des Einladungsbaums | Lebensdauer des Baums |

Nichts in dieser Tabelle kann Sie nach Wegfall des Kontos aus dem Dienst allein
identifizieren — ausser dem Benutzernamen selbst, der gerade deshalb aufbewahrt
wird, damit er weiterhin auf niemanden verweist.

## 5. Moderationsdaten

| Daten | Aufbewahrung |
|---|---|
| Meldungen (Meldende, Betroffene, Beschreibung, Zeit) | 2 Jahre |
| Mit Einwilligung beigefügte Nachrichteninhalte | 2 Jahre |
| Moderationsentscheide und -massnahmen | 2 Jahre |

## 6. Backups — die Aufbewahrungslücke

| Daten | Aufbewahrung |
|---|---|
| Verschlüsselte Datenbank-Backups | **14 Tage** |
| Backups von Serverkonfiguration und Signaturschlüsseln | 14 Tage |

**Ein gelöschtes Konto besteht in verschlüsselten Backups bis zu 14 Tage fort**,
bevor diese verfallen und vernichtet werden.

Dies wird ausdrücklich gesagt, weil ein Löschversprechen, das einem Backup-Plan
stillschweigend widerspricht, kein eingehaltenes Versprechen ist. Backups sind
verschlüsselt, werden nie durchsucht und dienen allein der Wiederherstellung des
Dienstes nach einem Ausfall.

## 7. Anrufdaten

| Daten | Aufbewahrung |
|---|---|
| Teilnahmeereignisse (wer, wann beigetreten) | Mit den Ereignissen des Raums |
| Anrufmedien | **Nicht aufgezeichnet.** Weitergeleitet und verworfen |
| Teilnahmeaufzeichnungen bei LiveKit | Nach deren eigener Aufbewahrung; die Unterhaltung wird ihnen nicht offengelegt |

## 8. Was nie erhoben wird

Kontakte, Adressbuch, Standort über den IP-Rückschluss hinaus, Werbekennungen,
Verhaltensanalysen, Geburtsdatum, Zahlungsdaten, biometrische Daten.

**Zahlungsdaten** bleiben auf dieser Liste, obwohl nun eine Spendenseite besteht.
Spenden werden vollständig von einem Dritten abgewickelt, und keine Zahlungsdaten
erreichen den Betreiber oder ein NexLink-System — siehe Datenschutzerklärung
§3.6.

## 9. Anlässe zur Überprüfung

Dieser Plan wird überprüft, wenn eine neue Datenkategorie eingeführt wird, sich
eine Aufbewahrungsfrist ändert, ein neuer Dritter hinzukommt — oder jährlich, je
nachdem, was zuerst eintritt.
