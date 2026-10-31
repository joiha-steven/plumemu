package fixture

// Text("in a comment") is not code.
@Composable
fun Good(count: Int) {
    Text(stringResource(R.string.home_title))
    BasicText(text = stringResource(R.string.home_title))
    Text("$count")
    Text(" · ")
    Icon(painter, contentDescription = null)
    val tag = "Text(\"inside a string\")"
    Log.d(TAG, "Logs are not UI text")
    val text = "A declaration, not UI text"
    var contentDescription = "Also a declaration"
    Text(
        text = stringResource(R.string.home_title),
        modifier = Modifier.testTag("title"),
    )
    if (text == "match") Text(stringResource(R.string.home_title))
}
