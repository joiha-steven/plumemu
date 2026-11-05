package com.plumemu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.flowOf

/**
 * Whether setup is shown again for the questions "Set up later" skipped (`SetupFlags.setupQuestionsSkipped`):
 * a folder exists and the person is [isOnHome] (not in Settings), then setup reopens once and stays until it is left
 * (finishing clears the flag; left unanswered, it may come back at the next start). A person who never adds a folder is never asked.
 */
@Composable
internal fun reopensSkippedSetup(
    setup: SetupHost?,
    isSkipped: Boolean,
    isOnHome: Boolean,
): Boolean {
    // Not saved: after a process death the model is new and is reopened properly.
    var isOpen by remember { mutableStateOf(false) }
    // Read only while the flag is set: a person past setup never builds the model.
    val model = remember(setup, isSkipped) { if (isSkipped) setup?.model else null }
    val hasFolder by remember(model) { model?.hasFolder ?: flowOf(false) }.collectAsStateWithLifecycle(false)
    val hasLeft by remember(model) { model?.hasLeftReopened ?: flowOf(false) }.collectAsStateWithLifecycle(false)
    LaunchedEffect(isSkipped) { if (!isSkipped) isOpen = false }
    LaunchedEffect(model, hasFolder, isOnHome, isOpen, hasLeft) {
        if (model != null && !isOpen && !hasLeft && hasFolder && isOnHome) {
            model.reopenForSkippedQuestions()
            isOpen = true
        }
    }
    return isSkipped && isOpen && !hasLeft
}
