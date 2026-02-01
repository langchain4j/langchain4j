package dev.langchain4j.kotlin.service

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.kotlin.model.chat.StreamingChatModelReply
import dev.langchain4j.kotlin.model.chat.StreamingChatModelReply.PartialResponse
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.service.TokenStream
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.function.Consumer

@ExtendWith(MockitoExtension::class)
internal class TokenStreamExtensionTest {

    @Mock
    private lateinit var tokenStream: TokenStream

    @Test
    fun `asFlow should handle errors`() = runTest {
        // Given
        val token = "Hello"
        val error = RuntimeException("Test error")

        // Capture the callbacks
        doAnswer { invocation ->
            val callback = invocation.getArgument<Consumer<String>>(0)
            callback.accept(token)
            tokenStream
        }.whenever(tokenStream).onPartialResponse(any())

        doAnswer { invocation ->
            val callback = invocation.getArgument<Consumer<Throwable>>(0)
            callback.accept(error)
            tokenStream
        }.whenever(tokenStream).onError(any())

        // When
        val result = tokenStream.asFlow()
            .catch { emit(it.message ?: "Unknown error") }
            .toList()

        // Then
        result.shouldContainExactly(token, error.message)
        verify(tokenStream).start()
    }

    @Test
    fun `asReplyFlow should handle errors`() = runTest {
        // Given
        val token = "Hello"
        val error = RuntimeException("Test error")

        // Capture the callbacks
        doAnswer { invocation ->
            val callback = invocation.getArgument<Consumer<String>>(0)
            callback.accept(token)
            tokenStream
        }.whenever(tokenStream).onPartialResponse(any())

        doAnswer { invocation ->
            val callback = invocation.getArgument<Consumer<Throwable>>(0)
            callback.accept(error)
            tokenStream
        }.whenever(tokenStream).onError(any())

        // When
        val result = tokenStream.asReplyFlow()
            .catch { emit(StreamingChatModelReply.Error(it)) }
            .toList()

        // Then
        result.size shouldBe 2
        result[0].shouldBeInstanceOf<PartialResponse>()
        (result[0] as PartialResponse).partialResponse shouldBe token

        result[1].shouldBeInstanceOf<StreamingChatModelReply.Error>()
        (result[1] as StreamingChatModelReply.Error).cause shouldBe error

        verify(tokenStream).start()
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `asFlow should use custom buffer capacity and overflow strategy`(
        includeCompleteResponse: Boolean
    ) = runTest {
        // Given
        val customCapacity = 5
        val maxTokens = 100
        val completeResponse = ChatResponse.builder()
            .aiMessage(AiMessage("Complete response"))
            .build()

        // Capture the callbacks
        doAnswer { invocation ->
            val callback = invocation.getArgument<Consumer<String>>(0)
            for (i in 0..maxTokens) {
                callback.accept("Token-$i")
            }
            tokenStream
        }.whenever(tokenStream).onPartialResponse(any())

        doAnswer { invocation ->
            val callback = invocation.getArgument<Consumer<ChatResponse>>(0)
            callback.accept(completeResponse)
            tokenStream
        }.whenever(tokenStream).onCompleteResponse(any())

        // When
        val result = tokenStream.asFlow(
            bufferCapacity = customCapacity,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
            includeCompleteResponse = includeCompleteResponse
        ).toList()

        // Then
        result.size shouldBe customCapacity + 1
        if (includeCompleteResponse) {
            result shouldBe listOf(
                "Token-0",
                "Token-97",
                "Token-98",
                "Token-99",
                "Token-100",
                "Complete response"
            )
        } else {
            result shouldBe listOf(
                "Token-0",
                "Token-96",
                "Token-97",
                "Token-98",
                "Token-99",
                "Token-100",
            )
        }
    }

    @Test
    fun `asReplyFlow should use custom buffer capacity and overflow strategy`() = runTest {
        // Given
        val customCapacity = 5
        val maxTokens = 100
        val completeResponse = ChatResponse.builder()
            .aiMessage(AiMessage("Complete response"))
            .build()

        // Capture the callbacks
        doAnswer { invocation ->
            val callback = invocation.getArgument<Consumer<String>>(0)
            for (i in 0..maxTokens) {
                callback.accept("Token-$i")
            }
            tokenStream
        }.whenever(tokenStream).onPartialResponse(any())

        doAnswer { invocation ->
            val callback = invocation.getArgument<Consumer<ChatResponse>>(0)
            callback.accept(completeResponse)
            tokenStream
        }.whenever(tokenStream).onCompleteResponse(any())

        // When
        val result = tokenStream.asReplyFlow(
            bufferCapacity = customCapacity,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        ).toList()

        // Then
        result.size shouldBe customCapacity + 1
        result[0] shouldBe PartialResponse("Token-0")
        for (i in 1..customCapacity - 1) {
            result[i] shouldBe PartialResponse("Token-${maxTokens - customCapacity + i + 1}")
        }
        result[customCapacity].shouldBeInstanceOf<StreamingChatModelReply.CompleteResponse>()
        (result[customCapacity] as StreamingChatModelReply.CompleteResponse).response shouldBe completeResponse
        verify(tokenStream).start()
    }
}
