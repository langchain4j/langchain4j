package dev.langchain4j.model.anthropic.common

import dev.langchain4j.model.anthropic.AnthropicChatModel
import dev.langchain4j.model.anthropic.AnthropicChatModelName
import dev.langchain4j.model.chat.common.AbstractChatModelErrorsTest
import me.kpavlov.aimocks.anthropic.MockAnthropic
import me.kpavlov.aimocks.core.AbstractBuildingStep
import kotlin.time.Duration
import kotlin.time.toJavaDuration

internal class AnthropicChatModelErrorsTest :
    AbstractChatModelErrorsTest<AnthropicChatModel, MockAnthropic>(
        mock = MockAnthropic(verbose = true)
    ) {

    // language=json
    override fun errorResponseBody(message: String) = """
        {
          "type": "error",
          "error": {
            "type": "does not matter",
            "message": "$message"
          }
        }
    """.trimIndent()

    override fun createModel(temperature: Double, timeout: Duration?): AnthropicChatModel {
        val builder = AnthropicChatModel.builder()
            .apiKey("dummy-key")
            .baseUrl(mock.baseUrl() + "/v1")
            .modelName(AnthropicChatModelName.CLAUDE_3_5_HAIKU_20241022)
            .maxTokens(20)
            .temperature(temperature)
            .logRequests(true)
            .logResponses(true)
        timeout?.let { builder.timeout(it.toJavaDuration()) }
        return builder.build()
    }

    override fun whenMockMatched(question: String, temperature: Double): AbstractBuildingStep<*, *> = mock.messages {
        userMessageContains(question)
        temperature(temperature)
    }
}
