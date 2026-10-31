# Translating Plumemu

For the people who translate Plumemu's screens. How strings are written in English and
kept in code is in [i18n.md](i18n.md); this page is what a translator needs. Languages shipped:
Spanish, French, Vietnamese, Chinese (Simplified and Traditional), Korean and Japanese.

This file is English-only like the rest of the repository (the english-only guard), so the
languages' own names below are written as HTML entities; they read normally in a Markdown viewer.

## What is translated

- One file per language: `app/src/main/res/values-<folder>/strings.xml`, a copy of
  `app/src/main/res/values/strings.xml` (the English, the master) with every value translated. It
  is the only module with strings.
- Leave out every string English marks `translatable="false"` (button letters, separators, font
  names, sample games, licence names). The translations guard fails if one is copied.
- Keep the keys, the order and the comments of English. The comments above a string or a group
  say where it shows, what a placeholder holds and how long it may be: read them first.
- Never change English in a translation task. If an English string is unclear or wrong, say so in
  the report; the main session changes English, and every translation follows.
- Save the file as UTF-8 in Unicode NFC (precomposed letters). Some editors save Vietnamese as a
  letter plus combining marks; the guard fails on that, because the Plum fonts have every
  precomposed Vietnamese letter but not every combining mark.
- XML escapes as in English: `\'` for an apostrophe, `\"` for a quote, `&amp;`, `&lt;`.
  A value that starts or ends with a space is quoted (`second_screen_separator`).

## Voice

- Short, plain and calm. Say what happened and what to do, in that order.
- No exclamation marks, no "Oops", no jokes, no blame ("you did something wrong").
- Address the person the way the language does for a friendly tool (per language below). Do not
  call the person "user" or "player".
- Buttons and menu entries are verbs or short nouns, like English: "Play", "Hide", "Fix name…".
  An entry that opens a panel keeps English's "…" (U+2026, the language's own form if it has one).
- Sentences end with the language's full stop. Labels and values (Settings rows, hint labels,
  badges) have none, as in English.
- Capitalisation: English uses sentence case ("Box art region", not "Box Art Region"). Follow the
  language's own rules; never capitalise every word.

## Words that stay as they are

Do not translate or transliterate:

- **Plumemu**, **ScreenScraper**, **RetroArch**, **Android**, **Steam**, **Wi-Fi**, **GitHub**.
- **System names** and their short names (SNES, PlayStation, Mega Drive, Game Boy Advance…): they
  come from the platform data, not from strings, and are the names people know from the consoles.
- **Button letters and names** on key caps: A B X Y, L1 R1, L2 R2, START, SELECT. They are
  `translatable="false"`; a hint's label next to them is translated ("Select", "Back").
- **Game titles** and **emulator names**: data, never in strings.
- Font names Plum Sans, Plum Serif, Plum Mono; licence names (MIT, Apache 2.0, OFL).
- Units with numbers stay the language's usual abbreviations (`%1$s min`, `%1$s h`, `MB`, `GB`).

## Glossary

Use one word for each idea everywhere. Pick the word once, write it in your language's section
below, and keep to it.

| English | Meaning in Plumemu |
|---|---|
| game | One game in the library, whatever number of files it has. Not "ROM", not "file". |
| system | A console or computer whose games are listed (SNES, PlayStation). Not "platform", not "console" in the UI. |
| library | Every game Plumemu found in the folders. |
| folder | A folder on the device or SD card that Plumemu scans for games. |
| scan | Plumemu reading the folders to find games. "Rescan" is doing it again. |
| emulator | The app that runs a system's games (RetroArch, DuckStation). |
| box art | The picture of a game's box front, downloaded from ScreenScraper. Always this term: not "cover", "picture" or "image". |
| Favorites | The list of games the person marked with Y. Name of an entry in the system column. |
| Recent | The list of games played lately. Name of an entry. |
| Apps | The entry listing the other Android apps installed. |
| Settings | Plumemu's settings screen (START). Not Android's settings: those are "Android's settings". |
| hide / hidden | Take a game or system out of the lists without deleting anything. |
| backup / back up | A file holding Plumemu's settings, library and box art. Noun "backup", verb "back up". |
| restore | Put a backup's contents back. "Undo restore" returns to the state before. |
| accent | The colour of the focus mark and highlights (Settings › Appearance › Accent color). |
| text size | Settings › Appearance › Text size: Small, Medium, Large, Larger, Largest. |
| text weight | Settings › Appearance › Text weight: Thin, Medium, Bold (how heavy the letters are drawn). |
| left edge | Settings › Appearance › Left edge: Game counts, System codes, Letters (what the narrow strip at the screen's left edge shows in a games list). |
| second screen | The bottom screen of a two-screen device (AYN Thor). |
| home screen | Android's home screen; "Plumemu as the home screen" means the home app. |
| shortcut | A pinned Android shortcut to a game (Steam games). |
| region | Where a game was released (USA, Europe, Japan). Region names are data (see below). |
| report a problem | Send a description of a problem to the developer from Settings › About. |
| statistics | Play time and counts in Settings › Statistics; "usage statistics" is the anonymous data sent to improve Plumemu. Keep the two apart. |
| account | The person's ScreenScraper account. "Sign in" / "Sign out". |

## Placeholders and plurals

- `%1$s`, `%2$s`, `%1$d` are values put in at run time (a name, a count, a size). Keep every
  placeholder English has, with the same number and letter; move them anywhere in the sentence.
  The guard fails on a missing, extra or changed placeholder. `%%` is a percent sign, `%n` a line
  break.
- Numbers come in already formatted for the language (`%1$s` holding "1,234" or "1.234"): never
  add digits or separators around them.
- `<plurals>` hold a string per quantity. Write the quantities **your language** uses
  (CLDR rules; Android picks the item): Vietnamese, Chinese, Korean and Japanese use only `other`;
  Spanish and French use `one` and `other` (and `many` for large round numbers on Android 14+, which falls
  back to `other` when absent). An `other` item is always required and has exactly English's
  placeholders. Another item may leave the number out ("one game" written as a word) but may not
  add a placeholder English's `other` lacks.
- Never build a sentence from pieces in a translation: every sentence is one string. If English
  joins strings (a separator such as " · "), the separator is `translatable="false"` on purpose.

## Length budget

The screens are small: 768 x 432 dp (Retroid Pocket 5), 837 x 471 (Thor), 667 x 375 (RP4 Pro) and
576 x 432 (Retroid Pocket Nova), at five text sizes. Write the shortest natural form.

- **Hint bar labels** (`home_hint_*`, `settings_hint_*`, `*_hint_*` next to a button letter):
  about 12 Latin characters or 6 CJK characters. At 576 wide the bar holds four or five hints and
  drops the least needed ones when they do not fit; a long label pushes others out.
- **Settings row labels and values** share one line at Medium: label about 22 characters, a
  cycled value about 16 (CJK about half). A row's label wraps at the larger sizes; a value is cut
  with "…".
- **Settings panel texts** (the 248 dp column on the right, `settings_*_body`): any length within
  reason; the panel scrolls. Two or three short sentences.
- **Badges** ("New", "Current", "Default"): one short word.
- **Titles of panels and messages**: one line at Medium, about 30 characters.
- Spanish runs about 25 % longer than English and Vietnamese about 30 %; Chinese, Korean and
  Japanese shorter in characters but each character is about twice as wide as a Latin letter.

After translating, the text-size fit tests (`app/src/test/java/com/plumemu/textsize/`) and the
reference shots run in the gate; a translation that overflows is shortened, not the layout.

## Fonts

Checked with fontTools on `core-ui/src/main/res/font/` (Plum Sans 0.0.3, Serif and
Mono 0.0.2, every weight):

- **Vietnamese**: all 144 precomposed vowel letters with their tones (both cases) and &#272;/&#273;
  are in all three fonts (146 of 146 checked). Missing are the combining circumflex (U+0302), breve
  (U+0306) and horn (U+031B), so decomposed text would fall back letter by letter: hence NFC (the
  guard). The tallest stacked capitals (&#7892;, &#7858;) rise above Plum Sans's ascender by 0.06 em (Serif
  and Mono stay inside); rows and hint labels have room for it at every size (shot
  `script_vietnamese_*`, all three fonts).
- **Spanish**: every letter and the inverted marks are in all three fonts.
- **French**: every accented letter (&#224; &#226; &#231; &#233; &#232; &#234; &#235; &#238; &#239; &#244; &#249; &#251; &#252; &#255;, &#339;, their capitals) and the
  no-break space U+00A0 are in all three fonts. The narrow no-break space U+202F is in Plum Sans
  only, so French uses U+00A0 before `: ; ? !`.
- **Chinese, Korean, Japanese**: no Plum font has them. Compose builds each font family from
  resource fonts, and Android's text engine falls back per character to the system's CJK font
  (Noto Sans CJK on Android 6 and later, the Thor's own on Android 13), so mixed text works with
  no change in `PlumFonts`. The Han forms (Chinese Simplified, Traditional or Japanese shapes of
  the same character) follow the process locale, which `AppLocale.wrap` sets to the chosen
  language (Android does it from 13). The fallback font is the same whichever Plum font is chosen,
  and it has one weight on Android 6 and 7: the focused row's 500 weight looks like 400 there
  (the focus fill and dot still mark it). Shots `script_chinese_simplified_*`,
  `script_chinese_traditional_*`, `script_korean_*`, `script_japanese_*` in core-ui
  (`ScriptsScreenshotTest`).

## Choosing a language

Settings › Advanced › Language lists "System default" and each shipped language in its own name (the
`language_name` string of its folder): English, Espa&#241;ol, Fran&#231;ais, Ti&#7871;ng Vi&#7879;t,
&#31616;&#20307;&#20013;&#25991;, &#32321;&#39636;&#20013;&#25991;, &#54620;&#44397;&#50612;,
&#26085;&#26412;&#35486;. System default follows Android's language; a language Plumemu does not
ship falls back to English.

- Stored as `appearance.language` (a tag such as `zh-TW`; absent for System default), in backups.
- Android 13 and later: the choice is Android's per-app language, so Android's Settings › Apps ›
  Plumemu › Language and Plumemu's row show the same; a change on either side is taken by the
  other (`LanguageSync`). `res/xml/locales_config.xml` lists the languages for Android's page.
- Android 6 to 12: every Activity and the Application wrap their context in the chosen language
  (`language/AppLocale.kt`, read from a small cache file before the first frame). A change
  recreates the screen. Text read through the application context while Plumemu runs (the name of
  Android's shortcuts list) follows only after Plumemu restarts.
  - The override holds the locale only (an empty `Configuration` plus `setLocale`): a copy of the
    whole configuration froze dark mode, orientation and size for the Application's context.
  - A context made from a wrapped one can lose the language: the second screen's `Presentation`
    builds its own with `createDisplayContext`, so its content is wrapped again with
    `AppLocale.localized`.
  - `onConfigurationChanged` hands over the system's configuration, so anything that follows the
    locale from it (the A–Z sort, `graph/GraphLocale.kt`) puts the picked language back on top.
  - `LanguageCache` keeps two tags apart: the language applied before Android 13 and the one last
    agreed with Android's per-app language. A phone upgraded from 12 to 13 has only the first, and
    its picked language is then handed to Android, not wiped.

### Chinese folders

| Language | Tag | Folder | Android picks it for |
|---|---|---|---|
| Simplified | `zh-CN` | `values-zh` | zh, zh-CN, zh-SG, zh-Hans-* |
| Traditional | `zh-TW` | `values-zh-rTW` | zh-TW; from Android 7 also zh-HK, zh-MO, zh-Hant-* |

Before Android 7 resources match by exact region only, so a phone set to Chinese (Hong Kong) or
(Macau) would get `values-zh`, the Simplified text. With System default on Android 6, Plumemu
points those two at `zh-TW` itself (`AppLocale.localeToApply`, tested in `AppLocaleTest`). Android
7 and later match by script and need nothing. One Traditional folder is enough: Hong Kong's
written forms differ from Taiwan's in a few words, not enough to keep two files in step. A
`values-zh-rHK` can be added later as a copy with those words changed.

## Adding a language

1. Copy `app/src/main/res/values/strings.xml` to `values-<folder>/strings.xml`, drop the
   `translatable="false"` strings, translate the rest, and set `language_name` to the language's
   own name, as people of that language write it.
2. Add one line to `AppLanguages.all` (`app/src/main/java/com/plumemu/language/AppLanguages.kt`)
   and the same tag to `app/src/main/res/xml/locales_config.xml`. The planned lines are in the
   comment above `AppLanguages`.
3. Run `python3 scripts/guards/translations.py` (keys, placeholders, plurals, NFC) and
   `./gradlew :app:testDebugUnitTest --tests com.plumemu.language.*` (`AppLanguagesTest`
   checks the list, the folders and `locales_config.xml` agree).
4. Look at the Settings and home screens in the language on the emulator (Settings › About ›
   Language) at 576 and 768 wide, Medium and Largest; shorten what is cut.
5. Fill in your language's section below with its choices.

## Public translations

The strings are readable in the public repository (GitHub joiha-steven/plumemu) under `translations/<lang>/strings.xml`, so people report or fix a
translation without the source being opened. Its `translations/README.md`
explains how. English is a translation like the others: its wording is reported and fixed the same way; reports come as issues from the translation issue form (label
`translation`), fixes as pull requests editing one file. Contributions come in under the public LICENSE.md's
"Contributions" clause; no translation service such as Weblate for now.

- **Out:** `python3 -I scripts/export-translations.py` (or `--to DIR` for a scratch copy) writes
  `en` (the English master without its `translatable="false"` strings and the comments that only
  described them) and every `values-<lang>` file as it is; `<lang>` is the folder suffix (`vi`,
  `zh-rTW`). Run it when the strings change before a release, and commit the public repository
  with the release. There is no marker for strings of unreleased features: everything translatable
  goes out. If a string must stay private until its feature ships, add such a marker first.
- **Back in, from a pull request:** check the diff on GitHub (text between tags only), then
  `python3 -I scripts/import-translation.py <public repo>/translations/<lang>/strings.xml`. It
  refuses a language the app does not have and a file that is not plain strings.xml; it
  normalises line ends and NFC, writes `values-<lang>/strings.xml` (`en`: `values/strings.xml`,
  merged element by element, since the public copy lacks the `translatable="false"` strings; its
  keys must be the master's), runs the translations guard and puts the old file back if it fails.
  An English placeholder change fails against every translation and is refused: do it by hand. It prints the keys that changed. Then
  `scripts/verify.sh --quick`, look at the changed screens as in "Adding a language" step 4, commit
  here, release, export, and merge or close the pull request with a link to the release.
- **From an issue:** change the line by hand in `values-<lang>/strings.xml`, same checks.
- A changed English string is printed by the import: look at the same key in every language, since
  the translations followed the old wording.

## Not in strings.xml

Text a person can see that does not come from `app/src/main/res/values/strings.xml`:

| Text | Where it comes from | Translated? | To translate it |
|---|---|---|---|
| System names and short names | `platforms/src/main/assets/platforms.json` | No, on purpose (ARCHITECTURE: names people know from the consoles) | Nothing |
| System "about" texts (31) | `system_about_*` in strings.xml (`home/SystemAbouts.kt` maps ids) | Yes, as strings | Nothing; each cites its English source |
| Game titles, regions in file names | The files, the name databases (No-Intro, Redump, MAME) | No | Nothing |
| Region names in Info and Other versions ("USA, Europe") | No-Intro's English names (`Region.noIntroName`), joined with ", " in `game/GameInfo.kt`, `game/OtherVersions.kt`, `graph/GraphNameFixes.kt` | No | A string per region (`region_*`) and a list separator string, mapped from `Region` in those three places |
| Name sources in Fix name ("No-Intro, Redump") | The name packs' names | No (proper names) | Nothing |
| Synopsis and genre in the details column and second screen | ScreenScraper, asked for English only (`GameMapper.FALLBACK_LANGUAGES`, `LibraryHomeArt.ENGLISH`) | No | Ask ScreenScraper for the app language first, English after (it has en, es, ja, zh, ko, not vi) |
| Emulator names, app names in Apps | Plumemu's emulator catalogue; Android's app labels (already in the system language) | No | Nothing |
| Plumemu's keyboard letters | `core-ui/.../keyboard/KeyboardLayout.kt`: QWERTY, "123", "ABC", "#+=" | No: it types the Latin letters of game titles | A layout per language, if ever wanted (Vietnamese tones, kana); Android's keyboard (Settings › Controls › Keyboard) covers it now |
| Key caps A B X Y, START, SELECT, separators, font, licence and sample game names | strings.xml, `translatable="false"` | No, on purpose | Nothing |
| Benchmark and preview content | `FakeHomeContent` (benchmark builds only) | No | Nothing: never in a person's build |
| Dates, numbers, sizes | Formatted with the locale (`rememberCountFormat`, `DateFormat`) | Yes, by Android | Nothing |

## Per language

Each translator fills in the section of their language: the glossary words chosen, the form of
address, anything a reviewer should know. Empty until then.

### Spanish (`es`, `values-es`)

Address: t&#250; (informal), never usted. Neutral Spanish understood in Spain and Latin America.

| English | Spanish | Note |
|---|---|---|
| game / system / library / folder | juego / sistema / biblioteca / carpeta | "ROM folders" is "Carpetas de ROMs" (gamers' plural) |
| scan / rescan | escanear, leer / Volver a escanear | The progress lines keep English's "reading" (Leyendo carpetas&#8230;) |
| naming games | identificar (Identificando juegos) | |
| emulator | emulador | |
| box art | car&#225;tula (plural car&#225;tulas) | Never portada, imagen |
| Favorites / Recent / Apps | Favoritos / Recientes / Apps | Apps, not Aplicaciones: short, and common in both regions |
| Settings | Ajustes | Android's own settings: "los ajustes de Android" |
| hide / hidden | ocultar / oculto, ocultos | |
| back up / backup | Copia de seguridad (the row and title), crear la copia, la copia | Values agree with copia: Guardada, Fallida |
| restore / Undo restore | Restaurar / Deshacer restauraci&#243;n | Values agree with restauraci&#243;n: Restaurada, Deshecha |
| accent color | Color de &#233;nfasis | Not "color de acento" |
| text size | Tama&#241;o del texto: Peque&#241;o, Mediano, Grande, Muy grande, Enorme, M&#225;ximo | |
| second screen | segunda pantalla | |
| home screen / main screen | pantalla de inicio (Android's home app, the Home button: bot&#243;n de inicio) / pantalla principal (Plumemu's own) | |
| shortcut | acceso directo; its 5-letter tag "Atajo" | |
| report a problem | Informar de un problema | |
| statistics / usage statistics | estad&#237;sticas de juego / estad&#237;sticas de uso | |
| account, sign in, sign out | cuenta, Iniciar sesi&#243;n, Cerrar sesi&#243;n | |
| On / Off | Activado / Desactivado | |
| build (this build) | versi&#243;n | |
| controller, pad | controlador | Neutral between mando (Spain) and control (Latin America) |
| crash report | informe de fallos | |

Hint labels: Elegir (both Select and Choose), Atr&#225;s, Abrir, Jugar, Buscar, Ajustes, Cambiar,
Cerrar, Men&#250;, Tama&#241;o, Cuadr&#237;cula, Lista, Otro sistema, Favorito / Quitar favorito, Borrar,
Espacio, May&#250;s, Guardar, Reintentar, Listo. Push right twice: "Jugar (&#215;2)" and, after the first
push, "Otra: jugar".

Style: "No se pudo&#8230;" for could not, "No se ha cambiado nada." for Nothing was changed,
"Int&#233;ntalo de nuevo." for Try again. Times after a clock placeholder read "a partir de las
%1$s" (a 1 o'clock time would want "la"; accepted). Dates follow "del %1$s" (medium date format).
Language's first choice is "Idioma del sistema", as Android's per-app language list names it.
Avoid wording that gives the person a gender ("conectado", "t&#250; mismo"): "Sin conexi&#243;n Wi-Fi",
"por tu cuenta", "elim&#237;nalo manualmente".
Quotation marks are &#8220; &#8221; as in English, which both regions read.

### French (`fr`, `values-fr`)

Contributed through the public repository by Jonathan Aceituno and reviewed by a native speaker;
the strings English added after that snapshot were translated to the same choices. Address: vous.
Standard French of France, understood in Belgium, Switzerland and Canada.

| English | French | Note |
|---|---|---|
| game / system / library / folder / file | jeu / syst&#232;me / biblioth&#232;que / dossier / fichier | "ROM folders" is "Dossiers de ROM" |
| scan / rescan | lire, lecture / Relire la biblioth&#232;que | As Spanish, the progress lines say reading: Lecture des dossiers&#8230; |
| naming games | identifier (Identification des jeux) | |
| emulator | &#233;mulateur | A RetroArch core is "c&#339;ur", as RetroArch's French screens say |
| box art | jaquette (plural jaquettes) | Never couverture, image |
| Favorites / Recent / Apps | Favoris / R&#233;cents / Applis | Favorite / Unfavorite in hints: Favori / Retirer favori |
| Settings | Param&#232;tres (the screen); r&#233;glages (what is set) | Android's own: les param&#232;tres d'Android |
| hide / hidden | masquer / masqu&#233; | |
| back up / backup / restore / Undo restore | sauvegarder / sauvegarde / restaurer / Annuler la restauration | Values agree with sauvegarde, restauration: Enregistr&#233;e, Annul&#233;e |
| second screen | second &#233;cran | |
| home screen / main screen | &#233;cran d'accueil (Android's home app, the Home button: bouton Accueil) / &#233;cran principal (Plumemu's own) | |
| shortcut | raccourci | |
| report a problem | Signaler un probl&#232;me | A sent one: un signalement |
| statistics / usage statistics | statistiques / statistiques d'usage | Crash reports: rapports de plantage |
| account, sign in, sign out | compte, Se connecter, Se d&#233;connecter | |
| On / Off | Activ&#233; / D&#233;sactiv&#233; | |
| shoulder buttons | g&#226;chettes | |
| app | appli (plural applis) | Android's own lists keep "Applications" |

Hint labels: Choisir, Retour, Ouvrir, Jouer, Chercher, Param&#232;tres, Autre syst&#232;me, Taille, Grille,
Liste, Menu, Favori / Retirer favori, Effacer, Espace, Maj, Enregistrer, R&#233;essayer. Push right twice:
"Jouer (&#215;2)" and, after the first push, "Encore : jouer". "Retirer favori", "Encore : jouer" and
"Encore : ouvrir" are 14 or 15 characters, over the 12 budget.

Typography: a no-break space (U+00A0, written as the character) before `: ; ? !` and between a
number and its unit where English has one. Quotes are straight (`\"`), not &#171; &#187;, as the
contributor chose. "Rien n'a &#233;t&#233; modifi&#233;." for Nothing was changed, "R&#233;essayez." for Try
again, "Plumemu n'a pas pu&#8230;" for could not. Avoid wording that gives the person a gender
("connect&#233;"): "votre connexion &#224; ScreenScraper est conserv&#233;e". Plurals: `one` covers 0 and 1
in French, so "0 jeu" is correct. Language names inside a sentence are lower case ("Traduction en
anglais", `home/RowVariants.kt`).

### Vietnamese (`vi`, `values-vi`)

Address: "b&#7841;n" where a pronoun is needed; most labels and messages need none. Plain modern
words, the register of Android's and Google Play's Vietnamese screens. Polite requests start with
"H&#227;y" ("H&#227;y th&#7917; l&#7841;i."); a failure is "Kh&#244;ng &#8230; &#273;&#432;&#7907;c" ("Kh&#244;ng l&#432;u &#273;&#432;&#7907;c thay &#273;&#7893;i n&#224;y."), and
"Nothing was changed" is "Ch&#432;a c&#243; g&#236; thay &#273;&#7893;i". Modern tone placement ("x&#243;a", "h&#7911;y", "t&#249;y ch&#7881;nh"),
as Android writes them.

Glossary:

| English | Vietnamese |
|---|---|
| game | tr&#242; ch&#417;i ("game" only inside "m&#225;y ch&#417;i game") |
| system | h&#7879; m&#225;y |
| library | th&#432; vi&#7879;n |
| folder / file | th&#432; m&#7909;c / t&#7879;p |
| scan, rescan | qu&#233;t, qu&#233;t l&#7841;i (also for English's "reading your folders") |
| emulator | tr&#236;nh gi&#7843; l&#7853;p |
| box art | &#7843;nh b&#236;a |
| Favorites / Recent / Apps | Y&#234;u th&#237;ch / G&#7847;n &#273;&#226;y / &#7912;ng d&#7909;ng |
| Settings / Android's settings | C&#224;i &#273;&#7863;t / c&#224;i &#273;&#7863;t c&#7911;a Android |
| hide / hidden | &#7849;n / &#273;&#227; &#7849;n |
| back up / backup / restore / undo restore | sao l&#432;u / b&#7843;n sao l&#432;u / kh&#244;i ph&#7909;c / ho&#224;n t&#225;c kh&#244;i ph&#7909;c |
| accent color / text color / background | m&#224;u nh&#7845;n / m&#224;u ch&#7919; / m&#224;u n&#7873;n |
| text size: Small &#8230; Largest | c&#7905; ch&#7919;: Nh&#7887;, V&#7915;a, L&#7899;n, L&#7899;n h&#417;n, R&#7845;t l&#7899;n, L&#7899;n nh&#7845;t |
| second screen | m&#224;n h&#236;nh ph&#7909; |
| home screen | m&#224;n h&#236;nh ch&#237;nh (Plumemu's own main screen is never called that: say "c&#7897;t h&#7879; m&#225;y", "danh s&#225;ch tr&#242; ch&#417;i") |
| shortcut | l&#7889;i t&#7855;t (short name beside a game: "Link", 5 characters at most) |
| region | khu v&#7921;c |
| add-on (a system kind) | ph&#7909; ki&#7879;n (m&#7903; r&#7897;ng) |
| report a problem | B&#225;o l&#7895;i; a sent report is "b&#225;o c&#225;o l&#7895;i" |
| statistics / usage statistics | th&#7889;ng k&#234; / th&#7889;ng k&#234; s&#7917; d&#7909;ng; crash reports "b&#225;o c&#225;o s&#7921; c&#7889;" |
| account, sign in, sign out | t&#224;i kho&#7843;n, &#273;&#259;ng nh&#7853;p, &#273;&#259;ng xu&#7845;t |
| log file | nh&#7853;t k&#253; |
| "X of Y" | "X tr&#234;n Y" ("12 tr&#234;n 40 tr&#242; ch&#417;i"); "X/Y" where a value is short |
| hint labels | Ch&#7885;n, Quay l&#7841;i, M&#7903;, Ch&#417;i, T&#236;m ki&#7871;m, C&#224;i &#273;&#7863;t, L&#432;u, X&#243;a, H&#7911;y, Xong, &#272;&#7893;i |

Units: "%1$s gi&#7901; %2$s ph&#250;t", "%1$s ph&#250;t"; battery "C&#242;n kho&#7843;ng 4 gi&#7901; 15 ph&#250;t", pace "30% m&#7895;i gi&#7901;".
Dates in the system texts as day/month ("15/7/1983"), decimals with a comma ("3,5 cm").
Wi-Fi, ScreenScraper, title ID, Steam app ID, arcade, ROM, Shift and Menu stay as they are.

### Chinese, Simplified (`zh-CN`, `values-zh`)

Mainland usage. Full-width punctuation in sentences (&#65292;&#12290;&#65306;&#65311;&#65288;&#65289;&#12289; and &#8220;&#8221; for names, button
labels and Settings paths: &#22312;&#8220;&#35774;&#32622; &#8250; &#28216;&#25103;&#24211; &#8250; ROM &#25991;&#20214;&#22841;&#8221;&#20013;). A space between Chinese and Latin
letters or digits (Plumemu &#26080;&#27861;&#8230;, 18 &#20010;&#24179;&#21488;), none next to full-width punctuation. Labels, hints
and values have no full stop.

Address: no pronoun where the sentence works without one; where one is needed, &#20320; (friendly
consumer tone), never &#24744;.

| English | Chinese | Note |
|---|---|---|
| game / library / folder | &#28216;&#25103; / &#28216;&#25103;&#24211; / &#25991;&#20214;&#22841; | |
| system | &#24179;&#21488; | &#31995;&#32479; reads as Android's operating system and &#20027;&#26426; leaves out handhelds, arcade boards and computers; &#24179;&#21488; is the word Chinese frontends use. Kinds: &#23478;&#29992;&#20027;&#26426;, &#25484;&#26426;, &#34903;&#26426;&#22522;&#26495;, &#30005;&#33041;, &#25193;&#23637;&#35774;&#22791;, &#25805;&#20316;&#31995;&#32479;. |
| scan / rescan | &#25195;&#25551; / &#37325;&#26032;&#25195;&#25551; | "Reading your folders" stays &#35835;&#21462;; naming games is &#35782;&#21035;. |
| emulator | &#27169;&#25311;&#22120; | Play with&#8230; is &#25351;&#23450;&#27169;&#25311;&#22120;&#8230; |
| box art | &#23553;&#38754; | Never &#22270;&#29255; or &#22270;&#20687;. |
| Favorites / Recent / Apps | &#25910;&#34255; / &#26368;&#36817; / &#24212;&#29992; | Favorite / Unfavorite: &#25910;&#34255; / &#21462;&#28040;&#25910;&#34255;. |
| Settings | &#35774;&#32622; | Android's settings: Android &#35774;&#32622;. |
| hide / hidden | &#38544;&#34255; / &#24050;&#38544;&#34255; | Hidden games: &#24050;&#38544;&#34255;&#30340;&#28216;&#25103;. |
| backup, back up / restore / Undo restore | &#22791;&#20221; / &#24674;&#22797; / &#25764;&#38144;&#24674;&#22797; | The kinds: &#35774;&#32622; and &#20840;&#37096;; a file's summary: &#35774;&#32622;&#22791;&#20221; / &#23436;&#25972;&#22791;&#20221;. Forget the copy: &#33293;&#24323;&#21103;&#26412;. |
| accent color | &#24378;&#35843;&#33394; | Text color &#25991;&#23383;&#39068;&#33394;, Background &#32972;&#26223;, Colors &#37197;&#33394;, Custom &#33258;&#23450;&#20041;. |
| text size | &#25991;&#23383;&#22823;&#23567; | &#23567;, &#20013;, &#22823;, &#36739;&#22823;, &#29305;&#22823;, &#26368;&#22823;. Android's own font size: &#23383;&#20307;&#22823;&#23567;. |
| second screen | &#21103;&#23631; | |
| home screen, home app, Home button | &#26700;&#38754;, &#26700;&#38754;&#24212;&#29992;, &#20027;&#39029;&#38190; | As MIUI and most mainland phones say it. Plumemu's own main screen: &#20027;&#30028;&#38754;. |
| shortcut | &#24555;&#25463;&#26041;&#24335; | Short name in lists: &#24555;&#25463;. |
| region | &#22320;&#21306; | |
| report a problem | &#21453;&#39304;&#38382;&#39064; | A report: &#21453;&#39304;. |
| statistics / usage statistics | &#32479;&#35745; (&#28216;&#29609;&#32479;&#35745;) / &#20351;&#29992;&#25968;&#25454; | Kept apart on purpose. |
| account, Sign in, Sign out | &#36134;&#21495;, &#30331;&#24405;, &#36864;&#20986;&#30331;&#24405; | ScreenScraper "closed for now": &#26242;&#20572;&#26381;&#21153;. |
| Nothing was changed | &#26410;&#20570;&#20219;&#20309;&#26356;&#25913; | Untouched: &#22343;&#26410;&#21463;&#24433;&#21709;. |
| play time / last played | &#28216;&#29609;&#26102;&#38271; / &#19978;&#27425;&#28216;&#29609; | Durations: 4 &#23567;&#26102; 12 &#20998;&#38047;. |

Hint labels (2 to 6 characters): &#36873;&#25321; (A, Select and Choose alike), &#36820;&#22238;, &#35774;&#32622;, &#24320;&#22987;&#28216;&#25103;, &#25171;&#24320;,
&#25353;&#20004;&#27425;&#24320;&#22987; / &#20877;&#25353;&#19968;&#27425;&#24320;&#22987;, &#20999;&#25442;&#24179;&#21488;, &#22823;&#23567;, &#32593;&#26684;&#35270;&#22270; / &#21015;&#34920;&#35270;&#22270;, &#33756;&#21333;, &#25628;&#32034;, &#21024;&#38500;, &#31354;&#26684;, &#20809;&#26631;,
&#22823;&#20889; (Shift), &#20445;&#23384;, &#37325;&#35797;, &#20851;&#38381;, &#35843;&#25972; (Left / Right), &#20999;&#25442;&#20998;&#31867; (L1 R1), &#35828;&#26126; (More), &#31227;&#21160;, &#23436;&#25104;,
&#21462;&#28040;, &#38544;&#34255;, &#26174;&#31034;, &#36755;&#20837;, &#19979;&#19968;&#39033;, &#20999;&#25442; (an On/Off switch), &#26242;&#19981;. The keyboard page keys stay 123
and ABC, as Chinese keyboards print them.

Game titles inside the system "about" texts use their common mainland names in &#12298;&#12299; where one is
well known (&#12298;&#22823;&#37329;&#21018;&#12299;, &#12298;&#20420;&#32599;&#26031;&#26041;&#22359;&#12299;, &#12298;&#36229;&#32423;&#39532;&#21147;&#27431; 64&#12299;, &#12298;&#23453;&#21487;&#26790; &#37329;&#183;&#38134;&#12299;, &#12298;&#21507;&#35910;&#20154;&#12299;), and stay in
English otherwise. AA batteries are 5 &#21495;&#30005;&#27744;.

### Chinese, Traditional (`zh-TW`, `values-zh-rTW`)

Taiwan usage (also shown in Hong Kong and Macau, see Chinese folders). Not a character-by-character
conversion of Simplified: words differ (for example software and settings terms), so it was
translated from English, with the Simplified file as a reference only where Taiwan says the same.
Wording follows Android's own zh-TW screens (&#35373;&#23450;, &#25033;&#29992;&#31243;&#24335;, &#25463;&#24465;, &#20027;&#30059;&#38754;, &#23433;&#35037;&#19981;&#26126;&#25033;&#29992;&#31243;&#24335;).

Full-width punctuation in sentences (&#65292;&#12290;&#65306;&#65311;&#65288;&#65289;&#12289;), &#12300;&#12301; for names, quotes, button labels and
Settings paths (&#22312;&#12300;&#35373;&#23450; &#8250; &#36938;&#25138;&#24235; &#8250; ROM &#36039;&#26009;&#22846;&#12301;&#20013;). A space between Chinese and Latin letters or
digits (Plumemu &#28961;&#27861;&#8230;, 18 &#20491;&#24179;&#21488;), none next to full-width punctuation; a no-break space
(`&#160;`) where English has one between a number and its unit. Labels, hints and values have no
full stop.

Address: no pronoun where the sentence works without one; where one is needed, &#20320;, never &#24744;.
Device: &#36889;&#37096;&#35037;&#32622; (Android's word), not &#35373;&#20633;.

| English | Chinese | Note |
|---|---|---|
| game / library / folder / file | &#36938;&#25138; / &#36938;&#25138;&#24235; / &#36039;&#26009;&#22846; / &#27284;&#26696; | Counter for games &#27454; (1,284 &#27454;&#36938;&#25138;); Simplified uses &#20010;. |
| system | &#24179;&#21488; | As Simplified: &#20027;&#27231; leaves out handhelds, arcade boards and computers, and &#31995;&#32113; reads as Android. Taiwan frontends say &#24179;&#21488; too. Kinds: &#23478;&#29992;&#20027;&#27231;, &#25484;&#27231;, &#34903;&#27231;&#22522;&#26495;, &#38651;&#33126;, &#25844;&#20805;&#35037;&#32622;, &#20316;&#26989;&#31995;&#32113;. |
| scan / rescan | &#25475;&#25551; / &#37325;&#26032;&#25475;&#25551; | "Reading your folders" is &#35712;&#21462;; naming games is &#36776;&#35672; (Simplified &#35782;&#21035;). Unidentified: &#26410;&#36776;&#35672;. |
| emulator | &#27169;&#25836;&#22120; | Play with&#8230; is &#25351;&#23450;&#27169;&#25836;&#22120;&#8230; |
| box art | &#23553;&#38754; | Never &#22294;&#29255; or &#24433;&#20687;. |
| Favorites / Recent / Apps | &#25910;&#34255; / &#26368;&#36817; / &#25033;&#29992;&#31243;&#24335; | Favorite / Unfavorite: &#25910;&#34255; / &#21462;&#28040;&#25910;&#34255;. Apps is &#25033;&#29992;&#31243;&#24335; as on Android in Taiwan, not &#25033;&#29992; or App. |
| Settings | &#35373;&#23450; | Android's settings: Android &#35373;&#23450;. |
| hide / hidden | &#38577;&#34255; / &#24050;&#38577;&#34255; | |
| backup, back up / restore / Undo restore | &#20633;&#20221; / &#36996;&#21407; / &#25764;&#37559;&#36996;&#21407; | Taiwan restores with &#36996;&#21407; (Simplified &#24674;&#22797;); "put back as it was" is &#24674;&#24489;&#21407;&#29376;. &#24489;&#21407; (Taiwan's undo) is kept for "cannot be undone" only, so &#25764;&#37559;&#36996;&#21407; never reads as a doubled word. Kinds &#35373;&#23450; and &#20840;&#37096;; summaries &#35373;&#23450;&#20633;&#20221; / &#23436;&#25972;&#20633;&#20221;. Forget the copy: &#25448;&#26820;&#21103;&#26412;. |
| accent color | &#24375;&#35519;&#33394; | Text color &#25991;&#23383;&#38991;&#33394;, Background &#32972;&#26223;, Colors &#37197;&#33394;, Custom &#33258;&#35330; (Simplified &#33258;&#23450;&#20041;). Font is &#23383;&#22411;, not &#23383;&#39636;. |
| text size | &#25991;&#23383;&#22823;&#23567; | &#23567;, &#20013;, &#22823;, &#36611;&#22823;, &#29305;&#22823;, &#26368;&#22823;. Android's own font size: &#23383;&#22411;&#22823;&#23567;. |
| grid view / list view | &#26684;&#29376;&#27298;&#35222; / &#28165;&#21934;&#27298;&#35222; | Google's zh-TW words. A list is &#28165;&#21934;, never &#21015;&#34920;. |
| second screen | &#21103;&#34722;&#24149; | |
| home screen, home app, Home button | &#20027;&#30059;&#38754;, &#20027;&#30059;&#38754;&#25033;&#29992;&#31243;&#24335;, &#20027;&#30059;&#38754;&#25353;&#37397; | Android's zh-TW words. Plumemu's own main screen: &#39318;&#38913;, so it is never confused with Android's &#20027;&#30059;&#38754;. |
| shortcut | &#25463;&#24465; | Short name in lists: &#25463;&#24465; too. |
| region | &#22320;&#21312; | &#32654;&#22283;, &#27472;&#27954;, &#26085;&#26412;, &#20840;&#29699;. |
| report a problem | &#22238;&#22577;&#21839;&#38988; | A report: &#22238;&#22577; (Simplified &#21453;&#39304;). |
| statistics / usage statistics | &#32113;&#35336; (&#36938;&#29609;&#32113;&#35336;) / &#20351;&#29992;&#36039;&#26009; | Kept apart on purpose. Crash report: &#30070;&#27231;&#22577;&#21578;. |
| account, Sign in, Sign out | &#24115;&#34399;, &#30331;&#20837;, &#30331;&#20986; | Username &#20351;&#29992;&#32773;&#21517;&#31281;. |
| play time / last played | &#36938;&#29609;&#26178;&#38291; / &#19978;&#27425;&#36938;&#29609; | Durations: 4 &#23567;&#26178; 12 &#20998;&#37912;. |
| log / export | &#35352;&#37636;&#27284; / &#21295;&#20986; | |
| server / mobile data / checksum | &#20282;&#26381;&#22120; / &#34892;&#21205;&#25976;&#25818; / &#27298;&#26597;&#30908; | |
| licence / open source | &#25480;&#27402; / &#38283;&#25918;&#21407;&#22987;&#30908; | |
| gamepad / controller | &#25163;&#25226; / &#25511;&#21046;&#22120; | Joy-Con &#25511;&#21046;&#22120; as Nintendo Taiwan writes it; cartridge &#21345;&#21283;, disc &#20809;&#30879;. |

Hint labels (2 to 6 characters): &#36984;&#25799; (A, Select and Choose alike), &#36820;&#22238;, &#35373;&#23450;, &#38283;&#22987;&#36938;&#25138;, &#38283;&#21855;,
&#25353;&#20841;&#27425;&#38283;&#22987; / &#20877;&#25353;&#19968;&#27425;&#38283;&#22987;, &#20999;&#25563;&#24179;&#21488;, &#22823;&#23567;, &#26684;&#29376;&#27298;&#35222; / &#28165;&#21934;&#27298;&#35222;, &#36984;&#21934;, &#25628;&#23563;, &#21034;&#38500;, &#31354;&#30333;, &#28216;&#27161;,
&#22823;&#23531; (Shift), &#20786;&#23384;, &#37325;&#35430;, &#38364;&#38281;, &#35519;&#25972; (Left / Right), &#20999;&#25563;&#20998;&#39006; (L1 R1), &#35498;&#26126; (More), &#31227;&#21205;, &#23436;&#25104;,
&#21462;&#28040;, &#38577;&#34255;, &#39023;&#31034;, &#36664;&#20837;, &#19979;&#19968;&#38917;, &#20999;&#25563; (an On/Off switch), &#26283;&#26178;&#19981;&#35201; (Android's zh-TW "Not now").
Setup's Start playing is &#38283;&#22987;&#36938;&#29609;, kept apart from Play (&#38283;&#22987;&#36938;&#25138;). The keyboard page keys stay
123 and ABC; the space key reads &#31354;&#30333;&#37749;. Every hint fits the 6-character budget.

Game titles inside the system "about" texts use their common Taiwan names in &#12298;&#12299; where one is well
known, which often differ from mainland names: &#12298;&#22823;&#37329;&#21083;&#12299;, &#12298;&#20420;&#32645;&#26031;&#26041;&#22602;&#12299;, &#12298;&#36229;&#32026;&#29802;&#21033;&#27472; 64&#12299;,
&#12298;&#31934;&#38728;&#23542;&#21487;&#22818; &#37329;&#12539;&#37504;&#12299;, &#12298;&#38899;&#36895;&#23567;&#23376;&#12299;, &#12298;&#23567;&#31934;&#38728;&#12299; (Pac-Man), &#12298;&#39405;&#23458;&#20219;&#21209;&#12299; (The Matrix),
&#12298;&#20448;&#30428;&#29557;&#36554;&#25163;&#65306;&#33258;&#30001;&#22478;&#25925;&#20107;&#12299;; others stay in English. Sega is written SEGA, as SEGA Taiwan writes it;
Sony stays in Latin letters. AA batteries are &#19977;&#34399;&#38651;&#27744; (Simplified 5 &#21495;&#30005;&#27744;). Colour names follow
Taiwan usage where it differs: &#21654;&#21857;&#33394; (Brown), &#33802;&#22982; (Lime), &#31859;&#30333; (Cream), &#22902;&#27833;&#40643; (Butter), &#34588;&#26691;
(Peach, since &#26691;&#33394; suggests scandal in Taiwan).

### Korean (`ko`, `values-ko`)

&#54644;&#50836;&#52404; for every message and explanation, the privacy and licence texts included (as One UI
writes them), so no screen mixes it with &#54633;&#45768;&#45796;&#52404;; the only &#54633;&#45768;&#45796; is the fixed thanks &#44048;&#49324;&#54633;&#45768;&#45796;. Labels,
buttons, hints and values are plain nouns (&#49444;&#51221;, &#46244;&#47196;, &#44160;&#49353;, &#49440;&#53469;) or noun phrases ending in &#51473;, &#46120;, &#50630;&#51020;.
No pronoun for the person. Instructions end in -&#54616;&#49464;&#50836;; a request to another app in -&#54644; &#51452;&#49464;&#50836;.

Names of buttons, rows and entries inside a sentence are quoted in &#8216; &#8217; (&#8216;&#45796;&#49884; &#49884;&#46020;&#8217;&#47484; &#49440;&#53469;&#54616;&#49464;&#50836;);
Settings paths are not (&#49444;&#51221; &#8250; &#46972;&#51060;&#48652;&#47084;&#47532; &#8250; ROM &#54260;&#45908;). Game titles in the system about texts are in &#12298; &#12299;
with their Korean names where one is known (&#12298;&#49800;&#54140; &#47560;&#47532;&#50724; 64&#12299;, &#12298;&#53580;&#53944;&#47532;&#49828;&#12299;, &#12298;&#54252;&#53011;&#47788;&#49828;&#53552; &#44552;&#183;&#51008;&#12299;), English
otherwise. Placeholders take no particle that depends on the last letter: the sentence names the
thing after it (%1$s &#54637;&#47785;&#51012;, %1$s &#54028;&#51068;&#51060;, %1$s &#50640;&#48044;&#47112;&#51060;&#53552;&#44032;, %1$s &#50545;&#51012;) or the placeholder sits where no
particle is needed. Counts put the noun first: &#44172;&#51076; 1,284&#44060;, &#44592;&#51333; 18&#44060;, &#54028;&#51068; %1$s&#44060;. Durations
4&#49884;&#44036; 12&#48516;, 45&#48516;; no space between a number and its Korean unit.

| English | Korean | Note |
|---|---|---|
| game / library / folder | &#44172;&#51076; / &#46972;&#51060;&#48652;&#47084;&#47532; / &#54260;&#45908; | |
| system | &#44592;&#51333; | The word Korean gamers use for a console model; &#49884;&#49828;&#53596; reads as the operating system, &#54540;&#47019;&#54268; as a store. Kinds: &#44032;&#51221;&#50857; &#44172;&#51076;&#44592;, &#55092;&#45824;&#50857; &#44172;&#51076;&#44592;, &#50500;&#52992;&#51060;&#46300; &#44592;&#54032;, &#52980;&#54504;&#53552;, &#54869;&#51109; &#44592;&#44592;, &#50868;&#50689;&#52404;&#51228;. |
| scan / rescan | &#49828;&#52884; / &#45796;&#49884; &#49828;&#52884; | Reading the folders is &#49828;&#52884;; the library or a list being read is &#48520;&#47084;&#50724;&#45796; (&#44172;&#51076;&#51012; &#48520;&#47084;&#50724;&#45716; &#51473;). Naming games: &#51060;&#47492; &#52286;&#44592;; identify / unidentified: &#54032;&#48324; / &#54032;&#48324;&#46104;&#51648; &#50506;&#51008;. |
| emulator | &#50640;&#48044;&#47112;&#51060;&#53552; | Play with&#8230;: &#50640;&#48044;&#47112;&#51060;&#53552; &#51648;&#51221;&#8230; |
| box art | &#48149;&#49828; &#50500;&#53944; | Always with the space. Never &#54364;&#51648;, &#52964;&#48260;, &#51060;&#48120;&#51648; or &#49324;&#51652;. |
| Favorites | &#51600;&#44200;&#52286;&#44592; | Y hint &#51600;&#44200;&#52286;&#44592; / &#51600;&#44200;&#52286;&#44592; &#54644;&#51228;; menu &#51600;&#44200;&#52286;&#44592;&#50640; &#52628;&#44032; / &#51600;&#44200;&#52286;&#44592;&#50640;&#49436; &#49325;&#51228; (One UI's words). |
| Recent | &#52572;&#44540; &#54540;&#47112;&#51060; | Sort by last played: &#52572;&#44540; &#54540;&#47112;&#51060;&#49692;. |
| Apps | &#50545; | Android's settings path: Android &#49444;&#51221; &#8250; &#50545; &#8250; &#44592;&#48376; &#50545; (AOSP Korean). |
| Settings | &#49444;&#51221; | Android's: Android &#49444;&#51221;. |
| hide / hidden | &#49704;&#44592;&#44592; / &#49704;&#44608; | Hidden games: &#49704;&#44596; &#44172;&#51076;. |
| backup, back up / restore / Undo restore | &#48177;&#50629; / &#48373;&#50896; / &#48373;&#50896; &#52712;&#49548; | The kinds &#49444;&#51221; and &#51204;&#52404;. The dialog's Undo button &#46104;&#46028;&#47532;&#44592; (&#52712;&#49548; is Cancel); Forget the copy: &#49324;&#48376; &#49325;&#51228;. |
| accent color | &#44053;&#51312; &#49353;&#49345; | Text color &#44544;&#51088; &#49353;&#49345;, Background &#48176;&#44221;, Colors (ready-made sets) &#49353;&#49345; &#53580;&#47560;, Custom &#49324;&#50857;&#51088; &#51648;&#51221;, Your own color &#49324;&#50857;&#51088; &#51648;&#51221; &#49353;&#49345;, By system &#44592;&#51333;&#48324;. |
| text size | &#44544;&#51088; &#53356;&#44592; | &#51089;&#44172;, &#48372;&#53685;, &#53356;&#44172;, &#45908; &#53356;&#44172;, &#50500;&#51452; &#53356;&#44172;, &#52572;&#45824; (One UI's steps). Android's own: Android&#51032; &#44544;&#44852; &#53356;&#44592;. |
| second screen | &#48372;&#51312; &#54868;&#47732; | |
| home screen, home app, Home button | &#54856; &#54868;&#47732;, &#54856; &#50545;, &#54856; &#48260;&#53948; | |
| shortcut | &#48148;&#47196;&#44032;&#44592; | The short name in lists is the same word (4 syllables). |
| region | &#51648;&#50669; | USA &#48513;&#48120;, Europe &#50976;&#47101;, Japan &#51068;&#48376;, World &#51204; &#49464;&#44228;. |
| report a problem | &#47928;&#51228; &#49888;&#44256; | A report: &#49888;&#44256;. |
| statistics / usage statistics | &#53685;&#44228;, &#54540;&#47112;&#51060; &#53685;&#44228; / &#49324;&#50857; &#53685;&#44228; | Kept apart on purpose. |
| account, Sign in, Sign out | &#44228;&#51221;, &#47196;&#44536;&#51064;, &#47196;&#44536;&#50500;&#50883; | |
| play time / last played | &#54540;&#47112;&#51060; &#49884;&#44036; / &#47560;&#51648;&#47561; &#54540;&#47112;&#51060; | |
| hint bar | &#48260;&#53948; &#44032;&#51060;&#46300; | Show button hints: &#48260;&#53948; &#44032;&#51060;&#46300; &#54364;&#49884;. |
| On / Off | &#53020;&#51664; / &#44732;&#51664; | A's hint on a switch: &#53020;&#44592;/&#45124;&#44592;. |

Hint labels (6 syllables at most): &#49440;&#53469; (Select and Choose alike), &#46244;&#47196;, &#49444;&#51221;, &#54540;&#47112;&#51060;, &#50676;&#44592;, &#46160; &#48264; &#45580;&#47084; &#49892;&#54665; /
&#46160; &#48264; &#45580;&#47084; &#50676;&#44592;, &#45796;&#49884; &#45580;&#47084; &#49892;&#54665; / &#45796;&#49884; &#45580;&#47084; &#50676;&#44592; (&#49892;&#54665;, not &#54540;&#47112;&#51060;, to stay within 6), &#44592;&#51333; &#51204;&#54872;, &#53356;&#44592;,
&#44536;&#47532;&#46300; &#48372;&#44592; / &#47785;&#47197; &#48372;&#44592;, &#47700;&#45684;, &#51600;&#44200;&#52286;&#44592; / &#51600;&#44200;&#52286;&#44592; &#54644;&#51228;, &#44160;&#49353;, &#49325;&#51228;, &#46916;&#50612;&#50416;&#44592; (the key itself prints
&#49828;&#54168;&#51060;&#49828;), &#52964;&#49436;, Shift, &#51200;&#51109;, &#45796;&#49884; &#49884;&#46020;, &#45803;&#44592;, &#48320;&#44221;, &#49465;&#49496; &#51060;&#46041;, &#49444;&#47749; (More), &#51060;&#46041;, &#50756;&#47308;, &#52712;&#49548;, &#49704;&#44592;&#44592;, &#54364;&#49884;,
&#51077;&#47141;, &#45796;&#51020;, &#45208;&#51473;&#50640;, &#44228;&#49549;, &#54260;&#45908; &#49440;&#53469;, &#54260;&#45908; &#52628;&#44032;, &#49884;&#51089;&#54616;&#44592;. The keyboard page keys stay 123 and ABC: the
keyboard types Latin letters.

Colour preset names: plain colours in native words (&#44160;&#51221;, &#48744;&#44053;, &#54028;&#46993;, &#52488;&#47197;, &#45432;&#46993;, &#48372;&#46972;, &#44040;&#49353;, &#54616;&#45720;&#49353;, &#48157;&#51008;&#49353;,
&#50612;&#46160;&#50868;&#49353;, &#51333;&#51060;, &#44516;), the rest as the loanwords Korean paint and fashion palettes use (&#44536;&#47000;&#54028;&#51060;&#53944;, &#48120;&#46300;&#45208;&#51060;&#53944;,
&#46972;&#48292;&#45908;, &#45348;&#51060;&#48708;, &#52264;&#53084;, &#48124;&#53944;, &#53076;&#46980;, &#54168;&#47532;&#50969;&#53364;).

### Japanese (`ja`, `values-ja`)

&#12391;&#12377;/&#12414;&#12377; for messages and explanations; labels, buttons and hints are
plain nouns (&#20307;&#35328;&#27490;&#12417;) or short verb stems, no polite endings. Katakana for loanwords only
where they are the common word. Japanese punctuation &#12289;&#12290; and full-width &#65288;&#65289;&#12300;&#12301;&#65306;&#65311; in
sentences; names of screens, rows and buttons quoted in &#12300;&#12301; (&#12300;&#35373;&#23450; &#8250; &#12521;&#12452;&#12502;&#12521;&#12522; &#8250; ROM&#12501;&#12457;&#12523;&#12480;&#12301;),
game titles in &#12302;&#12303; in the system about texts. No space between Japanese and Latin words or
numbers (Wi-Fi&#12395;&#25509;&#32154;, 18&#27231;&#31278;).

| English | Japanese | Note |
|---|---|---|
| game | &#12466;&#12540;&#12512;; counter &#26412; (1,284&#26412;) | Box-art and file counts use &#20491; |
| system | &#27231;&#31278;; counter &#27231;&#31278; (18&#27231;&#31278;) | The usual word for a console model; &#12471;&#12473;&#12486;&#12512; reads as translationese |
| library | &#12521;&#12452;&#12502;&#12521;&#12522; | |
| folder | &#12501;&#12457;&#12523;&#12480; | Android's own spelling |
| scan / rescan | &#12473;&#12461;&#12515;&#12531; / &#20877;&#12473;&#12461;&#12515;&#12531; | Most texts say &#35501;&#12415;&#36796;&#12416; (read), as English says "read" |
| emulator | &#12456;&#12511;&#12517;&#12524;&#12540;&#12479;&#12540; | |
| box art | &#12497;&#12483;&#12465;&#12540;&#12472;&#30011;&#20687; | The common Japanese word for a box front; never &#12459;&#12496;&#12540; or &#12508;&#12483;&#12463;&#12473;&#12450;&#12540;&#12488; |
| Favorites | &#12362;&#27671;&#12395;&#20837;&#12426; | Y hint: &#12362;&#27671;&#12395;&#20837;&#12426; / &#30331;&#37682;&#35299;&#38500; (fits 6); menu: &#12362;&#27671;&#12395;&#20837;&#12426;&#12395;&#36861;&#21152; / &#12362;&#27671;&#12395;&#20837;&#12426;&#12363;&#12425;&#22806;&#12377; |
| Recent | &#26368;&#36817;&#36938;&#12435;&#12384; | Short form of &#26368;&#36817;&#36938;&#12435;&#12384;&#12477;&#12501;&#12488;, fits the system column |
| Apps | &#12450;&#12503;&#12522; | |
| Settings | &#35373;&#23450; | Android's are Android&#12398;&#35373;&#23450; |
| hide / hidden | &#38750;&#34920;&#31034;&#12395;&#12377;&#12427; / &#38750;&#34920;&#31034; | |
| backup / back up | &#12496;&#12483;&#12463;&#12450;&#12483;&#12503; | |
| restore / Undo restore | &#24489;&#20803; / &#24489;&#20803;&#12434;&#21462;&#12426;&#28040;&#12377; | Forget the copy: &#12467;&#12500;&#12540;&#12434;&#30772;&#26820; |
| accent | &#12450;&#12463;&#12475;&#12531;&#12488;&#12459;&#12521;&#12540; | |
| Colors (ready-made sets) | &#37197;&#33394; | |
| text size | &#25991;&#23383;&#12469;&#12452;&#12474;: &#23567;, &#20013;, &#22823;, &#29305;&#22823;, &#36229;&#29305;&#22823;, &#26368;&#22823; | |
| second screen | &#12469;&#12502;&#30011;&#38754; | Not &#19979;&#30011;&#38754;: the screen need not be below |
| home screen | &#12507;&#12540;&#12512;&#30011;&#38754; | |
| shortcut | &#12471;&#12519;&#12540;&#12488;&#12459;&#12483;&#12488; | Short name &#12522;&#12531;&#12463; |
| region | &#22320;&#22495; | USA &#21271;&#31859;, Europe &#12520;&#12540;&#12525;&#12483;&#12497;, Japan &#26085;&#26412;, World &#12527;&#12540;&#12523;&#12489; |
| report a problem | &#21839;&#38988;&#12434;&#22577;&#21578; | |
| statistics | &#12503;&#12524;&#12452;&#35352;&#37682; (Settings &#8250; Statistics) | Usage statistics: &#21033;&#29992;&#32113;&#35336;, kept apart |
| account; Sign in / Sign out | &#12450;&#12459;&#12454;&#12531;&#12488;; &#12525;&#12464;&#12452;&#12531; / &#12525;&#12464;&#12450;&#12454;&#12488; | Android's words |
| hint bar | &#12508;&#12479;&#12531;&#12460;&#12452;&#12489; | Settings &#8250; Show button hints: &#12508;&#12479;&#12531;&#12460;&#12452;&#12489;&#12434;&#34920;&#31034; |
| Play | &#12503;&#12524;&#12452; | Setup's Start playing: &#12399;&#12376;&#12417;&#12427; |
| Select / Choose (A) | &#27770;&#23450; / &#36984;&#25246; | &#27770;&#23450; and &#25147;&#12427; are the confirm and back labels of Nintendo and PlayStation consoles in Japan |
| Back (B) | &#25147;&#12427; | |
| Try again | &#20877;&#35430;&#34892; (button); &#12418;&#12358;&#19968;&#24230;&#12362;&#35430;&#12375;&#12367;&#12384;&#12373;&#12356; (sentence) | |
| Not now | &#12354;&#12392;&#12391; | |
| identify / unidentified | &#21028;&#23450; / &#26410;&#21028;&#23450; | Name from: &#21517;&#21069;&#12398;&#21028;&#23450; |

Length: every hint label fits 6 CJK characters. Y on a favourite is &#12300;&#30331;&#37682;&#35299;&#38500;&#12301;, not &#12300;&#12362;&#27671;&#12395;&#20837;&#12426;&#35299;&#38500;&#12301; (7); the armed push-right hints are &#12300;&#25276;&#12377;&#12392;&#12503;&#12524;&#12452;&#12301; and &#12300;&#25276;&#12377;&#12392;&#38283;&#12367;&#12301;, not &#12300;&#12418;&#12358;&#19968;&#24230;&#12391;&#12503;&#12524;&#12452;&#12301; (8). Colour preset names are katakana where Japanese
uses the loanword (&#12464;&#12521;&#12501;&#12449;&#12452;&#12488;, &#12521;&#12505;&#12531;&#12480;&#12540;), kanji for plain colours (&#40658;, &#36196;, &#38738;, &#32209;, &#40644;, &#33590;, &#31354;&#33394;, &#26716;&#33394;, &#34276;&#33394;).
