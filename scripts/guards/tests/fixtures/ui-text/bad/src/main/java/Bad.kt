package fixture

// Every line marked must be reported, and nothing else.
@Composable
fun Bad(count: Int, context: Context, isOn: Boolean) {
    Text("Hello") // BAD
    BasicText("Plumemu") // BAD
    Text(text = "Settings") // BAD
    Image(painter, contentDescription = "Cover art") // BAD
    Text("${count} games") // BAD
    Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show() // BAD
    Text(
        """Raw text""" // BAD
    )
    Text(if (isOn) "Yes" else "No") // BAD
    Text(stringResource(R.string.home_title) + " (beta)") // BAD
    BasicText(
        text = stringResource(R.string.home_title),
        modifier = Modifier.padding(PlumTheme.spacing.row),
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        modifier = Modifier.testTag("title"),
        text = when (count) {
            0 -> "Nothing here" // BAD
            else -> stringResource(R.string.home_title)
        },
    )
    Box(Modifier.semantics { contentDescription = "Focused row" }) // BAD
    Box(Modifier.semantics { stateDescription = "On" }) // BAD
    Box(Modifier.clickable(onClickLabel = "Play", onClick = {})) // BAD
}

suspend fun notify(host: SnackbarHostState) {
    host.showSnackbar(
        message = "Folder removed", // BAD
        actionLabel = stringResource(R.string.undo),
    )
}
