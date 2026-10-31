# Translations

Plumemu's screens in every language it speaks, English included. Most of them have not yet been
read by a native speaker. If something reads wrong, too stiff, too long or simply not like your
language, please tell me or fix it.

| Folder | Language |
|---|---|
| [`en`](en/strings.xml) | English |
| [`vi`](vi/strings.xml) | Vietnamese |
| [`es`](es/strings.xml) | Spanish |
| [`ja`](ja/strings.xml) | Japanese |
| [`ko`](ko/strings.xml) | Korean |
| [`zh`](zh/strings.xml) | Chinese, Simplified |
| [`zh-rTW`](zh-rTW/strings.xml) | Chinese, Traditional |

These files are copied from the app before each release. An accepted fix goes into the app first
and shows up in its next release; this folder is refreshed at the same time.

## Report a wrong translation

Open an issue with the [translation template](https://github.com/joiha-steven/plumemu/issues/new?template=translation.yml). It
asks for:

- the language;
- where on the screen you saw it (for example "Settings › Home screen, the third row");
- the text as it is now;
- the text you suggest, and why if it is not obvious.

A screenshot helps. One issue can list several strings of the same language.

## Send a fix

1. Open the language's `strings.xml` above and press the pencil (Edit this file). GitHub makes a
   copy for you.
2. Change only the text between `>` and `<`. Keep everything else as it is:
   - the `name="…"` of each line, and the lines' order;
   - placeholders such as `%1$s`, `%2$d` and `%%`: the app puts a name, a number or a size there;
     you may move them inside the sentence, but not drop, add or renumber them;
   - `\'` for an apostrophe and `\"` for a quote, `&amp;` for &, `&lt;` for <.
3. Read the comment above a line (`<!-- … -->`) first: it says where the text shows and how long it
   may be. Short screens on handhelds cut long text.
4. Propose the change: one pull request per language file, with a line saying what you changed.

Every fix is checked by the same test the app uses (keys, placeholders, plural forms) before it
goes in.

To translate into a new language, open an issue first: a language also needs its own fonts and
settings in the app.

## Not here

The English file leaves out text that is never translated (button names such as A, B, START,
font names, licence names). Game titles, system names and emulator names are never translated.

## Your contribution

By sending a translation or a correction, you agree to the terms under Contributions in the
[licence](../LICENSE.md): Plumemu may use, change and distribute it inside the app and this
repository, without payment, and you confirm it is your own work. Contributors are credited in the
[changelog](../CHANGELOG.md) of the version that carries their fix, unless they ask not to be.
