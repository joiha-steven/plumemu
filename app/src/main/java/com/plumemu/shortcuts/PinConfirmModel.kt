package com.plumemu.shortcuts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope

/**
 * Keeps the confirm screen's state across a recreated Activity (a rotation, a controller plugged
 * in): the request is read once, and an add that is running when the Activity goes is still
 * answered on the new one. Without it the new Activity read the request again, found it answered
 * and said "can no longer be added" although the game was added.
 */
class PinConfirmModel(actions: PinConfirmActions) : ViewModel() {
    val state: PinConfirmState = PinConfirmState(actions, viewModelScope).also { it.start() }

    /** Makes the model with [actions] the first time; a recreated Activity gets the same model. */
    class Factory(private val actions: () -> PinConfirmActions) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == PinConfirmModel::class.java) { "Unknown model ${modelClass.name}" }
            @Suppress("UNCHECKED_CAST") // modelClass was checked to be PinConfirmModel just above.
            return PinConfirmModel(actions()) as T
        }
    }
}
