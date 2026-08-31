package com.termdroid

import org.junit.Assert.assertEquals
import org.junit.Test

class OfficialCliSetupStateTest {

    @Test
    fun muestraAccionCuandoFaltaUnRequisito() {
        val state = setupState("Permití el acceso a Termux.")

        assertEquals(
            OfficialCliSetupState.ActionRequired("Permití el acceso a Termux."),
            state,
        )
    }

    @Test
    fun verificaCuandoNoHayRequisitosPendientes() {
        assertEquals(OfficialCliSetupState.Checking, setupState(null))
    }
}
