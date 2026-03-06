package io.robothouse.agent.repository

import dev.langchain4j.agent.tool.Tool
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.agent.tool.ToolSpecifications
import dev.langchain4j.service.tool.DefaultToolExecutor
import dev.langchain4j.service.tool.ToolExecutor
import io.robothouse.agent.util.log
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

@Component
class ToolRepository(private val applicationContext: ApplicationContext) {

    private data class ToolEntry(
        val bean: Any,
        val specifications: List<ToolSpecification>,
        val executors: Map<String, ToolExecutor>
    )

    private val toolEntries: Map<String, ToolEntry> by lazy {
        val entries = mutableMapOf<String, ToolEntry>()
        for (beanName in applicationContext.beanDefinitionNames) {
            val bean = try {
                applicationContext.getBean(beanName)
            } catch (_: Exception) {
                continue
            }
            val toolMethods = bean.javaClass.methods.filter { it.isAnnotationPresent(Tool::class.java) }
            if (toolMethods.isNotEmpty()) {
                val simpleName = bean.javaClass.simpleName
                val specs = ToolSpecifications.toolSpecificationsFrom(bean)
                val executors = toolMethods.associate { method ->
                    val spec = ToolSpecifications.toolSpecificationFrom(method)
                    spec.name() to DefaultToolExecutor(bean, method) as ToolExecutor
                }
                entries[simpleName] = ToolEntry(bean, specs, executors)
                log.info { "Registered tool bean: $simpleName with methods: ${executors.keys}" }
            }
        }
        entries
    }

    fun getSpecificationsByNames(names: List<String>): List<ToolSpecification> =
        names.flatMap { name ->
            toolEntries[name]?.specifications ?: emptyList<ToolSpecification>().also {
                log.warn { "Tool bean not found: $name" }
            }
        }

    fun getExecutorsByNames(names: List<String>): Map<String, ToolExecutor> =
        names.fold(mutableMapOf()) { acc, name ->
            toolEntries[name]?.executors?.let { acc.putAll(it) }
            acc
        }

    fun getToolNames(): Set<String> = toolEntries.keys
}
