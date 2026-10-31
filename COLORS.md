# Colors

Settings › Appearance › Colors offers ten ready-made color sets. Each one is only a background, a
text color (light or dark) and an accent, plus, for some, the colors of the A, B, X and Y key
caps. Picking a set moves the Background, Text color and Accent color rows below it, so any set is
a starting point for your own.

| Set | Background | Text | Accent | A / B / X / Y keys |
|---|---|---|---|---|
| Plumemu (default) | `#000000` | light `#EDEDED` | `#DA4655` | usual |
| Paper | `#F4F3EF` | dark `#17181A` | `#9E2A3A` | usual |
| Pocket | `#CDCCC4` | dark | `#8C1D4E` | all four `#8C1D4E` |
| Dot matrix | `#B4DB11` | dark | `#2E5E2E` | usual |
| Super | `#D9D8D3` | dark | `#AA2924` | `#C8302B` / `#D9A400` / `#2B54B5` / `#2E8B4A` |
| Indigo | `#262158` | light | `#A79CF5` | usual |
| Charcoal | `#2A2B2E` | light | `#5FD38D` | `#3E6FD8` / `#2E9A47` / `#D9A400` / `#D9A400` |
| Ivory | `#F3EAD0` | dark | `#127087` | `#CF3A3A` / `#D9A400` / `#2F6FDB` / `#2E9A47` |
| Banana | `#F2CD3C` | dark | `#1F45A8` | `#C8302B` / `#0E7C7C` / `#926B00` / `#926B00` |
| Cream | `#E9DFC8` | dark | `#9A1F2B` | usual |

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
- **Its own name.** Sets may be inspired by classic handhelds, but are never named after a console,
  a company or a brand.
- **Different enough** from the sets above.

Contributions come in under the [licence](LICENSE.md)'s Contributions terms.
