package io.robothouse.agent.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChunkingServiceTest {

    private val chunkingService = ChunkingService()

    @Test
    fun `returns empty list for empty content`() {
        val result = chunkingService.chunk("", 500, 50)
        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `returns single chunk when content fits within target tokens`() {
        val content = "This is a short piece of content."
        val result = chunkingService.chunk(content, 500, 50)

        assertEquals(1, result.size)
        assertEquals(content, result[0])
    }

    @Test
    fun `splits long content into multiple chunks`() {
        val paragraphs = (1..20).map { "This is paragraph number $it with enough text to make it meaningful for testing purposes. It contains several sentences. Each sentence adds more tokens to the overall count." }
        val content = paragraphs.joinToString("\n\n")

        val result = chunkingService.chunk(content, 100, 10)

        assertTrue(result.size > 1, "Expected multiple chunks but got ${result.size}")
    }

    @Test
    fun `splits on markdown headings`() {
        val section1 = (1..10).joinToString(" ") { "Word$it in section one makes this longer." }
        val section2 = (1..10).joinToString(" ") { "Word$it in section two makes this longer." }
        val content = "## Section One\n$section1\n\n## Section Two\n$section2"

        val result = chunkingService.chunk(content, 30, 0)

        assertTrue(result.size >= 2, "Expected at least 2 chunks but got ${result.size}")
        assertTrue(result[0].contains("Section One"))
    }

    @Test
    fun `preserves content integrity across chunks`() {
        val sentences = (1..30).map { "Sentence number $it is here." }
        val content = sentences.joinToString(" ")

        val result = chunkingService.chunk(content, 50, 0)

        assertTrue(result.size > 1, "Expected multiple chunks")
        // All original sentences should appear in at least one chunk
        for (sentence in sentences) {
            assertTrue(result.any { it.contains(sentence) }, "Missing sentence: $sentence")
        }
    }

    @Test
    fun `handles whitespace-only content`() {
        val result = chunkingService.chunk("   \n\n   ", 500, 50)
        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `overlap creates content shared between adjacent chunks`() {
        val paragraphs = (1..20).map { "Paragraph $it contains specific unique content for testing overlap behavior in the chunking algorithm." }
        val content = paragraphs.joinToString("\n\n")

        val result = chunkingService.chunk(content, 80, 30)

        if (result.size >= 2) {
            // With overlap, consecutive chunks should share some text
            var hasOverlap = false
            for (i in 0 until result.size - 1) {
                val currentWords = result[i].split("\\s+".toRegex()).toSet()
                val nextWords = result[i + 1].split("\\s+".toRegex()).toSet()
                if (currentWords.intersect(nextWords).isNotEmpty()) {
                    hasOverlap = true
                    break
                }
            }
            assertTrue(hasOverlap, "Expected overlap between adjacent chunks")
        }
    }
}
