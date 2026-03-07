package io.robothouse.agent.controller

import io.robothouse.agent.model.ConversationMessage
import io.robothouse.agent.service.ConversationMemoryService
import io.robothouse.agent.service.StreamingChatService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Instant

@WebMvcTest(ChatController::class)
@Import(ChatControllerTest.TestConfig::class)
class ChatControllerTest {

    @TestConfiguration
    class TestConfig {
        @Bean
        fun streamingChatService(): StreamingChatService = mock()

        @Bean
        fun conversationMemoryService(): ConversationMemoryService = mock()
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var streamingChatService: StreamingChatService

    @Autowired
    lateinit var conversationMemoryService: ConversationMemoryService

    @Test
    fun `chat returns text event stream content type`() {
        val emitter = SseEmitter()
        whenever(streamingChatService.streamChat(any(), anyOrNull())).thenAnswer {
            Thread.startVirtualThread {
                emitter.send(SseEmitter.event().name("skill_matched").data("""{"type":"skill_matched"}"""))
                emitter.complete()
            }
            emitter
        }

        val mvcResult: MvcResult = mockMvc.perform(
            post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"message": "Hi"}""")
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mvcResult.asyncResult

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(content().contentType("text/event-stream"))
    }

    @Test
    fun `chat sends SSE events in response body`() {
        val emitter = SseEmitter()
        whenever(streamingChatService.streamChat(any(), anyOrNull())).thenAnswer {
            Thread.startVirtualThread {
                emitter.send(SseEmitter.event().name("skill_matched").data("""{"type":"skill_matched","skillName":"test"}"""))
                emitter.complete()
            }
            emitter
        }

        val mvcResult: MvcResult = mockMvc.perform(
            post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"message": "Hi"}""")
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mvcResult.asyncResult

        val result = mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andReturn()

        val body = result.response.contentAsString
        assert(body.contains("event:skill_matched")) { "Expected skill_matched event in: $body" }
    }

    @Test
    fun `chat passes conversationId to service`() {
        val emitter = SseEmitter()
        whenever(streamingChatService.streamChat(any(), anyOrNull())).thenAnswer {
            Thread.startVirtualThread {
                emitter.send(SseEmitter.event().name("conversation_started").data("""{"type":"conversation_started","conversationId":"a1b2c3d4-e5f6-7890-abcd-ef1234567890"}"""))
                emitter.complete()
            }
            emitter
        }

        val mvcResult: MvcResult = mockMvc.perform(
            post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"message": "Hi", "conversationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"}""")
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mvcResult.asyncResult

        val result = mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andReturn()

        val body = result.response.contentAsString
        assert(body.contains("conversation_started")) { "Expected conversation_started event in: $body" }
    }

    @Test
    fun `chat handles error when service throws`() {
        val emitter = SseEmitter()
        whenever(streamingChatService.streamChat(any(), anyOrNull())).thenAnswer {
            Thread.startVirtualThread {
                emitter.completeWithError(RuntimeException("Routing failed"))
            }
            emitter
        }

        val mvcResult: MvcResult = mockMvc.perform(
            post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"message": "Hi"}""")
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        val asyncResult = mvcResult.asyncResult
        assert(asyncResult is Exception) { "Expected async result to be an exception, got: $asyncResult" }
    }

    @Test
    fun `getHistory returns conversation messages`() {
        val convId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
        val messages = listOf(
            ConversationMessage(role = "user", content = "Hello", timestamp = Instant.parse("2026-03-07T10:00:00Z")),
            ConversationMessage(role = "assistant", content = "Hi there!", timestamp = Instant.parse("2026-03-07T10:00:01Z"))
        )
        whenever(conversationMemoryService.getHistory(convId)).thenReturn(messages)

        mockMvc.perform(get("/api/chat/$convId/history"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].role").value("user"))
            .andExpect(jsonPath("$[0].content").value("Hello"))
            .andExpect(jsonPath("$[1].role").value("assistant"))
            .andExpect(jsonPath("$[1].content").value("Hi there!"))
    }
}
