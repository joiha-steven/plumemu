# tools/sound

`make_sounds.py` synthesizes the three interface sounds (`ui_move`, `ui_confirm`, `ui_back`) into
`app/src/main/res/raw/` from sine waves and light FM (our own, nothing sampled; standard library only). Change
the numbers in the script and re-run `python3 tools/sound/make_sounds.py`; never edit the WAV files.
Check: `python3 -m unittest discover -s tools/sound`. Rules: [docs/design/rules.md](../../docs/design/rules.md) › Sound.
