package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationRequest;
import dev.langchain4j.model.moderation.ModerationResponse;
import dev.langchain4j.model.output.Response;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAiModerationModelTest {

    @Test
    @SuppressWarnings("unchecked")
    void should_return_typed_metadata() {
        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(
                SuccessfulHttpResponse.builder().statusCode(200).body("""
                        {
                          "id": "modr-123",
                          "model": "omni-moderation-latest",
                          "results": [
                            {
                              "flagged": false,
                              "categories": {
                                "harassment": false,
                                "harassment/threatening": false,
                                "hate": false,
                                "hate/threatening": false,
                                "illicit": false,
                                "illicit/violent": false,
                                "self-harm": false,
                                "self-harm/intent": false,
                                "self-harm/instructions": false,
                                "sexual": false,
                                "sexual/minors": false,
                                "violence": false,
                                "violence/graphic": false
                              },
                              "category_scores": {
                                "harassment": 0.001,
                                "harassment/threatening": 0.002,
                                "hate": 0.01,
                                "hate/threatening": 0.02,
                                "illicit": 0.021,
                                "illicit/violent": 0.022,
                                "self-harm": 0.03,
                                "self-harm/intent": 0.031,
                                "self-harm/instructions": 0.032,
                                "sexual": 0.04,
                                "sexual/minors": 0.05,
                                "violence": 0.06,
                                "violence/graphic": 0.07
                              },
                              "category_applied_input_types": {}
                            },
                            {
                              "flagged": true,
                              "categories": {
                                "harassment": false,
                                "harassment/threatening": false,
                                "hate": false,
                                "hate/threatening": false,
                                "illicit": false,
                                "illicit/violent": false,
                                "self-harm": false,
                                "self-harm/intent": false,
                                "self-harm/instructions": false,
                                "sexual": false,
                                "sexual/minors": false,
                                "violence": true,
                                "violence/graphic": false
                              },
                              "category_scores": {
                                "harassment": 0.101,
                                "harassment/threatening": 0.102,
                                "hate": 0.11,
                                "hate/threatening": 0.12,
                                "illicit": 0.121,
                                "illicit/violent": 0.122,
                                "self-harm": 0.13,
                                "self-harm/intent": 0.131,
                                "self-harm/instructions": 0.132,
                                "sexual": 0.14,
                                "sexual/minors": 0.15,
                                "violence": 0.95,
                                "violence/graphic": 0.17
                              },
                              "category_applied_input_types": {
                                "violence": ["text"]
                              }
                            }
                          ]
                        }
                        """).build());
        OpenAiModerationModel model = OpenAiModerationModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-api-key")
                .modelName(OpenAiModerationModelName.OMNI_MODERATION_LATEST)
                .build();

        // when
        ModerationResponse response = model.moderate(ModerationRequest.builder()
                .texts(List.of("I want to hug them.", "I want to kill them."))
                .build());

        // then
        assertThat(response.moderation()).isEqualTo(Moderation.flagged("I want to kill them."));
        assertThat(response.metadata())
                .containsEntry("id", "modr-123")
                .containsEntry("model", "omni-moderation-latest");
        assertThat((List<?>) response.metadata().get("results")).hasSize(2);
        assertThat(response.typedMetadata()).isInstanceOf(OpenAiModerationResponseMetadata.class);

        OpenAiModerationResponseMetadata metadata = (OpenAiModerationResponseMetadata) response.typedMetadata();
        assertThat(metadata.id()).isEqualTo("modr-123");
        assertThat(metadata.model()).isEqualTo("omni-moderation-latest");
        assertThat(metadata.results()).hasSize(2);
        assertThat(metadata.results().get(1).text()).isEqualTo("I want to kill them.");
        assertThat(metadata.results().get(1).categories()).containsEntry("harassment", false);
        assertThat(metadata.results().get(1).categories()).containsEntry("illicit", false);
        assertThat(metadata.results().get(1).categories()).containsEntry("violence", true);
        assertThat(metadata.results().get(1).categoryScores()).containsEntry("self-harm/intent", 0.131);
        assertThat(metadata.results().get(1).categoryScores()).containsEntry("illicit/violent", 0.122);
        assertThat(metadata.results().get(1).categoryScores()).containsEntry("violence", 0.95);
        assertThat(metadata.results().get(1).categoryAppliedInputTypes()).containsEntry("violence", List.of("text"));

        Map<String, Object> secondResult =
                (Map<String, Object>) ((List<?>) response.metadata().get("results")).get(1);
        Map<String, Boolean> categories = (Map<String, Boolean>) secondResult.get("categories");
        Map<String, Double> categoryScores = (Map<String, Double>) secondResult.get("categoryScores");
        Map<String, List<String>> categoryAppliedInputTypes =
                (Map<String, List<String>>) secondResult.get("categoryAppliedInputTypes");
        assertThat(categories).containsEntry("violence", true);
        assertThat(categoryScores).containsEntry("violence", 0.95);
        assertThat(categoryAppliedInputTypes).containsEntry("violence", List.of("text"));

        Response<Moderation> legacyResponse = model.moderate(
                List.of(UserMessage.from("I want to hug them."), UserMessage.from("I want to kill them.")));
        assertThat(legacyResponse.metadata())
                .containsEntry("id", "modr-123")
                .containsEntry("model", "omni-moderation-latest");
        assertThat((List<?>) legacyResponse.metadata().get("results")).hasSize(2);
    }
}
