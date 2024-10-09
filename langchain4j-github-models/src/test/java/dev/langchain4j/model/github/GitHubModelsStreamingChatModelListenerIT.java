package dev.langchain4j.model.github;

import com.azure.core.exception.ClientAuthenticationException;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static java.util.Collections.singletonList;

@EnabledIfEnvironmentVariable(named = "GITHUB_TOKEN", matches = ".+")
class GitHubModelsStreamingChatModelListenerIT extends StreamingChatModelListenerIT {

    @Override
    protected StreamingChatLanguageModel createModel(ChatModelListener listener) {
        return GitHubModelsStreamingChatModel.builder()
                .gitHubToken(System.getenv("GITHUB_TOKEN"))
                .modelName(modelName())
                .temperature(temperature())
                .topP(topP())
                .maxTokens(maxTokens())
                .logRequestsAndResponses(true)
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected String modelName() {
        return "gpt-4o-mini";
    }

    @Override
    protected StreamingChatLanguageModel createFailingModel(ChatModelListener listener) {
        return GitHubModelsStreamingChatModel.builder()
                .gitHubToken("banana")
                .modelName(modelName())
                .maxRetries(1)
                .logRequestsAndResponses(true)
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return ClientAuthenticationException.class;
    }

    @Override
    protected boolean assertTokenUsage() {
        return false;
    }
}
