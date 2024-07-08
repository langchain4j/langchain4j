package dev.langchain4j.model.workersai;

import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "WORKERS_AI_API_KEY", matches = ".*")
@EnabledIfEnvironmentVariable(named = "WORKERS_AI_ACCOUNT_ID", matches = ".*")
class WorkersAiLanguageModelIT {

    static WorkersAiLanguageModel languageModel;

    @BeforeAll
    static void initializeModel() {
        languageModel = WorkersAiLanguageModel.builder()
                .modelName(WorkersAiChatModelName.LLAMA2_7B_FULL.toString())
                .accountId(System.getenv("WORKERS_AI_ACCOUNT_ID"))
                .apiToken(System.getenv("WORKERS_AI_API_KEY"))
                .build();
    }
    @Test
    void generateText() {
        Response<String> joke = languageModel.generate("Tell me a joke about thw cloud");
        Assertions.assertNotNull(joke);
        Assertions.assertNotNull(joke.content());
        Assertions.assertNotNull(joke.finishReason());

    }
}
