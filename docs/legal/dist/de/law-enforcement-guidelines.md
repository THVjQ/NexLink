<!-- Erstellt von tools/legal/build-legal.py. Bearbeiten Sie docs/legal/de/law-enforcement-guidelines.md
     oder docs/legal/definitions.de.md, niemals diese Datei. -->

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

---

## Anhang A — Begriffsbestimmungen

Dieser Anhang ist in jedem NexLink-Rechtsdokument identisch. Er wird aus einer
einzigen Quelle gepflegt und maschinell angefügt, damit ein Begriff nicht in den
Nutzungsbedingungen das eine und in den Inhaltsrichtlinien etwas anderes
bedeuten kann.

Wird ein Begriff unter Verweis auf ein Gesetz definiert, so gilt die gesetzliche
Definition; der vorliegende Text ist eine Zusammenfassung in einfacher Sprache.
Bei Abweichungen **geht das Gesetz vor**.

### A.1 Dienst und Parteien

| Begriff | Bedeutung |
|---|---|
| **NexLink-Produkte** | Sämtliche vom Betreiber unter dem Namen NexLink veröffentlichte Software. Zum Zeitpunkt dieser Fassung: **NexLink** (SMS, Telefon und vereinheitlichter Posteingang für Android), **NexLink Social** (verschlüsselter Messenger für Android), **NexLink für Wear OS** und der **NexLink-Webclient**. Ein später hinzukommendes Produkt ist ab seiner Veröffentlichung erfasst. |
| **Dienst** | Die von Ihnen genutzten NexLink-Produkte sowie die sie tragende Serverinfrastruktur. |
| **Betreiber** | Die natürliche Person, welche die NexLink-Produkte veröffentlicht und den Dienst betreibt. Die Kontaktangaben sind auf der Kontaktseite veröffentlicht. |
| **Kontaktseite** | `https://thvjq.com.au/nexlink/contact` — die einzige veröffentlichte Quelle für die Post- und die elektronische Adresse des Betreibers. Sie wird aktuell gehalten, damit eine in einer installierten Anwendung eingebettete Zustelladresse nicht veralten kann. |
| **Sie**, **Nutzer** | Die natürliche Person, welche diese Dokumente angenommen hat und den Dienst nutzt. |
| **Konto** | Ihre Identität auf einem registrierungspflichtigen NexLink-Produkt. |
| **Gerät** | Ein Mobiltelefon, Tablet, eine Uhr oder ein Computer, der bei Ihrem Konto angemeldet ist oder ein NexLink-Produkt ausführt. |

### A.2 Daten und Inhalte

| Begriff | Bedeutung |
|---|---|
| **Inhalte** | Alles, was Sie über den Dienst senden, empfangen, hochladen, speichern oder übermitteln. |
| **Metadaten** | Durch Ihre Nutzung entstehende Informationen, die keine Inhalte sind — wer wann mit wem kommuniziert hat, Nachrichtengrössen, IP-Adressen, Gerätekennungen. Die Datenschutzerklärung nennt abschliessend, welche Angaben bestehen. |
| **Ende-zu-Ende-Verschlüsselung** | Verschlüsselung, bei der ausschliesslich die beteiligten Geräte die Entschlüsselungsschlüssel besitzen und der Betreiber sie nicht besitzt. |
| **Personendaten** | Im Sinne von Art. 5 lit. a des schweizerischen Datenschutzgesetzes (DSG) für Nutzer in der Schweiz und im Sinne von "personal information" nach Section 6 des australischen Privacy Act 1988 (Cth) für Nutzer in Australien. Beides bedeutet im Kern: alle Angaben, die sich auf eine bestimmte oder bestimmbare Person beziehen. |
| **Wiederherstellungsschlüssel** | Der Code, der den Zugang zu Ihrem verschlüsselten Nachrichtenverlauf wiederherstellt. Der Betreiber besitzt keine Kopie und kann ihn nicht neu ausstellen. |
| **Einladung** | Ein Einmalcode, der zur Erstellung eines Kontos auf einem nur auf Einladung zugänglichen NexLink-Produkt erforderlich ist. |

### A.3 Begriffe des verbotenen Verhaltens

Diese vier Definitionen bestehen, weil ein Verbot, das der Nutzer nicht
vorhersehen kann, kein faires Verbot ist, und weil die Durchsetzung gegen vage
umschriebenes Verhalten sowohl ungerecht als auch rechtlich anfällig ist. Jede
Definition stützt sich auf Gesetzesrecht, und jede enthält einen ausdrücklichen
Ausschluss.

#### **Terrorismus** / **Terroristische Handlung**

Für Nutzer in **Australien** gemäss Section 100.1 des *Criminal Code Act 1995*
(Cth): eine Handlung oder Handlungsandrohung, die

1. den Tod, eine schwere Körperverletzung oder eine schwere Sachbeschädigung verursacht, Leben gefährdet, die öffentliche Gesundheit oder Sicherheit ernsthaft gefährdet oder wesentliche elektronische Systeme schwerwiegend beeinträchtigt; **und**
2. in der Absicht begangen wird, ein politisches, religiöses oder ideologisches Anliegen zu fördern; **und**
3. in der Absicht begangen wird, eine Regierung durch Einschüchterung zu nötigen oder zu beeinflussen oder die Öffentlichkeit oder einen Teil davon einzuschüchtern.

Für Nutzer in der **Schweiz** im Sinne von Art. 260ter und Art. 260quinquies des
Schweizerischen Strafgesetzbuches (StGB) sowie des *Bundesgesetzes über
polizeiliche Massnahmen zur Bekämpfung von Terrorismus*: die Beteiligung an,
Unterstützung oder Finanzierung einer Organisation, die ihre Zwecke mit
Gewaltverbrechen verfolgt, oder die Begehung solcher Verbrechen, um eine
Bevölkerung einzuschüchtern oder einen Staat oder eine internationale
Organisation zu nötigen.

> **Ausdrücklicher Ausschluss.** Interessenvertretung, Protest, abweichende
> Meinung, Satire, Journalismus, wissenschaftliche Forschung, historische
> Dokumentation, künstlerische Darstellung und Arbeitskampfmassnahmen sind
> **kein** Terrorismus und durch kein NexLink-Dokument verboten, es sei denn,
> das Verhalten selbst erfüllt sämtliche Tatbestandsmerkmale der obigen
> Definition. Alle drei Merkmale der australischen Definition müssen erfüllt
> sein; eines allein genügt nicht. Dieser Ausschluss entspricht Section 100.1(3)
> des Criminal Code und wird hier wiedergegeben, damit ihn niemand suchen muss.

#### **Gewaltextremismus**

Weder das australische noch das schweizerische Recht kennt eine einheitliche
gesetzliche Definition. Der Betreiber legt daher bewusst eine enge Definition
zugrunde und spricht sie aus, statt sie der Auslegung zu überlassen:

Inhalte oder Verhalten, die **vorsätzlich zu rechtswidriger Gewalt gegen eine
Person oder Gruppe aufrufen, dazu anleiten, sie fördern oder zur Beteiligung
daran auffordern**, und zwar wegen deren Rasse, Religion, Nationalität,
ethnischer Herkunft, Geschlecht, Geschlechtsidentität, sexueller Orientierung,
Behinderung oder politischer Anschauung.

> **Ausdrücklicher Ausschluss.** Über Gewaltextremismus zu berichten, ihn zu
> verurteilen, zu analysieren, zu persiflieren, darüber aufzuklären oder ihm
> mit Gegenrede zu begegnen, ist **kein** Gewaltextremismus. Ein Geschehen zu
> beschreiben heisst nicht, es zu fördern. Eine unpopuläre, anstössige oder
> radikale politische oder religiöse Auffassung zu vertreten oder zu äussern,
> ist **kein** Gewaltextremismus und ist nicht verboten; das Verbot knüpft an
> die Aufforderung zu rechtswidriger Gewalt an und an nichts sonst.
>
> Der Betreiber behandelt einen Nutzer nicht aufgrund seiner politischen oder
> religiösen Überzeugungen, seiner Nationalität oder seiner Verbindungen zu
> Dritten als gewaltextremistisch, solange die vorstehend beschriebene
> vorsätzliche Aufforderung fehlt.

#### **Verbotene Organisation**

Eine Organisation, die nach Division 102 des *Criminal Code Act 1995* (Cth) als
terroristische Organisation gelistet ist oder die nach dem schweizerischen
*Embargogesetz* oder einer Verordnung des Bundesrates verboten oder
sanktioniert ist. Der Betreiber wendet die Liste derjenigen Rechtsordnung an, in
der sich der Nutzer befindet, und führt keine eigene Liste.

#### **Rechtswidrig**

Im Widerspruch zum Recht der Schweiz oder des Commonwealth of Australia und des
australischen Bundesstaates oder Territoriums, in dem sich der Nutzer befindet.
Ist ein Verhalten in der Rechtsordnung des Nutzers rechtmässig, so ist es nach
diesen Dokumenten nicht allein deshalb "rechtswidrig", weil es anderswo
rechtswidrig wäre.

### A.4 Rechts- und Zuständigkeitsbegriffe

| Begriff | Bedeutung |
|---|---|
| **Zugelassene Gebiete** | Die Schweiz und Australien. Der Dienst wird ausschliesslich Nutzern mit Wohnsitz in diesen beiden Ländern angeboten — siehe Nutzungsbedingungen §3.7. |
| **DSG** | Das schweizerische *Bundesgesetz über den Datenschutz* vom 25. September 2020, in Kraft seit 1. September 2023, samt Datenschutzverordnung (DSV). |
| **EDÖB** | Der *Eidgenössische Datenschutz- und Öffentlichkeitsbeauftragte*, Aufsichtsbehörde für Nutzer in der Schweiz. |
| **Privacy Act** | Der *Privacy Act 1988* (Cth) und die darauf gestützten Australian Privacy Principles. |
| **OAIC** | Das *Office of the Australian Information Commissioner*, Aufsichtsbehörde für Nutzer in Australien. |
| **ACL** | Das *Australian Consumer Law*, Schedule 2 zum *Competition and Consumer Act 2010* (Cth). |
| **Konsumentengarantien** | Die vom ACL gewährten Garantien, die vertraglich weder ausgeschlossen noch eingeschränkt oder abgeändert werden können. |

### A.5 Zum Verständnis dieser Dokumente

- **«Einschliesslich»** bedeutet «einschliesslich, aber nicht beschränkt auf».
- **«Schriftlich»** umfasst elektronische Nachrichten an die auf der Kontaktseite genannte Adresse.
- Überschriften dienen der Orientierung und haben keinen Einfluss auf die Auslegung.
- Ein Verweis auf ein Gesetz gilt als Verweis auf dessen jeweils geltende Fassung und auf jedes ersetzende Gesetz.
- Weichen die englische und die deutsche Fassung eines Dokuments voneinander ab, **geht die englische Fassung vor**, ausser der Nutzer ist Konsument mit Wohnsitz in der Schweiz und schweizerisches Recht schreibt etwas anderes vor.
