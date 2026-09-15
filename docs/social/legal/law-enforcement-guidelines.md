# Law Enforcement Guidelines — NexLink Social

**Version 0.2.0-draft** · Not yet published · §30.5

For law enforcement and legal practitioners. This document describes what data
exists and how requests are handled. **It is not legal advice and does not waive
any right of the Operator or of any user.**

---

## 1. What this Service is

A private, invite-only, end-to-end encrypted messaging and calling service
operated by an individual in New South Wales, Australia. It does not federate
with other servers. It has no corporate entity, no legal department, and no
dedicated point of contact beyond the Operator.

---

## 2. What does not exist

**Message content and call content are end-to-end encrypted. The Operator does
not hold the decryption keys and cannot produce plaintext.**

This is not a policy position that could be changed by a court order. There is
no key escrow, no server-side copy, and no mechanism by which the Operator could
comply. An order to produce message content would be an order to produce
something that does not exist in that form.

| Requested | Available? |
|---|---|
| Message content | **No** — not held in readable form, by design |
| Call content | **No** — end-to-end encrypted |
| Media files | Ciphertext only; keys not held |
| Historical message content | **No** |
| Ability to intercept future messages | **No** — would require a client-side change, and see §5 |

---

## 3. What does exist

| Requested | Available? | Retention |
|---|---|---|
| Account existence, username, creation date | Yes | Life of Account, tombstoned after |
| Email address | Only if the user provided one; many have not | Until deletion |
| Device list, last seen times | Yes | Until Device removed |
| Which Accounts share a conversation, and when messages were sent | Yes | Until deletion |
| Approximate message sizes | Yes | Until deletion |
| IP addresses and user agents | Yes | **28 days only** |
| The Invite record, including who issued the Invite | Yes | Retained after deletion |
| Reports made about an Account | Yes | 2 years |

**Note the 28-day limit on IP records.** A request received after that window
cannot be satisfied for that period, and no copy is retained elsewhere except in
database backups, which expire after 14 days.

---

## 4. How to make a request

1. Serve the request on the Operator at **google.alumni829@passmail.net**, or using the address in
   the service website.
2. Include the legal basis, the specific data sought, and the relevant time
   period.
3. **Requests must be specific.** Requests for "all data" or for content the
   Operator cannot produce will be answered by explaining what exists.

Requests will be verified as genuine and properly served before any response.

---

## 5. How requests are handled

1. The request is acknowledged and the date recorded.
2. **The Operator will obtain legal advice before responding.** This is a
   solo-operated service, and that step takes time.
3. The Operator determines what is being asked for and what actually exists.
4. The Operator complies only with what is lawfully required, and only to the
   extent required.
5. **The affected user is notified, unless the Operator is legally prohibited
   from notifying them.**
6. The request is recorded for transparency reporting.

---

## 6. Emergency requests

Where there is a credible risk of imminent serious harm, the Operator will act
as quickly as they are able. **Users should understand that "as quickly as able"
for a solo operator may be hours.** This Service should not be relied upon in a
situation requiring an immediate response.

---

## 7. Requests we will refuse

- Requests for message content, because it does not exist in readable form.
- Requests to add a backdoor, weaken encryption, or modify the client to
  intercept a user. Such a request would be resisted to the extent lawfully
  possible, and if compliance were compelled the Operator would consider
  discontinuing the Service instead.
- Informal requests without legal basis.
- Requests for data about users of other services.

---

## 8. Transparency reporting

The Operator intends to publish periodic figures: the number of requests
received, the number complied with, and the categories of data produced —
consistent with any legal restriction on disclosure.

Where the Operator is prohibited from disclosing that a request was received,
the report will say only what it lawfully can.

---

## 9. Preservation requests

The Operator will honour a lawful preservation request for data that exists at
the time the request is received. **A preservation request cannot preserve data
that has already expired**, including IP records older than 28 days.
