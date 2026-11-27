package com.github.username.cardapp.ui.landing

internal sealed interface DiceState {
    data object Idle : DiceState
    data class Rolling(val guess: Parity) : DiceState
    data class Result(val guess: Parity, val value: Int) : DiceState
}
