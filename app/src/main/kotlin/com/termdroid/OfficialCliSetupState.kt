package com.termdroid

import com.termdroid.rootfs.OfficialCliVersions

sealed interface OfficialCliSetupState {
    data object Checking : OfficialCliSetupState

    data class ActionRequired(val message: String) : OfficialCliSetupState

    data class Preparing(val message: String) : OfficialCliSetupState

    data class Ready(val versions: OfficialCliVersions) : OfficialCliSetupState

    data class Failed(val message: String) : OfficialCliSetupState
}

internal fun setupState(readinessError: String?): OfficialCliSetupState =
    readinessError?.let(OfficialCliSetupState::ActionRequired) ?: OfficialCliSetupState.Checking
