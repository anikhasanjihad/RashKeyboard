# KeyOS architecture

```
:engine  (pure Kotlin/JVM, no Android)        :app  (Android)
 ├─ Keys.kt          KeyDef, KeyRow, layout    ├─ ime/KeyOSService.kt   InputMethodService, text logic
 ├─ Layouts.kt       EN / BN-Bijoy / BN-phon.  ├─ ui/KeyboardView.kt    Canvas keyboard, touch, repeat
 └─ BanglaPhonetic.kt  Latin → Bengali         └─ MainActivity.kt       setup screen (Compose in Phase 2)
```

**Why this split.** The engine knows nothing about Android, so layouts and language logic are unit-testable on the JVM and new language packs can be added without touching the IME. The IME service owns the `InputConnection`; the view only reports key presses.

**Zero-lag decisions (Phase 1).**
- One custom `View` drawn on a `Canvas` (no per-key child views); no allocations in `onDraw`/touch path.
- Keys fire on `ACTION_DOWN`; only the touched key's rectangle is invalidated.
- Expensive `InputConnection` round-trips (auto-cap, double-space check) run only on space/enter/layout change, not on every letter.
- All layouts have 5 rows, so the keyboard height never jumps when switching.

**Planned.** Phase 2 `ThemeEngine` (JSON) + DataStore + Compose settings; Phase 3 animation engine + performance profiles; Phase 4 language packs/dictionaries (Room); Phase 5 voice/emoji/clipboard; Phase 6 gesture typing/accessibility/backup; Phase 7 device testing and release.
