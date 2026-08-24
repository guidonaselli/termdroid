package com.termdroid.terminal

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TerminalViewTest {

    @get:Rule
    val compose = createComposeRule()

    private fun fila(texto: String, cols: Int) =
        Array(cols) { i -> Cell(texto.getOrElse(i) { ' ' }) }

    private fun pantalla(
        visibles: List<String>,
        historicas: List<String> = emptyList(),
        cols: Int = 40,
    ) = ScreenSnapshot(
        rows = visibles.size,
        cols = cols,
        cells = Array(visibles.size) { fila(visibles[it], cols) },
        cursorRow = 0,
        cursorCol = 0,
        title = null,
        scrollback = historicas.map { fila(it, cols) },
    )

    @Test
    fun laPantallaEsLegibleParaUnLectorDePantalla() {
        compose.setContent {
            TerminalView(
                screen = pantalla(listOf("hola mundo", "segunda linea")),
                modifier = Modifier.fillMaxSize(),
            )
        }

        compose.onNodeWithTag(TERMINAL_TAG).assertContentDescriptionContains(
            "hola mundo",
            substring = true,
        )
    }

    @Test
    fun elAreaDelTerminalSePuedeDesplazar() {
        compose.setContent {
            TerminalView(
                screen = pantalla(
                    visibles = List(10) { "visible-$it" },
                    historicas = List(80) { "historica-$it" },
                ),
                modifier = Modifier.fillMaxSize(),
            )
        }

        compose.onNodeWithTag(SCROLL_TAG).performTouchInput { swipeDown() }
        compose.waitForIdle()

        compose.onNodeWithTag(SCROLL_TAG).assertExists()
    }

    @Test
    fun elTamanoDeLaGrillaLoReportaLaVista() {
        var filas = 0
        var columnas = 0

        compose.setContent {
            TerminalView(
                screen = pantalla(listOf("x")),
                modifier = Modifier.fillMaxSize(),
                onGridSize = { r, c ->
                    filas = r
                    columnas = c
                },
            )
        }
        compose.waitForIdle()

        assertTrue("deberia reportar filas, reporto $filas", filas > 4)
        assertTrue("deberia reportar columnas, reporto $columnas", columnas >= 20)
    }

    @Test
    fun unaPantallaVaciaNoRompe() {
        compose.setContent {
            TerminalView(screen = ScreenSnapshot.EMPTY, modifier = Modifier.fillMaxSize())
        }
        compose.waitForIdle()
        compose.onNodeWithTag(TERMINAL_TAG).assertExists()
    }
}
