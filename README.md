# KeyMSG

**A messaging app built for keypad phones.** Texts and picture messages, fully
usable with just the D-pad — made with kosher phones, flip phones, and
button-style Android devices in mind. Works on touchscreens too.

No app store needed. No Google account. Nothing leaves your phone.

---

## Features

- Conversations open immediately, even with years of history
- Full D-pad operation: hold to scroll fast, a date bubble shows your place,
  the selected item has a clear outline
- Physical softkey support with a setup screen (press left, press right, save);
  on-screen buttons as a fallback for phones whose softkeys don't reach apps
- Press a message to act on its contents: call a number, open a link, save a
  contact
- Group messages as one group conversation or as individual replies, set per
  group; view all participants and message or call any of them
- Per-conversation notification tone, vibration, or silence; keyword blocking
- Lock messages against deletion; recycle bin for deleted messages; scheduled
  sending
- Attach pictures, videos, audio, and contact cards — several at once; share
  into the app from the gallery
- Whole-app resize from 70% to 150%, plus separate text size settings
- Backup and restore to a single file, including attachments; restore writes
  into the phone's message storage
- Android contact-group recipients and keypad-accessible forwarding

## Installing

1. On the phone, open **[Releases](../../releases/latest)** and download the
   `KeyMSG-vX.Y.Z.apk` file.
2. Open the downloaded file. If the phone asks, allow installing from this
   source (one-time).
3. Open KeyMSG and follow the setup: make it the default messaging app, grant
   the permissions it asks for, map your softkeys (or skip), and let it import
   your existing messages.
4. The import can run in the background — press **Run in background** and start
   using the app right away.

## Building and signing

KeyMSG is a separate app with the package ID `io.github.ybeck.keymsg`. Debug
builds install as `io.github.ybeck.keymsg.debug`. Release builds are left
unsigned unless these environment variables point to your private signing key:

- `SIGNING_KEYSTORE_PATH`
- `SIGNING_KEYSTORE_PASSWORD`
- `SIGNING_KEY_ALIAS`
- `SIGNING_KEY_PASSWORD`

Never commit the keystore or its passwords. Keep the same key permanently;
Android accepts future KeyMSG updates only when they use that key. Automatic
update checks are disabled until this fork has its own signed GitHub releases.

## Moving to a new phone

1. Old phone: **Settings → Back up messages** — save the file somewhere you can
   move it (memory card, computer, Downloads).
2. New phone: install KeyMSG, then **Settings → Restore from backup** and pick
   the file.

Restore writes your messages into the phone's real message storage — they
become normal messages, visible to any messaging app, and messages already on
the phone are kept without duplicates.

## Tips

- **A softkey doesn't respond even after setup?** Some phones never deliver
  those keys to apps. Turn the softkey bar off (Settings → Softkey bar →
  Never) and on-screen buttons that work with the D-pad appear instead.
- **Group messages look mixed up, or pictures sit in the wrong chat?** Run
  **Settings → Re-import all messages** once — it rebuilds everything from the
  phone's message storage.
- **Search** from the main screen finds any message. The search bar can be
  hidden in Settings if you never use it.

## Privacy

Your messages stay on your phone. KeyMSG has no servers, no ads, no analytics,
and makes no network connections except sending and receiving MMS through your
carrier.

## License

KeyMSG is a fork of NovaLox and is free to use, copy, and share for
**noncommercial purposes** —
personal use, family, schools, charities, and the like — under the
[PolyForm Noncommercial License 1.0.0](LICENSE.md).

Using or distributing it **commercially** (for example, preinstalling it on
phones you sell, or bundling it with a paid service) requires permission —
open an issue on this page to ask.

Required Notice: Copyright theOnionsAreWatching
(https://github.com/theOnionsAreWatching/NovaLox)
