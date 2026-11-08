# Colors

Settings › Appearance › Colors offers seventeen ready-made color sets. Each one is only a background, a
text color (light or dark) and an accent, plus, for some, the colors of the A, B, X and Y key
caps. Picking a set moves the Background, Text color and Accent color rows below it, so any set is
a starting point for your own.

| Set | Background | Text | Accent | A / B / X / Y keys |
|---|---|---|---|---|
| Plumemu (default) | `#161A24` | light | `#3A7CEB` | usual |
| Daylight | `#ECEBE5` | dark | `#2A64D8` | usual |
| Ultramarine | `#0F2142` | light | `#F4B740` | usual |
| Magenta | `#240F1C` | light | `#F0649D` | usual |
| Peach | `#FDE3D3` | dark | `#00706B` | usual |
| Matcha | `#E4ECD1` | dark | `#3D6E1C` | usual |
| Mocha | `#F1E6DA` | dark | `#A4492A` | usual |
| Front loader | `#26272B` | light | `#F2545D` | `#D22A35` / `#D22A35` / `#3A3B40` / `#3A3B40` |
| Spice cube | `#231C50` | light | `#FF9F43` | `#2EA557` / `#D93A3E` / `#BDBCC8` / `#BDBCC8` |
| Pea green | `#C6D88F` | dark | `#2F5F2F` | usual |
| Pocket | `#D2D1CA` | dark | `#8C1D4E` | `#8C1D4E` / `#8C1D4E` / `#8C1D4E` / `#8C1D4E` |
| Lavender | `#DEDCE6` | dark | `#5B3EA1` | `#4E3591` / `#4E3591` / `#B2A5DB` / `#B2A5DB` |
| Red and gold | `#F3EBD8` | dark | `#9A1B2F` | `#9A1B2F` / `#9A1B2F` / `#C69A1E` / `#C69A1E` |
| Ivory | `#F3EAD0` | dark | `#127087` | `#CF3A3A` / `#D9A400` / `#2F6FDB` / `#2E9A47` |
| Clear grape | `#221838` | light | `#BE98FF` | usual |
| Cold grey | `#272B32` | light | `#8EC3FF` | `#3E6FD8` / `#2E9A47` / `#D9A400` / `#D9A400` |
| Banana | `#F2CF43` | dark | `#1F45A8` | `#C8302B` / `#0E7C7C` / `#926B00` / `#926B00` |

Light text is `#EBE7DD`, dark text `#1C1C1E`. Every other tone (panels, cards, keys, dividers) is the background itself a few steps lighter or darker in its own hue.

If you used Dot matrix, Super, Indigo, Charcoal or Cream, the app shows its closest new set instead: Pea green, Ivory, Spice cube, Cold grey and Red and gold. The Paper set's colours (the Paper page with the Red accent) are still there to pick.

## Suggest a color set

1. Build it in the app first: Settings › Appearance › Background, Text color and Accent color take
   any color. Look at it on the home screen, a game list and Settings.
2. Open an issue with the
   [color set template](https://github.com/joiha-steven/plumemu/issues/new?template=color-set.yml):
   the colors as `#RRGGBB`, the key cap colors if you want them, a name, and a screenshot.

What a set needs to get in:

- **Readable.** Every set passes the same contrast checks: text at least 7:1 on the background
  and 4.5:1 on the selected row, the accent at least 3.5:1 on the background and on the focused row's
  tint (so its ring shows), the text on the accent at 4.7:1. If a suggestion falls short, its lightness is nudged until it passes, and the issue says
  what changed.
- **Its own name.** Sets may be inspired by classic consoles and handhelds, but are never named after a console,
  a company or a brand.
- **Different enough** from the sets above.

Contributions come in under the [licence](LICENSE.md)'s Contributions terms.
