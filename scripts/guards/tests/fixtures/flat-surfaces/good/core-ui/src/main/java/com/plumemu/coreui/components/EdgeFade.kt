package com.plumemu.coreui.components

fun DrawScope.fade(clear: Color, opaque: Color) = drawRect(Brush.verticalGradient(listOf(clear, opaque)))
