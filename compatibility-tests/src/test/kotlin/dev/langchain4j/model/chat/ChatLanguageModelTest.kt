package dev.langchain4j.model.chat

import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.ModelDisabledException
import dev.langchain4j.model.chat.request.ChatRequest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class ChatLanguageModelTest {
    /**
     * Tests if Kotlin code runs correctly by verifying the behavior of a disabled chat language model.
     */
    @Test
    fun shouldRunKotlinCode() =
        runTest {
            val model = DisabledChatLanguageModel()
            assertFailsWith<ModelDisabledException> {
                model.chatAsync(ChatRequest.builder().messages(UserMessage("hello")))
            }
        }
}
