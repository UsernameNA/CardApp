# CLAUDE.md


- **One class/interface per file** in production code (not tests)
- **`@Preview` functions** go at the end of the file
- **Package**: `com.github.username.cardapp` (namespace in build.gradle.kts)
- **Serialization**: Kotlin Serialization (not Gson) — `@Serializable` classes are kept via ProGuard rules
- **Card images**: `file:///android_asset/images/${card.primarySlug}.webp`
- **Rarity values** (title-cased in DB): Ordinary, Exceptional, Elite, Unique
- **Promo set names**: "Arthurian Legends Promo" is the correct name — do not shorten to "Arthurian Legends"

## Design Language ("Arcane Codex")

Leather-bound grimoire aesthetic. Color palette defined in `ui/theme/Color.kt` (Leather, Gold, Cream, Burgundy, Ink families). `leatherBackground()` modifier in `ui/theme/Modifiers.kt` applies radial gradient + dark vignette.

## Compose Gotchas

- `Modifier.size()` requires explicit `import androidx.compose.foundation.layout.size`
- `DrawScope.center` = `Offset(size.width/2, size.height/2)` — capture it before entering `withTransform` lambdas
- `TextStyle.brush` works for gradient text; `color` is ignored when brush is set
- minSdk 36 — AGSL `RuntimeShader` (API 33+) is always available
