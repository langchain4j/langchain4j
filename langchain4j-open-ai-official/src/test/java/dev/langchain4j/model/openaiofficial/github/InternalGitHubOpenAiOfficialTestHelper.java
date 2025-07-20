package dev.langchain4j.model.openaiofficial.github;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModel;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for testing GitHub Models models.
 * <p>
 * Tests will run depending on the available environment variables:
 * - GITHUB_TOKEN: GitHub Models models will be tested
 * <p>
 */
public class InternalGitHubOpenAiOfficialTestHelper {

    private static final Logger log = LoggerFactory.getLogger(InternalGitHubOpenAiOfficialTestHelper.class);

    public static final com.openai.models.ChatModel CHAT_MODEL_NAME = com.openai.models.ChatModel.GPT_4O_MINI;
    public static final com.openai.models.ChatModel CHAT_MODEL_NAME_ALTERNATE = com.openai.models.ChatModel.GPT_4O;

    // Chat models
    static final OpenAiOfficialChatModel GITHUB_MODELS_CHAT_MODEL;

    static {
        // Set up GitHub Models models if the environment variables are set
        if (System.getenv("GITHUB_TOKEN") != null) {
            GITHUB_MODELS_CHAT_MODEL = OpenAiOfficialChatModel.builder()
                    .isGitHubModels(true)
                    .modelName(CHAT_MODEL_NAME)
                    .build();
        } else {
            GITHUB_MODELS_CHAT_MODEL = null;
        }
    }

    static List<ChatModel> chatModelsNormalAndJsonStrict() {
        List<ChatModel> models = new ArrayList<>();
        if (GITHUB_MODELS_CHAT_MODEL != null) {
            models.add(GITHUB_MODELS_CHAT_MODEL);
        }
        if (models.isEmpty()) {
            log.error("Testing normal model & JSON strict model: skipping tests as GitHub Models keys are not set");
        }
        return models;
    }
}
