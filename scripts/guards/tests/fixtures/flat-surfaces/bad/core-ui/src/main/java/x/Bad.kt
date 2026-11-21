package x

fun DrawScope.face(top: Color, bottom: Color) {
    drawRect(Brush.verticalGradient(listOf(top, bottom))) // BAD
    drawRect(Brush.radialGradient(listOf(top, bottom))) // BAD
    val shader = android.graphics.LinearGradient(0f, 0f, 0f, 1f, 0, 0, Shader.TileMode.CLAMP) // BAD
    drawRect(
        Brush // BAD
            .horizontalGradient(listOf(top, bottom)),
    )
}
