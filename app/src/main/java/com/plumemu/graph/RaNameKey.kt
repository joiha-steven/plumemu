package com.plumemu.graph

import com.plumemu.identify.name.MatchKey
import com.plumemu.identify.name.TitleCleaner

/**
 * The key a game name is compared by when a RetroAchievements game is looked for by name: the
 * title cleaned of tags and regions, then the library's own title key ([MatchKey]), so a file name
 * and a RetroAchievements title that read the same give the same key.
 */
internal fun raNameKey(name: String): String = MatchKey.of(TitleCleaner.clean(name).title)
