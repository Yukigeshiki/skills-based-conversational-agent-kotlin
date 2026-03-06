package io.robothouse.agent.controller

import io.robothouse.agent.service.StreamingChatService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@WebMvcTest(ChatController::class)
@Import(ChatControllerTest.TestConfig::class)
class ChatControllerTest {

    @TestConfiguration
    class TestConfig {
        @Bean
        fun streamingChatService(): StreamingChatService = mock()
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var streamingChatService: StreamingChatService

    @Test
    fun `chat returns text event stream content type`() {
        val emitter = SseEmitter()
        whenever(streamingChatService.streamChat(any())).thenAnswer {
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
        whenever(streamingChatService.streamChat(any())).thenAnswer {
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
    fun `chat handles error when service throws`() {
        val emitter = SseEmitter()
        whenever(streamingChatService.streamChat(any())).thenAnswer {
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
}
