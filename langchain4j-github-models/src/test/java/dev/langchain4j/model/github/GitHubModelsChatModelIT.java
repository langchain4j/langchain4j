package dev.langchain4j.model.github;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.github.InternalGitHubModelHelper.DEFAULT_CHAT_MODEL_NAME;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;

public class GitHubModelsChatModelIT {

    private static final Logger logger = LoggerFactory.getLogger(GitHubModelsChatModelIT.class);

    @Test
    void should_generate_answer_and_finish_reason_stop() {

        GitHubModelsChatModel model = GitHubModelsChatModel.builder()
                .gitHubToken(System.getenv("GITHUB_TOKEN"))
                .modelName(DEFAULT_CHAT_MODEL_NAME)
                .logRequestsAndResponses(true)
                .build();

        UserMessage userMessage = userMessage("What is the capital of France?");
        Response<AiMessage> response = model.generate(userMessage);
        logger.info("Response: {}", response.content().text());
        assertThat(response.content().text()).contains("Paris");
        assertThat(response.finishReason()).isEqualTo(STOP);
    }

}
