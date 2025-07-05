package dev.langchain4j.model.googleai.common

import dev.langchain4j.model.chat.common.AbstractChatModelErrorsTest
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel
import me.kpavlov.aimocks.core.AbstractBuildingStep
import me.kpavlov.aimocks.gemini.MockGemini
import kotlin.time.Duration
import kotlin.time.toJavaDuration

private const val MODEL_NAME = "gemini-2.0-flash"

internal class GoogleAiGeminiChatModelErrorsTest :
    AbstractChatModelErrorsTest<GoogleAiGeminiChatModel, MockGemini>(
        mock = MockGemini(verbose = false)
    ) {

    override fun createModel(temperature: Double, timeout: Duration?): GoogleAiGeminiChatModel {
        val builder = GoogleAiGeminiChatModel.builder()
            .apiKey("dummy-api-key")
            .modelName(MODEL_NAME)
            .baseUrl(mock.baseUrl())
            .maxRetries(0)
        timeout?.let { builder.timeout(it.toJavaDuration()) }
        return builder.build()
    }

    override fun whenMockMatched(question: String, temperature: Double): AbstractBuildingStep<*, *> {
        return mock.generateContent {
            userMessageContains(question)
            path("/models/$MODEL_NAME:generateContent")
            temperature(temperature)
        }
    }
}
