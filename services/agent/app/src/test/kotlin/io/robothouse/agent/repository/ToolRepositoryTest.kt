package io.robothouse.agent.repository

import dev.langchain4j.agent.tool.Tool
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.context.support.GenericApplicationContext

class ToolRepositoryTest {

    class TestTool {
        @Tool("A test tool")
        fun doSomething(): String = "done"
    }

    class NotATool {
        fun regularMethod(): String = "not a tool"
    }

    private fun createContext(vararg classes: Class<*>): GenericApplicationContext {
        val context = GenericApplicationContext()
        for (clazz in classes) {
            val bd = GenericBeanDefinition()
            bd.setBeanClass(clazz)
            context.registerBeanDefinition(clazz.simpleName, bd)
        }
        context.refresh()
        return context
    }

    @Test
    fun `discovers beans with @Tool methods`() {
        val context = createContext(TestTool::class.java, NotATool::class.java)
        val repository = ToolRepository(context)

        assertEquals(setOf("TestTool"), repository.getToolNames())
    }

    @Test
    fun `getSpecificationsByNames returns specs for matching tools`() {
        val context = createContext(TestTool::class.java)
        val repository = ToolRepository(context)
        val specs = repository.getSpecificationsByNames(listOf("TestTool"))

        assertEquals(1, specs.size)
        assertEquals("doSomething", specs[0].name())
    }

    @Test
    fun `getExecutorsByNames returns executors for matching tools`() {
        val context = createContext(TestTool::class.java)
        val repository = ToolRepository(context)
        val executors = repository.getExecutorsByNames(listOf("TestTool"))

        assertTrue(executors.containsKey("doSomething"))
    }

    @Test
    fun `getSpecificationsByNames returns empty for unknown names`() {
        val context = createContext(TestTool::class.java)
        val repository = ToolRepository(context)
        val specs = repository.getSpecificationsByNames(listOf("NonExistent"))

        assertTrue(specs.isEmpty())
    }
}
