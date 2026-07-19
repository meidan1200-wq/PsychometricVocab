package com.example.psychometricvocab

import com.example.psychometricvocab.data.Word
import com.example.psychometricvocab.data.SrsEngine
import org.junit.Test
import org.junit.Assert.*

class SrsEngineTest {
    @Test
    fun testWordSelectionAlgorithm() {
        val words = listOf(
            Word(1, "track", "A (easy)", "def", 1, 1.5f, 0, 5, 0, 0, true),
            Word(2, "track", "B (hard)", "def", 1, 0.5f, 0, 1, 4, 0, false),
            Word(3, "track", "C (medium)", "def", 1, 0.8f, 0, 2, 1, 0, false),
            Word(4, "track", "D (very hard)", "def", 1, 0.2f, 0, 0, 5, 0, false),
            Word(5, "track", "E (new)", "def", 1, 0.0f, 0, 0, 0, 0, false),
            Word(6, "track", "F (missed once)", "def", 1, 0.9f, 0, 4, 1, 0, false)
        )

        val selected = SrsEngine.selectWordsForSession(words, 6)
        
        // Expected order: D, B, C, F, E, A
        assertEquals("D (very hard)", selected[0].word)
        assertEquals("B (hard)", selected[1].word)
        assertEquals("C (medium)", selected[2].word)
        assertEquals("F (missed once)", selected[3].word)
        assertEquals("E (new)", selected[4].word)
        assertEquals("A (easy)", selected[5].word)
    }
}
