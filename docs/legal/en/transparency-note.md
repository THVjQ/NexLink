# What we can and cannot see — NexLink

**Version 0.3.0-draft** · Not yet published · §3.7, §4.7

> **Scope.** Applies to all NexLink Products.
>
> **Draft.** Not reviewed by a lawyer. See `docs/legal/README.md`.

This is the plain-language version. The Privacy Policy is the formal one; where
they differ that one governs — but they are written to agree, and a discrepancy
is a bug worth reporting.

---

## The short version

Your messages are end-to-end encrypted. The server stores them as scrambled data
and has never had the keys. Nobody operating this service can read what you
write, and that is a property of the encryption rather than a promise about our
behaviour.

What we **can** see is the shape of your use: who you talk to, when, and how
often. That is real information about you, and this page does not pretend
otherwise.

---

## We cannot see

- **The content of your messages.** Not now, not later, not if asked, not if
  ordered by a court. There is nothing to hand over.
- **The content of your calls.**
- **Your photos, videos and files.** Encrypted before they leave your phone.
- **Your message history.** It lives on your devices.
- **Who is in your contacts.** We never ask for them.
- **Where you are**, beyond what your IP address implies.

## We can see

- **Who you message, and when.** The server routes your messages, so it knows
  which accounts are involved and the timing.
- **How often you send messages, and roughly how large they are.**
- **Your IP address**, which approximates your location and names your internet
  provider. Kept 28 days — **six months if the service moves to Switzerland**,
  because Swiss surveillance law sets that period. Privacy Policy §3.3a explains
  it, including what it does to deletion.
- **Which devices you use**, and when each last connected.
- **Which emoji you react with, and to whose message.** Matrix sends reactions
  unencrypted. The message being reacted to stays encrypted; the reaction does
  not. We would rather tell you than let you assume otherwise.
- **That a message arrived for you, and when** — see Google, below.
- **Who invited you.**

---

## The three companies involved, and exactly what each learns

We use as few as possible. None of them can read your messages.

### Cloudflare
Carries traffic between your phone and the server, because the server sits on a
home internet connection with no public address of its own.

**Learns:** your IP address, when you connect, how much data flows.
**Cannot:** read anything encrypted, which is everything that matters.

### Google — push notifications
When a message arrives for you, the server asks Google to wake the app on your
phone. Without this, messages would only arrive while the app was open, or the
app would have to hold a connection open and flatten your battery.

**Learns:** that a notification should go to your device, and when.
**Is sent:** an event identifier and a room identifier. **Not the message, not
the sender's name, not a single word of text.** Your phone decrypts locally to
work out what the notification should say.

Most encrypted messengers have exactly this property. Most do not mention it.

### LiveKit — calls
Relays audio and video during a call.

**Learns:** that someone joined a call, when, and for how long.
**Cannot:** hear or see the call — the media is encrypted end-to-end.
**Is not told:** which conversation the call belongs to. The room is identified
to them only by a one-way hash.

---

## Who runs this

One person, not a company. There is no support team, no service level agreement
and no guarantee of uptime. If something breaks at 2 a.m. it stays broken until
they wake up.

That cuts both ways, and it is the honest trade this service asks you to make.
There is no advertising business model, nothing is sold, and nobody is
monetising your attention — because there is nobody to do it.

---

## What happens when you leave

Delete your account in the app, or at `https://nexlink.thvjq.com.au/delete/`
without the app installed.

**Removed:** your account, every session, your profile, your uploaded files,
your encrypted backup, your email address if you gave one, and the record of the
addresses you connected from.

**Kept, and why:**
- **Your username** — so nobody can register it later and be mistaken for you.
- **That you accepted the terms and confirmed your age** — the record that you
  did, not the details.
- **The invite you joined with** — a one-way code and timestamps, so abuse can
  be traced.

**And one thing worth knowing:** deleted data sits inside encrypted backups for
up to 14 days before those expire. That is normal, and we would rather say it
than have you discover the gap between "deleted" and a backup schedule.

---

## The limits of all this

- **Your phone is the weak point.** Everything above concerns the server. If
  someone unlocks your phone, they read your messages. Set a screen lock.
- **The other person can screenshot.** Encryption protects a message in transit
  and at rest. It cannot stop a recipient keeping it, and nothing can.
- **Metadata could be compelled.** We cannot be made to produce message content,
  because we do not have it. A lawful order for the "can see" list above is a
  different matter, and we would tell you about it unless legally forbidden.
- **Deleting your account does not delete messages from other people's phones.**
  We have no reach into someone else's device, and would not want one.
- **There is no scanning for bad content**, and there cannot be — that is the
  same encryption working. Safety here depends on blocking and reporting, both
  of which are in the app.

---

## If something is wrong

If any statement on this page turns out to be inaccurate, that is a bug and we
want to know. The whole value of this document is that it is checkable.
