package com.plumemu.scraper.work

import com.plumemu.scraper.work.ScrapeRun.End

/** The worse of two stops of a [ScrapeRun]: Stopped, then Paused (the later resume), then Retry, then Continue. */
internal fun worseStop(current: End?, new: End): End {
    if (current == null) return new
    if (current is End.Paused && new is End.Paused) return if (new.resumeAtMillis > current.resumeAtMillis) new else current
    return if (rank(new) > rank(current)) new else current
}

private fun rank(end: End): Int = when (end) {
    End.Done -> 0
    End.Continue, End.HeldOff -> 1
    End.Retry -> 2
    is End.Paused -> 3
    End.Stopped -> 4
}
