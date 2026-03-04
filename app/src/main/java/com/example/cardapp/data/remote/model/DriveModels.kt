package com.example.cardapp.data.remote.model

data class DriveFileList(
    val files: List<DriveFile> = emptyList(),
    val nextPageToken: String?,
)

data class DriveFile(
    val id: String,
    val name: String,
)
