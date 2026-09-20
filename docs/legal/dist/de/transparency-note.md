<!-- Erstellt von tools/legal/build-legal.py. Bearbeiten Sie docs/legal/de/transparency-note.md
     oder docs/legal/definitions.de.md, niemals diese Datei. -->

# Was wir sehen können und was nicht — NexLink

**Version 0.3.0-draft** · Noch nicht veröffentlicht · §3.7, §4.7

> **Geltungsbereich.** Gilt für alle NexLink-Produkte.
>
> **Entwurf.** Nicht juristisch geprüft. **Massgeblich ist die englische Fassung.**

Dies ist die Fassung in einfacher Sprache. Die Datenschutzerklärung ist die
förmliche; bei Abweichungen geht jene vor — beide sind jedoch so verfasst, dass
sie übereinstimmen, und eine Abweichung ist ein meldenswerter Fehler.

---

## Kurz gefasst

Ihre Nachrichten sind Ende-zu-Ende-verschlüsselt. Der Server speichert sie als
unlesbare Daten und hat die Schlüssel nie besessen. Niemand, der diesen Dienst
betreibt, kann lesen, was Sie schreiben — und das ist eine Eigenschaft der
Verschlüsselung, kein Versprechen über unser Verhalten.

Was wir **sehen** können, ist die Form Ihrer Nutzung: mit wem Sie sprechen, wann
und wie oft. Das sind echte Informationen über Sie, und diese Seite tut nicht so,
als wäre es anders.

---

## Wir können nicht sehen

- **Den Inhalt Ihrer Nachrichten.** Weder jetzt noch später, weder auf Anfrage
  noch auf gerichtliche Anordnung. Es gibt nichts herauszugeben.
- **Den Inhalt Ihrer Anrufe.**
- **Ihre Fotos, Videos und Dateien.** Sie werden verschlüsselt, bevor sie Ihr
  Telefon verlassen.
- **Ihren Nachrichtenverlauf.** Er liegt auf Ihren Geräten.
- **Wer in Ihren Kontakten ist.** Wir fragen nie danach.
- **Wo Sie sind**, über den Rückschluss aus der IP-Adresse hinaus.

## Wir können sehen

- **Wem Sie schreiben und wann.** Der Server leitet Ihre Nachrichten weiter und
  kennt daher die beteiligten Konten und die Zeitpunkte.
- **Wie oft Sie Nachrichten senden und ungefähr wie gross sie sind.**
- **Ihre IP-Adresse**, die Ihren Standort annähert und Ihren Internetanbieter
  benennt. 28 Tage aufbewahrt — **sechs Monate, falls der Dienst in die Schweiz
  verlegt wird**, weil das schweizerische Überwachungsrecht diese Frist setzt.
  §3.3a der Datenschutzerklärung erklärt dies samt Folgen für die Löschung.
- **Welche Geräte Sie nutzen** und wann sich jedes zuletzt verbunden hat.
- **Mit welchem Emoji Sie auf wessen Nachricht reagieren.** Matrix überträgt
  Reaktionen unverschlüsselt. Die Nachricht, auf die reagiert wird, bleibt
  verschlüsselt; die Reaktion nicht. Wir sagen es Ihnen lieber, als Sie etwas
  anderes annehmen zu lassen.
- **Dass eine Nachricht für Sie eingetroffen ist, und wann** — siehe Google
  unten.
- **Wer Sie eingeladen hat.**

---

## Die drei beteiligten Unternehmen, und was genau jedes erfährt

Wir setzen so wenige wie möglich ein. Keines kann Ihre Nachrichten lesen.

### Cloudflare
Transportiert den Verkehr zwischen Ihrem Telefon und dem Server, weil dieser an
einem privaten Internetanschluss ohne eigene öffentliche Adresse steht.

**Erfährt:** Ihre IP-Adresse, wann Sie sich verbinden, wie viele Daten fliessen.
**Kann nicht:** Verschlüsseltes lesen — also alles, worauf es ankommt.

### Google — Push-Mitteilungen
Trifft eine Nachricht für Sie ein, bittet der Server Google, die App auf Ihrem
Telefon zu wecken. Ohne dies kämen Nachrichten nur bei geöffneter App an, oder
die App müsste eine Verbindung offenhalten und Ihren Akku leeren.

**Erfährt:** dass eine Mitteilung an Ihr Gerät gehen soll, und wann.
**Erhält:** eine Ereignis- und eine Raumkennung. **Nicht die Nachricht, nicht den
Namen des Absenders, kein einziges Wort Text.** Ihr Telefon entschlüsselt lokal,
um den Text der Mitteilung zu ermitteln.

Die meisten verschlüsselten Messenger haben genau diese Eigenschaft. Die meisten
erwähnen sie nicht.

### LiveKit — Anrufe
Leitet Ton und Bild während eines Anrufs weiter.

**Erfährt:** dass jemand einem Anruf beigetreten ist, wann und wie lange.
**Kann nicht:** den Anruf hören oder sehen — die Medien sind
Ende-zu-Ende-verschlüsselt.
**Erfährt nicht:** zu welcher Unterhaltung der Anruf gehört. Der Raum wird ihnen
gegenüber nur durch einen Einweg-Hash bezeichnet.

---

## Wer dies betreibt

Eine Person, kein Unternehmen. Es gibt kein Support-Team, keine
Dienstgütevereinbarung und keine Verfügbarkeitsgarantie. Geht um zwei Uhr nachts
etwas kaputt, bleibt es kaputt, bis diese Person aufwacht.

Das schneidet in beide Richtungen, und es ist der ehrliche Handel, den dieser
Dienst Ihnen vorschlägt. Es gibt kein Werbegeschäftsmodell, nichts wird verkauft,
und niemand macht Ihre Aufmerksamkeit zu Geld — weil niemand da ist, der es täte.

---

## Was geschieht, wenn Sie gehen

Löschen Sie Ihr Konto in der App oder unter
`https://nexlink.thvjq.com.au/delete/` ohne installierte App.

**Entfernt:** Ihr Konto, jede Sitzung, Ihr Profil, hochgeladene Dateien, Ihr
verschlüsseltes Backup, eine allfällige E-Mail-Adresse und die Aufzeichnung der
Adressen, von denen aus Sie sich verbunden haben.

**Behalten, und weshalb:**
- **Ihr Benutzername** — damit ihn niemand später registrieren und mit Ihnen
  verwechselt werden kann.
- **Dass Sie die Bedingungen angenommen und Ihr Alter bestätigt haben** — der
  Nachweis, dass Sie es taten, nicht die Einzelheiten.
- **Die Einladung, mit der Sie kamen** — ein Einwegcode und Zeitstempel, damit
  Missbrauch zurückverfolgt werden kann.

**Und eines ist wissenswert:** Gelöschte Daten liegen bis zu 14 Tage in
verschlüsselten Backups, bevor diese verfallen. Das ist üblich, und wir sagen es
lieber, als Sie die Lücke zwischen «gelöscht» und einem Backup-Plan entdecken zu
lassen.

---

## Die Grenzen von alledem

- **Ihr Telefon ist die Schwachstelle.** Alles Obige betrifft den Server.
  Entsperrt jemand Ihr Telefon, liest er Ihre Nachrichten. Richten Sie eine
  Bildschirmsperre ein.
- **Die Gegenseite kann Bildschirmfotos machen.** Verschlüsselung schützt eine
  Nachricht auf dem Transportweg und im Ruhezustand. Sie kann nicht verhindern,
  dass ein Empfänger sie behält, und nichts kann das.
- **Metadaten könnten herausverlangt werden.** Zur Herausgabe von
  Nachrichteninhalten können wir nicht gezwungen werden, weil wir sie nicht
  haben. Eine rechtmässige Anordnung betreffend die obige Liste «Wir können
  sehen» ist etwas anderes, und wir würden es Ihnen mitteilen, sofern uns dies
  nicht rechtlich untersagt ist.
- **Die Löschung Ihres Kontos löscht keine Nachrichten auf den Telefonen
  anderer.** Wir haben keinen Zugriff auf fremde Geräte und möchten auch keinen.
- **Es gibt keine Durchsuchung nach problematischen Inhalten**, und es kann
  keine geben — das ist dieselbe Verschlüsselung bei der Arbeit. Sicherheit
  beruht hier auf Blockieren und Melden; beides ist in der App vorhanden.

---

## Wenn etwas nicht stimmt

Erweist sich eine Aussage auf dieser Seite als unzutreffend, so ist das ein
Fehler, und wir möchten davon erfahren. Der ganze Wert dieses Dokuments liegt
darin, dass es überprüfbar ist.

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
