# Third-party material

- `app/src/main/assets/eff_large_wordlist.txt` — the EFF Long Wordlist by the Electronic
  Frontier Foundation (https://www.eff.org/dice), licensed under CC BY 3.0 US. Used unchanged
  (dice numbers removed) to suggest backup passwords.
- `app/src/main/res/drawable/sym_*.xml` — Material Symbols by Google
  (https://github.com/google/material-design-icons), Apache License 2.0, outlined style, unchanged
  except for removing the theme tint attribute.
- Libraries used as dependencies, each under its own licence (the same list, with the texts the
  licences ask to show, is in the app: Options → About → Libraries and licences):
  - SQLCipher for Android (Zetetic LLC): database encryption. BSD-style licence
    (`app/src/main/assets/licenses/sqlcipher.txt`, from zetetic.net/sqlcipher/license). It carries
    SQLite and LibTomCrypt 1.18.2 (both in the public domain) inside, as Zetetic's release notes say.
  - Bouncy Castle: Argon2id for backup passwords. MIT-style licence (its text is inside the library
    and is shown in the app).
  - Tink (Google): streaming encryption of backups. Apache License 2.0. It carries Protocol Buffers
    (Google, BSD 3-Clause licence, `app/src/main/assets/licenses/protobuf.txt`) and Gson
    (Apache License 2.0) inside.
  - zip4j: password-protected ZIP for export. Apache License 2.0.
  - LiteRT-LM (Google AI Edge): optional on-device AI. Apache License 2.0.
  - AndroidX and Jetpack Compose (Google), Kotlin and kotlinx.coroutines (JetBrains).
    Apache License 2.0.
  - The libraries these bring along are used under their own licences.
- The optional Gemma model is not included; the owner downloads it under its own terms.
