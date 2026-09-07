package com.playtorrio.tv.ui.screens.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.playtorrio.tv.core.iptv.channels.HardcodedChannel
import com.playtorrio.tv.core.iptv.channels.HardcodedChannels
import com.playtorrio.tv.core.iptv.model.ChannelHit

@Composable
fun IptvHomeScreen(
    viewModel: IptvViewModel,
    onOpenPortals: () -> Unit,
    onPlayStream: (streamUrl: String, title: String) -> Unit
) {
    val isScraping by viewModel.isScraping.collectAsState()
    val statusText by viewModel.statusText.collectAsState()
    val verifiedPortals by viewModel.verifiedPortals.collectAsState()
    val canGetMore by viewModel.canGetMore.collectAsState()

    var selectedChannelForSources by remember { mutableStateOf<HardcodedChannel?>(null) }

    val featuredChannels = remember {
        listOfNotNull(
            HardcodedChannels.byId("ufc"),
            HardcodedChannels.byId("champions_league"),
            HardcodedChannels.byId("espn_plus"),
            HardcodedChannels.byId("f1"),
            HardcodedChannels.byId("nba"),
            HardcodedChannels.byId("bein_sports")
        )
    }

    val combatChannels = remember { HardcodedChannels.byCategory("Combat") }
    val premierChannels = remember { HardcodedChannels.byCategory("Premier") }
    val usSportsChannels = remember { HardcodedChannels.byCategory("US Sports") }
    val soccerChannels = remember { HardcodedChannels.byCategory("Soccer") }
    val racingChannels = remember { HardcodedChannels.byCategory("Racing") }
    val movieChannels = remember { HardcodedChannels.byCategory("Movies") }
    val newsChannels = remember { HardcodedChannels.byCategory("News") }
    val arabicChannels = remember { HardcodedChannels.byCategory("Arabic") }
    val discoveryChannels = remember { HardcodedChannels.byCategory("Discovery") }
    val kidsChannels = remember { HardcodedChannels.byCategory("Kids") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {
            // Top Action Bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(end = 16.dp)
                    ) {
                        Text(
                            text = "PlayTorrio Live IPTV",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isScraping) statusText else "Community Live Portals (${verifiedPortals.size} active)",
                            color = if (isScraping) Color(0xFF38BDF8) else Color(0x88FFFFFF),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onOpenPortals,
                            modifier = Modifier.height(38.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Portals (${verifiedPortals.size})", fontSize = 12.sp, maxLines = 1)
                        }

                        if (canGetMore && !isScraping) {
                            Button(
                                onClick = { viewModel.getMorePortals() },
                                modifier = Modifier.height(38.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Get More", fontSize = 12.sp, maxLines = 1)
                            }
                        }

                        Button(
                            onClick = { viewModel.scrapePortals(reset = true) },
                            modifier = Modifier.height(38.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            enabled = !isScraping,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isScraping) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isScraping) "Scraping..." else "Scrape Portals", fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }

            // 1. Spotlight Hero Carousel
            item {
                SpotlightHeroCarousel(
                    featuredChannels = featuredChannels,
                    onWatchNow = { channel ->
                        viewModel.openChannel(channel)
                        selectedChannelForSources = channel
                    },
                    onSourcesTap = { channel ->
                        viewModel.openChannel(channel)
                        selectedChannelForSources = channel
                    }
                )
            }

            // 2. Curated Slider Sections
            item {
                IptvCategoryRow(
                    title = "Combat & Martial Arts",
                    subtitle = "UFC Fight Pass, WWE, AEW, World Boxing & PPV",
                    channels = combatChannels,
                    onChannelClick = { channel ->
                        viewModel.openChannel(channel)
                        selectedChannelForSources = channel
                    }
                )
            }

            item {
                IptvCategoryRow(
                    title = "ESPN & College Basketball (NCAA)",
                    subtitle = "ESPN+, ESPN, ESPN2, ESPNU, NCAA Men's & Women's CBB, SEC & ACC",
                    channels = premierChannels,
                    onChannelClick = { channel ->
                        viewModel.openChannel(channel)
                        selectedChannelForSources = channel
                    }
                )
            }

            item {
                IptvCategoryRow(
                    title = "US Major Leagues & Sports",
                    subtitle = "NBA TV, NFL Network, RedZone, MLB, NHL, Fox Sports & CBS Sports",
                    channels = usSportsChannels,
                    onChannelClick = { channel ->
                        viewModel.openChannel(channel)
                        selectedChannelForSources = channel
                    }
                )
            }

            item {
                IptvCategoryRow(
                    title = "Global Football & Soccer",
                    subtitle = "UEFA Champions League, Premier League, beIN Sports, TNT Sports",
                    channels = soccerChannels,
                    onChannelClick = { channel ->
                        viewModel.openChannel(channel)
                        selectedChannelForSources = channel
                    }
                )
            }

            item {
                IptvCategoryRow(
                    title = "Motorsport & Racing",
                    subtitle = "Formula 1, MotoGP, NASCAR Cup, IndyCar",
                    channels = racingChannels,
                    onChannelClick = { channel ->
                        viewModel.openChannel(channel)
                        selectedChannelForSources = channel
                    }
                )
            }

            item {
                IptvCategoryRow(
                    title = "Movies & Premium Networks",
                    subtitle = "HBO, Showtime, Starz, Cinemax",
                    channels = movieChannels,
                    onChannelClick = { channel ->
                        viewModel.openChannel(channel)
                        selectedChannelForSources = channel
                    }
                )
            }

            item {
                IptvCategoryRow(
                    title = "24/7 Global News Networks",
                    subtitle = "CNN, BBC World, Fox News, Sky News, Al Jazeera",
                    channels = newsChannels,
                    onChannelClick = { channel ->
                        viewModel.openChannel(channel)
                        selectedChannelForSources = channel
                    }
                )
            }

            item {
                IptvCategoryRow(
                    title = "Arabic & Regional Hub",
                    subtitle = "beIN Sports Premium, SSC Sports, MBC",
                    channels = arabicChannels,
                    onChannelClick = { channel ->
                        viewModel.openChannel(channel)
                        selectedChannelForSources = channel
                    }
                )
            }

            item {
                IptvCategoryRow(
                    title = "Discovery & Documentaries",
                    subtitle = "National Geographic, Discovery Channel",
                    channels = discoveryChannels,
                    onChannelClick = { channel ->
                        viewModel.openChannel(channel)
                        selectedChannelForSources = channel
                    }
                )
            }

            item {
                IptvCategoryRow(
                    title = "Kids & Family",
                    subtitle = "Cartoon Network, Disney Channel",
                    channels = kidsChannels,
                    onChannelClick = { channel ->
                        viewModel.openChannel(channel)
                        selectedChannelForSources = channel
                    }
                )
            }
        }

        // Live Channel Sources Dialog
        selectedChannelForSources?.let { channel ->
            IptvChannelSourcesDialog(
                viewModel = viewModel,
                channel = channel,
                onDismiss = { selectedChannelForSources = null },
                onPlayHit = { hit ->
                    selectedChannelForSources = null
                    onPlayStream(hit.streamUrl, "${channel.name} (${hit.portal.name})")
                }
            )
        }
    }
}
