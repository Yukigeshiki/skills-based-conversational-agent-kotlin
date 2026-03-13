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

    companion object {
        /**
         * Common abbreviations that should not trigger sentence splits.
         * Checked case-insensitively against the token preceding a period.
         */
        private val ABBREVIATIONS = setOf(
            "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "ave", "blvd",
            "gen", "gov", "sgt", "cpl", "pvt", "capt", "lt", "col", "maj",
            "dept", "univ", "assn", "bros", "inc", "ltd", "co", "corp",
            "vs", "etc", "approx", "appt", "apt", "dept", "est", "min",
            "max", "misc", "tech", "temp", "vol", "calif", "fig", "eq",
            "no", "nos", "op", "cit", "vol", "rev", "jan", "feb", "mar",
            "apr", "jun", "jul", "aug", "sep", "sept", "oct", "nov", "dec",
            "ed", "trans", "e", "i"
        )

        /**
         * Pattern matching sentence-ending punctuation followed by whitespace,
         * used as candidate split points. Actual splitting is refined by
         * checking for abbreviations and numeric contexts.
         */
        private val SENTENCE_BOUNDARY_CANDIDATES = Regex("([.!?])\\s+")
    }

    /**
     * Splits text on sentence boundaries while preserving abbreviations,
     * decimal numbers, and other common non-sentence-ending periods.
     *
     * Scans for candidate boundaries (punctuation + whitespace) and skips
     * splits after known abbreviations, single uppercase initials, and
     * digits (e.g. "3.14 is pi" won't split at the period).
     */
    private fun splitBySentence(text: String): List<String> {
        val sentences = mutableListOf<String>()
        var start = 0

        for (match in SENTENCE_BOUNDARY_CANDIDATES.findAll(text)) {
            val punctuation = match.groupValues[1]
            val splitPos = match.range.first + 1 // right after the punctuation char

            if (punctuation == ".") {
                val before = text.substring(start, match.range.first)
                val lastToken = before.trimEnd().split(Regex("\\s+")).lastOrNull() ?: ""

                // Skip abbreviations (e.g. "Dr." "etc.")
                if (lastToken.removeSuffix(".").lowercase() in ABBREVIATIONS) continue

                // Skip single uppercase letter initials (e.g. "J. K. Rowling")
                if (lastToken.length == 1 && lastToken[0].isUpperCase()) continue

                // Skip digits before period (e.g. "3.14", "v2.0")
                if (lastToken.isNotEmpty() && lastToken.last().isDigit()) continue

                // Skip if the character after the whitespace is lowercase (e.g. "e.g. something")
                val afterPos = match.range.last + 1
                if (afterPos < text.length && text[afterPos].isLowerCase()) continue
            }

            sentences.add(text.substring(start, splitPos).trim())
            start = splitPos
        }

        val remainder = text.substring(start).trim()
        if (remainder.isNotEmpty()) {
            sentences.add(remainder)
        }

        return sentences.filter { it.isNotEmpty() }
    }

    private fun countTokens(text: String): Int {
        return encoding.countTokens(text)
    }
}
