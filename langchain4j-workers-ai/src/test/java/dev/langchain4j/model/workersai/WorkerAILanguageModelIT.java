package dev.langchain4j.model.workersai;

import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@Disabled("Requires a Workers ai account")
@EnabledIfEnvironmentVariable(named = "WORKERS_AI_API_KEY", matches = ".*")
@EnabledIfEnvironmentVariable(named = "WORKERS_AI_ACCOUNT_ID", matches = ".*")
class WorkerAILanguageModelIT {

    static WorkersAiLanguageModel languageModel;

    @BeforeAll
    static void initializeModel() {
        languageModel = WorkersAiChatModel.builder()
                .modelName(WorkersAiModelName.LLAMA2_7B_FULL)
                .accountIdentifier(System.getenv("WORKERS_AI_ACCOUNT_ID"))
                .token(System.getenv("WORKERS_AI_API_KEY"))
                .buildLanguageModel();
    }
    @Test
    void generateText() {
        Response<String> joke = languageModel.generate("Tell me jokeC cloud");
        Assertions.assertNotNull(joke);
    }
}
