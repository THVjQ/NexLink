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
