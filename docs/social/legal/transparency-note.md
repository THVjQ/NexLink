# What we can and cannot see — NexLink Social

**Version 0.1.0-draft** · Not yet published · §4.7

This is the plain-language version. The Privacy Policy is the formal one; where
they differ, that one governs — but they should not differ, and if you spot a
place where they do, that is a bug worth reporting.

## The short version

Your messages are end-to-end encrypted. The server stores them as scrambled data
and has never had the keys. Nobody operating this service can read what you
write, and that is a property of the encryption rather than a promise about our
conduct.

What we **can** see is the shape of your use: who you talk to, when, and how
often. That is real information about you and this page does not pretend
otherwise.

## We cannot see

- **The content of your messages.** Not now, not later, not if asked.
- **The content of your calls.**
- **Your photos, videos and files.** They are encrypted before upload.
- **Your message history.** It lives on your devices.

## We can see

- **Who you message, and when.** The server routes your messages, so it knows
  the accounts involved and the timing.
- **How often you send messages, and roughly how large they are.**
- **Your IP address**, which approximates your location and your internet
  provider.
- **Which devices you use**, and when each last connected.
- **Which emoji you react with, and to whose message.** Matrix sends reactions
  unencrypted. The message being reacted to stays encrypted; the reaction does
  not. We would rather tell you than let you assume otherwise.
- **That a message arrived for you, and when.** To notify your phone, we ask
  Google's push service to wake the app. Google is told that *something*
  arrived and when — never what it says.

## Third parties, and exactly what each one learns

We use as few as possible, and none of them can read your messages.

| Who | What they learn | Why |
|---|---|---|
| **Cloudflare** | Your IP address, and that you connected | It carries traffic to the server, which has no public address of its own |
| **Google (FCM)** | That a notification was sent to your device, and when | It is the only way to wake an Android app without draining the battery |
| **LiveKit** | That someone was in a call, when, and for how long | It relays call audio and video. The audio and video are encrypted; it cannot hear them, and it is not told which conversation the call belongs to |

## Who runs this

One person, not a company. There is no support team, no SLA, and no guarantee
of uptime. If something breaks at 2 a.m. it stays broken until they wake up.

That cuts both ways, and it is the honest trade this service asks you to make:
there is no advertising business model, nothing is sold, and nobody is
monetising your attention — because there is nobody to do it.

## What happens when you leave

You can delete your account from inside the app, or from
`https://nexlink.thvjq.com.au/delete/` without the app installed. It removes
your account, your profile, your uploaded files, your encrypted backup and the
record of the addresses you connected from.

Three things are kept, and the Privacy Policy explains why:

- **Your username**, so nobody can register it later and be mistaken for you.
- **That you accepted the terms and confirmed your age** — the record that you
  did, not the details.
- **The invite you joined with**, as a one-way code and timestamps, so abuse can
  be traced.

## The limits of all this

- **Your phone is the weak point.** Everything above is about the server. If
  someone unlocks your phone, they read your messages. Set a screen lock.
- **The other person can screenshot.** Encryption protects a message in transit
  and at rest. It cannot stop a recipient keeping it.
- **We could be compelled to keep metadata.** We cannot be compelled to produce
  message content, because we do not have it — but a lawful order for the
  information in the "can see" list is a different matter.
