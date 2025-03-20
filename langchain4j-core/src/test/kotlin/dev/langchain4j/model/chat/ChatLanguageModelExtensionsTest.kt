package dev.langchain4j.model.chat

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import dev.langchain4j.data.message.UserMessage.userMessage
import dev.langchain4j.internal.VirtualThreadUtils
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.util.concurrent.Executors

@ExtendWith(MockitoExtension::class)
internal class ChatLanguageModelExtensionsTest {
    @Mock
    private lateinit var chatLanguageModel: ChatLanguageModel

    @Mock
    private lateinit var chatResponse: ChatResponse

    @Mock
    private lateinit var chatRequest: ChatRequest

    @Captor
    private lateinit var chatRequestCaptor: ArgumentCaptor<ChatRequest>

    @Mock
    private lateinit var chatRequestBuilder: ChatRequest.Builder

    @Test
    fun `chatAsync with ChatRequest should return ChatResponse`() =
        runTest {
            whenever(chatLanguageModel.chat(chatRequest)).thenReturn(chatResponse)

            val response = chatLanguageModel.chatAsync(chatRequest)

            response shouldBe chatResponse
        }

    @Test
    fun `chatAsync with custom dispatcher`() =
        runTest {
            whenever(chatLanguageModel.chat(chatRequest)).thenReturn(chatResponse)

            // some custom coroutine context
            val ctx =
                VirtualThreadUtils
                    .createVirtualThreadExecutor { Executors.newSingleThreadExecutor() }!!
                    .asCoroutineDispatcher()

            // when
            val response = chatLanguageModel.chatAsync(request = chatRequest, coroutineContext = ctx)

            // then
            response shouldBe chatResponse
        }

    @Test
    fun `chatAsync with ChatRequest Builder should return ChatResponse`() =
        runTest {
            whenever(chatRequestBuilder.build()).thenReturn(chatRequest)
            whenever(chatLanguageModel.chat(chatRequest)).thenReturn(chatResponse)

            val response = chatLanguageModel.chat(chatRequestBuilder)

            assertThat(response).isEqualTo(chatResponse)
        }

    @Test
    fun `chat(_) with Type-safe builder should return ChatResponse`() =
        runTest {
            whenever(chatLanguageModel.chat(chatRequestCaptor.capture()))
                .thenReturn(chatResponse)

            val userMessage = userMessage("Hello")
            val response =
                chatLanguageModel.chat {
                    messages += userMessage
                    parameters { }
                }

            assertThat(chatRequestCaptor.value.messages()).containsExactly(userMessage)
            response shouldBe chatResponse
        }
}
