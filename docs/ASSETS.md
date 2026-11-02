# Assets and attribution

## Supplied Tokyo Studio / “Tokyo Knight” wallpaper

The app can load the supplied Tom Ballard **Tokyo Studio** image (the owner calls
it the Tokyo Knight background) locally. It is not committed to this repository.

Upstream source: [tcballard/omarchy-theme-tokyo-roadster](https://github.com/tcballard/omarchy-theme-tokyo-roadster),
theme code MIT, Copyright (c) 2026 Tom Ballard. The repository's
[CREDITS.md](https://github.com/tcballard/omarchy-theme-tokyo-roadster/blob/main/CREDITS.md)
states that the Roadster photographs/artworks are user-supplied and that the
original photographer, source URL, and media redistribution licence were not
established. The theme's MIT licence covers configuration/code, not the images.

For that reason:

- `local-assets/` is ignored by git.
- The clean-clone build uses an original, code-drawn neon-road fallback.
- A private demo can place a licensed copy at `local-assets/wallpaper.jpg` and
  explicitly opt in with `-Pomarchy.includeLocalAssets=true`; that APK/AAB embeds
  it as `assets/wallpaper.jpg`.
- Default builds — including release builds without the explicit flag — exclude
  `local-assets/` and use the fallback.
- The app also looks for `roadster.jpg` in its private files directory.
- Do not publish builds, screenshots, or mirrors containing the Roadster image
  until the media rights are cleared.

The screenshot in the README was taken with the **fallback** background.

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
