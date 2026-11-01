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
- A local demo can place a licensed copy at `local-assets/wallpaper.jpg`; the
  debug APK embeds it as `assets/wallpaper.jpg`.
- The app also looks for `roadster.jpg` in its private files directory.
- Do not publish builds, screenshots, or mirrors containing the Roadster image
  until the media rights are cleared.

The screenshot in the README was taken with the **fallback** background.

## Theme palettes

Palette values follow Omarchy's MIT themes (Tokyo Night, Osaka Jade, Gruvbox,
Catppuccin). No upstream QML or artwork is copied into the renderer.

## Fonts

The renderer uses Android's built-in monospace typeface. No font file is
bundled.
