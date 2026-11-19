# KeyOS — Android keyboard (IME)

A real Android Input Method Editor written in Kotlin. Goals: **unlimited customization, zero-lag typing, beautiful animations**, and genuine multi-language support (Bengali first).

> Status: **Phase 1 of 7** (native IME foundation). See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).

## What works in Phase 1

- Real `InputMethodService` that works in any normal text field (min API 26, target API 35).
- Three layouts, switched with the 🌐 key:
  - **English** QWERTY with number row, shift / caps-lock (double tap), auto-capitalisation, double-space → ". ".
  - **Bengali (Bijoy-style, traditional)** with Bengali digits, kar/vowel signs, `্র`, `র্`, plus an extras page (অআ key) for independent vowels and signs.
  - **Bengali (phonetic, Avro-style)**: type `bangla` → বাংলা, `ami` → আমি, `boi` → বই. Live composing text, backspace edits the Latin buffer.
- Symbols page (?123), Enter honours the field's IME action (Search / Go / Send …), backspace key-repeat, multi-touch, haptics.
- Password fields automatically use the English layout; number/phone fields open on the symbols page.
- Light/dark colours follow the system. Portrait and landscape, any screen size (layout is computed from width; height is capped at 50% of the screen).
- Privacy: no `INTERNET` permission, no analytics, no data backup.

## Not done yet (planned phases)

Theme editor, custom fonts, animations, performance profiles, dictionaries / suggestions / autocorrect, swipe typing, emoji, clipboard, voice typing, Compose settings app, backup. **None of these are claimed to work yet.**

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
