package com.plumemu.coreui.testing

import com.plumemu.coreui.components.BackgroundBuilds

/** How many background image builds still wait or run: zero means every landing is already posted to the main looper (other modules' tests). */
fun imageBuildsWaiting(): Int = BackgroundBuilds.waiting
