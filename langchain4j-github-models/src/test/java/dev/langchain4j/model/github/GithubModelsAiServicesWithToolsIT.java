package dev.langchain4j.model.github;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServicesWithNewToolsIT;

import java.util.List;

import static dev.langchain4j.model.github.GitHubModelsChatModelName.GPT_4_O_MINI;
import static java.util.Collections.singletonList;

class GithubModelsAiServicesWithToolsIT extends AiServicesWithNewToolsIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return singletonList(
                GitHubModelsChatModel.builder()
                        .gitHubToken(System.getenv("GITHUB_TOKEN"))
                        .modelName(GPT_4_O_MINI)
                        .temperature(0.0)
                        .logRequestsAndResponses(true)
                        .build()
        );
    }
}
