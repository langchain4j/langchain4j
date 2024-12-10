package dev.langchain4j.model.chat

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.output.Response
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
internal class ChatLanguageModelExtensionsTest {

    @Mock
    private lateinit var chatLanguageModel: ChatLanguageModel

    @Mock
    private lateinit var chatResponse: ChatResponse

    @Mock
    private lateinit var chatRequest: ChatRequest

    @Mock
    private lateinit var chatRequestBuilder: ChatRequest.Builder

    @Mock
    private lateinit var aiMessage: AiMessage

    @Test
    fun `chatAsync with ChatRequest should return ChatResponse`() = runTest {

        whenever(chatLanguageModel.chat(chatRequest)).thenReturn(chatResponse)

        val response = chatLanguageModel.chatAsync(chatRequest)

        assertThat(response).isEqualTo(chatResponse)
    }

    @Test
    fun `chatAsync with ChatRequest Builder should return ChatResponse`() = runTest {
        whenever(chatRequestBuilder.build()).thenReturn(chatRequest)
        whenever(chatLanguageModel.chat(chatRequest)).thenReturn(chatResponse)

        val response = chatLanguageModel.chatAsync(chatRequestBuilder)

        assertThat(response).isEqualTo(chatResponse)
    }

    @Test
    fun `generateAsync should return Response of AiMessage`() = runTest {
        val messages = listOf<ChatMessage>()

        whenever(chatLanguageModel.generate(messages)).thenReturn(Response(aiMessage))

        val response = chatLanguageModel.generateAsync(messages)

        assertThat(response.content()).isSameAs(aiMessage)
    }
}
