package io.robothouse.agent.service

import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.service.tool.ToolExecutor
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

    /**
     * Returns tool specifications for the given bean names.
     */
    fun getSpecificationsByNames(names: List<String>): List<ToolSpecification> =
        toolRepository.getSpecificationsByNames(names)

    /**
     * Returns a map of tool method names to their executors for the given bean names.
     */
    fun getExecutorsByNames(names: List<String>): Map<String, ToolExecutor> =
        toolRepository.getExecutorsByNames(names)
}
