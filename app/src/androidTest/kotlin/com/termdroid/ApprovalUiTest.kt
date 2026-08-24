package com.termdroid

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ApprovalUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val comandoPeligroso = "rm -rf /data/data/com.termdroid/files/workspace"

    @Test
    fun laAprobacionMuestraElComandoExactoYNoUnResumen() {
        compose.setContent {
            TermdroidTheme {
                ApprovalCard(
                    pending = PendingApproval("t1", "bash", comandoPeligroso),
                    onDecide = {},
                )
            }
        }

        compose.onNodeWithText(comandoPeligroso, substring = true).assertIsDisplayed()
        compose.onNodeWithText("bash", substring = true).assertIsDisplayed()
    }

    @Test
    fun aprobarYRechazarDevuelvenLoQueCorresponde() {
        val decisiones = mutableListOf<Boolean>()
        compose.setContent {
            TermdroidTheme {
                ApprovalCard(
                    pending = PendingApproval("t1", "bash", "ls"),
                    onDecide = { decisiones += it },
                )
            }
        }

        compose.onNodeWithText("Aprobar").performClick()
        compose.onNodeWithText("Rechazar").performClick()

        assertEquals(listOf(true, false), decisiones)
    }

    @Test
    fun laTarjetaDelToolMuestraNombreYEstado() {
        compose.setContent {
            TermdroidTheme {
                ToolCardView(
                    ChatItem.ToolCard(
                        id = 1,
                        toolUseId = "t1",
                        name = "write_file",
                        description = "escribir notas.txt (12 bytes)",
                        status = ToolStatus.ERROR,
                        output = "No se pudo escribir",
                    ),
                )
            }
        }

        compose.onNodeWithText("write_file").assertIsDisplayed()
        compose.onNodeWithText("error").assertIsDisplayed()
        compose.onNodeWithText("escribir notas.txt", substring = true).assertIsDisplayed()
    }

    @Test
    fun unRechazoSeVeComoRechazado() {
        compose.setContent {
            TermdroidTheme {
                ToolCardView(
                    ChatItem.ToolCard(
                        id = 1,
                        toolUseId = "t1",
                        name = "bash",
                        description = "rm -rf /",
                        status = ToolStatus.RECHAZADO,
                    ),
                )
            }
        }

        compose.onNodeWithText("rechazado").assertIsDisplayed()
        compose.onNodeWithText("rm -rf /", substring = true).assertIsDisplayed()
    }

    @Test
    fun elPensamientoArrancaPlegado() {
        compose.setContent {
            TermdroidTheme {
                ChatBubble(ChatItem.Thinking(1, "razonamiento largo que no deberia verse"))
            }
        }

        compose.onNodeWithText("pensando", substring = true).assertIsDisplayed()
        compose.onNodeWithText("razonamiento largo", substring = true).assertDoesNotExist()
    }

    @Test
    fun elPensamientoSeAbreAlTocarlo() {
        compose.setContent {
            TermdroidTheme {
                ChatBubble(ChatItem.Thinking(1, "razonamiento visible"))
            }
        }

        compose.onNodeWithText("pensando", substring = true).performClick()
        compose.onNodeWithText("razonamiento visible", substring = true).assertIsDisplayed()
    }
}
