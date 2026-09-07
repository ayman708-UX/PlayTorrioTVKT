package com.playtorrio.tv.core.iptv.context

import com.playtorrio.tv.core.iptv.channels.HardcodedChannel
import com.playtorrio.tv.core.iptv.channels.HardcodedChannels
import com.playtorrio.tv.core.iptv.model.IptvStream
import com.playtorrio.tv.core.iptv.model.VerifiedPortal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class IptvLiveContext {
    data class Portal(
        val portal: VerifiedPortal,
        val categoryName: String,
        val currentStreamId: String,
        val channels: List<IptvStream>
    ) : IptvLiveContext()

    data class Hardcoded(
        val currentChannelId: String,
        val allChannels: List<HardcodedChannel>
    ) : IptvLiveContext()
}

object IptvChannelContextHolder {
    private val _currentContext = MutableStateFlow<IptvLiveContext?>(null)
    val currentContext: StateFlow<IptvLiveContext?> = _currentContext.asStateFlow()

    fun setPortalContext(
        portal: VerifiedPortal,
        categoryName: String,
        currentStreamId: String,
        channels: List<IptvStream>
    ) {
        _currentContext.value = IptvLiveContext.Portal(
            portal = portal,
            categoryName = categoryName,
            currentStreamId = currentStreamId,
            channels = channels
        )
    }

    fun setHardcodedContext(
        currentChannelId: String,
        allChannels: List<HardcodedChannel> = HardcodedChannels.all
    ) {
        _currentContext.value = IptvLiveContext.Hardcoded(
            currentChannelId = currentChannelId,
            allChannels = allChannels
        )
    }

    fun updateCurrentStreamId(streamId: String) {
        val ctx = _currentContext.value
        if (ctx is IptvLiveContext.Portal) {
            _currentContext.value = ctx.copy(currentStreamId = streamId)
        }
    }

    fun updateCurrentHardcodedId(channelId: String) {
        val ctx = _currentContext.value
        if (ctx is IptvLiveContext.Hardcoded) {
            _currentContext.value = ctx.copy(currentChannelId = channelId)
        }
    }

    fun clearContext() {
        _currentContext.value = null
    }

    fun hasContext(): Boolean = _currentContext.value != null
}
