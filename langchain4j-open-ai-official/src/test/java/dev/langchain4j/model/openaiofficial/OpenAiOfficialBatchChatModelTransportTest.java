package dev.langchain4j.model.openaiofficial;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.openai.azure.AzureOpenAIServiceVersion;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.batch.BatchPagination;
import dev.langchain4j.model.batch.BatchRequest;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Verifies the URLs that a client built by this integration actually sends to, rather than by an injected
 * one. The Azure {@code /openai} prefix and {@code api-version} are applied by the SDK based on the
 * hostname, so they cannot be reproduced against a local server; what is covered here is that batch and
 * file operations are never scoped to a deployment.
 */
class OpenAiOfficialBatchChatModelTransportTest {

    private static final String BATCH_JSON = """
            {"id":"batch_abc","object":"batch","endpoint":"/chat/completions","input_file_id":"file-in",\
            "completion_window":"24h","created_at":1700000000,"status":"validating"}""";
    private static final String FILE_JSON = """
            {"id":"file-in","object":"file","bytes":1,"created_at":1700000000,"filename":"batch.jsonl",\
            "purpose":"batch","status":"processed"}""";

    private WireMockServer wireMockServer;

    @BeforeEach
    void beforeEach() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        wireMockServer.stubFor(
                WireMock.post(WireMock.urlPathMatching(".*/files")).willReturn(WireMock.okJson(FILE_JSON)));
        wireMockServer.stubFor(
                WireMock.post(WireMock.urlPathMatching(".*/batches")).willReturn(WireMock.okJson(BATCH_JSON)));
        wireMockServer.stubFor(
                WireMock.get(WireMock.urlPathMatching(".*/batches/batch_abc")).willReturn(WireMock.okJson(BATCH_JSON)));
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathMatching(".*/batches/batch_abc/cancel"))
                .willReturn(WireMock.okJson(BATCH_JSON)));
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathMatching(".*/batches"))
                .willReturn(WireMock.okJson("{\"object\":\"list\",\"data\":[],\"has_more\":false}")));
    }

    @AfterEach
    void afterEach() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    private OpenAiOfficialBatchChatModel foundryModel(String baseUrl) {
        return OpenAiOfficialBatchChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey("test-key")
                .isMicrosoftFoundry(true)
                .azureOpenAIServiceVersion(AzureOpenAIServiceVersion.getV2024_10_21())
                .modelName("gpt-4o-mini")
                .microsoftFoundryDeploymentName("my-deployment")
                .build();
    }

    private List<String> sentUrls() {
        return wireMockServer.findAll(WireMock.anyRequestedFor(WireMock.anyUrl())).stream()
                .map(LoggedRequest::getUrl)
                .toList();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/"})
    void should_not_scope_microsoft_foundry_batch_and_file_urls_to_a_deployment(String baseUrlSuffix) {
        OpenAiOfficialBatchChatModel model = foundryModel(wireMockServer.baseUrl() + baseUrlSuffix);

        model.submit(new BatchRequest<>(
                List.of(ChatRequest.builder().messages(UserMessage.from("hi")).build())));

        assertThat(sentUrls()).isNotEmpty().noneMatch(url -> url.contains("deployments"));
        assertThat(sentUrls()).anyMatch(url -> url.endsWith("/files"));
        assertThat(sentUrls()).anyMatch(url -> url.endsWith("/batches"));
    }

    @Test
    void should_not_scope_microsoft_foundry_retrieve_cancel_and_list_to_a_deployment() {
        OpenAiOfficialBatchChatModel model = foundryModel(wireMockServer.baseUrl());

        model.retrieve("batch_abc");
        model.cancel("batch_abc");
        model.list(new BatchPagination(10, "batch_previous"));

        assertThat(sentUrls())
                .isNotEmpty()
                .noneMatch(url -> url.contains("deployments"))
                .anyMatch(url -> url.startsWith("/batches/batch_abc"))
                .anyMatch(url -> url.startsWith("/batches/batch_abc/cancel"))
                .anyMatch(url -> url.contains("limit=10") && url.contains("after=batch_previous"));
    }

    @Test
    void should_use_plain_paths_when_not_microsoft_foundry() {
        OpenAiOfficialBatchChatModel model = OpenAiOfficialBatchChatModel.builder()
                .baseUrl(wireMockServer.baseUrl())
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .build();

        model.retrieve("batch_abc");

        assertThat(sentUrls()).containsExactly("/batches/batch_abc");
    }
}
