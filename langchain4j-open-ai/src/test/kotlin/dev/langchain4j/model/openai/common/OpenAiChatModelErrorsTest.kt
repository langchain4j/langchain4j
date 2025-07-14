import dev.langchain4j.model.chat.common.AbstractChatModelErrorsTest
import dev.langchain4j.model.openai.OpenAiChatModel
import me.kpavlov.aimocks.core.AbstractBuildingStep
import me.kpavlov.aimocks.openai.MockOpenai
import kotlin.time.Duration
import kotlin.time.toJavaDuration

private const val MODEL_NAME = "gpt-4.1-nano"

internal class OpenAiChatModelErrorsTest :
    AbstractChatModelErrorsTest<OpenAiChatModel, MockOpenai>(
        mock = MockOpenai(verbose = true)
    ) {

    override fun createModel(temperature: Double, timeout: Duration?): OpenAiChatModel {
        val builder = OpenAiChatModel.builder()
            .apiKey("dummy-api-key")
            .modelName(MODEL_NAME)
            .baseUrl(mock.baseUrl())
            .maxRetries(0)
        timeout?.let { builder.timeout(it.toJavaDuration()) }
        return builder.build()
    }

    override fun whenMockMatched(question: String, temperature: Double): AbstractBuildingStep<*, *> = mock.completion {
        userMessageContains(question)
        temperature(temperature)
    }
}
