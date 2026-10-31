package fixture

// Every line marked BAD must be reported, and nothing else.
val a = Color(0xFF0E0F11) // BAD
val b = Color(0.1f, 0.2f, 0.3f) // BAD
val c = Color(red = 0.5f, green = 0.5f, blue = 0.5f) // BAD
val d = Color.Red // BAD
val e = android.graphics.Color.BLACK // BAD
val f = android.graphics.Color.argb(255, 0, 0, 0) // BAD
val g = android.graphics.Color.parseColor("#FF0000") // BAD
val h = "#0E0F11" // BAD
val i = 16.dp // BAD
val j = 12.sp // BAD
val k = 1.5.dp // BAD
val l = Dp(4f) // BAD
val m = 0.sp // BAD
val n = Color(-16777216) // BAD
val brand = 0xFF0E0F11 // BAD
val brandWithSeparators = 0xFF_0E_0F_11 // BAD
val o = (8).dp // BAD
val p = Dp(value = 4f) // BAD
val q = TextUnit(14f, TextUnitType.Sp) // BAD
val r = 1.5.em // BAD
val s = Color( // BAD
    0xFF0E0F11, // BAD
)
