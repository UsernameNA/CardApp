package com.github.username.cardapp.ui.scan

sealed class ScanStatus {
    data object Idle : ScanStatus()
    data object Scanning : ScanStatus()
    data object NotFound : ScanStatus()
    data class Found(val cardName: String) : ScanStatus()
    data object AutoWatching : ScanStatus()
    data object AutoCooldown : ScanStatus()
}
