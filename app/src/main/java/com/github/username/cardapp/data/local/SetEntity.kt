package com.github.username.cardapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sets")
data class SetEntity(
    @PrimaryKey val name: String,
    val releasedAt: String,
    val releaseOrder: Int,
)
