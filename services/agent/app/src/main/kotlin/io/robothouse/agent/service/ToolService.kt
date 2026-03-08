package io.robothouse.agent.service

import io.robothouse.agent.repository.ToolRepository
import org.springframework.stereotype.Service

/**
 * Service layer for tool-related operations.
 */
@Service
class ToolService(
    private val toolRepository: ToolRepository
) {

    /**
     * Returns a sorted list of all registered tool bean names.
     */
    fun getToolNames(): List<String> = toolRepository.getToolNames().sorted()
}
