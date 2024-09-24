package dev.langchain4j.model.github;

import com.azure.core.exception.ClientAuthenticationException;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.ChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static java.util.Collections.singletonList;

@EnabledIfEnvironmentVariable(named = "GITHUB_TOKEN", matches = ".+")
class GitHubModelsChatModelListenerIT extends ChatModelListenerIT {

    @Override
    protected ChatLanguageModel createModel(ChatModelListener listener) {
        return GitHubModelsChatModel.builder()
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
    protected ChatLanguageModel createFailingModel(ChatModelListener listener) {
        return GitHubModelsChatModel.builder()
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
}
