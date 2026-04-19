package com.playtorrio.tv.ui.screens

/**
 * Session-scoped flag so the in-app update popup is shown at most once per
 * app process launch (HomeScreen recomposes on every back-nav return).
 */
internal object UpdatePromptShownThisSession {
    var value: Boolean = false
}
