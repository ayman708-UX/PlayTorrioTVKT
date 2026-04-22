package com.playtorrio.tv.data.iptv

import androidx.compose.ui.graphics.Color

/**
 * A "branded" channel that the user can pick from a curated grid. We then
 * fan out across their verified portals, fetch live channels matching any
 * of [keywords], and run an alive check on the resulting URLs.
 *
 * `keywords` are case-insensitive substring matches against the channel name
 * returned by Xtream `get_live_streams`. Any match wins, unless the name
 * also contains a string in [exclude] (used to filter false positives).
 *
 * Order matters for the UI grid — earlier entries render first.
 */
data class HardcodedChannel(
    val id: String,
    val name: String,
    val short: String,
    val keywords: List<String>,
    val gradient: List<Color>,
    val exclude: List<String> = emptyList(),
)

object HardcodedChannels {
    private val SoccerGrad = listOf(Color(0xFF7C3AED), Color(0xFF22D3EE))
    private val UsSportGrad = listOf(Color(0xFFEF4444), Color(0xFFF59E0B))
    private val WrestlingGrad = listOf(Color(0xFFB91C1C), Color(0xFF1F2937))
    private val MotorGrad = listOf(Color(0xFFDC2626), Color(0xFF111827))
    private val FightGrad = listOf(Color(0xFFF97316), Color(0xFF7C2D12))
    private val UkSportGrad = listOf(Color(0xFF1D4ED8), Color(0xFF22C55E))
    private val NewsGrad = listOf(Color(0xFF0EA5E9), Color(0xFF1E293B))
    private val MovieGrad = listOf(Color(0xFFEC4899), Color(0xFF8B5CF6))
    private val KidsGrad = listOf(Color(0xFFFBBF24), Color(0xFFF472B6))
    private val MusicGrad = listOf(Color(0xFF06B6D4), Color(0xFFA855F7))
    private val DocGrad = listOf(Color(0xFF14B8A6), Color(0xFF0F766E))
    private val EntGrad = listOf(Color(0xFFF59E0B), Color(0xFFDB2777))
    private val ArabicGrad = listOf(Color(0xFF059669), Color(0xFF064E3B))

    val all: List<HardcodedChannel> = listOf(
        // ── Combat sports ───────────────────────────────────────────────
        HardcodedChannel(
            id = "ufc",
            name = "UFC",
            short = "UFC",
            keywords = listOf(
                "ufc", "ufc fight pass", "ufc ppv", "ufc on espn",
                "ufc fight night", "ufc 3", "ufc apex", "ufc on abc",
            ),
            gradient = FightGrad,
        ),
        HardcodedChannel(
            id = "wwe",
            name = "WWE",
            short = "WWE",
            keywords = listOf(
                "wwe", "wwe network", "wwe ppv", "wwe raw", "wwe smackdown",
                "wwe nxt", "wwe premium", "wrestlemania", "summerslam", "royal rumble",
                "money in the bank", "survivor series",
            ),
            gradient = WrestlingGrad,
        ),
        HardcodedChannel(
            id = "aew",
            name = "AEW",
            short = "AEW",
            keywords = listOf(
                "aew", "all elite wrestling", "aew dynamite", "aew rampage",
                "aew collision", "aew revolution", "aew double or nothing",
                "aew full gear",
            ),
            gradient = WrestlingGrad,
        ),
        HardcodedChannel(
            id = "boxing",
            name = "Boxing",
            short = "BOX",
            keywords = listOf(
                "boxing", "ppv box", "fight night", "fite tv", "fite",
                "matchroom", "top rank", "premier boxing", "pbc ", "queensberry",
                "espn boxing", "showtime boxing", "boxnation", "fight network",
                "dazn boxing", "boxing nation", "golden boy", "wbo", "wbc", "ibf", "wba",
                "world boxing", "championship boxing", "super middleweight", "heavyweight",
                "sky sports boxing",
            ),
            exclude = listOf("box office", "xbox", "boxset", "box set", "music box", "kids box"),
            gradient = FightGrad,
        ),
        HardcodedChannel(
            id = "bellator",
            name = "Bellator MMA",
            short = "BLR",
            keywords = listOf("bellator", "bellator mma", "pfl ", "pfl mma", "professional fighters league"),
            gradient = FightGrad,
        ),
        HardcodedChannel(
            id = "ppv_events",
            name = "PPV Events",
            short = "PPV",
            keywords = listOf(" ppv", "ppv ", "pay per view", "pay-per-view"),
            gradient = FightGrad,
        ),
        HardcodedChannel(
            id = "one_championship",
            name = "ONE Championship",
            short = "ONE",
            keywords = listOf("one championship", "one fc", "one mma", "one martial arts"),
            gradient = FightGrad,
        ),

        // ── Motorsport ──────────────────────────────────────────────────
        HardcodedChannel(
            id = "f1",
            name = "Formula 1",
            short = "F1",
            keywords = listOf(
                "f1 tv", "formula 1", "formula one",
                "sky f1", "skysports f1", "sky sport f1", "sky sports f1",
                "ssf1", "ss f1", "espn f1", "f1 race", "grand prix", "gp race",
                "f1 qualifying", "formula 1 grand prix",
            ),
            gradient = MotorGrad,
        ),
        HardcodedChannel(
            id = "motogp",
            name = "MotoGP",
            short = "GP",
            keywords = listOf("motogp", "moto gp", "moto-gp", "moto2", "moto3", "motogp race"),
            gradient = MotorGrad,
        ),
        HardcodedChannel(
            id = "nascar",
            name = "NASCAR",
            short = "NSC",
            keywords = listOf("nascar", "nascar cup", "daytona 500", "nascar xfinity"),
            gradient = MotorGrad,
        ),
        HardcodedChannel(
            id = "indycar",
            name = "IndyCar",
            short = "INDY",
            keywords = listOf("indycar", "indy car", "indy 500", "indianapolis 500"),
            gradient = MotorGrad,
        ),
        HardcodedChannel(
            id = "wrc",
            name = "WRC Rally",
            short = "WRC",
            keywords = listOf("wrc", "world rally", "rallytv", "rally tv", "rally championship"),
            gradient = MotorGrad,
        ),
        HardcodedChannel(
            id = "superbike",
            name = "Superbike / WSBK",
            short = "WSBK",
            keywords = listOf("wsbk", "superbike", "world superbike", "worldsbk"),
            gradient = MotorGrad,
        ),

        // ── Soccer / Football ──────────────────────────────────────────
        HardcodedChannel(
            id = "bein_sports",
            name = "beIN Sports",
            short = "BEIN",
            keywords = listOf(
                "bein sport", "bein sports", "beinsports", "bein ",
                "bein 1", "bein 2", "bein 3", "bein hd", "bein max",
                "bein sports arabia", "bein sport arabic",
            ),
            gradient = SoccerGrad,
        ),
        HardcodedChannel(
            id = "sky_sports",
            name = "Sky Sports",
            short = "SKY",
            keywords = listOf(
                "sky sport", "sky sports", "skysports", "sky sports main event",
                "sky sports premier league", "sky sports football", "sky sports action",
                "sky sports arena", "sky sports cricket", "sky sports golf",
                "sky sports f1",
            ),
            exclude = listOf("sky sports news"),
            gradient = UkSportGrad,
        ),
        HardcodedChannel(
            id = "tnt_sports",
            name = "TNT Sports",
            short = "TNT",
            keywords = listOf(
                "tnt sport", "tnt sports", "bt sport", "btsport",
                "tnt sports 1", "tnt sports 2", "tnt sports 3", "tnt sports 4",
                "bt sport 1", "bt sport 2", "bt sport 3",
            ),
            gradient = UkSportGrad,
        ),
        HardcodedChannel(
            id = "champions_league",
            name = "Champions League",
            short = "UCL",
            keywords = listOf(
                "champions league", "uefa champions", "ucl ", " ucl ",
                "uefa europa", "europa league", "conference league", "uecl",
                "bein champions", "bein ucl", "tnt sports ucl",
                "sky sports ucl", "cbs sports ucl", "paramount+ ucl",
                "dazn champions", "canal+ champions",
            ),
            gradient = SoccerGrad,
        ),
        HardcodedChannel(
            id = "premier_league",
            name = "Premier League",
            short = "EPL",
            keywords = listOf(
                "premier league", "epl ", "barclays premier", " bpl ",
                "sky sports pl", "bein epl", "nbc premier league",
                "peacock premier league", "manutd", "man city", "arsenal fc",
            ),
            gradient = SoccerGrad,
        ),
        HardcodedChannel(
            id = "la_liga",
            name = "La Liga",
            short = "LL",
            keywords = listOf("laliga", "la liga", "movistar laliga", "laliga tv", "laliga ea sports"),
            gradient = SoccerGrad,
        ),
        HardcodedChannel(
            id = "serie_a",
            name = "Serie A",
            short = "SA",
            keywords = listOf("serie a", "dazn italia", "sky calcio", "calcio", "italian football", "serie a tim"),
            gradient = SoccerGrad,
        ),
        HardcodedChannel(
            id = "bundesliga",
            name = "Bundesliga",
            short = "BL",
            keywords = listOf("bundesliga", "sky bundesliga", "dazn bundes", "german football", "bundesliga 2"),
            gradient = SoccerGrad,
        ),
        HardcodedChannel(
            id = "ligue_1",
            name = "Ligue 1",
            short = "L1",
            keywords = listOf(
                "ligue 1", "ligue1", "canal+ sport", "canal plus sport",
                "rmc sport", "prime video ligue", "french football", "ligue 2",
                "dazn ligue",
            ),
            gradient = SoccerGrad,
        ),
        HardcodedChannel(
            id = "mls",
            name = "MLS",
            short = "MLS",
            keywords = listOf(" mls", "mls ", "major league soccer", "apple mls", "mls season pass", "mls next"),
            gradient = SoccerGrad,
        ),
        HardcodedChannel(
            id = "world_cup",
            name = "World Cup",
            short = "WC",
            keywords = listOf("world cup", "fifa world", "fifa+", "fifa plus", "qatar 2022", "wc 2026"),
            gradient = SoccerGrad,
        ),
        HardcodedChannel(
            id = "eredivisie",
            name = "Eredivisie",
            short = "ERE",
            keywords = listOf("eredivisie", "dutch football", "netherlands football"),
            gradient = SoccerGrad,
        ),
        HardcodedChannel(
            id = "primeira_liga",
            name = "Primeira Liga",
            short = "PL",
            keywords = listOf("primeira liga", "liga portugal", "portuguese football", "liga nos", "sport tv portugal"),
            gradient = SoccerGrad,
        ),
        HardcodedChannel(
            id = "super_lig",
            name = "Süper Lig",
            short = "SL",
            keywords = listOf("super lig", "süper lig", "turkish football", "bein turkey"),
            gradient = SoccerGrad,
        ),
        HardcodedChannel(
            id = "african_football",
            name = "African Football",
            short = "CAF",
            keywords = listOf("afcon", "africa cup", "caf champions", "caf cl", "caf confederations", "supersport africa"),
            gradient = SoccerGrad,
        ),
        HardcodedChannel(
            id = "copa_libertadores",
            name = "Copa Libertadores",
            short = "LIB",
            keywords = listOf("copa libertadores", "libertadores", "copa sudamericana", "conmebol"),
            gradient = SoccerGrad,
        ),
        HardcodedChannel(
            id = "beout_q",
            name = "SuperSport",
            short = "SS",
            keywords = listOf("supersport", "super sport", "dstv sport"),
            gradient = SoccerGrad,
        ),

        // ── US sports ──────────────────────────────────────────────────
        HardcodedChannel(
            id = "espn",
            name = "ESPN",
            short = "ESPN",
            keywords = listOf(
                "espn", "espn2", "espn 2", "espnews", "espn+", "espn plus",
                "espn deportes", "espn u", "espnu",
            ),
            gradient = UsSportGrad,
        ),
        HardcodedChannel(
            id = "fox_sports",
            name = "Fox Sports",
            short = "FOX",
            keywords = listOf("fox sport", "fox sports", "fs1", "fs2", "fox soccer", "fox deportes"),
            gradient = UsSportGrad,
        ),
        HardcodedChannel(
            id = "nbc_sports",
            name = "NBC Sports",
            short = "NBC",
            keywords = listOf("nbc sport", "nbc sports", "nbcsn", "peacock sport", "nbc gold"),
            gradient = UsSportGrad,
        ),
        HardcodedChannel(
            id = "cbs_sports",
            name = "CBS Sports",
            short = "CBS",
            keywords = listOf("cbs sport", "cbs sports", "paramount sport", "cbs sports hq"),
            gradient = UsSportGrad,
        ),
        HardcodedChannel(
            id = "tnt_us",
            name = "TNT (USA)",
            short = "TNT",
            keywords = listOf("tnt usa", "tnt us ", "tnt hd usa", "max sports"),
            gradient = UsSportGrad,
        ),
        HardcodedChannel(
            id = "nba",
            name = "NBA",
            short = "NBA",
            keywords = listOf("nba ", "nba tv", "nba league pass", "nba hd", "nba g league"),
            gradient = UsSportGrad,
        ),
        HardcodedChannel(
            id = "nfl",
            name = "NFL",
            short = "NFL",
            keywords = listOf("nfl ", "nfl network", "nfl hd", "nfl sunday ticket", "nfl game pass"),
            gradient = UsSportGrad,
        ),
        HardcodedChannel(
            id = "nfl_redzone",
            name = "NFL RedZone",
            short = "RZ",
            keywords = listOf("redzone", "red zone"),
            gradient = UsSportGrad,
        ),
        HardcodedChannel(
            id = "nhl",
            name = "NHL",
            short = "NHL",
            keywords = listOf("nhl ", "nhl network", "nhl hd", "nhl tv", "hockey night"),
            gradient = UsSportGrad,
        ),
        HardcodedChannel(
            id = "mlb",
            name = "MLB",
            short = "MLB",
            keywords = listOf("mlb ", "mlb network", "mlb hd", "mlb tv", "mlb extra innings"),
            gradient = UsSportGrad,
        ),
        HardcodedChannel(
            id = "bally_sports",
            name = "Bally Sports",
            short = "BSN",
            keywords = listOf("bally sport", "bally sports", "fanduel sports network"),
            gradient = UsSportGrad,
        ),
        HardcodedChannel(
            id = "tennis_channel",
            name = "Tennis Channel",
            short = "TEN",
            keywords = listOf(
                "tennis channel", "tennis hd", "atp tennis", "wta tennis",
                "tennis tv", "wimbledon", "us open tennis", "french open",
                "australian open", "roland garros", "atp tour", "wta tour",
            ),
            gradient = SoccerGrad,
        ),
        HardcodedChannel(
            id = "golf_channel",
            name = "Golf Channel",
            short = "GLF",
            keywords = listOf(
                "golf channel", "golf tv", "sky golf", "pga tour",
                "masters golf", "the open golf", "ryder cup", "golf live",
            ),
            gradient = SoccerGrad,
        ),
        HardcodedChannel(
            id = "amazon_prime_sport",
            name = "Prime Video Sport",
            short = "PVS",
            keywords = listOf(
                "prime video sport", "prime sport", "amazon sport", "amazon prime sport",
                "prime video nfl", "prime video ucl", "prime video ligue",
            ),
            gradient = UsSportGrad,
        ),

        // ── Cricket ────────────────────────────────────────────────────
        HardcodedChannel(
            id = "cricket",
            name = "Cricket",
            short = "CRK",
            keywords = listOf(
                "cricket", "star sports cricket", "sky sports cricket",
                "willow cricket", "willow tv", "sony ten", "sony six cricket",
                "espn cricinfo", "icc cricket", "test match", "odi ", "t20 ",
                "ipl ", "indian premier league", "big bash", "cpl cricket",
                "the hundred", "county cricket",
            ),
            gradient = SoccerGrad,
        ),

        // ── DAZN / general sports ──────────────────────────────────────
        HardcodedChannel(
            id = "dazn",
            name = "DAZN",
            short = "DAZN",
            keywords = listOf("dazn"),
            gradient = FightGrad,
        ),
        HardcodedChannel(
            id = "eurosport",
            name = "Eurosport",
            short = "EURO",
            keywords = listOf(
                "eurosport", "eurosport 1", "eurosport 2", "discovery+ sport",
                "gcn+ ", "cycling tv",
            ),
            gradient = SoccerGrad,
        ),
        HardcodedChannel(
            id = "axs_wrestling",
            name = "Wrestling AXS",
            short = "AXS",
            keywords = listOf("axs tv", "njpw", "new japan pro", "impact wrestling", "tna "),
            gradient = WrestlingGrad,
        ),
        HardcodedChannel(
            id = "sport_tv",
            name = "Sport TV (general)",
            short = "STV",
            keywords = listOf("sport tv", "sportv", "sport channel", "sports channel"),
            gradient = SoccerGrad,
        ),

        // ── News ───────────────────────────────────────────────────────
        HardcodedChannel(
            id = "cnn",
            name = "CNN",
            short = "CNN",
            keywords = listOf("cnn", "cnn international", "cnn hd"),
            gradient = NewsGrad,
        ),
        HardcodedChannel(
            id = "bbc_news",
            name = "BBC News",
            short = "BBC",
            keywords = listOf("bbc news", "bbc world", "bbc world news"),
            gradient = NewsGrad,
        ),
        HardcodedChannel(
            id = "fox_news",
            name = "Fox News",
            short = "FXN",
            keywords = listOf("fox news", "fox business"),
            gradient = NewsGrad,
        ),
        HardcodedChannel(
            id = "msnbc",
            name = "MSNBC",
            short = "MSN",
            keywords = listOf("msnbc"),
            gradient = NewsGrad,
        ),
        HardcodedChannel(
            id = "sky_news",
            name = "Sky News",
            short = "SKN",
            keywords = listOf("sky news", "sky news arabia"),
            gradient = NewsGrad,
        ),
        HardcodedChannel(
            id = "al_jazeera",
            name = "Al Jazeera",
            short = "AJ",
            keywords = listOf("al jazeera", "aljazeera", "jazeera", "al jazeera english", "al jazeera arabic"),
            gradient = NewsGrad,
        ),
        HardcodedChannel(
            id = "cnbc",
            name = "CNBC",
            short = "CNBC",
            keywords = listOf("cnbc", "cnbc international", "cnbc arabic"),
            gradient = NewsGrad,
        ),
        HardcodedChannel(
            id = "bloomberg",
            name = "Bloomberg",
            short = "BLM",
            keywords = listOf("bloomberg", "bloomberg tv"),
            gradient = NewsGrad,
        ),
        HardcodedChannel(
            id = "france24",
            name = "France 24",
            short = "F24",
            keywords = listOf("france 24", "france24"),
            gradient = NewsGrad,
        ),
        HardcodedChannel(
            id = "dw_news",
            name = "DW News",
            short = "DW",
            keywords = listOf("dw news", "deutsche welle", " dw "),
            gradient = NewsGrad,
        ),
        HardcodedChannel(
            id = "euronews",
            name = "Euronews",
            short = "EN",
            keywords = listOf("euronews", "euro news"),
            gradient = NewsGrad,
        ),
        HardcodedChannel(
            id = "rt_news",
            name = "RT News",
            short = "RT",
            keywords = listOf("rt news", "russia today"),
            gradient = NewsGrad,
        ),
        HardcodedChannel(
            id = "trt_world",
            name = "TRT World",
            short = "TRT",
            keywords = listOf("trt world", "trt haber"),
            gradient = NewsGrad,
        ),
        HardcodedChannel(
            id = "sky_sports_news",
            name = "Sky Sports News",
            short = "SSN",
            keywords = listOf("sky sports news", "ssn "),
            gradient = NewsGrad,
        ),

        // ── UK general ─────────────────────────────────────────────────
        HardcodedChannel(
            id = "bbc_one",
            name = "BBC One",
            short = "BBC1",
            keywords = listOf("bbc one", "bbc1", "bbc 1"),
            gradient = UkSportGrad,
        ),
        HardcodedChannel(
            id = "bbc_two",
            name = "BBC Two",
            short = "BBC2",
            keywords = listOf("bbc two", "bbc2", "bbc 2"),
            gradient = UkSportGrad,
        ),
        HardcodedChannel(
            id = "itv",
            name = "ITV",
            short = "ITV",
            keywords = listOf("itv1", "itv 1", "itv2", "itv 2", "itv3", "itv 3", "itv4", "itv 4", "itv hd", "itvx"),
            gradient = UkSportGrad,
        ),
        HardcodedChannel(
            id = "channel_4",
            name = "Channel 4",
            short = "CH4",
            keywords = listOf("channel 4", "channel4", "ch4 ", "e4 ", " e4", "more4"),
            gradient = UkSportGrad,
        ),
        HardcodedChannel(
            id = "channel_5",
            name = "Channel 5",
            short = "CH5",
            keywords = listOf("channel 5", "channel5", "ch5 ", "5star", "5 star", "5usa"),
            gradient = UkSportGrad,
        ),

        // ── Movies / premium ────────────────────────────────────────────
        HardcodedChannel(
            id = "hbo",
            name = "HBO",
            short = "HBO",
            keywords = listOf("hbo", "hbo max", "max originals", "hbo signature", "hbo family"),
            gradient = MovieGrad,
        ),
        HardcodedChannel(
            id = "showtime",
            name = "Showtime",
            short = "SHO",
            keywords = listOf("showtime", "showtime 2"),
            gradient = MovieGrad,
        ),
        HardcodedChannel(
            id = "starz",
            name = "Starz",
            short = "STZ",
            keywords = listOf("starz", "starz encore"),
            gradient = MovieGrad,
        ),
        HardcodedChannel(
            id = "cinemax",
            name = "Cinemax",
            short = "CMX",
            keywords = listOf("cinemax", "max prime"),
            gradient = MovieGrad,
        ),
        HardcodedChannel(
            id = "paramount",
            name = "Paramount",
            short = "PAR",
            keywords = listOf("paramount network", "paramount+", "paramount plus", "paramount channel"),
            gradient = MovieGrad,
        ),
        HardcodedChannel(
            id = "amc",
            name = "AMC",
            short = "AMC",
            keywords = listOf(" amc ", "amc hd", "amc usa", "amc network", "amc+"),
            gradient = MovieGrad,
        ),
        HardcodedChannel(
            id = "fx",
            name = "FX",
            short = "FX",
            keywords = listOf(" fx ", "fx hd", "fxx", "fx usa", "fx movie"),
            gradient = MovieGrad,
        ),
        HardcodedChannel(
            id = "tnt_drama",
            name = "TBS / TNT Drama",
            short = "TBS",
            keywords = listOf("tbs ", "tbs hd", "tnt drama"),
            gradient = MovieGrad,
        ),
        HardcodedChannel(
            id = "usa_network",
            name = "USA Network",
            short = "USA",
            keywords = listOf("usa network", " usa hd", "usa channel"),
            gradient = MovieGrad,
        ),
        HardcodedChannel(
            id = "syfy",
            name = "Syfy",
            short = "SYFY",
            keywords = listOf("syfy", "sci fi", "sci-fi"),
            gradient = MovieGrad,
        ),
        HardcodedChannel(
            id = "lifetime",
            name = "Lifetime",
            short = "LIFE",
            keywords = listOf("lifetime", "lifetime movie"),
            gradient = MovieGrad,
        ),
        HardcodedChannel(
            id = "hallmark",
            name = "Hallmark",
            short = "HLM",
            keywords = listOf("hallmark", "hallmark channel", "hallmark movies"),
            gradient = MovieGrad,
        ),
        HardcodedChannel(
            id = "tcm",
            name = "TCM",
            short = "TCM",
            keywords = listOf("tcm ", "turner classic"),
            gradient = MovieGrad,
        ),
        HardcodedChannel(
            id = "sky_cinema",
            name = "Sky Cinema",
            short = "SCIN",
            keywords = listOf(
                "sky cinema", "sky movies", "sky cinema premiere",
                "sky cinema action", "sky cinema comedy", "sky cinema drama",
            ),
            gradient = MovieGrad,
        ),
        HardcodedChannel(
            id = "osn_movies",
            name = "OSN Movies",
            short = "OSNM",
            keywords = listOf("osn movies", "osn cinema", "osn series", "osn streaming"),
            gradient = MovieGrad,
        ),
        HardcodedChannel(
            id = "netflix",
            name = "Netflix",
            short = "NF",
            keywords = listOf("netflix"),
            gradient = MovieGrad,
        ),
        HardcodedChannel(
            id = "apple_tv",
            name = "Apple TV+",
            short = "ATV",
            keywords = listOf("apple tv", "apple tv+", "appletv"),
            gradient = MovieGrad,
        ),

        // ── Documentary / lifestyle ────────────────────────────────────
        HardcodedChannel(
            id = "discovery",
            name = "Discovery",
            short = "DISC",
            keywords = listOf("discovery", "discovery+", "discovery channel"),
            exclude = listOf("kids"),
            gradient = DocGrad,
        ),
        HardcodedChannel(
            id = "history",
            name = "History",
            short = "HIST",
            keywords = listOf("history channel", "history hd", "history us", "history uk", " hist "),
            gradient = DocGrad,
        ),
        HardcodedChannel(
            id = "nat_geo",
            name = "Nat Geo",
            short = "NATGEO",
            keywords = listOf("national geographic", "nat geo", "natgeo", "nat-geo", "nat geo wild"),
            gradient = DocGrad,
        ),
        HardcodedChannel(
            id = "animal_planet",
            name = "Animal Planet",
            short = "AP",
            keywords = listOf("animal planet"),
            gradient = DocGrad,
        ),
        HardcodedChannel(
            id = "tlc",
            name = "TLC",
            short = "TLC",
            keywords = listOf("tlc "),
            gradient = DocGrad,
        ),
        HardcodedChannel(
            id = "food_network",
            name = "Food Network",
            short = "FOOD",
            keywords = listOf("food network", "food channel"),
            gradient = DocGrad,
        ),
        HardcodedChannel(
            id = "hgtv",
            name = "HGTV",
            short = "HGTV",
            keywords = listOf("hgtv"),
            gradient = DocGrad,
        ),
        HardcodedChannel(
            id = "investigation",
            name = "ID",
            short = "ID",
            keywords = listOf("investigation discovery", " id channel", " id hd", "id usa"),
            gradient = DocGrad,
        ),
        HardcodedChannel(
            id = "vice",
            name = "Vice / Vice TV",
            short = "VICE",
            keywords = listOf("vice tv", "vicetv", "vice channel"),
            gradient = DocGrad,
        ),

        // ── Kids ───────────────────────────────────────────────────────
        HardcodedChannel(
            id = "cartoon_network",
            name = "Cartoon Network",
            short = "CN",
            keywords = listOf("cartoon network", "cartoonnetwork", " cn hd"),
            gradient = KidsGrad,
        ),
        HardcodedChannel(
            id = "disney",
            name = "Disney",
            short = "DSN",
            keywords = listOf("disney channel", "disney hd", "disney xd", "disney junior", "disney jr"),
            exclude = listOf("disney+", "disney plus"),
            gradient = KidsGrad,
        ),
        HardcodedChannel(
            id = "nickelodeon",
            name = "Nickelodeon",
            short = "NICK",
            keywords = listOf("nickelodeon", "nick jr", "nick hd", "nicktoons", "nick "),
            gradient = KidsGrad,
        ),
        HardcodedChannel(
            id = "boomerang",
            name = "Boomerang",
            short = "BOOM",
            keywords = listOf("boomerang"),
            gradient = KidsGrad,
        ),
        HardcodedChannel(
            id = "pbs_kids",
            name = "PBS Kids",
            short = "PBS",
            keywords = listOf("pbs kids", "pbs hd", " pbs "),
            gradient = KidsGrad,
        ),
        HardcodedChannel(
            id = "baby_tv",
            name = "Baby TV",
            short = "BTV",
            keywords = listOf("baby tv", "baby channel"),
            gradient = KidsGrad,
        ),
        HardcodedChannel(
            id = "spacetoon",
            name = "Spacetoon",
            short = "SPC",
            keywords = listOf("spacetoon"),
            gradient = KidsGrad,
        ),

        // ── Music ──────────────────────────────────────────────────────
        HardcodedChannel(
            id = "mtv",
            name = "MTV",
            short = "MTV",
            keywords = listOf(" mtv ", "mtv hd", "mtv usa", "mtv uk", "mtv live", "mtv 80s", "mtv 90s", "mtv hits", "mtv music"),
            gradient = MusicGrad,
        ),
        HardcodedChannel(
            id = "vh1",
            name = "VH1",
            short = "VH1",
            keywords = listOf("vh1", "vh-1"),
            gradient = MusicGrad,
        ),
        HardcodedChannel(
            id = "bet",
            name = "BET",
            short = "BET",
            keywords = listOf("bet ", "bet hd", "bet hip", "bet usa"),
            gradient = MusicGrad,
        ),
        HardcodedChannel(
            id = "mbc_masr",
            name = "MBC Masr",
            short = "MBM",
            keywords = listOf("mbc masr", "mbc مصر"),
            gradient = ArabicGrad,
        ),

        // ── Comedy ─────────────────────────────────────────────────────
        HardcodedChannel(
            id = "comedy_central",
            name = "Comedy Central",
            short = "CC",
            keywords = listOf("comedy central", "comedy central hd"),
            gradient = EntGrad,
        ),
        HardcodedChannel(
            id = "adult_swim",
            name = "Adult Swim",
            short = "AS",
            keywords = listOf("adult swim"),
            gradient = EntGrad,
        ),

        // ── Spanish / Latin ────────────────────────────────────────────
        HardcodedChannel(
            id = "telemundo",
            name = "Telemundo",
            short = "TLM",
            keywords = listOf("telemundo"),
            gradient = EntGrad,
        ),
        HardcodedChannel(
            id = "univision",
            name = "Univision",
            short = "UNI",
            keywords = listOf("univision", "unimas"),
            gradient = EntGrad,
        ),
        HardcodedChannel(
            id = "tudn",
            name = "TUDN",
            short = "TUDN",
            keywords = listOf("tudn"),
            gradient = EntGrad,
        ),

        // ── Arabic ─────────────────────────────────────────────────────
        HardcodedChannel(
            id = "mbc",
            name = "MBC",
            short = "MBC",
            keywords = listOf(
                "mbc1", "mbc 1", "mbc2", "mbc 2", "mbc3", "mbc 3", "mbc4", "mbc 4",
                "mbc action", "mbc max", "mbc drama", "mbc bollywood", "mbc persia",
            ),
            gradient = ArabicGrad,
        ),
        HardcodedChannel(
            id = "rotana",
            name = "Rotana",
            short = "ROT",
            keywords = listOf("rotana", "rotana cinema", "rotana khalijiah", "rotana music", "rotana klassik"),
            gradient = ArabicGrad,
        ),
        HardcodedChannel(
            id = "osn",
            name = "OSN",
            short = "OSN",
            keywords = listOf("osn", "osn hd", "osn yahala", "osn living"),
            gradient = ArabicGrad,
        ),
        HardcodedChannel(
            id = "abu_dhabi",
            name = "Abu Dhabi TV",
            short = "ADTV",
            keywords = listOf("abu dhabi tv", "ad sport", "ad sports", "abu dhabi sport"),
            gradient = ArabicGrad,
        ),
        HardcodedChannel(
            id = "dubai_tv",
            name = "Dubai TV",
            short = "DXB",
            keywords = listOf("dubai tv", "dubai one", "dubai sport"),
            gradient = ArabicGrad,
        ),
        HardcodedChannel(
            id = "saudi_tv",
            name = "Saudi TV",
            short = "STV",
            keywords = listOf("saudi tv", "ksa sport", "ksa sports", "saudi sport"),
            gradient = ArabicGrad,
        ),
        HardcodedChannel(
            id = "alarabiya",
            name = "Al Arabiya",
            short = "ARB",
            keywords = listOf("al arabiya", "alarabiya", "arabiya"),
            gradient = NewsGrad,
        ),
        HardcodedChannel(
            id = "almajd",
            name = "Al Majd",
            short = "MAJ",
            keywords = listOf("al majd", "almajd", "majd quran", "majd kids"),
            gradient = ArabicGrad,
        ),

        // ── Indian ─────────────────────────────────────────────────────
        HardcodedChannel(
            id = "star_plus",
            name = "Star Plus",
            short = "STR",
            keywords = listOf("star plus", "star+", "star sports india", "star sports 1", "star sports 2", "star gold"),
            gradient = EntGrad,
        ),
        HardcodedChannel(
            id = "zee_tv",
            name = "Zee TV",
            short = "ZEE",
            keywords = listOf("zee tv", "zee cinema", "zee anmol", "zee news", "zee entertainment"),
            gradient = EntGrad,
        ),
        HardcodedChannel(
            id = "sony_india",
            name = "Sony (India)",
            short = "SNY",
            keywords = listOf("sony tv", "sony max", "sony sab", "sony six", "sony pix", "sony ten", "sony liv"),
            gradient = EntGrad,
        ),
        HardcodedChannel(
            id = "colors",
            name = "Colors",
            short = "CLR",
            keywords = listOf("colors tv", "colors hd", "colors cineplex", "colors rishtey"),
            gradient = EntGrad,
        ),
        HardcodedChannel(
            id = "sun_tv",
            name = "Sun TV",
            short = "SUN",
            keywords = listOf("sun tv", "sun news", "sun music", "surya tv", "kiran tv"),
            gradient = EntGrad,
        ),
        HardcodedChannel(
            id = "aaj_tak",
            name = "Aaj Tak / India Today",
            short = "AAJ",
            keywords = listOf("aaj tak", "india today", "india news"),
            gradient = NewsGrad,
        ),
    )

    fun byId(id: String): HardcodedChannel? = all.firstOrNull { it.id == id }

    /**
     * True when [name] contains any [keywords] entry AND does NOT contain
     * any [exclude] entry. All matches are case-insensitive substring checks.
     */
    fun matches(
        name: String,
        keywords: List<String>,
        exclude: List<String> = emptyList(),
    ): Boolean {
        val lower = name.lowercase()
        if (exclude.any { lower.contains(it) }) return false
        return keywords.any { lower.contains(it) }
    }
}
