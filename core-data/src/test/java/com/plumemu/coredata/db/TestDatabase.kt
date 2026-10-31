package com.plumemu.coredata.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider

internal fun inMemoryDatabase(): PlumDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java)
        .allowMainThreadQueries()
        .build()

internal fun game(
    folderId: Long,
    documentId: String,
    systemId: String = "snes",
    title: String = documentId,
    parent: String = "root",
    relativePath: String = documentId,
    isPrimaryCopy: Boolean = true,
) = GameEntity(
    folderId = folderId,
    documentId = documentId,
    parentDocumentId = parent,
    relativePath = relativePath,
    fileName = documentId,
    sizeBytes = 1,
    modifiedAt = 1,
    title = title,
    sortName = title.lowercase(),
    regions = "",
    discGroup = null,
    discCount = 1,
    systemId = systemId,
    isPrimaryCopy = isPrimaryCopy,
)
