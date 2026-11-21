package x

fun DrawScope.sample(top: Color, bottom: Color) = drawRect(Brush.verticalGradient(listOf(top, bottom)))
