"""Hand-kept decisions of the converter. Everything here is documented in README.md."""

FORMAT_VERSION = 2

# RULES R5: the twelve systems tested on hardware (Game Boy and Game Boy Color are two systems).
TESTED = {"nes", "snes", "gb", "gbc", "gba", "n64", "nds", "megadrive", "psx", "psp", "ps2",
          "gc", "arcade"}

# ES-DE systems that are only a region or variant folder of another system.
ES_MERGE = {
    "famicom": "nes", "sfc": "snes", "snesna": "snes", "megadrivejp": "megadrive",
    "megacdjp": "megacd", "sega32xjp": "sega32x", "sega32xna": "sega32x", "saturnjp": "saturn",
    "neogeocdjp": "neogeocd", "genesis": "megadrive", "segacd": "megacd", "tg16": "pcengine",
    "mark3": "mastersystem", "amiga600": "amiga", "amiga1200": "amiga",
}

# Daijishō unique id -> ES-DE system name, where the two projects name a system differently.
DJ_TO_ES = {
    "genesis": "megadrive", "3ds": "n3ds", "vita": "psvita", "ws": "wonderswan",
    "wsc": "wonderswancolor", "coleco": "colecovision", "cpc": "amstradcpc",
    "jaguar": "atarijaguar", "jaguarcd": "atarijaguarcd", "lynx": "atarilynx",
    "master": "mastersystem", "segacd": "megacd", "sg1000": "sg-1000", "tgcd": "tg-cd",
    "tg16": "pcengine", "cdi": "cdimono1", "g7400": "videopac", "gw": "gameandwatch",
    "appleii": "apple2",
}

# Folder names people use that neither source lists. The tested systems, psvita (owner
# request 2026-10-22: the AYN Thor has Vita3K) and the MinUI/Onion tags and stock firmware folder
# names of identification step S3 (sms, gg, pce, ps); others get the names the sources give. A folder name may belong to one system only (checked by convert.py);
# "mame", "fbneo" and "neogeo" are systems of their own, so they are not listed under arcade.
EXTRA_FOLDERS = {
    "nes": ["famicom", "fc"], "snes": ["sfc", "superfamicom", "super nintendo"],
    "gb": ["gameboy", "game boy"], "gbc": ["gameboycolor", "game boy color"],
    "gba": ["gameboyadvance", "game boy advance"], "n64": ["nintendo64"],
    "nds": ["ds", "nintendods"], "megadrive": ["genesis", "md", "sega genesis"],
    "psx": ["ps1", "playstation", "psone", "ps", "playstation 1"], "psp": ["playstationportable"],
    "ps2": ["playstation2"], "gc": ["gamecube", "ngc", "cube"],
    # MinUI/Onion folder tags ("Sega Master System (SMS)") and stock firmware folders.
    "mastersystem": ["sms"], "gamegear": ["gg"], "pcengine": ["pce"],
    "psvita": ["ps vita", "playstationvita", "playstation vita"],
}

# Extensions a source lists that are not game files Plumemu can launch. Daijisho's PS Vita players
# take ".dpt", Daijisho's own placeholder whose title ID lives in Daijisho's database
# ({tags.vita_game_id}); Plumemu reads the title ID from the ES-DE style ".psvita" file only.
# Also extensions of another system's games: ".ndd" is a 64DD disk (system n64dd), so an N64 folder
# or the picked folder hands it to n64dd (step S3).
# ES-DE's windows lists ".steam" for its GameNative commands, which Plumemu does not read (they are
# %INJECT%): left there, a .steam file in a Windows folder would be a Windows game no Windows emulator
# can start. Without it the file is a Steam game wherever it lies (the scan's extension rule; WinNative,
# in both systems, ties a Windows folder to Steam). Owner 2026-10-23.
EXCLUDED_EXTENSIONS = {"psvita": {"dpt"}, "n64": {"ndd"}, "windows": {"steam"}}

# Extensions of game files the sources do not list (identify step S5a): compressed Switch dumps
# (".nsz" NSP, ".xcz" XCI; Switch emulators open them) and 3DS installable archives (".cia"). The
# converter fails if another system lists one of them.
EXTRA_EXTENSIONS = {"switch": {"nsz", "xcz"}, "n3ds": {"cia"}}

SHORT_NAMES = {
    "nes": "NES", "snes": "SNES", "gb": "GB", "gbc": "GBC", "gba": "GBA", "n64": "N64",
    "nds": "DS", "megadrive": "Mega Drive", "psx": "PS1", "psp": "PSP", "ps2": "PS2",
    "gc": "GameCube", "arcade": "Arcade", "android": "Android",
}

# Daijishō boxArtAspectRatioId values that are wider than tall (N64, NES, SNES style boxes).
LANDSCAPE_BOX_ART_IDS = {1, 3, 4}

# The emulator that comes first for each tested system (owner plan: docs/plans/2026-10-21-emulator-setup.md).
# Systems not listed keep ES-DE's own first emulator. The rest of each list keeps ES-DE order.
FIRST_EMULATOR = {
    "nes": "retroarch-mesen", "snes": "retroarch-snes9x", "gb": "retroarch-gambatte",
    "gbc": "retroarch-gambatte", "gba": "retroarch-mgba", "megadrive": "retroarch-genesis-plus-gx",
    "arcade": "retroarch-mamearcade", "psx": "duckstation", "psp": "ppsspp", "ps2": "nethersx2",
    "gc": "dolphin",
}

# Box front width over height, measured live on 2026-10-23 from one US box front per system
# (ScreenScraper box-2D; a sample, not a median). A measured system's `cover` follows its ratio
# (landscape from LANDSCAPE_MIN_RATIO), so the measurement replaces Daijisho's id there: NES and
# Mega Drive boxes are tall, not landscape. The home grid uses the ratio until the person's own
# covers give a median (docs/design/rules.md > Grid); other systems get their class's default.
COVER_RATIOS = {
    "android": 1.00,  # an app icon is square; the class stays portrait (below LANDSCAPE_MIN_RATIO)
    "nes": 0.73, "snes": 1.37, "n64": 1.44, "megadrive": 0.71, "mastersystem": 0.71,
    "gb": 1.00, "gbc": 1.00, "gba": 1.00, "nds": 1.12, "psx": 1.17, "ps2": 0.715, "psp": 0.575,
    "gc": 0.72, "wii": 0.715, "saturn": 0.65, "dreamcast": 1.00, "pcengine": 0.85,
}

# From this width over height a box is landscape (the app's landscape class starts here too).
LANDSCAPE_MIN_RATIO = 1.2

# Emulators used by these systems are listed but never chosen by default (RULES R30).
LEGAL_RISK_SYSTEMS = {"switch"}

# Leading maker names dropped when a short name is made from the full name.
MAKER_PREFIXES = ("Nintendo ", "Sony ", "Sega ", "Microsoft ", "Bandai ", "Coleco ", "Mattel ", "Magnavox ",
                  "NEC ", "SNK ", "Philips ", "Panasonic ", "Emerson ")
# Short names that cannot be made from the name by the rule above. Checked against the name length.
SHORT_NAME_OVERRIDES = {
    "3do": "3DO", "amigacd32": "Amiga CD32", "arduboy": "Arduboy", "atari7800": "Atari 7800",
    "atomiswave": "Atomiswave", "bbcmicro": "BBC Micro", "cannonball": "Cannonball",
    "cavestory": "Cave Story", "chailove": "ChaiLove", "channelf": "Channel F",
    "consolearcade": "Console Arcade", "cps": "CPS", "cps1": "CPS-1", "cps2": "CPS-2", "cps3": "CPS-3",
    "crvision": "CreatiVision", "daphne": "Daphne", "easyrpg": "EasyRPG", "fds": "Famicom Disk",
    "flashback": "Flashback", "intellivision": "Intellivision", "j2me": "Java ME",
    "lcdgames": "LCD Games", "lowresnx": "LowRes NX", "lutro": "Lutro", "mame": "MAME",
    "megaduck": "Mega Duck", "mess": "MESS", "moto": "MO/TO", "multivision": "Multivision",
    "naomi": "NAOMI", "ngpc": "NGP Color", "pcarcade": "PC Arcade", "pico8": "PICO-8",
    "scummvm": "ScummVM", "sg-1000": "SG-1000", "stv": "Titan Video", "supervision": "Supervision",
    "tic80": "TIC-80", "triforce": "Triforce", "uzebox": "Uzebox", "vircon32": "Vircon32",
    "wasm4": "WASM-4", "zmachine": "Z-machine", "zxspectrum": "ZX Spectrum",
}
MAX_SHORT_NAME = 16

# Display name: what people call the system, shown in the main screen's system column.
# Made from the full name by dropping brackets and the maker (see convert.display_name),
# except for ids in DISPLAY_NAMES. Ids in KEEP_MAKER keep the maker because it is part of the name.
MAX_DISPLAY_NAME = 22
DISPLAY_MAKER_PREFIXES = MAKER_PREFIXES + ("Atari ", "Commodore ", "Apple ")
KEEP_MAKER = {"n64", "n64dd", "nds", "ndsi", "n3ds", "switch", "c64", "pet", "plus4", "vic20",
              "atari2600", "atari5200", "atari7800", "atari800", "atarist", "atarixe", "atarijaguar",
              "atarijaguarcd", "atarilynx"}
DISPLAY_NAMES = {
    "android": "Android games",
    "nes": "NES", "snes": "Super Nintendo", "gb": "Game Boy", "gbc": "Game Boy Color",
    "gba": "Game Boy Advance", "n64": "Nintendo 64", "nds": "Nintendo DS", "megadrive": "Mega Drive",
    "psx": "PlayStation", "psp": "PSP", "ps2": "PlayStation 2", "gc": "GameCube", "arcade": "Arcade",
    "3do": "3DO", "arcadia": "Arcadia 2001", "arduboy": "Arduboy", "atari7800": "Atari 7800",
    "atomiswave": "Atomiswave", "bbcmicro": "BBC Micro", "cannonball": "Cannonball",
    "cavestory": "Cave Story", "chailove": "ChaiLove", "consolearcade": "Console Arcade",
    "cps": "CPS", "cps1": "CPS-1", "cps2": "CPS-2", "cps3": "CPS-3", "daphne": "Daphne",
    "dos": "DOS", "easyrpg": "EasyRPG", "ebook": "Book Reader", "epic": "Epic Games Store",
    "fds": "Famicom Disk System", "flash": "Adobe Flash", "flashback": "Flashback",
    "gameandwatch": "Game and Watch", "genesismsu": "Genesis MSU", "j2me": "Java ME",
    "lcdgames": "LCD Games", "lowresnx": "LowRes NX", "lutro": "Lutro", "mame": "MAME",
    "megaduck": "Mega Duck", "mess": "MESS", "moto": "MO/TO", "naomigd": "NAOMI GD-ROM",
    "ngpc": "Neo Geo Pocket Color", "pc": "IBM PC", "pc88": "PC-8800", "pc98": "PC-9800",
    "pcarcade": "PC Arcade", "pico8": "PICO-8", "pokemini": "Pokemon Mini", "samcoupe": "SAM Coupe",
    "scummvm": "ScummVM", "sega32x": "Mega Drive 32X", "snesmsu1": "SNES MSU-1", "stv": "Titan Video",
    "steam": "Steam", "tic80": "TIC-80", "triforce": "Triforce", "uzebox": "Uzebox",
    "vircon32": "Vircon32", "wasm4": "WASM-4", "zmachine": "Z-machine", "pet": "Commodore PET", "adam": "Coleco Adam", "pico": "Sega Pico", "intellivision": "Intellivision",
    "ps3": "PlayStation 3", "pspminis": "PSP Minis", "sgb": "Super Game Boy",
}

# Settings › Library › System order › Maker & year (owner, 2026-10-23): who made the system and the
# year it first went on sale anywhere, from well-known facts. Only hardware and operating systems
# have one; game engines, fantasy consoles, stores, media players and multi-system emulators are
# left out on purpose and go last, by name. The maker is the name people know the system by
# (Philips for the Videopac, Panasonic for the first 3DO console).
MAKER_YEAR = {
    "3do": ("Panasonic", 1993), "adam": ("Coleco", 1983), "amiga": ("Commodore", 1985),
    "amigacd32": ("Commodore", 1993), "amstradcpc": ("Amstrad", 1984), "apple2": ("Apple", 1977),
    "arcadia": ("Emerson", 1982), "arduboy": ("Arduboy", 2016), "atari2600": ("Atari", 1977),
    "atari5200": ("Atari", 1982), "atari7800": ("Atari", 1986), "atari800": ("Atari", 1979),
    "atarijaguar": ("Atari", 1993), "atarijaguarcd": ("Atari", 1995), "atarilynx": ("Atari", 1989),
    "atarist": ("Atari", 1985), "atarixe": ("Atari", 1985), "atomiswave": ("Sammy", 2003),
    "bbcmicro": ("Acorn", 1981), "c64": ("Commodore", 1982), "cdimono1": ("Philips", 1991),
    "cdtv": ("Commodore", 1991), "channelf": ("Fairchild", 1976), "colecovision": ("Coleco", 1982),
    "cps": ("Capcom", 1988), "cps1": ("Capcom", 1988), "cps2": ("Capcom", 1993), "cps3": ("Capcom", 1996),
    "crvision": ("VTech", 1981), "dos": ("Microsoft", 1981), "dreamcast": ("Sega", 1998),
    "fds": ("Nintendo", 1986), "gameandwatch": ("Nintendo", 1980), "gamegear": ("Sega", 1990),
    "gb": ("Nintendo", 1989), "gba": ("Nintendo", 2001), "gbc": ("Nintendo", 1998), "gc": ("Nintendo", 2001),
    "gx4000": ("Amstrad", 1990), "intellivision": ("Mattel", 1979), "macintosh": ("Apple", 1984),
    "mastersystem": ("Sega", 1985), "megacd": ("Sega", 1991), "megadrive": ("Sega", 1988),
    "megaduck": ("Creatronic", 1993), "model2": ("Sega", 1993), "model3": ("Sega", 1996),
    "moto": ("Thomson", 1982), "msx": ("ASCII", 1983), "msx1": ("ASCII", 1983), "msx2": ("ASCII", 1985),
    "msxturbor": ("ASCII", 1990), "multivision": ("Tsukuda", 1983), "n3ds": ("Nintendo", 2011),
    "n64": ("Nintendo", 1996), "n64dd": ("Nintendo", 1999), "naomi": ("Sega", 1998), "naomi2": ("Sega", 2000),
    "naomigd": ("Sega", 2001), "nds": ("Nintendo", 2004), "ndsi": ("Nintendo", 2008), "neogeo": ("SNK", 1990),
    "neogeocd": ("SNK", 1994), "nes": ("Nintendo", 1983), "ngage": ("Nokia", 2003), "ngp": ("SNK", 1998),
    "ngpc": ("SNK", 1999), "odyssey2": ("Magnavox", 1978), "palm": ("Palm", 1996), "pc": ("IBM", 1981),
    "pc88": ("NEC", 1981), "pc98": ("NEC", 1982), "pcengine": ("NEC", 1987), "pcenginecd": ("NEC", 1988),
    "pcfx": ("NEC", 1994), "pet": ("Commodore", 1977), "pico": ("Sega", 1993), "plus4": ("Commodore", 1984),
    "pokemini": ("Nintendo", 2001), "ps2": ("Sony", 2000), "ps3": ("Sony", 2006), "psp": ("Sony", 2004),
    "pspminis": ("Sony", 2009), "psvita": ("Sony", 2011), "psx": ("Sony", 1994), "samcoupe": ("MGT", 1989),
    "satellaview": ("Nintendo", 1995), "saturn": ("Sega", 1994), "sega32x": ("Sega", 1994),
    "sg-1000": ("Sega", 1983), "sgb": ("Nintendo", 1994), "snes": ("Nintendo", 1990),
    "spectravideo": ("Spectravideo", 1983), "stv": ("Sega", 1994), "sufami": ("Bandai", 1996),
    "supergrafx": ("NEC", 1989), "supervision": ("Watara", 1992), "switch": ("Nintendo", 2017),
    "tg-cd": ("NEC", 1989), "to8": ("Thomson", 1986), "triforce": ("Namco, Sega, Nintendo", 2002),
    "type-x": ("Taito", 2004), "vc4000": ("Interton", 1978), "vectrex": ("GCE", 1982),
    "vic20": ("Commodore", 1980), "videopac": ("Philips", 1978), "virtualboy": ("Nintendo", 1995),
    "vsmile": ("VTech", 2004), "wii": ("Nintendo", 2006), "wiiu": ("Nintendo", 2012),
    "wiiware": ("Nintendo", 2008), "windows": ("Microsoft", 1985), "windows3x": ("Microsoft", 1990),
    "windows9x": ("Microsoft", 1995), "wonderswan": ("Bandai", 1999), "wonderswancolor": ("Bandai", 2000),
    "x1": ("Sharp", 1982), "x68000": ("Sharp", 1987), "xbox": ("Microsoft", 2001),
    "xbox360": ("Microsoft", 2005), "zx81": ("Sinclair", 1981), "zxspectrum": ("Sinclair", 1982),
}

# Settings › Home screen › System info (owner, 2026-10-24): what kind of thing each system with a maker
# and year (MAKER_YEAR, same ids) is, and the year its maker stopped making it, for the line
# "Nintendo · Home console · 1990-2003". kind is one of KINDS. end_year is the last year of production
# in the last region (the maker's own, not a licensee's); None where the system is still made or where
# no reliable year exists: never a guess, the reason stands in the comment. Source for every entry is
# its Wikipedia page, https://en.wikipedia.org/wiki/<page>, checked 2026-10-24. The download stores
# (NO_KIND: wiiware, pspminis) are not hardware, so they keep their maker and year but have no kind.
# IN_PRODUCTION lists the systems still made (the info line then reads "2017-present"); a system in
# neither IN_PRODUCTION nor with an end year is unknown (the line shows the launch year alone).
KINDS = ("console", "handheld", "arcade", "computer", "add-on", "os")
LAST_END_YEAR = 2026
NO_KIND = {"wiiware", "pspminis"}
IN_PRODUCTION = {"arduboy", "macintosh", "switch", "windows"}
KIND_END_YEAR = {
    "3do": ("console", 1996),  # 3DO_Interactive_Multiplayer
    "adam": ("computer", 1985),  # Coleco_Adam
    "amiga": ("computer", 1994),  # Amiga (Commodore's bankruptcy; Escom made a few until 1996)
    "amigacd32": ("console", 1994),  # Amiga_CD32
    "amstradcpc": ("computer", 1990),  # Amstrad_CPC
    "apple2": ("computer", 1993),  # Apple_II
    "arcadia": ("console", 1984),  # Arcadia_2001 (Emerson Arcadia 2001)
    "arduboy": ("handheld", None),  # Arduboy (still made)
    "atari2600": ("console", 1992),  # Atari_2600
    "atari5200": ("console", 1984),  # Atari_5200
    "atari7800": ("console", 1992),  # Atari_7800
    "atari800": ("computer", 1992),  # Atari_8-bit_computers (the whole 8-bit family)
    "atarijaguar": ("console", 1996),  # Atari_Jaguar
    "atarijaguarcd": ("add-on", 1996),  # Atari_Jaguar_CD
    "atarilynx": ("handheld", 1995),  # Atari_Lynx
    "atarist": ("computer", 1993),  # Atari_ST
    "atarixe": ("computer", 1992),  # Atari_8-bit_computers (the whole 8-bit family)
    "atomiswave": ("arcade", None),  # Sammy_Atomiswave (arcade board: no maker date for the end of production)
    "bbcmicro": ("computer", 1994),  # BBC_Micro
    "c64": ("computer", 1994),  # Commodore_64
    "cdimono1": ("console", 1999),  # CD-i
    "cdtv": ("console", None),  # Commodore_CDTV (no end date in the source)
    "channelf": ("console", 1983),  # Fairchild_Channel_F
    "colecovision": ("console", 1985),  # ColecoVision
    "cps": ("arcade", 2003),  # CP_System (the whole family, so the last board: CPS-2)
    "cps1": ("arcade", 1995),  # CP_System (infobox discontinued)
    "cps2": ("arcade", 2003),  # CP_System_II (infobox discontinued)
    "cps3": ("arcade", 1999),  # CP_System_III (infobox discontinued)
    "crvision": ("console", 1986),  # VTech_CreatiVision
    "dos": ("os", None),  # MS-DOS (operating system: no manufacturing end)
    "dreamcast": ("console", 2001),  # Dreamcast
    "fds": ("add-on", 1993),  # Famicom_Disk_System (the drive; disk software went on until 2003)
    "gameandwatch": ("handheld", 1991),  # Game_%26_Watch
    "gamegear": ("handheld", 1997),  # Game_Gear (Sega; Majesco's later run is not Sega's)
    "gb": ("handheld", 2003),  # Game_Boy
    "gba": ("handheld", 2010),  # Game_Boy_Advance
    "gbc": ("handheld", 2003),  # Game_Boy_Color
    "gc": ("console", 2007),  # GameCube
    "gx4000": ("console", 1991),  # Amstrad_GX4000
    "intellivision": ("console", 1990),  # Intellivision (INTV Corporation, after Mattel sold it)
    "macintosh": ("computer", None),  # Macintosh (still made)
    "mastersystem": ("console", 1996),  # Master_System (Europe, the last region)
    "megacd": ("add-on", 1996),  # Sega_CD
    "megadrive": ("console", 1997),  # Sega_Genesis (Sega; Majesco's later run is not Sega's)
    "megaduck": ("handheld", None),  # Mega_Duck (no end date in the source)
    "model2": ("arcade", None),  # List_of_Sega_arcade_system_boards (arcade board: no maker date for the end of production)
    "model3": ("arcade", None),  # List_of_Sega_arcade_system_boards (arcade board: no maker date for the end of production)
    "moto": ("computer", None),  # Thomson_MO5 (Thomson MO and TO families: no single end date)
    "msx": ("computer", 1993),  # MSX (the turbo R, the last MSX)
    "msx1": ("computer", None),  # MSX (a standard, not a product line: no end date)
    "msx2": ("computer", None),  # MSX (a standard, not a product line: no end date)
    "msxturbor": ("computer", 1993),  # MSX
    "multivision": ("console", None),  # Othello_Multivision (no end date in the source)
    "n3ds": ("handheld", 2020),  # Nintendo_3DS
    "n64": ("console", 2002),  # Nintendo_64
    "n64dd": ("add-on", 2001),  # Nintendo_64DD
    "naomi": ("arcade", None),  # Sega_NAOMI (arcade board: no maker date for the end of production)
    "naomi2": ("arcade", None),  # Sega_NAOMI (arcade board: no maker date for the end of production)
    "naomigd": ("arcade", None),  # Sega_NAOMI (arcade board: no maker date for the end of production)
    "nds": ("handheld", None),  # Nintendo_DS (Nintendo has not disclosed when it stopped)
    "ndsi": ("handheld", None),  # Nintendo_DSi (Nintendo has not disclosed when it stopped)
    "neogeo": ("console", 1997),  # Neo_Geo_(system) (the AES home console; the MVS arcade board went on until 2004)
    "neogeocd": ("console", 1997),  # Neo_Geo_CD
    "nes": ("console", 2003),  # Nintendo_Entertainment_System (Famicom in Japan, the last region)
    "ngage": ("handheld", 2005),  # N-Gage_(device)
    "ngp": ("handheld", 2001),  # Neo_Geo_Pocket (Japan, the last region)
    "ngpc": ("handheld", 2001),  # Neo_Geo_Pocket (Japan, the last region; one page for both)
    "odyssey2": ("console", 1984),  # Magnavox_Odyssey_2
    "palm": ("handheld", 2011),  # Palm_(PDA)
    "pc": ("computer", 1987),  # IBM_Personal_Computer (the original IBM PC line)
    "pc88": ("computer", None),  # PC-8800_series (no end date in the source)
    "pc98": ("computer", 2003),  # PC-9800_series
    "pcengine": ("console", 1994),  # TurboGrafx-16
    "pcenginecd": ("add-on", None),  # TurboGrafx-16 (no end date in the source)
    "pcfx": ("console", 1998),  # PC-FX
    "pet": ("computer", 1982),  # Commodore_PET
    "pico": ("console", 2005),  # Sega_Pico (Japan, the last region)
    "plus4": ("computer", 1985),  # Commodore_Plus/4
    "pokemini": ("handheld", None),  # Pok%C3%A9mon_Mini (the only year given is marked citation needed)
    "ps2": ("console", 2013),  # PlayStation_2
    "ps3": ("console", 2017),  # PlayStation_3 (Japan, the last region)
    "psp": ("handheld", 2014),  # PlayStation_Portable
    "psvita": ("handheld", 2019),  # PlayStation_Vita
    "psx": ("console", 2006),  # PlayStation_(console)
    "samcoupe": ("computer", 1992),  # SAM_Coup%C3%A9
    "satellaview": ("add-on", 2000),  # Satellaview
    "saturn": ("console", 2000),  # Sega_Saturn (Japan, the last region)
    "sega32x": ("add-on", 1996),  # 32X
    "sg-1000": ("console", None),  # SG-1000 (the source gives only the release dates of its models)
    "sgb": ("add-on", 1998),  # Super_Game_Boy (Super Game Boy 2, Japan)
    "snes": ("console", 2003),  # Super_Nintendo_Entertainment_System (Super Famicom in Japan, the last region)
    "spectravideo": ("computer", None),  # Spectravideo (no end date in the source)
    "stv": ("arcade", None),  # List_of_Sega_arcade_system_boards (arcade board: no maker date for the end of production)
    "sufami": ("add-on", None),  # SuFami_Turbo (the source gives only the 1996 release)
    "supergrafx": ("console", None),  # PC_Engine_SuperGrafx (no end date in the source)
    "supervision": ("handheld", None),  # Watara_Supervision (no end date in the source)
    "switch": ("console", None),  # Nintendo_Switch (still made outside Europe)
    "tg-cd": ("add-on", None),  # TurboGrafx-16 (no end date in the source)
    "to8": ("computer", None),  # Thomson_MO5 (Thomson MO and TO families: no single end date)
    "triforce": ("arcade", None),  # List_of_Sega_arcade_system_boards (arcade board: no maker date for the end of production)
    "type-x": ("arcade", None),  # Taito_Type_X (arcade board: no maker date for the end of production)
    "vc4000": ("console", 1983),  # Interton_Video_Computer_4000
    "vectrex": ("console", 1984),  # Vectrex
    "vic20": ("computer", 1985),  # VIC-20
    "videopac": ("console", 1984),  # Magnavox_Odyssey_2 (Videopac is the Odyssey 2 in Europe)
    "virtualboy": ("console", 1996),  # Virtual_Boy
    "vsmile": ("console", None),  # V.Smile (the only year given is marked citation needed)
    "wii": ("console", 2013),  # Wii (Japan and Europe; a US date for 2014 is marked citation needed)
    "wiiu": ("console", 2017),  # Wii_U
    "windows": ("os", None),  # Microsoft_Windows (operating system: still made)
    "windows3x": ("os", None),  # Windows_3.0 (operating system: no manufacturing end)
    "windows9x": ("os", None),  # Windows_9x (operating system: no manufacturing end)
    "wonderswan": ("handheld", 2003),  # WonderSwan
    "wonderswancolor": ("handheld", 2003),  # WonderSwan_Color
    "x1": ("computer", 1987),  # Sharp_X1
    "x68000": ("computer", None),  # X68000 (the source gives 1999 or 2000)
    "xbox": ("console", 2006),  # Xbox_(console)
    "xbox360": ("console", 2016),  # Xbox_360
    "zx81": ("computer", 1984),  # ZX81
    "zxspectrum": ("computer", 1992),  # ZX_Spectrum (Amstrad, after it bought Sinclair)
}

# Short system names for the mixed lists (Recent, Favorites, Search): the right-hand column of a
# game row shows these in place of the year, so the person can tell which system a game is from
# (owner, 2026-10-23, option A). What players say: NES, SNES, GBA, PS1, MD for Mega Drive/Genesis.
# Every system has one, at most MAX_ABBREVIATION characters, no two alike (convert.py checks).
# The full table for review: docs/research/2026-10-23-system-abbreviations.md.
MAX_ABBREVIATION = 5
ABBREVIATION = {
    "android": "Droid",
    "3do": "3DO", "adam": "Adam", "amiga": "Amiga", "amigacd32": "CD32", "amstradcpc": "CPC",
    "apple2": "A2", "arcade": "ARC", "arcadia": "A2001", "arduboy": "Ardu", "atari2600": "2600",
    "atari5200": "5200", "atari7800": "7800", "atari800": "A800", "atarijaguar": "JAG",
    "atarijaguarcd": "JAGCD", "atarilynx": "LYNX", "atarist": "ST", "atarixe": "XE", "atomiswave": "AW",
    "bbcmicro": "BBC", "c64": "C64", "cannonball": "Cball", "cavestory": "Cave", "cdimono1": "CD-i",
    "cdtv": "CDTV", "chailove": "Chai", "channelf": "ChanF", "chip8": "CHIP8", "colecovision": "CV",
    "consolearcade": "CARC", "cps": "CPS", "cps1": "CPS1", "cps2": "CPS2", "cps3": "CPS3",
    "crvision": "CRV", "daphne": "Daph", "doom": "Doom", "dos": "DOS", "dreamcast": "DC",
    "easyrpg": "ERPG", "ebook": "Book", "epic": "Epic", "fba": "FBA", "fbneo": "FBNeo", "fds": "FDS",
    "flash": "Flash", "flashback": "FBack", "gameandwatch": "G&W", "gamegear": "GG", "gb": "GB",
    "gba": "GBA", "gbc": "GBC", "gc": "GC", "genesismsu": "MSU", "gx4000": "GX4K", "intellivision": "INTV",
    "j2me": "J2ME", "laserdisc": "LD", "lcdgames": "LCD", "lowresnx": "LRNX", "lutro": "Lutro",
    "macintosh": "Mac", "mame": "MAME", "mastersystem": "SMS", "megacd": "MCD", "megadrive": "MD",
    "megaduck": "Duck", "mess": "MESS", "model2": "M2", "model3": "M3", "moto": "MOTO", "msx": "MSX",
    "msx1": "MSX1", "msx2": "MSX2", "msxturbor": "MSXTR", "multivision": "OMV", "n3ds": "3DS",
    "n64": "N64", "n64dd": "64DD", "naomi": "NAOMI", "naomi2": "NAO2", "naomigd": "NAOGD", "nds": "DS",
    "ndsi": "DSi", "neogeo": "NEO", "neogeocd": "NEOCD", "nes": "NES", "ngage": "NGage", "ngp": "NGP",
    "ngpc": "NGPC", "odyssey2": "O2", "palm": "Palm", "pc": "PC", "pc88": "PC88", "pc98": "PC98",
    "pcarcade": "PCARC", "pcengine": "PCE", "pcenginecd": "PCECD", "pcfx": "PCFX", "pet": "PET",
    "pico": "Pico", "pico8": "PICO8", "plus4": "PLUS4", "pokemini": "PMini", "ports": "Ports", "ps2": "PS2",
    "ps3": "PS3", "psp": "PSP", "pspminis": "Minis", "psvita": "Vita", "psx": "PS1", "quake": "Quake",
    "rpgmaker": "RPGM", "samcoupe": "SAM", "satellaview": "BS-X", "saturn": "SAT", "scummvm": "SCUMM",
    "sega32x": "32X", "sg-1000": "SG1K", "sgb": "SGB", "snes": "SNES", "snesmsu1": "MSU-1",
    "spectravideo": "SVI", "steam": "Steam", "stv": "STV", "sufami": "SFT", "supergrafx": "SGX",
    "supervision": "WSV", "switch": "NSW", "symbian": "Symb", "tg-cd": "TGCD", "tic80": "TIC80",
    "to8": "TO8", "triforce": "TriF", "type-x": "TYPEX", "uzebox": "UZE", "vc4000": "VC4K",
    "vectrex": "VEC", "vic20": "VIC20", "videopac": "G7000", "videos": "Video", "vircon32": "V32",
    "virtualboy": "VB", "vpinball": "VPX", "vsmile": "VSML", "wasm4": "WASM4", "wii": "Wii", "wiiu": "WiiU",
    "wiiware": "WiiW", "windows": "Win", "windows3x": "Win3", "windows9x": "Win9x", "wonderswan": "WS",
    "wonderswancolor": "WSC", "x1": "X1", "x68000": "X68K", "xbox": "Xbox", "xbox360": "X360",
    "zmachine": "ZM", "zx81": "ZX81", "zxspectrum": "ZX",
}

# Three-character codes for the home screen's left edge (Settings › Appearance › Left edge › System codes,
# owner 2026-10-26): the 48 dp peek shows a system's code where it showed its count. What players say where
# it fits in three (NES, GBA, PS1, MD, N64, 3DS), else the first letters; the Super Nintendo is SNS (SFC is the
# Japanese name, SNES takes four). Every system has one, at most CODE_LENGTH characters, no two alike
# (convert.py checks). Recent, Favorites and Apps take REC, FAV and APP from the app's strings.
CODE_LENGTH = 3
CODE = {
    "android": "AND",
    "3do": "3DO", "adam": "ADM", "amiga": "AMI", "amigacd32": "CD3", "amstradcpc": "CPC",
    "apple2": "AP2", "arcade": "ARC", "arcadia": "A21", "arduboy": "ARD", "atari2600": "260",
    "atari5200": "520", "atari7800": "780", "atari800": "A80", "atarijaguar": "JAG",
    "atarijaguarcd": "JCD", "atarilynx": "LNX", "atarist": "AST", "atarixe": "AXE", "atomiswave": "AWV",
    "bbcmicro": "BBC", "c64": "C64", "cannonball": "CBL", "cavestory": "CAV", "cdimono1": "CDI",
    "cdtv": "CTV", "chailove": "CHA", "channelf": "CHF", "chip8": "CH8", "colecovision": "COL",
    "consolearcade": "CAR", "cps": "CPS", "cps1": "CP1", "cps2": "CP2", "cps3": "CP3",
    "crvision": "CRV", "daphne": "DAP", "doom": "DOM", "dos": "DOS", "dreamcast": "DC",
    "easyrpg": "ERP", "ebook": "EBK", "epic": "EPC", "fba": "FBA", "fbneo": "FBN", "fds": "FDS",
    "flash": "FLA", "flashback": "FLB", "gameandwatch": "G&W", "gamegear": "GG", "gb": "GB",
    "gba": "GBA", "gbc": "GBC", "gc": "GC", "genesismsu": "GMS", "gx4000": "GX4",
    "intellivision": "INT", "j2me": "J2M", "laserdisc": "LD", "lcdgames": "LCD", "lowresnx": "LRN",
    "lutro": "LUT", "macintosh": "MAC", "mame": "MAM", "mastersystem": "SMS", "megacd": "MCD",
    "megadrive": "MD", "megaduck": "MDK", "mess": "MES", "model2": "M2", "model3": "M3", "moto": "MOT",
    "msx": "MSX", "msx1": "MX1", "msx2": "MX2", "msxturbor": "MXR", "multivision": "OMV", "n3ds": "3DS",
    "n64": "N64", "n64dd": "64D", "naomi": "NAO", "naomi2": "NA2", "naomigd": "NGD", "nds": "NDS",
    "ndsi": "DSI", "neogeo": "NEO", "neogeocd": "NGC", "nes": "NES", "ngage": "NGE", "ngp": "NGP",
    "ngpc": "NPC", "odyssey2": "OD2", "palm": "PLM", "pc": "PC", "pc88": "P88", "pc98": "P98",
    "pcarcade": "PCA", "pcengine": "PCE", "pcenginecd": "PCD", "pcfx": "PFX", "pet": "PET",
    "pico": "PIC", "pico8": "PI8", "plus4": "PL4", "pokemini": "PKM", "ports": "PRT", "ps2": "PS2",
    "ps3": "PS3", "psp": "PSP", "pspminis": "PSM", "psvita": "VIT", "psx": "PS1", "quake": "QUK",
    "rpgmaker": "RPG", "samcoupe": "SAM", "satellaview": "BSX", "saturn": "SAT", "scummvm": "SCU",
    "sega32x": "32X", "sg-1000": "SG1", "sgb": "SGB", "snes": "SNS", "snesmsu1": "SMU",
    "spectravideo": "SVI", "steam": "STM", "stv": "STV", "sufami": "SFT", "supergrafx": "SGX",
    "supervision": "WSV", "switch": "NSW", "symbian": "SYM", "tg-cd": "TCD", "tic80": "T80",
    "to8": "TO8", "triforce": "TRI", "type-x": "TYX", "uzebox": "UZE", "vc4000": "VC4",
    "vectrex": "VEC", "vic20": "V20", "videopac": "VPC", "videos": "VID", "vircon32": "V32",
    "virtualboy": "VB", "vpinball": "VPX", "vsmile": "VSM", "wasm4": "WA4", "wii": "WII", "wiiu": "WIU",
    "wiiware": "WWR", "windows": "WIN", "windows3x": "W3X", "windows9x": "W9X", "wonderswan": "WS",
    "wonderswancolor": "WSC", "x1": "X1", "x68000": "X68", "xbox": "XBX", "xbox360": "X36",
    "zmachine": "ZMA", "zx81": "Z81", "zxspectrum": "ZXS",
}

# Systems whose games are installed apps, not files (RULES R45, owner 2026-10-27). They have no
# folders, extensions or emulators and carry `"source": "apps"` in the file. Name, display name,
# abbreviation, code, colour and cover ratio come from the tables above like any other system.
APPS_SYSTEMS = {"android": "Android games"}

# Installed app package name -> the system it belongs to (`android` or `ports`), shipped as `knownApps`.
# Only packages a file in this repo states; never a guess. The person's own choice in the app wins.
KNOWN_APP_SYSTEMS = {"android", "ports"}
KNOWN_APPS = {
    # Delta Touch, the Doom engines app: docs/research/2026-10-23-launcher-comparison.md section 8
    # (native Android source ports), Play Store id in its reference 21p.
    "com.opentouchgaming.deltatouch": "ports",
}

# The tested systems come first on the main screen, in this order; the rest follow alphabetically.
FIRST_ORDER = ["snes", "gba", "psx", "psp", "nds", "gc", "megadrive", "nes", "gb", "gbc", "n64", "ps2",
               "arcade"]

# Builds of an ES-DE emulator under another package name that ES-DE's find rules do not list, by
# find-rule key (lowercase): (package, activity), added after ES-DE's own entries as variants. Each
# keeps the emulator's launch, so only builds that start games the same way belong here. The
# dual-screen builds (public issue #1), package and activity read from their sources:
# Eden Duo, github.com/igawa6/eden-duo src/android/app/duo.gradle (applicationId) and its manifest;
# PPSSPP Duo, github.com/mkaflowski/PPSSPP-Duo android/build.gradle.kts (the "duo" flavour).
EXTRA_VARIANTS = {
    "eden": [("dev.igawa6.edenduo", "org.yuzu.yuzu_emu.activities.EmulationActivity")],
    "ppsspp": [("org.ppsspp.ppssppduo", "org.ppsspp.ppsspp.PpssppActivity")],
}

# Emulators no source gives in a form the converter reads, written by hand and checked by
# common.check_launch. The Steam apps (owner approved 2026-10-23; research
# docs/research/2026-10-23-launcher-comparison.md section 7.1): a Steam game is a small ".steam" text
# file holding the Steam app id (GameNative's "Export for frontend"), and the app gets that id
# (game mode "steam_app_id"). ES-DE gives GameNative and GameHub Lite as %INJECT%=%ROM% commands,
# which stay skipped in general (an %INJECT% of another file would be guessed); its GameHub Lite
# command also sends localGameId and lists repackaged builds under other apps' names, which section
# 7.1 does not. DroidDeck is in neither source. Each is added to its systems after the sources'
# emulators, in this order; the id must not be one the sources already give.
HAND_EMULATORS = [
    {
        # Explicit intent to .MainActivity; app_id is read as an Int (getIntExtra), game_source names the store.
        "id": "gamenative", "name": "GameNative", "systems": ["steam"],
        "package": "app.gamenative", "activity": "app.gamenative.MainActivity",
        "action": "app.gamenative.LAUNCH_GAME",
        "game": {"mode": "steam_app_id", "target": "extra", "key": "app_id", "type": "int"},
        "extras": {"game_source": "STEAM"},
        "accepts": ["steam"],
    },
    {
        # Patched GameSir GameHub; the same activity under both package names it ships as.
        "id": "gamehub-lite", "name": "GameHub Lite", "systems": ["steam"],
        "package": "gamehub.lite", "activity": "com.xj.landscape.launcher.ui.gamedetail.GameDetailActivity",
        "variants": [("emuready.gamehub.lite", "com.xj.landscape.launcher.ui.gamedetail.GameDetailActivity")],
        "action": "gamehub.lite.LAUNCH_GAME",
        "game": {"mode": "steam_app_id", "target": "extra", "key": "steamAppId", "type": "string"},
        "extras": {"autoStartGame": True},
        "accepts": ["steam"],
    },
    {
        # Its documented deep link (the game must be in its library); no activity is named, Android
        # picks DroidDeck's activity for the link inside the package.
        "id": "droiddeck", "name": "DroidDeck", "systems": ["steam"],
        "package": "com.droiddeck.launcher",
        "action": "android.intent.action.VIEW",
        "game": {"mode": "steam_app_id", "target": "data", "template": "droiddeck://game/{id}"},
        "accepts": ["steam"],
    },
]

# Extensions of its systems a source emulator cannot open: its "accepts" becomes every other extension
# of its systems (a hand-kept emulator says what it accepts in its own "accepts"). Each must be an
# extension of one of the emulator's systems (checked). WinNative takes a file path (ES-DE's
# shortcut_path=%ROM%): a .steam file holds only a Steam app id, which only the Steam apps start;
# its .desktop, .epic, .gog, .amazon, .pcgame and .app files stay its own. Owner 2026-10-23.
NOT_ACCEPTED = {"winnative": ["steam"]}

# Emulators whose file types are read from Daijisho (audit item 20): their "accepts" is the union of the
# acceptedFilenameRegex of the named players (sources/daijisho/<Platform>.json, one player or more for each of
# the emulator's systems; checked), within their systems' extensions. A game of another type is then not
# offered to them nor recommended for them (EmulatorResolver, Play with). Every other emulator takes every
# extension of its systems, as before: a list is named here only where Daijisho's list is the emulator's own
# (it matches the emulator's documented formats, or differs from the system's other players), never where it
# is the platform's list copied to every player or where it leaves out a format the emulator is known to
# open. Left out on purpose, so they stay "anything": the PS1 standalones Daijisho gives DuckStation's list
# (ePSXe, FPse, ARMSX1, EmuCoreR) and pcsx_rearmed (its list misses iso and exe); the PS2 standalones (no
# .cso for the PCSX2-based NetherSX2, AetherSX2 and ARMSX2) and Play!; every PSP and Switch emulator (one copied
# list each, refusing only archives or .nsz/.xcz, which would leave those files with no emulator); Flycast,
# Dolphin and the Mega CD, PC Engine CD and Azahar-line 3DS emulators (a system without a Daijisho list, or
# compressed 3DS files the list does not name). The converter refuses a list that leaves a file type of a
# system with no emulator at all.
ACCEPTS_FROM_DAIJISHO = {
    # PS1 (SonyPlayStation.json). DuckStation's list is its own open-file filter (cue/bin, img, iso, chd, ecm,
    # mds, pbp, m3u, exe/psexe, psf); SwanStation and GoOseStation are DuckStation forks with the same list.
    # Beetle PSX needs a cue, toc or ccd sheet, never a bare .bin/.iso (libretro's core info says the same).
    "duckstation": ["psx - Duckstation"],
    "retroarch-duckstation": ["psx - RetroArch - duckstation"],
    "retroarch-swanstation": ["psx - RetroArch - swanstation"],
    "retroarch-goosestation": ["psx - RetroArch - goosestation"],
    "retroarch-mednafen-psx": ["psx - Retroarch - beetle psx"],
    "retroarch-mednafen-psx-hw": ["psx - Retroarch - beetle psx hw"],
    # PS2 (SonyPlayStation2.json): the PCSX2-based cores, one list naming .cso; no .m3u, .ciso, .isz.
    "retroarch-pcsx2": ["ps2 - RetroArch - LRPS2"],
    "retroarch-pcee2": ["ps2 - RetroArch - pcee2"],
    "retroarch-armsx2": ["ps2 - RetroArch - ARMSX2"],
    # Saturn (SegaSaturn.json; ST-V in SegaTitanVideo.json for Beetle Saturn). Each player has its own list: Saturn.emu
    # and Beetle Saturn take sheets (cue, ccd, toc), chd and m3u, no bare .bin/.iso; Ymir no .bin, .toc or .zip;
    # YabaSanshiro no .m3u or .toc.
    "saturn-emu": ["saturn - Saturn.EMU"],
    "retroarch-mednafen-saturn": ["saturn - RetroArch - beetle saturn", "stv - RetroArch - beetle saturn"],
    "retroarch-ymir": ["saturn - RetroArch - emir"],
    "retroarch-yabasanshiro": ["saturn - RetroArch - yabasanshiro"],
    "retroarch-yabause": ["saturn - RetroArch - yabause"],
    # Dreamcast (SegaDreamcast.json; Reicast's Atomiswave in Atomiswave.json). Redream: no .iso; Reicast: no .iso or
    # .m3u; EmuCoreH: gdi, cdi, chd, cue and m3u only.
    "redream": ["dreamcast - Redream"],
    "com-reicast-emulator": ["dreamcast - Reicast", "atomiswave - com.reicast.emulator"],
    "com-sbro-emucoreh": ["dreamcast - EmuCoreH"],
    # GameCube and Wii (NintendoGameCube.json, NintendoWii.json, NintendoWiiWare.json): the older Dolphin forks
    # (MMJR, Ishiiruka, PrimeHack) take no .wia and no .json; only PrimeHack's Wii player takes .m3u. Data
    # splits PrimeHack in two (a GameCube and a Wii launch), so each takes its own system's player.
    "dolphin-mmjr": ["gc - Dolphin org.mm.jr fork", "wii - Dolphin org.mm.jr fork"],
    "dolphin-mmjr2": ["gc - Dolphin mmjr", "wii - Dolphin mmjr"],
    "org-mm-j": ["gc - Dolphin org.mm.j fork", "wii - Dolphin org.mm.j fork", "wiiware - Dolphin org.mm.j fork"],
    "org-dolphinemu-mmjr3": ["gc - Dolphin mmjr3", "wii - Dolphin mmjr3", "wiiware - Dolphin mmjr3"],
    "org-dolphin-ishiirukadark": ["gc - Dolphin Ishiiruka", "wii - Dolphin Ishiiruka", "wiiware - Dolphin Ishiiruka"],
    "org-dolphinemu-primehack": ["gc - Dolphin (Primehack)"],
    "org-shiiion-primehack": ["gc - Dolphin (Primehack - Shiiion)"],
    "org-dolphinemu-primehack-2": ["wii - Dolphin (Primehack)"],
    "org-shiiion-primehack-2": ["wii - Dolphin (Primehack - Shiiion)"],
    # 3DS (Nintendo3DS.json): the Citra line opens 3ds, cci, cxi, 3dsx, app, axf and elf; a .cia must be installed
    # first, and the compressed .zcci, .zcxi and .z3dsx came later with Azahar.
    "citra": ["3ds - Citra Nightly"],
    "citra-canary": ["3ds - Citra Canary"],
    "citra-mmj": ["3ds - org.citra.emu"],
    "mandarine": ["3ds - Mandarine"],
    "io-github-borked3ds-android": ["3ds - Borked 3DS"],
    "org-gamerytb-lemonade-canary": ["3ds - Lemonade Alpha"],
    "retroarch-citra": ["3ds - RetroArch - citra"],
    "retroarch-panda3ds": ["3ds - RetroArch - panda3ds"],
}

# Files a "uri" emulator opens only by file path, never by content URI; the app passes the path for these
# when it knows it (a USB drive has none, so the URI is passed as before) and the URI for every other file.
# DuckStation's playlist loader fails on a content URI ("Failed to open CD image ... .m3u: errno 2", Thor,
# 2026-10-27) though it opens a .chd by URI; the .m3u must stay the target so discs can be switched.
GAME_PATH_EXTENSIONS = {"duckstation": ["m3u"]}

# A system's whole emulator order where the merge rules' order is not wanted; must name exactly the
# system's emulators. Steam: the Steam apps that start a .steam file first, GameNative first (owner
# 2026-10-23), then WinNative for .desktop files.
SYSTEM_EMULATOR_ORDER = {"steam": ["gamenative", "gamehub-lite", "droiddeck", "winnative"]}

# Settings › Appearance › Accent color › By system (owner, 2026-10-25): the colour each system is known by,
# "RRGGBB". Hardware, logo or box colour, never a name; where each comes from is listed in
# docs/history/2026-10-25-accent-by-system.md. A system not here has none and the accent falls back.
SYSTEM_COLORS = {
    "android": "3DDC84",  # Android's own green (the robot logo)
    "3do": "E4572E", "adam": "8B5A2B", "amiga": "E2231A", "amigacd32": "2D5DA8", "amstradcpc": "D2402B",
    "apple2": "4FA83D", "arcadia": "D9822B", "arduboy": "E5322D", "atari2600": "8A5A32", "atari5200": "D8332A",
    "atari7800": "C0392B", "atari800": "C9B48A", "atarijaguar": "C4161C", "atarijaguarcd": "D9A441",
    "atarilynx": "F28C28", "atarist": "4A9B5C", "atarixe": "7A8FA6", "atomiswave": "E86A33", "bbcmicro": "D0312D",
    "c64": "6C5EB5", "cdimono1": "1B75BC", "cdtv": "6B7C8F", "colecovision": "D1202E", "cps": "D2232A",
    "cps1": "D2232A", "cps2": "D2232A", "cps3": "D2232A", "dos": "A8A8A8", "dreamcast": "F26B21", "fds": "E3A21A",
    "gameandwatch": "D9B44A", "gamegear": "2F5FB3", "gb": "9BBC0F", "gba": "5A4FCF", "gbc": "35B3A8", "gc": "6A5ACD",
    "intellivision": "C97B2D", "macintosh": "C9C3B0", "mastersystem": "E0242B", "megacd": "00A0C8",
    "megadrive": "0060A8", "megaduck": "F4C20D", "msx": "DA2127", "msx1": "DA2127", "msx2": "DA2127",
    "msxturbor": "DA2127", "n3ds": "CE181E", "n64": "2FA84F", "n64dd": "1B6EC2", "naomi": "1E73BE",
    "naomi2": "1E73BE", "naomigd": "1E73BE", "model2": "0060A8", "model3": "0060A8", "stv": "E0242B",
    "triforce": "6A5ACD", "type-x": "1D4E9E", "nds": "8FA3B5", "ndsi": "3B6FB6", "neogeo": "E8B310",
    "neogeocd": "C98B1A", "ngp": "5E6A7A", "ngpc": "00A0B0", "nes": "C8352E", "ngage": "4C9A2A", "odyssey2": "6B4A2E",
    "videopac": "D0462B", "channelf": "B8651B", "pc88": "2E5CA8", "pc98": "1F7A8C", "pcengine": "E4402B",
    "pcenginecd": "3B6EA8", "pcfx": "6E7B8B", "pico8": "FF004D", "tic80": "6C4AB6", "pet": "6A5E9E",
    "plus4": "7E8AA0", "pokemini": "4F7FC0", "ps2": "2B54A5", "ps3": "C8C8D0", "psp": "3D7EBF", "pspminis": "3D7EBF",
    "psvita": "1E90E0", "psx": "8C8C94", "saturn": "2B6CB0", "sega32x": "D9232D", "sg-1000": "E8591F",
    "sgb": "8E7CC3", "snes": "7B5EA7", "steam": "1B6AA5", "supergrafx": "A32638", "switch": "E60012",
    "tg-cd": "F58220", "vectrex": "4CAF50", "virtualboy": "E5262B", "vsmile": "3DAE2B", "wii": "00A0D6",
    "wiiware": "00A0D6", "wiiu": "009AC7", "windows": "0078D4", "windows3x": "3F6FD1", "windows9x": "008080",
    "wonderswan": "3C8DBC", "wonderswancolor": "E36C8F", "x1": "D8261D", "x68000": "B0B0B8", "xbox": "107C10",
    "xbox360": "8DC63F", "zx81": "B7B7B7", "zxspectrum": "E0302F", "vic20": "8C6D46",
}
