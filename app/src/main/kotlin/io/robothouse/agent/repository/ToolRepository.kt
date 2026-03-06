package io.robothouse.agent.repository

import dev.langchain4j.agent.tool.Tool
import io.robothouse.agent.util.log
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

@Component
class ToolRepository(private val applicationContext: ApplicationContext) {

    private val toolBeans: Map<String, Any> by lazy {
        val beans = mutableMapOf<String, Any>()
        for (beanName in applicationContext.beanDefinitionNames) {
            val bean = try {
                applicationContext.getBean(beanName)
            } catch (_: Exception) {
                continue
            }
            val hasToolMethods = bean.javaClass.methods.any { it.isAnnotationPresent(Tool::class.java) }
            if (hasToolMethods) {
                val simpleName = bean.javaClass.simpleName
                beans[simpleName] = bean
                log.info { "Registered tool bean: $simpleName" }
            }
        }
        beans
    }

    fun getToolsByNames(names: List<String>): List<Any> =
        names.mapNotNull { name ->
            toolBeans[name].also {
                if (it == null) log.warn { "Tool bean not found: $name" }
            }
        }

    fun getToolNames(): Set<String> = toolBeans.keys
}
