# Bundled fonts

All three families are bundled locally as **variable** TrueType fonts and are
licensed under the **SIL Open Font License 1.1** (OFL). Weights are derived at
runtime via `androidx.compose.ui.text.font.FontVariation` (supported on minSdk 26).

| Resource | Family | Source (google/fonts, OFL) |
|----------|--------|----------------------------|
| `space_grotesk.ttf` | Space Grotesk | `ofl/spacegrotesk/SpaceGrotesk[wght].ttf` |
| `inter.ttf` | Inter | `ofl/inter/Inter[opsz,wght].ttf` |
| `jetbrains_mono.ttf` | JetBrains Mono | `ofl/jetbrainsmono/JetBrainsMono[wght].ttf` |

To refresh them, re-download from https://github.com/google/fonts and overwrite
these files (keep the lowercase resource names — Android resource naming rules).
