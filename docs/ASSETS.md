# Assets and attribution

## Tokyo Studio / “Tokyo Knight” artwork

The README hero and the private Tokyo Night build use **Tom Ballard’s Tokyo
Studio** artwork (the owner calls it the Tokyo Knight background).

Upstream source: [tcballard/omarchy-theme-tokyo-roadster](https://github.com/tcballard/omarchy-theme-tokyo-roadster),
theme code MIT, Copyright (c) 2026 Tom Ballard. The repository's
[CREDITS.md](https://github.com/tcballard/omarchy-theme-tokyo-roadster/blob/main/CREDITS.md)
states that the Roadster photographs/artworks are user-supplied and that the
original photographer, source URL, and media redistribution licence were not
separately established. The theme's MIT licence covers configuration/code, not
the artwork.

Use and credit:

- `docs/img/hero-tokyo-night.webp` is committed for the README and public site,
  credited to Tom Ballard in [`CREDITS.md`](../CREDITS.md).
- `local-assets/` remains ignored by git. A private app build can embed the
  owner's copy with `-Pomarchy.includeLocalAssets=true`.
- Default/public app builds exclude `local-assets/` and use the Omarchy theme
  backgrounds or the code-drawn fallback.
- The project [MIT licence](../LICENSE) covers project code and original assets,
  not third-party artwork.
- Verify the artwork's media rights before commercial redistribution. If the
  rights holder asks, remove the README/site hero and the local app asset.

The README hero now uses Tokyo Studio with credit; other app previews use
public-safe Omarchy backgrounds or the fallback.

## Theme palettes and backgrounds

Palette values follow the MIT-licensed themes in
[omacom/omarchy](https://github.com/omacom/omarchy/tree/master/themes):
Tokyo Night, Jade Horse/Osaka Jade, Gruvbox, Catppuccin, Kanagawa, Everforest,
Nord, Ethereal, Ristretto, and Hackerman.

The bundled `core/src/main/assets/themes/*.webp` backgrounds are resized and
recompressed from the matching Omarchy theme `backgrounds/` directories:

| App theme | Omarchy source |
|---|---|
| Tokyo Night | `tokyo-night/backgrounds/1-winding-road.webp` |
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
separate media rights. These assets are for the private demo; verify each
image's redistribution rights before a public release or public screenshot use.
The exact Tom Ballard Tokyo Studio image remains the separate local-only
override described above.

## Fonts

The renderer uses Android's built-in monospace typeface. No font file is
bundled.
