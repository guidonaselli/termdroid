package com.termdroid.tools.unix

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobTest {

    private fun matches(pattern: String, path: String) = globToRegex(pattern).matches(path)

    @Test
    fun asteriscoSimpleNoCruzaDirectorios() {
        assertTrue(matches("*.kt", "Main.kt"))
        assertFalse(matches("*.kt", "src/Main.kt"))
    }

    @Test
    fun dobleAsteriscoCruzaDirectorios() {
        assertTrue(matches("**/*.kt", "src/main/Main.kt"))
        assertTrue(matches("**/*.kt", "Main.kt"))
    }

    @Test
    fun patronConDirectorioFijo() {
        assertTrue(matches("src/**/*.kt", "src/a/b/C.kt"))
        assertFalse(matches("src/**/*.kt", "test/a/C.kt"))
    }

    @Test
    fun signoDePreguntaEsUnCaracter() {
        assertTrue(matches("a?c.txt", "abc.txt"))
        assertFalse(matches("a?c.txt", "ac.txt"))
        assertFalse(matches("a?c.txt", "a/c.txt"))
    }

    /** El punto es literal, no "cualquier caracter". */
    @Test
    fun elPuntoEsLiteral() {
        assertTrue(matches("a.txt", "a.txt"))
        assertFalse(matches("a.txt", "axtxt"))
    }

    @Test
    fun caracteresDeRegexNoSeInterpretan() {
        assertTrue(matches("a+b.txt", "a+b.txt"))
        assertFalse(matches("a+b.txt", "aab.txt"))
        assertTrue(matches("(x).txt", "(x).txt"))
    }
}
