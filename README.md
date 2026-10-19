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
- Images and videos open in KeyMSG's viewer; photos use original image tiles
  for detailed zoom, with left/right browsing and no automatic Gallery copies
- Choose **Open with…** from the viewer menu to use another installed app
- Whole-app resize from 70% to 150%, plus separate text size settings
- Built-in weekly update checks and installer through signed GitHub releases
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
Android accepts future KeyMSG updates only when they use that key.

For GitHub release builds, add these repository secrets under **Settings →
Secrets and variables → Actions**:

- `KEYSTORE_BASE64`: the output of `base64 -w 0 /path/to/key-msg.jks`
- `KEYSTORE_PASSWORD`: the keystore password
- `KEY_ALIAS`: the signing-key alias
- `KEY_PASSWORD`: the signing-key password

The release workflow verifies the key, builds and verifies the signed APK, and
attaches it to a GitHub release. Keystores, APKs, and signing-property files are
excluded by `.gitignore`.

## Updating

KeyMSG checks the latest stable GitHub release about once a week when the app
opens. If a newer version exists, it offers to download the signed APK and then
opens Android's installer for confirmation. You can also check immediately from
**Settings → Check for updates**. Releases created by the workflow start as
prereleases; edit the tested release on GitHub and clear **Set as a pre-release**
before users should receive it. Version `0.1.4` must be installed manually
because earlier KeyMSG builds do not contain the updater; later stable releases
will then be detected automatically.

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

Your messages stay on your phone. KeyMSG has no servers, no ads, and no
analytics. Network connections are limited to sending and receiving MMS through
your carrier and checking KeyMSG's public GitHub releases for updates.

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
