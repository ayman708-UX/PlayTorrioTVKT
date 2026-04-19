package com.playtorrio.tv.data.profile

/**
 * Hardcoded avatar catalog for profile pictures.
 *
 * Curated character art URLs. Loaded by Coil through the global ImageLoader
 * configured in [com.playtorrio.tv.App] (which already sends a proper
 * User-Agent header).
 */
object AvatarCatalog {

    data class Avatar(val label: String, val url: String)

    val all: List<Avatar> = listOf(
        Avatar("Spider-Man",      "https://cdn.mos.cms.futurecdn.net/TeMTjhZaFLdaNTKjyXeJPd.jpg"),
        Avatar("Iron Man",        "https://playcontestofchampions.com/wp-content/uploads/2023/04/champion-iron-man-infinity-war.webp"),
        Avatar("Batman",          "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTX3ZeC7Nbmc7S9w7f5Iaa2R7TJu85fZYxAgg&s"),
        Avatar("Superman",        "https://preview.redd.it/what-about-superman-do-you-think-makes-him-the-greatest-v0-08c9a7jru54d1.jpeg?width=1080&crop=smart&auto=webp&s=2805c3c1d1d78470e1bf1b19a7b537685652b824"),
        Avatar("Wonder Woman",    "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcReIReolJV8IaAgujrJwca9sAwo5-uQyMFp_Q&s"),
        Avatar("Black Panther",   "https://www.sideshow.com/cdn-cgi/image/quality=90,f=auto/https://www.sideshow.com/storage/product-images/910233/black-panther-deluxe_marvel_gallery_61eb5a329c25b.jpg"),
        Avatar("Thor",            "https://upload.wikimedia.org/wikipedia/en/3/3c/Chris_Hemsworth_as_Thor.jpg"),
        Avatar("Hulk",            "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQaB6qXmmkFpFswsLVt6qz5swbXSeJkwG9XDQ&s"),
        Avatar("Doctor Strange",  "https://static.wikia.nocookie.net/disney/images/d/dc/Doctor_Strange_-_Profile.png/revision/latest?cb=20220804200852"),
        Avatar("Wolverine",       "https://img.asmedia.epimg.net/resizer/v2/JRNLXSYQ6FBXFHBEXX6IIEFOZU.jpg?auth=00797de5a03ca0ff06333b7f5cfc1c6b13a6afb52620be3157b11f4d2e9e22b7&width=1472&height=1104&smart=true"),
        Avatar("Deadpool",        "https://static.wikia.nocookie.net/marvelcinematicuniverse/images/a/ad/Deadpool_Infobox.png/revision/latest/thumbnail/width/360/height/450?cb=20240522015012"),
        Avatar("Joker",           "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSU0e1oje8lXK_q7SFTY1kOf-Qez45QIykAPg&s"),
        Avatar("Harry Potter",    "https://static.wikia.nocookie.net/neoencyclopedia/images/4/44/HarryPotter5poster.jpg/revision/latest?cb=20121121021021"),
        Avatar("Darth Vader",     "https://m.media-amazon.com/images/M/MV5BNTQwMGU5MjUtZmFhMi00OGFkLWFiMDUtY2EzODk3NDM3NTI5XkEyXkFqcGc@._V1_QL75_UY281_CR31,0,500,281_.jpg"),
        Avatar("Yoda",            "https://hips.hearstapps.com/hmg-prod/images/grogu-baby-yoda-the-child-1606497947.png?crop=0.421xw:1.00xh;0.349xw,0&resize=1200:*"),
        Avatar("Walter White",    "https://static.wikia.nocookie.net/breakingbad/images/b/b4/Walter_2008.png/revision/latest/scale-to-width/360?cb=20200704164147"),
        Avatar("Eleven",          "https://s2.r29static.com/bin/entry/75e/x,80/2214031/image.jpg"),
        Avatar("Goku",            "https://cdng.europosters.eu/pod_public/750/260203.jpg"),
        Avatar("Naruto",          "https://cdng.europosters.eu/pod_public/1300/229900.jpg"),
        Avatar("Pikachu",         "https://www.denofgeek.com/wp-content/uploads/2021/04/Pikachu.png?resize=768%2C432"),
        Avatar("Mario",           "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQ6PdNYIYdX1nnhvn1XVMKt3_qJxR5pMpXLfw&s"),
        Avatar("Sonic",           "https://yt3.ggpht.com/ZTmtyDVh-Bo0BLDWRp_FTfE4gwFbwC3-W5L23V97QRV2Ebsqk4P3Etg4vKk4UOtvIZTBce1sTYT6dw=s1024-nd-v1"),
        Avatar("Master Chief",    "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQSgBQgPv5BqeQXRFJOLzN1HXWUEgUYtLSdfA&s"),
        Avatar("Barbie",          "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSPpd2dKUULlTHDgoufs2wFrK7KQlYsPp5WFw&s"),
        Avatar("Wednesday",       "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQ7JRhlHE_JfEz0mzEpdH7XwLrB9KbBBi1qhQ&s"),
    )

    fun default(): String = all.first().url
}
