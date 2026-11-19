package dev.langchain4j.model.anthropic;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.havingExactly;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.model.catalog.ModelDescription;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class AnthropicModelCatalogTest {

    private WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void should_fetch_all_pages() {
        // given
        stubPage(null, """
                {
                  "data": [
                    {"id": "claude-a", "type": "model", "display_name": "A", "created_at": "2025-01-01T00:00:00Z"},
                    {"id": "claude-b", "type": "model", "display_name": "B", "created_at": "2025-01-02T00:00:00Z"}
                  ],
                  "first_id": "claude-a",
                  "last_id": "claude-b",
                  "has_more": true
                }
                """);
        stubPage("claude-b", """
                {
                  "data": [
                    {"id": "claude-c", "type": "model", "display_name": "C", "created_at": "2025-01-03T00:00:00Z"}
                  ],
                  "first_id": "claude-c",
                  "last_id": "claude-c",
                  "has_more": false
                }
                """);

        // when
        List<ModelDescription> models = catalogBuilder().build().listModels();

        // then
        assertThat(models).extracting(ModelDescription::name).containsExactly("claude-a", "claude-b", "claude-c");
        wireMockServer.verify(
                1,
                getRequestedFor(urlPathEqualTo("/v1/models"))
                        .withQueryParam("limit", equalTo("1000"))
                        .withQueryParam("after_id", absent()));
        wireMockServer.verify(
                1,
                getRequestedFor(urlPathEqualTo("/v1/models"))
                        .withQueryParam("limit", equalTo("1000"))
                        .withQueryParam("after_id", equalTo("claude-b")));
    }

    @Test
    @Timeout(10)
    void should_stop_when_page_has_no_data_and_no_last_id() {
        // given
        stubPage(null, "{\"has_more\": true}");

        // when
        List<ModelDescription> models = catalogBuilder().build().listModels();

        // then
        assertThat(models).isEmpty();
        wireMockServer.verify(1, getRequestedFor(urlPathEqualTo("/v1/models")));
    }

    @Test
    void should_send_single_lifecycle() {
        // given
        stubSinglePage();

        // when
        catalogBuilder().lifecycles(AnthropicModelLifecycle.ACTIVE).build().listModels();

        // then
        wireMockServer.verify(
                getRequestedFor(urlPathEqualTo("/v1/models")).withQueryParam("lifecycle[]", havingExactly("active")));
    }

    @Test
    void should_send_multiple_lifecycles() {
        // given
        stubSinglePage();

        // when
        catalogBuilder()
                .lifecycles(List.of(AnthropicModelLifecycle.DEPRECATED, AnthropicModelLifecycle.RETIRED))
                .build()
                .listModels();

        // then
        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/v1/models"))
                .withQueryParam("lifecycle[]", havingExactly("deprecated", "retired")));
    }

    @Test
    void should_not_send_lifecycle_when_not_set() {
        // given
        stubSinglePage();

        // when
        catalogBuilder().build().listModels();

        // then
        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/v1/models")).withQueryParam("lifecycle[]", absent()));
    }

    @Test
    void should_throw_when_api_returns_error() {
        // given
        wireMockServer.stubFor(get(urlPathEqualTo("/v1/models"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"type\": \"error\", \"error\": {\"type\": \"authentication_error\"}}")));

        // when-then
        assertThatThrownBy(() -> catalogBuilder().build().listModels()).isInstanceOf(HttpException.class);
    }

    private AnthropicModelCatalog.Builder catalogBuilder() {
        return AnthropicModelCatalog.builder()
                .baseUrl("http://localhost:" + wireMockServer.port() + "/v1")
                .apiKey("test-key");
    }

    private void stubPage(String afterId, String body) {
        wireMockServer.stubFor(get(urlPathEqualTo("/v1/models"))
                .withQueryParam("after_id", afterId == null ? absent() : equalTo(afterId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private void stubSinglePage() {
        stubPage(null, "{\"data\": [], \"has_more\": false}");
    }
}
