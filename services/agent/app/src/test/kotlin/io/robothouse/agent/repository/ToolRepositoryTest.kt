package io.robothouse.agent.repository

import dev.langchain4j.agent.tool.Tool
import io.robothouse.agent.service.HttpToolCacheService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
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
        val repository = ToolRepository(context, null)

        assertEquals(setOf("TestTool"), repository.getToolNames())
    }

    @Test
    fun `getSpecificationsByNames returns specs for matching tools`() {
        val context = createContext(TestTool::class.java)
        val repository = ToolRepository(context, null)
        val specs = repository.getSpecificationsByNames(listOf("TestTool"))

        assertEquals(1, specs.size)
        assertEquals("doSomething", specs[0].name())
    }

    @Test
    fun `getExecutorsByNames returns executors for matching tools`() {
        val context = createContext(TestTool::class.java)
        val repository = ToolRepository(context, null)
        val executors = repository.getExecutorsByNames(listOf("TestTool"))

        assertTrue(executors.containsKey("doSomething"))
    }

    @Test
    fun `getSpecificationsByNames returns empty for unknown names`() {
        val context = createContext(TestTool::class.java)
        val repository = ToolRepository(context, null)
        val specs = repository.getSpecificationsByNames(listOf("NonExistent"))

        assertTrue(specs.isEmpty())
    }

    @Test
    fun `getToolNames includes http tool names when cache service provided`() {
        val httpToolCacheService: HttpToolCacheService = mock()
        whenever(httpToolCacheService.getAll()).thenReturn(mapOf("weatherApi" to HttpToolCacheService.CachedTool(mock(), mock())))

        val context = createContext(TestTool::class.java)
        val repository = ToolRepository(context, httpToolCacheService)
        val names = repository.getToolNames()

        assertTrue(names.contains("TestTool"))
        assertTrue(names.contains("weatherApi"))
    }

    @Test
    fun `getToolNames works when httpToolService is null`() {
        val context = createContext(TestTool::class.java)
        val repository = ToolRepository(context, null)
        val names = repository.getToolNames()

        assertEquals(setOf("TestTool"), names)
    }
}
