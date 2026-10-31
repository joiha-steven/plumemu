package fixture

suspend fun inTest(): Int =
    try {
        1
    } catch (e: Exception) {
        0
    }
