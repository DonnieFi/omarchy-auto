# Assets and attribution

## Tokyo Studio / “Tokyo Knight” artwork

The README hero and the default Tokyo Night app background use **Tom Ballard’s
Tokyo Studio** artwork (the owner calls it the Tokyo Knight background).

Upstream source: [tcballard/omarchy-theme-tokyo-roadster](https://github.com/tcballard/omarchy-theme-tokyo-roadster),
theme code MIT, Copyright (c) 2026 Tom Ballard. Tom confirmed the artwork is
open source and approved its inclusion in the repository, README, public site,
and app builds on 2026-11-02.

Use and credit:

- `docs/img/hero-tokyo-night.webp` is the README and public-site hero.
- `core/src/main/assets/themes/tokyo-night.webp` is the default Tokyo Night app
  background, cropped/resized to 1600×900 from Tom’s image.
- `local-assets/` remains ignored by git for owner-specific private overrides.
  It is no longer required for Tom’s image; normal debug and release builds
  include the tracked Tokyo Night asset.
- Setup’s `USE TOM BACKGROUND` copies the bundled Tokyo Night asset into
  app-private storage so it survives theme changes. `CHOOSE BACKGROUND` stores
  a user-selected image there; `USE THEME BACKGROUNDS` removes the custom
  override.
- The project [MIT licence](../LICENSE) covers project code and original assets.
  The Tokyo Studio image is included with Tom’s permission and remains credited
  to Tom Ballard.

Other app previews use the public-safe Omarchy theme backgrounds or the fallback.

## Theme palettes and backgrounds

Palette values follow the MIT-licensed themes in
[omacom/omarchy](https://github.com/omacom/omarchy/tree/master/themes):
Tokyo Night, Jade Horse/Osaka Jade, Gruvbox, Catppuccin, Kanagawa, Everforest,
Nord, Ethereal, Ristretto, and Hackerman.

The bundled `core/src/main/assets/themes/*.webp` backgrounds: Tokyo Night is
Tom Ballard’s Tokyo Studio image; the other nine are resized and recompressed
from the matching Omarchy theme `backgrounds/` directories.

| App theme | Source / credit |
|---|---|
| Tokyo Night | Tom Ballard / Tokyo Studio, 1600×900 (approved 2026-11-02) |
| Osaka Jade | `jade-horse/backgrounds/03-carved-jade-horse.webp` |
| Gruvbox | `gruvbox/backgrounds/5-leaves.jpg` |
| Catppuccin | `catppuccin/backgrounds/2-waves.webp` |
| Kanagawa | `kanagawa/backgrounds/1-kanagawa.jpg` |
| Everforest | `everforest/backgrounds/1-tree-tops.webp` |
| Nord | `nord/backgrounds/1-city-view.webp` |
| Ethereal | `ethereal/backgrounds/1-cosmic.webp` |
| Ristretto | `ristretto/backgrounds/1-color-curves.webp` |
| Hackerman | `hackerman/backgrounds/1-synth-scape.jpg` |

Omarchy's repository is MIT-licensed, but individual background images may have
separate media rights. These nine Omarchy-derived assets are still for the
private demo; verify each image's redistribution rights before a public release
or public screenshot use. Tokyo Night is the separate Tom Ballard image, whose
inclusion is covered by the permission above.

## Fonts

The renderer uses Android's built-in monospace typeface. No font file is
bundled.
