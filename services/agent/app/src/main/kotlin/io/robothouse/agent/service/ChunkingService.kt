package io.robothouse.agent.service

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.EncodingType
import org.springframework.stereotype.Service

/**
 * Splits text content into token-bounded chunks for embedding.
 *
 * Uses a structural splitting algorithm: first splits on markdown headings,
 * then on paragraph breaks, then on sentence boundaries. Accumulates fragments
 * into chunks of approximately [targetTokens] tokens with [overlapTokens] token
 * overlap between consecutive chunks.
 */
@Service
class ChunkingService {

    private val encoding by lazy {
        Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE)
    }

    /**
     * Splits [content] into chunks targeting [targetTokens] tokens each,
     * with [overlapTokens] token overlap between consecutive chunks.
     * Returns a single-element list if the content fits within one chunk.
     */
    fun chunk(content: String, targetTokens: Int, overlapTokens: Int): List<String> {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return emptyList()

        if (countTokens(trimmed) <= targetTokens) return listOf(trimmed)

        val fragments = splitStructurally(trimmed)
        return assembleChunks(fragments, targetTokens, overlapTokens)
    }

    /**
     * Splits text structurally: first by markdown headings, then by paragraphs,
     * then by sentences for fragments that are still too large.
     */
    private fun splitStructurally(text: String): List<String> {
        // Split on markdown headings (keep heading with its section)
        val sections = text.split(Regex("(?=^#{1,6}\\s)", RegexOption.MULTILINE))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val fragments = mutableListOf<String>()
        for (section in sections) {
            // Split sections on paragraph breaks
            val paragraphs = section.split(Regex("\\n\\s*\\n"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            for (paragraph in paragraphs) {
                fragments.add(paragraph)
            }
        }

        return fragments
    }

    /**
     * Assembles fragments into chunks respecting token limits and overlap.
     */
    private fun assembleChunks(fragments: List<String>, targetTokens: Int, overlapTokens: Int): List<String> {
        val chunks = mutableListOf<String>()
        var currentParts = mutableListOf<String>()
        var currentTokenCount = 0

        for (fragment in fragments) {
            val fragmentTokens = countTokens(fragment)

            // If a single fragment exceeds target, split it by sentences
            if (fragmentTokens > targetTokens) {
                // Flush current accumulator
                if (currentParts.isNotEmpty()) {
                    chunks.add(currentParts.joinToString("\n\n"))
                    currentParts = getOverlapParts(currentParts, overlapTokens)
                    currentTokenCount = currentParts.sumOf { countTokens(it) }
                }
                // Split large fragment by sentences
                val sentences = splitBySentence(fragment)
                for (sentence in sentences) {
                    val sentenceTokens = countTokens(sentence)
                    if (currentTokenCount + sentenceTokens > targetTokens && currentParts.isNotEmpty()) {
                        chunks.add(currentParts.joinToString(" "))
                        currentParts = getOverlapParts(currentParts, overlapTokens)
                        currentTokenCount = currentParts.sumOf { countTokens(it) }
                    }
                    currentParts.add(sentence)
                    currentTokenCount += sentenceTokens
                }
                continue
            }

            if (currentTokenCount + fragmentTokens > targetTokens && currentParts.isNotEmpty()) {
                chunks.add(currentParts.joinToString("\n\n"))
                currentParts = getOverlapParts(currentParts, overlapTokens)
                currentTokenCount = currentParts.sumOf { countTokens(it) }
            }

            currentParts.add(fragment)
            currentTokenCount += fragmentTokens
        }

        if (currentParts.isNotEmpty()) {
            val lastChunk = currentParts.joinToString("\n\n")
            // Merge small trailing chunk with previous if possible
            if (chunks.isNotEmpty() && countTokens(lastChunk) < overlapTokens) {
                val prev = chunks.removeAt(chunks.lastIndex)
                chunks.add("$prev\n\n$lastChunk")
            } else {
                chunks.add(lastChunk)
            }
        }

        return chunks
    }

    /**
     * Returns trailing parts from [parts] whose combined token count
     * is at most [overlapTokens], for use as overlap in the next chunk.
     */
    private fun getOverlapParts(parts: List<String>, overlapTokens: Int): MutableList<String> {
        if (overlapTokens <= 0) return mutableListOf()

        val result = mutableListOf<String>()
        var tokens = 0
        for (part in parts.reversed()) {
            val partTokens = countTokens(part)
            if (tokens + partTokens > overlapTokens) break
            result.add(0, part)
            tokens += partTokens
        }
        return result
    }

    /**
     * Splits text on sentence boundaries (period/question mark/exclamation mark
     * followed by whitespace or end of string).
     */
    private fun splitBySentence(text: String): List<String> {
        return text.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun countTokens(text: String): Int {
        return encoding.countTokens(text)
    }
}
