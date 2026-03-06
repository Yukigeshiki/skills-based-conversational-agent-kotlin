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
    fun `getToolsByNames returns matching tools`() {
        val context = createContext(TestTool::class.java)
        val repository = ToolRepository(context)
        val tools = repository.getToolsByNames(listOf("TestTool"))

        assertEquals(1, tools.size)
        assertTrue(tools[0] is TestTool)
    }

    @Test
    fun `getToolsByNames skips unknown names`() {
        val context = createContext(TestTool::class.java)
        val repository = ToolRepository(context)
        val tools = repository.getToolsByNames(listOf("NonExistent"))

        assertTrue(tools.isEmpty())
    }
}
