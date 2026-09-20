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
