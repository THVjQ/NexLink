<!-- Erstellt von tools/legal/build-legal.py. Bearbeiten Sie docs/legal/de/data-retention-schedule.md
     oder docs/legal/definitions.de.md, niemals diese Datei. -->

# Aufbewahrungsplan — NexLink

**Version 0.3.0-draft** · Noch nicht veröffentlicht · §32.7

> **Geltungsbereich.** Gilt für Daten auf den Servern des Betreibers, heute also nur für
NexLink Social. NexLink, Wear OS und NexLink Bridge speichern ihre Daten auf Ihrem
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
| IP-Adressen | **28 Tage** — sechs Monate bei Betrieb aus der Schweiz | Automatischer Ablauf; Entfernung bei Kontolöschung, **ausser bei gesetzlicher Aufbewahrungspflicht** |
| User-Agent-Zeichenfolgen | 28 Tage — sechs Monate bei Betrieb aus der Schweiz | Wie vor |
| Geräteliste und öffentliche Schlüssel | Bis zur Entfernung des Geräts | Abmeldung; Entfernen des Geräts |
| Push-Token | Bis Abmeldung oder Geräteentfernung | Abmeldung |
| Serverprotokolle | 28 Tage — sechs Monate bei Betrieb aus der Schweiz | Automatische Rotation |

**IP-Aufzeichnungen werden bei der Löschung entfernt, nicht bloss dem Ablauf
überlassen.** Ein gelöschtes Konto hinterlässt keine 28 Tage Verbindungshistorie.

**Dies gilt bei Betrieb aus Australien. Bei Betrieb aus der Schweiz gilt es
nicht**, und §3.3a der Datenschutzerklärung sagt dies, statt beide Aussagen
einander widersprechen zu lassen: Die sechsmonatige Aufbewahrungsfrist des BÜPF
überdauert, wo sie gilt, die Kontolöschung. Alles Übrige wird weiterhin sofort
entfernt.

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
| **NexLink-Produkte** | Sämtliche vom Betreiber unter dem Namen NexLink veröffentlichte Software. Zum Zeitpunkt dieser Fassung: **NexLink** (SMS, Telefon und vereinheitlichter Posteingang für Android), **NexLink Social** (verschlüsselter Messenger für Android), **NexLink für Wear OS** und **NexLink Bridge** (die Computer-Brücke in NexLink samt Webclient). Ein später hinzukommendes Produkt ist ab seiner Veröffentlichung erfasst. |
| **Dienst** | Die von Ihnen genutzten NexLink-Produkte sowie die sie tragende Serverinfrastruktur. |
| **Betreiber** | Die natürliche Person, welche die NexLink-Produkte veröffentlicht und den Dienst betreibt. Die Kontaktangaben sind auf der Kontaktseite veröffentlicht. |
| **Kontaktseite** | `https://thvjq.com.au/nexlink/contact` — die einzige veröffentlichte Quelle für die Kontaktangaben des Betreibers. Sie wird aktuell gehalten, damit eine in einer installierten Anwendung eingebettete Zustelladresse nicht veralten kann. Sie veröffentlicht eine E-Mail-Adresse; eine Postadresse wird auf Anfrage sowie jeder berechtigten Behörde oder Partei bekannt gegeben. |
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
