# Richtlinien für Strafverfolgungsbehörden — NexLink

**Version 0.3.0-draft** · Noch nicht veröffentlicht · §30.5

> **Geltungsbereich.** Gilt für alle NexLink-Produkte. Nur NexLink Social hat serverseitige Daten.
>
> **Entwurf.** Nicht juristisch geprüft. **Massgeblich ist die englische Fassung.**

Für Strafverfolgungsbehörden und Rechtsanwältinnen und Rechtsanwälte. Dieses
Dokument beschreibt, welche Daten bestehen und wie Ersuchen behandelt werden.
**Es ist keine Rechtsberatung und verzichtet auf kein Recht des Betreibers oder
eines Nutzers.**

---

## 1. Was dieser Dienst ist

Ein privater, nur auf Einladung zugänglicher, Ende-zu-Ende-verschlüsselter
Nachrichten- und Anrufdienst, betrieben von einer natürlichen Person in New South
Wales, Australien, und ausschliesslich Nutzern in der Schweiz und in Australien
angeboten. Er föderiert nicht mit anderen Servern. Es gibt keine juristische
Person, keine Rechtsabteilung und keine über den Betreiber hinausgehende
Kontaktstelle.

---

## 2. Was nicht existiert

**Nachrichten- und Anrufinhalte sind Ende-zu-Ende-verschlüsselt. Der Betreiber
besitzt die Entschlüsselungsschlüssel nicht und kann keinen Klartext
herausgeben.**

Dies ist keine Haltung, die sich durch eine gerichtliche Anordnung ändern liesse.
Es gibt keine Schlüsselhinterlegung, keine serverseitige Kopie und keinen
Mechanismus, über den der Betreiber nachkommen könnte. Eine Anordnung zur
Herausgabe von Nachrichteninhalten wäre eine Anordnung zur Herausgabe von etwas,
das in dieser Form nicht existiert.

| Ersucht | Verfügbar? |
|---|---|
| Nachrichteninhalte | **Nein** — konstruktionsbedingt nicht in lesbarer Form vorhanden |
| Anrufinhalte | **Nein** — Ende-zu-Ende-verschlüsselt |
| Mediendateien | Nur Geheimtext; Schlüssel werden nicht gehalten |
| Historische Nachrichteninhalte | **Nein** |
| Möglichkeit, künftige Nachrichten abzufangen | **Nein** — erforderte eine Änderung auf der Clientseite, siehe §5 |

---

## 3. Was existiert

| Ersucht | Verfügbar? | Aufbewahrung |
|---|---|---|
| Bestehen des Kontos, Benutzername, Erstellungsdatum | Ja | Kontodauer, danach gesperrt |
| E-Mail-Adresse | Nur wenn angegeben; viele haben keine | Bis zur Löschung |
| Geräteliste, letzte Verbindungszeiten | Ja | Bis zur Entfernung des Geräts |
| Welche Konten eine Unterhaltung teilen und wann gesendet wurde | Ja | Bis zur Löschung |
| Ungefähre Nachrichtengrössen | Ja | Bis zur Löschung |
| IP-Adressen und User-Agents | Ja | **Nur 28 Tage** |
| Einladungsdatensatz, einschliesslich ausstellender Person | Ja | Bleibt nach Löschung erhalten |
| Meldungen über ein Konto | Ja | 2 Jahre |

**Beachten Sie die Frist von 28 Tagen für IP-Aufzeichnungen.** Ein später
eingehendes Ersuchen kann für diesen Zeitraum nicht erfüllt werden; eine Kopie
wird ausser in Datenbank-Backups, die nach 14 Tagen verfallen, nirgends
aufbewahrt.

---

## 4. Wie ein Ersuchen zu stellen ist

1. Stellen Sie das Ersuchen dem Betreiber unter der auf der **Kontaktseite**
   veröffentlichten Adresse zu: `https://thvjq.com.au/nexlink/contact`.
2. Nennen Sie die Rechtsgrundlage, die konkret verlangten Daten und den
   massgeblichen Zeitraum.
3. **Ersuchen müssen bestimmt sein.** Ersuchen um «sämtliche Daten» oder um
   Inhalte, die der Betreiber nicht herausgeben kann, werden mit einer
   Darstellung dessen beantwortet, was existiert.

Ersuchen werden vor jeder Antwort auf Echtheit und ordnungsgemässe Zustellung
geprüft.

---

## 5. Wie Ersuchen behandelt werden

1. Das Ersuchen wird bestätigt und das Datum festgehalten.
2. **Der Betreiber holt vor einer Antwort Rechtsrat ein.** Dies ist ein von einer
   Person betriebener Dienst, und dieser Schritt braucht Zeit.
3. Der Betreiber ermittelt, was verlangt wird und was tatsächlich existiert.
4. Der Betreiber kommt nur dem nach, was rechtlich geboten ist, und nur in dem
   gebotenen Umfang.
5. **Die betroffene Person wird benachrichtigt, sofern dies dem Betreiber nicht
   rechtlich untersagt ist.**
6. Das Ersuchen wird für die Transparenzberichterstattung festgehalten.

---

## 6. Dringlichkeitsersuchen

Besteht eine glaubhafte Gefahr unmittelbaren schweren Schadens, handelt der
Betreiber so rasch, wie es ihm möglich ist. **Nutzer sollten verstehen, dass «so
rasch wie möglich» bei einem Einzelbetreiber Stunden bedeuten kann.** Auf diesen
Dienst sollte in einer Lage, die eine sofortige Reaktion erfordert, nicht
vertraut werden.

---

## 7. Ersuchen, die abgelehnt werden

- Ersuchen um Nachrichteninhalte, weil diese nicht in lesbarer Form existieren.
- Ersuchen um eine Hintertür, um eine Schwächung der Verschlüsselung oder um eine
  Änderung des Clients zum Abfangen eines Nutzers. Einem solchen Ersuchen würde
  im rechtlich möglichen Umfang entgegengetreten; würde die Befolgung erzwungen,
  zöge der Betreiber die Einstellung des Dienstes in Betracht.
- Formlose Ersuchen ohne Rechtsgrundlage.
- Ersuchen um Daten über Nutzer anderer Dienste.

---

## 8. Transparenzberichterstattung

Der Betreiber beabsichtigt, periodisch Zahlen zu veröffentlichen: eingegangene
Ersuchen, befolgte Ersuchen und die Kategorien herausgegebener Daten — im
Rahmen allfälliger rechtlicher Offenlegungsbeschränkungen.

Ist dem Betreiber die Offenlegung eines Ersuchens untersagt, nennt der Bericht
nur das rechtlich Zulässige.

---

## 9. Sicherstellungsersuchen

Der Betreiber kommt einem rechtmässigen Sicherstellungsersuchen für Daten nach,
die im Zeitpunkt des Eingangs bestehen. **Ein Sicherstellungsersuchen kann keine
bereits verfallenen Daten sichern**, einschliesslich IP-Aufzeichnungen, die älter
als 28 Tage sind.
