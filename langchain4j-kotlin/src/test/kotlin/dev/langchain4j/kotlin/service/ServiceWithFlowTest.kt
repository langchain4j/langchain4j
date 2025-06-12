package dev.langchain4j.kotlin.service

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isInstanceOf
import assertk.assertions.startsWith
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.kotlin.model.chat.StreamingChatModelReply
import dev.langchain4j.kotlin.model.chat.StreamingChatModelReply.CompleteResponse
import dev.langchain4j.kotlin.model.chat.StreamingChatModelReply.PartialResponse
import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import dev.langchain4j.service.AiServices
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.UserName
import dev.langchain4j.service.V
import io.kotest.matchers.collections.shouldStartWith
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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
internal class ServiceWithFlowTest {
    @Mock
    private lateinit var model: StreamingChatModel

    @Test
    fun `Should use TokenStreamToStringFlowAdapter`() = runTest {
        val partialToken1 = "Hello"
        val partialToken2 = "world"
        val completeResponse = ChatResponse.builder().aiMessage(AiMessage("Hello")).build()

        doAnswer {
            val handler = it.arguments[1] as StreamingChatResponseHandler
            handler.onPartialResponse(partialToken1)
            handler.onPartialResponse(partialToken2)
            handler.onCompleteResponse(completeResponse)
        }.whenever(model).chat(any<ChatRequest>(), any<StreamingChatResponseHandler>())

        val assistant =
            AiServices
                .builder(Assistant::class.java)
                .streamingChatModel(model)
                .build()

        val result = assistant.askQuestion(userName = "My friend", question = "How are you?")
            .toList()

        assertThat(result).containsExactly(partialToken1, partialToken2)
    }

    @Test
    fun `Should use TokenStreamToStringFlowAdapter error`() = runTest {
        val partialToken1 = "Hello"
        val partialToken2 = "world"
        val error = RuntimeException("Test error")

        doAnswer {
            val handler = it.arguments[1] as StreamingChatResponseHandler
            handler.onPartialResponse(partialToken1)
            handler.onPartialResponse(partialToken2)
            handler.onError(error)
        }.whenever(model)
            .chat(any<ChatRequest>(), any<StreamingChatResponseHandler>())

        val assistant =
            AiServices
                .builder(Assistant::class.java)
                .streamingChatModel(model)
                .build()


        val response = assistant.askQuestion(userName = "My friend", question = "How are you?")
            .catch {
                val message =
                    requireNotNull(it.message) { "Only $error is allowed to occur here but found $it" }
                emit(message)
            }.toList()

        assertThat(response).containsExactly(partialToken1, partialToken2, error.message)
    }

    @Test
    fun `Should use TokenStreamToReplyFlowAdapter`() = runTest {
        val partialToken1 = "Hello"
        val partialToken2 = "world"
        val completeResponse = ChatResponse.builder().aiMessage(AiMessage("Hello")).build()

        doAnswer {
            val handler = it.arguments[1] as StreamingChatResponseHandler
            handler.onPartialResponse(partialToken1)
            handler.onPartialResponse(partialToken2)
            handler.onCompleteResponse(completeResponse)
        }.whenever(model).chat(any<ChatRequest>(), any<StreamingChatResponseHandler>())

        val assistant =
            AiServices
                .builder(Assistant::class.java)
                .streamingChatModel(model)
                .build()

        val result = assistant.askQuestion2(userName = "My friend", question = "How are you?")
            .toList()

        result shouldStartWith listOf(
            PartialResponse(partialToken1),
            PartialResponse(partialToken2)
        )
        assertThat(result).index(2).isInstanceOf(CompleteResponse::class)
    }

    @Test
    fun `Should use TokenStreamToReplyFlowAdapter error`() = runTest {
        val partialToken1 = "Hello"
        val partialToken2 = "world"
        val error = RuntimeException("Test error")

        doAnswer {
            val handler = it.arguments[1] as StreamingChatResponseHandler
            handler.onPartialResponse(partialToken1)
            handler.onPartialResponse(partialToken2)
            handler.onError(error)
        }.whenever(model).chat(any<ChatRequest>(), any<StreamingChatResponseHandler>())

        val assistant =
            AiServices
                .builder(Assistant::class.java)
                .streamingChatModel(model)
                .build()

        val response = assistant.askQuestion2(userName = "My friend", question = "How are you?")
            .catch { emit(StreamingChatModelReply.Error(it)) }
            .toList()

        assertThat(response).hasSize(3)
        assertThat(response).startsWith(PartialResponse(partialToken1), PartialResponse(partialToken2))
        assertThat(response).index(2).isInstanceOf(StreamingChatModelReply.Error::class)
    }

    @Suppress("unused")
    interface Assistant {
        @UserMessage(
            "Hello, I am {{ userName }}. {{ message }}."
        )
        fun askQuestion(
            @UserName userName: String,
            @V("message") question: String,
        ): Flow<String>

        @UserMessage(
            "Hello, I am {{ userName }}. {{ message }}."
        )
        fun askQuestion2(
            @UserName userName: String,
            @V("message") question: String,
        ): Flow<StreamingChatModelReply>
    }
}
