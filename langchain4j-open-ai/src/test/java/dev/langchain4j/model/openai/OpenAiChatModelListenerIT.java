package dev.langchain4j.model.openai;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static java.util.Collections.singletonList;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.ChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import me.kpavlov.aimocks.openai.MockOpenai;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiChatModelListenerIT extends ChatModelListenerIT {

    private static final MockOpenai MOCK_OPENAI = new MockOpenai(0, true);

    @Override
    protected ChatLanguageModel createModel(ChatModelListener listener) {
        return OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(modelName())
                .temperature(temperature())
                .topP(topP())
                .maxTokens(maxTokens())
                .logRequests(true)
                .logResponses(true)
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected String modelName() {
        return GPT_4_O_MINI.toString();
    }

    @Override
    protected ChatLanguageModel createFailingModel(ChatModelListener listener) {

        MOCK_OPENAI
                .completion(request -> {
                    request.seed(100500);
                })
                .responds(resp -> {
                    resp.setFinishReason("banana");
                });

        return OpenAiChatModel.builder()
                .apiKey("banana")
                .baseUrl("http://localhost:" + MOCK_OPENAI.port() + "/v1")
                .modelName("foo")
                .seed(100500)
                .maxRetries(1)
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return HttpException.class;
    }
}
