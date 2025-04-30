package dev.langchain4j.kotlin.model.chat

import assertk.assertFailure
import assertk.assertions.hasMessage
import dev.langchain4j.data.message.AiMessage.aiMessage
import dev.langchain4j.data.message.UserMessage.userMessage
import dev.langchain4j.kotlin.model.chat.StreamingChatModelReply.CompleteResponse
import dev.langchain4j.kotlin.model.chat.StreamingChatModelReply.PartialResponse
import dev.langchain4j.kotlin.model.chat.chatFlow
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
internal class StreamingChatModelExtensionsKtTest {
    @Mock
    private lateinit var mockModel: dev.langchain4j.model.chat.StreamingChatModel

    @Test
    fun `chatFlow should handle partial and complete responses correctly`() =
        runTest {
            val token1 = "Hello"
            val token2 = "world"
            val completeResponse = prepareMockResponse(token1, token2)

            val flow =
                mockModel.chatFlow {
                    messages += userMessage("Hey, there!")
                    parameters {
                        temperature = 0.7
                        maxOutputTokens = 42
                    }
                }
            verifyFlowResponse(flow, completeResponse, token1, token2)
        }

    private fun prepareMockResponse(vararg tokens: String): ChatResponse {
        val completeResponse =
            ChatResponse
                .builder()
                .aiMessage(aiMessage("Hello"))
                .build()

        // Simulate the streaming behavior with a mocked handler
        doAnswer {
            val handler = it.arguments[1] as StreamingChatResponseHandler
            tokens.forEach { token ->
                handler.onPartialResponse(token)
            }
            handler.onCompleteResponse(completeResponse)
        }.whenever(mockModel).chat(any<ChatRequest>(), any<StreamingChatResponseHandler>())
        return completeResponse
    }

    private suspend fun verifyFlowResponse(
        flow: Flow<dev.langchain4j.kotlin.model.chat.StreamingChatModelReply>,
        completeResponse: ChatResponse,
        vararg tokens: String
    ) {
        val result = flow.toList()

        // Assert partial responses
        result shouldContainExactly
            tokens.map { PartialResponse(it) } +
            CompleteResponse(completeResponse)
    }

    @Test
    fun `chatFlow should handle errors`() =
        runTest {
            val error = RuntimeException("Test error")

            // Simulate the error during streaming
            doAnswer {
                val handler = it.arguments[1] as StreamingChatResponseHandler
                handler.onError(error)
            }.whenever(mockModel).chat(any<ChatRequest>(), any<StreamingChatResponseHandler>())

            val flow =
                mockModel.chatFlow {
                    messages += userMessage("Hey, there!")
                }

            assertFailure {
                flow.toList()
            }.hasMessage("Test error")
        }
}
