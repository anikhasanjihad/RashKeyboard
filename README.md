# KeyOS — Android keyboard (IME)

A real Android Input Method Editor written in Kotlin. Goals: **unlimited customization, zero-lag typing, beautiful animations**, and genuine multi-language support (Bengali first).

> Status: **Phase 1 of 7** (native IME foundation). See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).

## What works now (Phase 1, Gboard-style UI)

- Real `InputMethodService` that works in normal text fields (min API 26, target API 35).
- **Gboard-like interface:** suggestion strip on top, rounded keys, light/dark colours following the system, accent-coloured Enter, key-preview bubble on press.
- **Long-press alternates** (à é ñ ß … , ¹ ½ , ৳ → $ € £ ¥ ₹ , Bengali digit → 1 2 3 …) with slide-to-select.
- **Slide on the space bar** to move the cursor.
- **Word suggestions** (English): shows what you typed + up to 2 completions; tap to insert. Learns your own words locally (stored in app-private storage, never sent anywhere, not learned in password/email/URL fields). Built-in list is a small starter list only, not a full dictionary yet.
- Three layouts, switched with the 🌐 key:
  - **English** QWERTY with number row, shift / caps-lock (double tap), auto-capitalisation, double-space → ". ".
  - **Bengali (Bijoy-style, traditional)** with Bengali digits, kar/vowel signs, `্র`, `র্`, plus an extras page (অআ key) for independent vowels and signs.
  - **Bengali (phonetic, Avro-style)**: `bangla` → বাংলা, `ami` → আমি, `boi` → বই, with live composing text.
- Symbols page (?123), Enter honours the field's IME action, backspace key-repeat, multi-touch, haptics.
- Password fields use the English layout without suggestions; number/phone fields open on the symbols page.
- Privacy: no `INTERNET` permission, no analytics, no data backup.

## Not done yet (planned phases)

Theme editor, custom fonts, animations, performance profiles, full dictionaries / autocorrect / Bengali suggestions / next-word prediction, swipe typing, emoji, clipboard, voice typing, Compose settings app, backup. **None of these are claimed to work yet.**

## Build

Requires Android Studio (Koala or newer) with JDK 17+.

1. Open this folder in Android Studio, let Gradle sync.
2. Run the `app` configuration on a device/emulator (or `./gradlew :app:assembleDebug`).
3. Open the KeyOS app → *Enable KeyOS* → *Choose KeyOS as keyboard*.

Unit tests (Bengali phonetic engine): `./gradlew :engine:test`.

Optional GitHub Actions (`docs/ci/android.yml.txt` (copy it to `.github/workflows/android.yml` to enable CI)) builds a debug APK on every push; download it from the run's *Artifacts*.

## Honest notes

- The Bengali phonetic engine is unit-tested. The Android UI/IME code has **not yet been run on a physical device by the author**; first-run bug reports are expected and welcome.
- The Bijoy-style key positions follow the commonly used layout but should be checked by a native Bengali typist; open an issue for any wrong key.
- Compatibility across manufacturers is **untested** until entries appear in [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).
