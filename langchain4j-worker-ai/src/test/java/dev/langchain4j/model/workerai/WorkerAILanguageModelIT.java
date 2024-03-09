package dev.langchain4j.model.workerai;

import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@Disabled("requires a worker ai account")
@EnabledIfEnvironmentVariable(named = "WORKERAI_API_KEY", matches = ".*")
@EnabledIfEnvironmentVariable(named = "WORKERAI_ACCOUNT_ID", matches = ".*")
public class WorkerAILanguageModelIT {

    static WorkerAiLanguageModel languageModel;

    @BeforeAll
    public static void initializeModel() {
        languageModel = WorkerAiChatModel.builder()
                .modelName(WorkerAiModelName.LLAMA2_7B_FULL)
                .accountIdentifier(System.getenv("WORKERAI_ACCOUNT_ID"))
                .token(System.getenv("WORKERAI_API_KEY"))
                .buildLanguageModel();
    }
    @Test
    public void generateText() {
        Response<String> joke = languageModel.generate("Tell me jokeC cloud");
        Assertions.assertNotNull(joke);
    }
}
