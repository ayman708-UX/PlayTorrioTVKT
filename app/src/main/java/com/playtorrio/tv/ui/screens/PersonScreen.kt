package com.playtorrio.tv.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.playtorrio.tv.data.model.PersonCastCredit
import com.playtorrio.tv.data.model.PersonCrewCredit
import com.playtorrio.tv.data.model.PersonImage
import com.playtorrio.tv.ui.screens.person.PersonViewModel
import com.playtorrio.tv.ui.screens.person.WorkFilter

private val AccentPrimary = Color(0xFF818CF8)
private val AccentSecondary = Color(0xFFC084FC)
private val AccentTertiary = Color(0xFF38BDF8)
private val GoldStar = Color(0xFFFFD700)
private val SurfaceGlass = Color.White.copy(alpha = 0.06f)
private val SurfaceGlassBorder = Color.White.copy(alpha = 0.1f)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PersonScreen(
    personId: Int,
    navController: NavController,
    viewModel: PersonViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(personId) { viewModel.load(personId) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    navController.popBackStack()
                    true
                } else false
            }
    ) {
        when {
            state.isLoading -> PersonLoading()
            state.error != null -> PersonError(state.error!!)
            state.person != null -> PersonContent(state, viewModel, navController)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PersonContent(
    state: com.playtorrio.tv.ui.screens.person.PersonUiState,
    viewModel: PersonViewModel,
    navController: NavController
) {
    val person = state.person!!
    val scrollState = rememberScrollState()

    // Ambient backdrop from first photo
    val bgUrl = person.profileUrl
    Box(modifier = Modifier.fillMaxSize()) {
        // Blurred background
        bgUrl?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .blur(12.dp)
                    .graphicsLayer { alpha = 0.3f }
                    .drawWithContent {
                        drawContent()
                        drawRect(Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black),
                            startY = size.height * 0.3f
                        ))
                    },
                contentScale = ContentScale.Crop
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(top = 40.dp)
        ) {
            // ── Hero: Photo + Bio ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp).focusable(),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Profile photo
                Box {
                    AsyncImage(
                        model = person.profileUrl,
                        contentDescription = person.name,
                        modifier = Modifier
                            .width(180.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, SurfaceGlassBorder, RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = person.name ?: "Unknown",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-0.5).sp
                        ),
                        color = Color.White
                    )

                    Spacer(Modifier.height(8.dp))

                    // Metadata row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        person.knownForDepartment?.let {
                            Text(
                                text = it.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, fontSize = 10.sp
                                ),
                                color = AccentPrimary,
                                modifier = Modifier
                                    .border(1.dp, AccentPrimary.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        person.birthday?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }

                        person.age?.let {
                            Text(
                                text = if (person.deathday != null) "(died age $it)" else "(age $it)",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.4f)
                            )
                        }
                    }

                    person.placeOfBirth?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "📍 $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Biography
                    person.biography?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 8, overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Stats
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        PersonStat("Credits", "${state.castCredits.size + state.crewCredits.size}")
                        val movieCount = state.castCredits.count { it.isMovie } + state.crewCredits.count { it.isMovie }
                        val tvCount = state.castCredits.count { !it.isMovie } + state.crewCredits.count { !it.isMovie }
                        PersonStat("Movies", "$movieCount")
                        PersonStat("TV Shows", "$tvCount")
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── Photos ──
            if (state.photos.size > 1) {
                PersonSectionTitle("Photos")
                Spacer(Modifier.height(12.dp))
                PhotoRow(state.photos)
                Spacer(Modifier.height(28.dp))
            }

            // ── Filmography with filter ──
            if (state.castCredits.isNotEmpty() || state.crewCredits.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(horizontal = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "FILMOGRAPHY",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 13.sp
                        ),
                        color = Color.White.copy(alpha = 0.55f)
                    )
                    Box(
                        modifier = Modifier.weight(1f).height(1.dp)
                            .background(Brush.horizontalGradient(
                                colors = listOf(SurfaceGlassBorder, Color.Transparent)
                            ))
                    )
                    // Filter pills
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.focusGroup()) {
                        WorkFilter.entries.forEach { filter ->
                            FilterPill(
                                label = filter.name,
                                isSelected = state.filter == filter,
                                onClick = { viewModel.setFilter(filter) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Cast credits
                if (state.filteredCast.isNotEmpty()) {
                    Text(
                        text = "As Actor",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = AccentSecondary.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 48.dp, bottom = 10.dp)
                    )
                    PersonCreditRow(state.filteredCast, navController)
                    Spacer(Modifier.height(20.dp))
                }

                // Crew credits
                if (state.filteredCrew.isNotEmpty()) {
                    Text(
                        text = "Behind the Camera",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = AccentTertiary.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 48.dp, bottom = 10.dp)
                    )
                    PersonCrewRow(state.filteredCrew, navController)
                    Spacer(Modifier.height(20.dp))
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ════════════════════════════════════════════════════════
// FILTER PILL
// ════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FilterPill(label: String, isSelected: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = if (isSelected) AccentPrimary else if (isFocused) Color.White.copy(alpha = 0.4f) else SurfaceGlassBorder

    Card(
        onClick = onClick,
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .border(1.dp, borderColor, RoundedCornerShape(16.dp)),
        scale = CardDefaults.scale(focusedScale = 1f),
        shape = CardDefaults.shape(RoundedCornerShape(16.dp))
    ) {
        Box(
            modifier = Modifier
                .background(
                    if (isSelected) AccentPrimary.copy(alpha = 0.2f)
                    else SurfaceGlass
                )
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp
                ),
                color = if (isSelected) AccentPrimary else Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

// ════════════════════════════════════════════════════════
// PHOTO ROW
// ════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PhotoRow(photos: List<PersonImage>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.focusGroup()
    ) {
        items(photos.take(20), key = { it.filePath }) { photo ->
            var isFocused by remember { mutableStateOf(false) }
            val scale by animateFloatAsState(
                if (isFocused) 1.05f else 0.95f,
                spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "s"
            )

            Card(
                onClick = { },
                modifier = Modifier
                    .height(200.dp)
                    .aspectRatio(2f / 3f)
                    .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (isFocused) 1f else 0.7f }
                    .then(
                        if (isFocused) Modifier.border(
                            1.5.dp,
                            Brush.sweepGradient(listOf(
                                AccentSecondary.copy(alpha = 0.8f),
                                AccentPrimary.copy(alpha = 0.6f),
                                AccentSecondary.copy(alpha = 0.8f)
                            )),
                            RoundedCornerShape(10.dp)
                        ) else Modifier
                    )
                    .onFocusChanged { isFocused = it.isFocused },
                scale = CardDefaults.scale(focusedScale = 1f),
                shape = CardDefaults.shape(RoundedCornerShape(10.dp))
            ) {
                AsyncImage(
                    model = photo.url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════
// CREDIT ROWS
// ════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PersonCreditRow(credits: List<PersonCastCredit>, navController: NavController) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.focusGroup()
    ) {
        items(credits.take(30), key = { it.id }) { credit ->
            CreditPosterCard(
                posterUrl = credit.posterUrl,
                title = credit.displayTitle,
                subtitle = credit.character,
                year = credit.year,
                rating = credit.voteAverage,
                isMovie = credit.isMovie,
                onClick = {
                    navController.navigate("detail/${credit.id}/${credit.isMovie}")
                }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PersonCrewRow(credits: List<PersonCrewCredit>, navController: NavController) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.focusGroup()
    ) {
        items(credits.take(30), key = { "${it.id}_${it.job}" }) { credit ->
            CreditPosterCard(
                posterUrl = credit.posterUrl,
                title = credit.displayTitle,
                subtitle = credit.job,
                year = credit.year,
                rating = credit.voteAverage,
                isMovie = credit.isMovie,
                onClick = {
                    navController.navigate("detail/${credit.id}/${credit.isMovie}")
                }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CreditPosterCard(
    posterUrl: String?,
    title: String,
    subtitle: String?,
    year: String?,
    rating: Double?,
    isMovie: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (isFocused) 1.08f else 0.95f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "s"
    )
    val cardAlpha by animateFloatAsState(
        if (isFocused) 1f else 0.65f, tween(300), label = "a"
    )

    Column(
        modifier = Modifier
            .width(130.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = cardAlpha }
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .then(
                    if (isFocused) Modifier.border(
                        1.5.dp,
                        Brush.sweepGradient(listOf(
                            Color.White.copy(alpha = 0.6f),
                            AccentPrimary.copy(alpha = 0.8f),
                            AccentSecondary.copy(alpha = 0.6f),
                            Color.White.copy(alpha = 0.4f)
                        )),
                        RoundedCornerShape(10.dp)
                    ) else Modifier
                )
                .onFocusChanged { isFocused = it.isFocused },
            scale = CardDefaults.scale(focusedScale = 1f),
            shape = CardDefaults.shape(RoundedCornerShape(10.dp))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (posterUrl != null) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(SurfaceGlass),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title.take(1),
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White.copy(alpha = 0.2f)
                        )
                    }
                }
                // Type badge
                val badgeColor = if (isMovie) AccentPrimary else AccentTertiary
                Text(
                    text = if (isMovie) "MOVIE" else "TV",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold, fontSize = 7.sp, letterSpacing = 0.8.sp
                    ),
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(badgeColor.copy(alpha = 0.85f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
                // Rating
                rating?.takeIf { it > 0 }?.let {
                    Text(
                        text = "★ %.1f".format(it),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold, fontSize = 8.sp
                        ),
                        color = GoldStar,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.6f),
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = AccentSecondary.copy(alpha = if (isFocused) 0.8f else 0.4f),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        year?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = Color.White.copy(alpha = 0.3f)
            )
        }
    }
}

// ════════════════════════════════════════════════════════
// HELPERS
// ════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PersonStat(label: String, value: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White.copy(alpha = 0.9f)
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 1.sp),
            color = Color.White.copy(alpha = 0.35f)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PersonSectionTitle(title: String) {
    Row(
        modifier = Modifier.padding(horizontal = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 13.sp
            ),
            color = Color.White.copy(alpha = 0.55f)
        )
        Spacer(Modifier.width(16.dp))
        Box(
            modifier = Modifier.weight(1f).height(1.dp)
                .background(Brush.horizontalGradient(
                    colors = listOf(SurfaceGlassBorder, Color.Transparent)
                ))
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PersonLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("LOADING", style = MaterialTheme.typography.labelMedium.copy(
            letterSpacing = 4.sp, fontWeight = FontWeight.Light
        ), color = Color.White.copy(alpha = 0.3f))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PersonError(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = Color.Red.copy(alpha = 0.8f))
    }
}
