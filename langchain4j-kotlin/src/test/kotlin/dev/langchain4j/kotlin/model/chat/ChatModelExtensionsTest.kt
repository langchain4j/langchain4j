package dev.langchain4j.kotlin.model.chat

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import dev.langchain4j.data.message.UserMessage.userMessage
import dev.langchain4j.internal.VirtualThreadUtils
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

@ExtendWith(MockitoExtension::class)
@TestInstance(Lifecycle.PER_CLASS)
internal class ChatModelExtensionsTest {
    @Mock
    private lateinit var chatModel: dev.langchain4j.model.chat.ChatModel

    @Mock
    private lateinit var chatResponse: ChatResponse

    @Mock
    private lateinit var chatRequest: ChatRequest

    @Captor
    private lateinit var chatRequestCaptor: ArgumentCaptor<ChatRequest>

    @Mock
    private lateinit var chatRequestBuilder: ChatRequest.Builder

    private lateinit var coroutineContext: CoroutineContext

    @BeforeAll
    fun beforeAll() {
        coroutineContext =
            VirtualThreadUtils
                .createVirtualThreadExecutor { Executors.newSingleThreadExecutor() }!!
                .asCoroutineDispatcher()
    }

    @Test
    fun `chatAsync with ChatRequest should return ChatResponse`() =
        runTest {
            whenever(chatModel.chat(chatRequest)).thenReturn(chatResponse)

            val response = chatModel.chatAsync(chatRequest)

            response shouldBe chatResponse
        }

    @Test
    fun `chatAsync with custom dispatcher`() =
        runTest {
            whenever(chatModel.chat(chatRequest)).thenReturn(chatResponse)

            // when
            val response =
                chatModel.chatAsync(
                    request = chatRequest,
                    coroutineContext = this@ChatModelExtensionsTest.coroutineContext
                )

            // then
            response shouldBe chatResponse
        }

    @Test
    fun `chatAsync with ChatRequest Builder should return ChatResponse`() =
        runTest {
            whenever(chatRequestBuilder.build()).thenReturn(chatRequest)
            whenever(chatModel.chat(chatRequest)).thenReturn(chatResponse)

            val response = chatModel.chat(chatRequestBuilder)

            assertThat(response).isEqualTo(chatResponse)
        }

    @Test
    fun `chat(_) with Type-safe builder should return ChatResponse`() =
        runTest {
            whenever(chatModel.chat(chatRequestCaptor.capture()))
                .thenReturn(chatResponse)

            val userMessage = userMessage("Hello")
            val response =
                chatModel.chat {
                    messages += userMessage
                    parameters { }
                }

            assertThat(chatRequestCaptor.value.messages()).containsExactly(userMessage)
            response shouldBe chatResponse
        }

    @Test
    fun `chat(_) with Type-safe builder and custom dispatcher should return ChatResponse`() =
        runTest {
            whenever(chatModel.chat(chatRequestCaptor.capture()))
                .thenReturn(chatResponse)

            val userMessage = userMessage("Hello")
            val response =
                chatModel.chat(coroutineContext = coroutineContext) {
                    messages += userMessage
                    parameters { }
                }

            assertThat(chatRequestCaptor.value.messages()).containsExactly(userMessage)
            response shouldBe chatResponse
        }

    @Test
    fun `defaultCoroutineContext() should return dispatcher based on virtual thread support`() {
        val context = defaultCoroutineContext()

        if (VirtualThreadUtils.isVirtualThreadsSupported()) {
            // On Java 21+, should return a virtual thread dispatcher;
            // We can verify this by checking if a thread created by this dispatcher is a virtual thread
            runTest {
                val isVirtualThread: Boolean = withContext(context) {
                    VirtualThreadUtils.isVirtualThread()
                }
                isVirtualThread shouldBe true
            }
        } else {
            // On Java 20 or lower, should return Dispatchers.IO
            context shouldBe Dispatchers.IO
        }
    }
}
