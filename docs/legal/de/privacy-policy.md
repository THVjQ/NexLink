# Datenschutzerklärung — NexLink

**Version 0.3.0-draft** · Gilt für alle NexLink-Produkte · Noch nicht veröffentlicht · §4.7, §32

> **Entwurf.** Nicht von einer Juristin oder einem Juristen geprüft. Siehe `docs/legal/README.md`.
>
> **Massgeblich ist die englische Fassung.**

*Eine Zusammenfassung in einfacher Sprache findet sich im Transparenzhinweis.
Bei Abweichungen geht dieses Dokument vor — beide sind jedoch so verfasst, dass
sie übereinstimmen, und eine Abweichung ist ein meldenswerter Fehler.*

---

## 1. Verantwortlichkeit und ehrliche Einordnung

### 1.1 Der Betreiber
Die NexLink-Produkte werden von einer natürlichen Person in New South Wales,
Australien, veröffentlicht. Es gibt kein Unternehmen, keinen
Datenschutzbeauftragten und kein Datenschutzteam. Eine Person trifft diese
Entscheidungen und beantwortet diese Anfragen.

### 1.2 Kontakt
Der Betreiber, unter der auf der **Kontaktseite** veröffentlichten Post- und
elektronischen Adresse: `https://thvjq.com.au/nexlink/contact`

Die Adresse wird dort veröffentlicht und nicht in dieses Dokument geschrieben,
damit sie aktuell bleibt. Eine in einer installierten Anwendung eingebettete
Adresse lässt sich auf einem Gerät, das nie aktualisiert wird, nicht berichtigen.

### 1.3 Welches Datenschutzrecht für Sie gilt
Der Dienst wird nur in der Schweiz und in Australien angeboten
(Nutzungsbedingungen Ziff. 3.7). Diese Erklärung ist so verfasst, dass sie
beiden Rechtsordnungen genügt.

- **In der Schweiz** gilt das *Bundesgesetz über den Datenschutz* (DSG). Ihre
  Aufsichtsbehörde ist der **EDÖB**.
- **In Australien** gelten der *Privacy Act 1988* (Cth) und die Australian
  Privacy Principles. Ihre Aufsichtsbehörde ist das **OAIC**.

**Weichen die beiden Rechtsordnungen voneinander ab, wendet diese Erklärung die
strengere Regel auf alle an.** Zwei Standards zu führen hiesse, einem Teil der
Nutzer stillschweigend weniger zu geben, und der Unterschied ist das nicht wert.

Der Betreiber ist eine natürliche Person und kein Unternehmen mit einem Umsatz,
der ihn ohne Weiteres den Australian Privacy Principles unterstellen würde.
**Diese Erklärung ist gleichwohl so verfasst, als gälten sie**, weil die
Alternative darin bestünde, eine Ausnahme als Grund für einen weniger
sorgfältigen Umgang mit Ihren Daten anzuführen — für einen Dienst, dessen
gesamtes Versprechen der Datenschutz ist, keine vertretbare Position.

### 1.4 Begriffsbestimmungen
Die definierten Begriffe finden sich in **Anhang A** am Ende dieses Dokuments.

---

## 2. Der entscheidende Punkt

Nachrichten- und Anruf**inhalte** in NexLink Social sind
Ende-zu-Ende-verschlüsselt. Der Server speichert Geheimtext und hat die Schlüssel
nie besessen.

Dies ist eine Eigenschaft des Systementwurfs, kein Versprechen über unser
Verhalten. Es bedeutet, dass der Betreiber Ihre Nachrichteninhalte auf keine
Anfrage hin herausgeben **kann** — weder auf Ihre, noch auf die eines Dritten
oder eines Gerichts.

Es bedeutet zugleich, dass der Betreiber einiges über die *Form* Ihrer Nutzung
sehen **kann**. Diese Erklärung legt das vollständig dar, statt die
Verschlüsselung mehr versprechen zu lassen, als sie hält.

---

## 3. Welches Produkt was verarbeitet

Die Produkte unterscheiden sich erheblich darin, was sie berühren. Lesen Sie den
Abschnitt zu dem Produkt, das Sie nutzen.

### 3.0 NexLink (SMS, Telefon, Posteingang)

**Allein genutzt sendet NexLink nichts an den Betreiber.** Es gibt kein Konto,
keine Telemetrie, keine Analyse und keinen Server des Betreibers, mit dem
gesprochen würde. Alles Folgende bleibt auf Ihrem Gerät.

| Daten | Wohin | Wozu |
|---|---|---|
| Ihre SMS und MMS | Nur Ihr Gerät | Es ist Ihre SMS-App |
| Ihre Kontakte | Nur Ihr Gerät | Um Namen statt Nummern anzuzeigen |
| Ihre Anrufliste | Nur Ihr Gerät | Um kürzliche Anrufe anzuzeigen |
| Mitteilungen anderer Messenger | Nur Ihr Gerät | Der Posteingang liest sie, um eine Liste zu bilden |
| Angehängte Fotos, Audio und Video | Ihr Gerät, dann Ihr Anbieter | MMS |

**Der vereinheitlichte Posteingang liest Mitteilungen anderer Apps** (Signal,
WhatsApp, Telegram, Messenger, Discord, Instagram, Steam und NexLink Social)
über die Android-Berechtigung für Mitteilungszugriff, die Sie ausdrücklich
erteilen müssen. Diese Inhalte werden auf Ihrem Gerät gelesen, in Ihrem
Posteingang angezeigt und **nirgendwohin übermittelt**.

**Die Computer-Brücke ist die einzige Ausnahme, und sie ist optional.**
Aktivieren Sie sie, werden Ihre Nachrichten an einen **von Ihnen selbst
betriebenen Server** unter einer von Ihnen eingegebenen Adresse gesendet.
NexLink hat keinen voreingestellten Server, keinen fest hinterlegten Schlüssel
und keine Identität des Betreibers; der Betreiber erhält nichts und sieht
nichts. Nachrichten werden vor dem Verlassen des Telefons mit dem öffentlichen
Schlüssel Ihres eigenen Browsers verschlüsselt.

### 3.1 NexLink Social — Angaben, die Sie machen

| Daten | Zweck | Rechtsgrundlage | Aufbewahrung |
|---|---|---|---|
| Benutzername (dauerhaft) | Identifiziert Ihr Konto und leitet Nachrichten | Vertrag | Dauerhaft — siehe §7 |
| Passwort (gehasht) | Authentifizierung | Vertrag | Bis zur Löschung |
| Anzeigename | Zeigt Sie anderen Nutzern | Vertrag | Bis zur Löschung |
| Profilbild | Zeigt Sie anderen Nutzern | Vertrag | Bis zur Löschung |
| E-Mail-Adresse (optional) | Ausschliesslich Kontowiederherstellung | Einwilligung | Bis zur Löschung oder Entfernung |
| Altersbestätigung (Ja/Nein) | Rechtliche Zulassung | Gesetzliche Pflicht | Kontodauer + Verjährungsfrist |
| Nachweis der Annahme der Bedingungen | Nachweis der Zustimmung | Gesetzliche Pflicht | Kontodauer + Verjährungsfrist |

**Wir erheben Ihr Geburtsdatum nicht.** Nur, ob Sie das Mindestalter von 16
Jahren bestätigt haben.

### 3.2 NexLink Social — durch die Nutzung entstehende Daten

| Daten | Zweck | Rechtsgrundlage | Aufbewahrung |
|---|---|---|---|
| Verschlüsselte Nachrichteninhalte | Zustellung | Vertrag | Bis zur Löschung; nach Ihrer Einstellung |
| Verschlüsselte Mediendateien | Zustellung | Vertrag | Bis zur Löschung oder Bereinigung |
| Raummitgliedschaft | Zustellung | Vertrag | Bis zum Verlassen oder zur Löschung |
| Ereignis-Metadaten: wer, wann, Grösse | Beim Routing unvermeidbar | Berechtigtes Interesse — Betrieb | Bis zur Kontolöschung |
| Reaktions-Emoji, Absender, Ziel | Matrix überträgt Reaktionen unverschlüsselt | Vertrag | Bis zur Löschung |
| Lesebestätigungen, Tippanzeigen | Funktionsbetrieb | Vertrag | Vorübergehend |
| Geräteliste und öffentliche Geräteschlüssel | Verschlüsselung und Ihre eigene Sicherheitsübersicht | Vertrag | Bis zur Entfernung des Geräts |
| Push-Token | Zustellung von Mitteilungen | Vertrag | Bis Geräteentfernung oder Abmeldung |

### 3.3 Automatisch erhobene Daten

| Daten | Zweck | Rechtsgrundlage | Aufbewahrung |
|---|---|---|---|
| IP-Adresse | Missbrauchsbearbeitung, Diagnose | Berechtigtes Interesse — Sicherheit | **28 Tage**, und bei Kontolöschung entfernt |
| User-Agent / Client-Version | Diagnose, Kompatibilität | Berechtigtes Interesse | 28 Tage |
| Verbindungszeitstempel | Sicherheit und Diagnose | Berechtigtes Interesse | 28 Tage |

### 3.4 Einladungen

| Daten | Zweck | Rechtsgrundlage | Aufbewahrung |
|---|---|---|---|
| Einweg-Hash des Einladungscodes | Verhindert Wiederverwendung | Vertrag | Lebensdauer des Einladungsbaums |
| Wer hat sie ausgestellt, und wann | Missbrauchsverfolgung, Baumintegrität | Berechtigtes Interesse | **Nach Löschung erhalten**, pseudonymisiert — siehe §7 |
| Ob und wann sie eingelöst wurde | Baumintegrität | Berechtigtes Interesse | Wie vor |

### 3.5 Meldungen und Moderation

| Daten | Zweck | Rechtsgrundlage | Aufbewahrung |
|---|---|---|---|
| Meldung: wer, über wen, wann, Beschreibung | Sicherheit | Berechtigtes Interesse — Schutz der Nutzer | 2 Jahre |
| Nachrichteninhalt in einer Meldung | Sicherheit | **Ausdrückliche Einwilligung**, je Meldung | 2 Jahre |
| Ergriffene Moderationsmassnahmen | Rechenschaft, Beschwerden | Berechtigtes Interesse | 2 Jahre |

**Inhalte erreichen den Betreiber ausschliesslich über eine Meldung, bei der der
meldende Nutzer ihrer Beifügung ausdrücklich zugestimmt hat**, indem er ein
nicht vorangekreuztes Kästchen aktiviert. Einen anderen Weg gibt es nicht, und es
existiert kein Betreiberschlüssel, der eine Unterhaltung lesen könnte.

### 3.6 Spenden — was geschieht, wenn Sie die Spendenseite nutzen

Die Produkte sind unentgeltlich (Nutzungsbedingungen Ziff. 2.7). Aus ihnen wird
auf eine **freiwillige Spendenseite** eines Dritten (Buy Me a Coffee) verlinkt.

| Daten | Wer hält sie | Was der Betreiber sieht |
|---|---|---|
| Ihre Karten- oder Bankdaten | Der Spendenanbieter, nie der Betreiber | **Nichts** |
| Ihr Name oder gewählter Anzeigename und eine allfällige Nachricht | Der Spendenanbieter | Was Sie dort eingetragen haben |
| Betrag und Datum | Der Spendenanbieter | Betrag und Datum |
| Ihre E-Mail-Adresse, sofern der Anbieter sie weitergibt | Der Spendenanbieter | Möglicherweise, je nach dessen Einstellungen |

- **Der Betreiber erhält, sieht und speichert Ihre Zahlungsdaten nicht.** Sie
  durchlaufen kein NexLink-System.
- **Eine Spende wird nicht mit Ihrem Konto verknüpft.** Der Betreiber verbindet
  eine Spende nicht mit einem NexLink-Benutzernamen und fragt beim Spenden nicht
  nach Ihrem Konto. Stellen Sie diese Verbindung in einer Spendennachricht
  selbst her, so haben Sie dies selbst getan.
- **Der Spendenanbieter ist ein eigener Verantwortlicher** mit eigener
  Datenschutzerklärung und eigener Rechtsordnung. Lesen Sie diese vor dem
  Spenden; der Betreiber hat darauf keinen Einfluss.
- Spenden verschafft keinen Vorteil, und nicht zu spenden hat keine Folgen
  (Nutzungsbedingungen Ziff. 2.7).

### 3.7 Was wir niemals erheben
- Ihre Kontakte oder Ihr Adressbuch, auf keinem Server.
- Ihren Standort. Kein NexLink-Produkt verlangt eine Standortberechtigung.
- Werbe- oder Tracking-Kennungen.
- Verhaltensanalysen.
- Ihr Geburtsdatum.
- **Ihre Zahlungsdaten** — siehe §3.6. Spenden werden vollständig von einem
  Dritten abgewickelt; keine Zahlungsdaten erreichen den Betreiber.

---

## 4. Was wir nicht sehen können, genau benannt

Bei NexLink Social kann der Betreiber nicht lesen:
- den Text Ihrer Nachrichten;
- Ton oder Bild Ihrer Anrufe;
- Ihre Fotos, Videos oder Dateien;
- Ihren Nachrichtenverlauf.

Der Betreiber **kann** sehen:
- welche Konten an einer Unterhaltung beteiligt sind und wann eine Nachricht
  gesendet wurde;
- ungefähre Nachrichtengrössen;
- Ihre IP-Adresse, während 28 Tagen;
- welche Geräte Sie nutzen und wann sich jedes zuletzt verbunden hat;
- **mit welchem Emoji Sie auf wessen Nachricht reagiert haben.** Matrix überträgt
  Reaktionen unverschlüsselt; die Nachricht selbst bleibt verschlüsselt. Wir legen
  dies offen, weil es zutrifft und weil es schlimmer wäre, dabei ertappt zu
  werden, es verschwiegen zu haben.

Bei NexLink kann der Betreiber **überhaupt nichts** sehen, weil kein Server
existiert, mit dem er es könnte.

---

## 5. Dritte

### 5.1 Cloudflare
Transportiert den Netzverkehr zwischen Ihnen und dem Server, der keine eigene
öffentliche Adresse hat. Cloudflare sieht Ihre IP-Adresse, die Verbindungszeiten
und das Verkehrsvolumen. Verschlüsselte Inhalte kann es nicht lesen.

### 5.2 Google — Firebase Cloud Messaging
Stellt Push-Mitteilungen auf Android-Geräten zu. Google wird mitgeteilt, **dass**
eine Mitteilung an Ihr Gerät zugestellt werden soll, und wann. Die Mitteilung
enthält nur eine Ereignis- und eine Raumkennung — **niemals Nachrichteninhalt,
Absendername oder Text.** Ihr Gerät entschlüsselt anschliessend lokal zur
Anzeige.

Dies ist eine Offenlegung von Metadaten an einen Dritten. Die meisten
verschlüsselten Messenger haben diese Eigenschaft, und die meisten erwähnen sie
nicht. Wir erwähnen sie.

### 5.3 LiveKit
Leitet Ton und Bild während Anrufen weiter. LiveKit sieht, dass ein Teilnehmer
einem Anruf beigetreten ist, wann und wie lange. Anrufmedien sind
Ende-zu-Ende-verschlüsselt; LiveKit kann sie weder hören noch sehen. **Die
zugehörige Unterhaltung wird LiveKit nicht offengelegt** — der Raum wird ihm
gegenüber durch einen Einweg-Hash bezeichnet.

### 5.4 Buy Me a Coffee — ausschliesslich Spenden
Wickelt freiwillige Spenden ab, wenn Sie eine leisten. Berührt keine
Nachrichtendaten, keine Kontodaten und keine Produktfunktion. Siehe §3.6.
**Öffnen Sie die Spendenseite nie, erhält dieser Dritte nichts über Sie.**

### 5.5 Google Play
Verteilt die Android-Anwendungen und übermittelt dem Betreiber aggregierte,
nicht identifizierende Installations- und Absturzzahlen. Dies ist eine eigene
Funktion von Play; der Betreiber kann sie nicht abschalten und erfährt darüber
Ihre Identität nicht.

### 5.6 Keine weiteren
Wir setzen keine Analyseanbieter, keine Werbenetzwerke, keine
personenbezogen übermittelnde Absturzberichterstattung und keinen Dienst ein,
der Sie profiliert.

### 5.7 Übermittlung ins Ausland und die geplante Verlegung in die Schweiz

Die Server des Dienstes stehen **derzeit in Australien**. Cloudflare und Google
arbeiten global, weshalb Verkehrsmetadaten auch ausserhalb beider Zugelassener
Gebiete verarbeitet werden können. Inhalte sind unabhängig vom Transportweg
Ende-zu-Ende-verschlüsselt.

**Der Betreiber beabsichtigt, Server in der Schweiz zu betreiben.** In diesem
Fall gilt:

- Personendaten australischer Nutzer werden in die Schweiz übermittelt. Die
  Schweiz wird vom OAIC als vergleichbares Datenschutzregime anerkannt; die
  Übermittlung erfolgt nach APP 8.
- Personendaten schweizerischer Nutzer werden in der Schweiz gehalten; eine
  Übermittlung nach Australien erfolgt nach den Regeln des DSG über die
  Bekanntgabe ins Ausland — Art. 16 DSG, gestützt auf die Anerkennung eines
  angemessenen Schutzes durch den Bundesrat, andernfalls auf vertragliche
  Garantien.
- **Sie werden vor der Verlegung informiert**, da sich dadurch die zuständige
  Aufsichtsbehörde ändert. Diese Erklärung wird angepasst und bei wesentlicher
  Änderung eine erneute Annahme eingeholt.

> ⚠️ **Prüfhinweis.** Ob Australien auf der Staatenliste des Bundesrates mit
> angemessenem Datenschutz aufgeführt ist, ist vor jeder Übermittlung zu
> bestätigen und die Garantie entsprechend zu wählen. Verlassen Sie sich nicht
> auf die Zusammenfassung in diesem Absatz.

---

## 6. Sicherheit

### 6.1 Getroffene Massnahmen
- **Ende-zu-Ende-Verschlüsselung** von Nachrichten- und Anrufinhalten mit dem
  Matrix-Protokoll (Olm und Megolm).
- **Verschlüsselung der Serverdatenbank im Ruhezustand.**
- **Verschlüsselung der Backups**, bevor sie den Server verlassen.
- **Registrierung nur auf Einladung**, sodass keine offene Angriffsfläche für die
  Kontoerstellung besteht.
- **Keine Föderation** — der Server tauscht keine Daten mit anderen
  Matrix-Servern aus.
- **Administrativer Zugang ausschliesslich für den Betreiber**, über einen
  authentifizierten privaten Kanal, nie öffentlich erreichbar.
- **Automatisierte tägliche Prüfungen**, dass die Sicherheitskonfiguration nicht
  abgewichen ist, ausgeführt von einer separaten Maschine.

### 6.2 Die Grenzen, klar benannt
- **Ihr Gerät ist die Schwachstelle.** Wer Ihr Telefon entsperren kann, kann Ihre
  Nachrichten lesen. Richten Sie eine Bildschirmsperre ein.
- **Empfänger können behalten, was Sie senden.** Verschlüsselung steuert nicht,
  was nach der Zustellung geschieht.
- **Metadaten sind nicht durch Verschlüsselung geschützt** und könnten
  herausverlangt werden — siehe die Richtlinien für Strafverfolgungsbehörden.
- **Dies ist ein von einer Person betriebener Dienst.** Es gibt kein
  Sicherheitsteam rund um die Uhr.

### 6.3 Meldung von Datenschutzverletzungen
Tritt eine Verletzung ein, die voraussichtlich zu einem hohen Risiko führt,
werden die betroffenen Nutzer unmittelbar und unverzüglich benachrichtigt.

- **Australien:** Das OAIC wird benachrichtigt, soweit das Notifiable-Data-
  Breaches-Regime dies verlangt.
- **Schweiz:** Der EDÖB wird so rasch als möglich benachrichtigt, soweit Art. 24
  DSG dies verlangt; betroffene Personen werden informiert, soweit dies zu ihrem
  Schutz erforderlich ist oder der EDÖB es verlangt.

Eine Meldung nennt den Hergang, die betroffenen Daten, die getroffenen
Massnahmen und das, was Sie tun sollten.

Da Inhalte Ende-zu-Ende-verschlüsselt sind, würde eine Serverkompromittierung
Metadaten offenlegen, nicht Nachrichten. Diese Unterscheidung wird zutreffend
dargestellt und nicht zur Verharmlosung verwendet.

---

## 7. Aufbewahrung und Löschung

### 7.1 Löschung Ihres Kontos
Sie können Ihr Konto jederzeit löschen, in der App oder unter
`https://nexlink.thvjq.com.au/delete/` ohne installierte App.

Die Löschung entfernt: das Konto und sämtliche Sitzungen, Ihren Anzeigenamen und
Ihr Bild, hochgeladene Dateien, Ihr verschlüsseltes Schlüssel-Backup, eine
allfällige E-Mail-Adresse sowie Ihre IP- und User-Agent-Aufzeichnungen.

### 7.2 Was nach der Löschung erhalten bleibt, und weshalb

| Erhalten | Weshalb | Grundlage |
|---|---|---|
| Ihr Benutzername, gesperrt | Damit er nie neu vergeben und zur Identitätstäuschung genutzt werden kann | Berechtigtes Interesse — Schutz Dritter |
| Der Annahmenachweis | Nachweis der Annahme der Bedingungen und der Altersbestätigung | Gesetzliche Pflicht; Rechtsverteidigung |
| Der Einladungsdatensatz — Einweg-Hash und Zeitstempel, pseudonymisiert | Missbrauchsverfolgung und Integrität des Einladungsbaums | Berechtigtes Interesse |

### 7.3 Backups — der Teil, den die meisten Erklärungen auslassen
**Ein gelöschtes Konto besteht in verschlüsselten Datenbank-Backups bis zu 14
Tage fort**, bevor diese verfallen und vernichtet werden.

Das ist üblich und vertretbar, und es steht hier, weil ein Löschversprechen, das
einem Backup-Plan stillschweigend widerspricht, kein eingehaltenes Versprechen
ist. Backups sind verschlüsselt und werden ausser zur Wiederherstellung des
Dienstes weder durchsucht noch verwendet.

### 7.4 Nachrichten im Besitz anderer
Die Löschung Ihres Kontos löscht nicht die von Ihnen gesendeten Nachrichten auf
den Geräten der Empfänger. Wir können nicht auf das Gerät einer anderen Person
zugreifen und möchten es auch nicht können.

### 7.5 Vollständiger Aufbewahrungsplan
Siehe das separate Dokument «Aufbewahrungsplan».

---

## 8. Ihre Rechte

### 8.1 Was Sie verlangen können
Diese Rechte bestehen sowohl nach dem DSG als auch nach dem Privacy Act. Wo der
Umfang abweicht, gilt der weitere.

- **Auskunft** — eine Kopie der über Sie gehaltenen Personendaten.
- **Berichtigung** — von allem Unrichtigen.
- **Löschung** — Ihres Kontos, vorbehältlich §7.2.
- **Datenherausgabe/-übertragung** — in einem gängigen elektronischen Format
  (Art. 28 DSG).
- **Widerspruch** — gegen eine auf berechtigtes Interesse gestützte Bearbeitung.
- **Einschränkung** — der Bearbeitung während der Klärung einer Streitigkeit.
- **Auskunft über eine Bekanntgabe ins Ausland** — in welchen Staat und gestützt
  auf welche Garantie (Art. 19 DSG).
- **Keine automatisierte Einzelentscheidung** — siehe §10; es gibt keine.

### 8.2 Was Sie selbst erledigen können
- **Löschung**: in der App oder auf der Löschseite.
- **Datenherausgabe**: die App exportiert Ihre Nachrichten als JSON. **Wir können
  das nicht für Sie tun** — wir können sie nicht lesen. Dies ist der klarste
  Fall, in dem die Verschlüsselung ändert, wer ein Recht erfüllen kann.
- **Berichtigung** Ihres Anzeigenamens: in der App.

### 8.3 Wofür Sie sich an den Betreiber wenden
Auskunft über serverseitige Aufzeichnungen, Widerspruch, Einschränkung oder
alles, was die App nicht abbildet. Wenden Sie sich über die Kontaktseite an den
Betreiber.

Rechnen Sie mit einer Antwort **innert 30 Tagen**. Das DSG erlaubt eine
Verlängerung bei komplexen Gesuchen; ist eine nötig, werden Sie innert der 30
Tage unter Angabe des Grundes informiert. Ein Einzelbetreiber kann die volle
Frist beanspruchen.

**Auskunftsgesuche sind kostenlos**, wie beide Gesetze es verlangen.

### 8.4 Identitätsprüfung
Bevor wir auf ein Gesuch zu einem Konto reagieren, prüfen wir, dass Sie es
kontrollieren, in der Regel durch eine Handlung aus dem angemeldeten Konto
heraus. Eine E-Mail-Adresse allein genügt uns nicht als Nachweis, weil dies ein
Weg zu den Daten einer anderen Person wäre.

### 8.5 Das Gesuch, das nicht erfüllt werden kann
Ein Gesuch um den **Inhalt** Ihrer auf dem Server liegenden Nachrichten kann
nicht erfüllt werden, weil der Server ihn nicht in lesbarer Form hält. Das ist
keine Verweigerung; es gibt nichts herauszugeben. Ihr Gerät hält ihn, und das
Export-Werkzeug ist der Weg dazu.

### 8.6 Beschwerden
Wenden Sie sich zunächst über die Kontaktseite an den Betreiber.

- **Schweiz:** Sie können den Eidgenössischen Datenschutz- und
  Öffentlichkeitsbeauftragten (edoeb.admin.ch) benachrichtigen und nach Art. 32
  und 41 DSG zivilrechtlich vorgehen.
- **Australien:** Sie können sich beim Office of the Australian Information
  Commissioner (oaic.gov.au) beschweren.

Sie müssen sich nicht zuerst beim Betreiber beschweren, und nichts in dieser
Erklärung verlangt dies.

---

## 9. Kinder
Der Dienst wird Personen unter **16** Jahren nicht angeboten
(Nutzungsbedingungen Ziff. 3.1). Wir halten wissentlich keine Daten über Personen
darunter, und ein solches Konto wird entfernt.

---

## 10. Automatisierte Entscheidungen
Es gibt keine. Kein Profiling, keine automatisierte Moderation, keine
algorithmische Sortierung. Jede Moderationsentscheidung trifft ein Mensch.

---

## 11. Cookies und lokale Speicherung
Die Webseiten — Element Web, die Löschseite und die Kontaktseite — nutzen die
lokale Browserspeicherung ausschliesslich für Sitzung und Einstellungen. Es gibt
keine Tracking-Cookies, keine Drittanbieter-Cookies und keine Werbekennungen.

---

## 12. Änderungen dieser Erklärung
Diese Erklärung ist versioniert. Eine wesentliche Änderung erfordert eine erneute
Annahme in der App, sodass Sie nicht stillschweigend auf andere Bedingungen
umgestellt werden können. Die Versionshistorie ist auf Anfrage erhältlich.

---
