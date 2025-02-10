package dev.langchain4j.model.chat

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasMessage
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.UserMessage.userMessage
import dev.langchain4j.model.chat.StreamingChatLanguageModelReply.CompleteResponse
import dev.langchain4j.model.chat.StreamingChatLanguageModelReply.PartialResponse
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
internal class StreamingChatLanguageModelExtensionsKtTest {
    @Mock
    private lateinit var mockModel: StreamingChatLanguageModel

    @Test
    fun `chatFlow should handle partial and complete responses correctly`() =
        runTest {
            val partialToken1 = "Hello"
            val partialToken2 = "world"
            val completeResponse = ChatResponse.builder().aiMessage(AiMessage("Hello")).build()

            // Simulate the streaming behavior with a mocked handler
            doAnswer {
                val handler = it.arguments[1] as StreamingChatResponseHandler
                handler.onPartialResponse(partialToken1)
                handler.onPartialResponse(partialToken2)
                handler.onCompleteResponse(completeResponse)
            }.whenever(mockModel).chat(any<ChatRequest>(), any<StreamingChatResponseHandler>())

            val flow =
                mockModel.chatFlow {
                    messages += userMessage("Hey, there!")
                }
            val result = flow.toList()

            // Assert partial responses
            assertThat(
                result
            ).containsExactly(
                PartialResponse(partialToken1),
                PartialResponse(partialToken2),
                CompleteResponse(completeResponse)
            )

            // Verify interactions
            verify(mockModel).chat(any<ChatRequest>(), any<StreamingChatResponseHandler>())
        }

    @Test
    fun `chatFlow should handle errors correctly`() =
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
