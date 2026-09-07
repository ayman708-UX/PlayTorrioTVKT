package androidx.media3.common

class PlayTorrioEngineConfig {
    companion object {
        @JvmStatic fun playtorrioMode(): PlayTorrioEngineConfig = PlayTorrioEngineConfig()
        @JvmStatic fun stockMode(): PlayTorrioEngineConfig = PlayTorrioEngineConfig()
        @JvmStatic fun set(config: PlayTorrioEngineConfig) {}
        @JvmStatic fun get(): PlayTorrioEngineConfig = PlayTorrioEngineConfig()
    }
}
