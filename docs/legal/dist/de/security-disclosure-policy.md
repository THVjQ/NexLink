<!-- Erstellt von tools/legal/build-legal.py. Bearbeiten Sie docs/legal/de/security-disclosure-policy.md
     oder docs/legal/definitions.de.md, niemals diese Datei. -->

# Richtlinie zur Offenlegung von Sicherheitslücken — NexLink

**Version 0.3.0-draft** · Noch nicht veröffentlicht · §30

> **Geltungsbereich.** Gilt für alle NexLink-Produkte und die sie tragenden Server.
>
> **Entwurf.** Nicht juristisch geprüft. **Massgeblich ist die englische Fassung.**

---

## 1. Geltungsbereich

Diese Richtlinie erfasst die Android-Anwendung NexLink Social, deren Heimserver
unter `nexlink.thvjq.com.au`, NexLink Bridge samt Webclient, die Seite zur Kontolöschung und die
dafür betriebenen Hilfsdienste.

Sie erfasst **nicht** die Drittdienste, auf die der Dienst angewiesen ist —
Cloudflare, Google Firebase, LiveKit —, die eigene Meldeprogramme führen, und
auch nicht das Matrix-Protokoll oder den Synapse-Heimserver, die deren
Betreuerinnen und Betreuern zu melden sind.

---

## 2. Die ehrliche Einschränkung

Dieser Dienst wird von einer einzelnen Person als privates Projekt betrieben. Es
gibt:

- **Kein Bug-Bounty-Programm** und keinerlei Vergütung.
- **Keine zugesicherte Reaktionszeit**, siehe jedoch §5 zur Absicht.
- **Kein eigenes Sicherheitsteam.** Wer Ihre Meldung liest, ist dieselbe Person,
  die den Code geschrieben hat und den Server betreibt.

Dies wird gesagt, damit niemand Aufwand in der Erwartung eines
Unternehmensprozesses verschwendet.

---

## 3. Wie zu melden ist

Wenden Sie sich an den Betreiber unter der auf der **Kontaktseite**
veröffentlichten Adresse: `https://thvjq.com.au/nexlink/contact`, mit:

- was Sie gefunden haben und wo;
- den Schritten zur Reproduktion;
- was eine angreifende Person damit tun könnte;
- ob Sie es anderswo offengelegt haben.

Ist das Problem schwerwiegend, vermerken Sie dies in der Betreffzeile.

---

## 4. Was wir von Ihnen erbitten

- **Greifen Sie nicht auf Daten anderer Nutzer zu, ändern oder löschen Sie sie
  nicht.** Nutzen Sie Ihr eigenes Konto oder bitten Sie um ein Testkonto.
- **Beeinträchtigen Sie den Dienst nicht** für andere — keine
  Dienstverweigerungsangriffe, keine Lasttests.
- **Setzen Sie kein Social Engineering** gegen den Betreiber oder Nutzer ein.
- **Räumen Sie angemessene Zeit zur Behebung ein**, bevor Sie öffentlich
  offenlegen. Üblich sind 90 Tage; weniger, wenn die Lücke aktiv ausgenutzt
  wird — in diesem Fall hat der Betreiber gegen eine frühere Offenlegung nichts
  einzuwenden.

Prüfungen innerhalb dieser Grenzen behandelt der Betreiber nicht als unbefugt.

---

## 5. Was Sie erwarten können

| Schritt | Angestrebte Frist |
|---|---|
| Empfangsbestätigung | 7 Tage |
| Erste Einschätzung | 14 Tage |
| Behebung eines kritischen Problems | So rasch wie möglich; eine vorübergehende Abschaltung ist eine zulässige Sofortmassnahme |
| Behebung eines weniger schweren Problems | Nach bestem Bemühen |
| Nennung | Angeboten, auf Wunsch unterlassen |

Dies sind Absichten, keine Zusagen. Ein Einzelbetreiber kann abwesend sein.

---

## 6. Schweregrad, und was zu einer Abschaltung führt

Manche Probleme sind schlimmer als ein nicht verfügbarer Dienst. Der Betreiber
nimmt den Dienst vom Netz, statt ihn weiterzubetreiben, wenn:

- Nachrichtenklartext serverseitig offenliegt;
- private Schlüssel oder der Signaturschlüssel des Servers offenliegen;
- sich eine angreifende Person als anderer Nutzer authentifizieren kann;
- eine angreifende Person unbemerkt ein Gerät zum Konto eines anderen hinzufügen
  kann.

Verfügbarkeit ist hier nicht der höchste Wert. Vertraulichkeit ist es.

---

## 7. Was keine Sicherheitslücke ist

- Dass der Server Metadaten hält. Dies ist in der Datenschutzerklärung
  dokumentiert und eine Eigenschaft des Entwurfs.
- Dass Reaktionen unverschlüsselt gesendet werden. Dokumentiert und eine
  Eigenschaft des Matrix-Protokolls.
- Dass ein Empfänger ein Bildschirmfoto machen kann.
- Dass es keine Verfügbarkeitsgarantie gibt.
- Fehlende Sicherheits-Header ohne nachgewiesene Auswirkung.
- Von einem automatischen Scanner erzeugte, ungeprüfte Meldungen.

---

## 8. Wenn eine Verletzung eintritt

Der Betreiber benachrichtigt betroffene Nutzer unmittelbar und unverzüglich und
benachrichtigt das Office of the Australian Information Commissioner oder den
Eidgenössischen Datenschutz- und Öffentlichkeitsbeauftragten, soweit dies
erforderlich ist.

Eine Meldung nennt den Hergang, die betroffenen Daten, die getroffenen
Massnahmen und das, was der Nutzer tun sollte. Da Inhalte
Ende-zu-Ende-verschlüsselt sind, würde eine serverseitige Verletzung Metadaten
offenlegen, nicht Nachrichten — und diese Unterscheidung wird zutreffend
dargestellt und nicht zur Verharmlosung verwendet.

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
