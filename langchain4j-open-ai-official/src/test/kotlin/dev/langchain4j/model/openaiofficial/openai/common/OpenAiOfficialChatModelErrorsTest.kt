import dev.langchain4j.model.chat.common.AbstractChatModelErrorsTest
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModel
import me.kpavlov.aimocks.core.AbstractBuildingStep
import me.kpavlov.aimocks.openai.MockOpenai
import kotlin.time.Duration
import kotlin.time.toJavaDuration


private val MODEL_NAME: String = com.openai.models.ChatModel.GPT_4_1.asString()

internal class OpenAiOfficialChatModelErrorsTest :
    AbstractChatModelErrorsTest<OpenAiOfficialChatModel, MockOpenai>(
        mock = MockOpenai(verbose = true)
    ) {

    override fun createModel(temperature: Double, timeout: Duration?): OpenAiOfficialChatModel {
        val builder = OpenAiOfficialChatModel.builder()
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
