package io.robothouse.agent.repository

import dev.langchain4j.agent.tool.Tool
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.agent.tool.ToolSpecifications
import dev.langchain4j.service.tool.DefaultToolExecutor
import dev.langchain4j.service.tool.ToolExecutor
import io.robothouse.agent.service.HttpToolCacheService
import io.robothouse.agent.util.log
import org.springframework.beans.factory.BeanCreationException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

/**
 * Discovers and caches all Spring beans annotated with LangChain4j's @Tool,
 * and merges results with HTTP tools registered in the database.
 */
@Component
class ToolRepository(
    private val applicationContext: ApplicationContext,
    @param:Autowired(required = false)
    private val httpToolCacheService: HttpToolCacheService? = null
) {

    /**
     * Holds the discovered bean, its tool specifications, and executors
     * keyed by tool method name.
     */
    private data class ToolEntry(
        val bean: Any,
        val specifications: List<ToolSpecification>,
        val executors: Map<String, ToolExecutor>
    )

    /**
     * Lazily scans the Spring application context for beans with @Tool-annotated
     * methods, building specifications and executors for each. The result is
     * cached for the lifetime of the application.
     */
    private val toolEntries: Map<String, ToolEntry> by lazy {
        val entries = mutableMapOf<String, ToolEntry>()
        for (beanName in applicationContext.beanDefinitionNames) {
            val bean = try {
                applicationContext.getBean(beanName)
            } catch (_: NoSuchBeanDefinitionException) {
                continue
            } catch (_: BeanCreationException) {
                log.debug { "Skipping bean that failed to create: $beanName" }
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

    /**
     * Returns tool specifications for the given names, merging bean-based
     * and http tools.
     */
    fun getSpecificationsByNames(names: List<String>): List<ToolSpecification> {
        val beanSpecs = names.flatMap { name ->
            toolEntries[name]?.specifications ?: emptyList()
        }
        val httpToolNames = names.filter { it !in toolEntries }
        val httpTools = if (httpToolNames.isNotEmpty()) {
            httpToolCacheService?.getAll() ?: emptyMap()
        } else {
            emptyMap()
        }
        val httpToolSpecs = httpToolNames.mapNotNull { httpTools[it]?.specification }
        val resolved = beanSpecs + httpToolSpecs
        val unresolvedNames = names.filter { name ->
            name !in toolEntries && httpTools[name] == null
        }
        unresolvedNames.forEach { log.warn { "Tool not found: $it" } }
        return resolved
    }

    /**
     * Returns a map of tool method names to their executors for the given
     * names, merging bean-based and HTTP tools.
     */
    fun getExecutorsByNames(names: List<String>): Map<String, ToolExecutor> {
        val result = mutableMapOf<String, ToolExecutor>()
        val httpToolNames = mutableListOf<String>()
        names.forEach { name ->
            val entry = toolEntries[name]
            if (entry != null) {
                result.putAll(entry.executors)
            } else {
                httpToolNames.add(name)
            }
        }
        if (httpToolNames.isNotEmpty()) {
            val httpTools = httpToolCacheService?.getAll() ?: emptyMap()
            httpToolNames.forEach { name ->
                httpTools[name]?.executor?.let { result[name] = it }
            }
        }
        return result
    }

    /**
     * Returns the names of all registered bean-based tools only.
     */
    fun getBeanToolNames(): Set<String> = toolEntries.keys

    /**
     * Returns the names of all registered tools (bean-based and HTTP).
     */
    fun getToolNames(): Set<String> {
        val httpToolNames = httpToolCacheService?.getAll()?.keys ?: emptySet()
        return toolEntries.keys + httpToolNames
    }
}
