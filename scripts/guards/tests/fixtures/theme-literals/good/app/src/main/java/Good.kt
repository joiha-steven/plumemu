package fixture

// Color(0xFF000000) and 16.dp in a comment are not code.
@Composable
fun Good() {
    Box(Modifier.background(Color.Transparent).padding(0.dp).size(PlumTheme.spacing.row))
    val unset = Color.Unspecified
    val bar = android.graphics.Color.TRANSPARENT
    val label = "16.dp is text here"
    val width = columns.dp
}

val issue = "Issue #123456"
val mask = 0xFF
val flags = 0x00FF00
val wide = 0x1234567890L
val template = "${16}.dp"
val fromToken = Dp(PlumTheme.spacing.row.value)
