package dev.langchain4j.model.vertexai.anthropic;

import static dev.langchain4j.model.ModelProvider.GOOGLE_VERTEX_AI_ANTHROPIC;
import static dev.langchain4j.model.vertexai.anthropic.VertexAiAnthropicFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.auth.oauth2.GoogleCredentials;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junitpioneer.jupiter.RetryingTest;

/**
 * Integration tests for VertexAiClaudeChatModel with custom credentials
 *
 * Prerequisites:
 * - Set GCP_PROJECT_ID environment variable with your Google Cloud project ID
 * - Set GCP_LOCATION environment variable with your preferred location (e.g., "us-central1")
 * - Ensure you have access to Claude models in Vertex AI Model Garden
 * - Authenticate with Google Cloud (gcloud auth application-default login)
 */
@EnabledIfEnvironmentVariable(named = "GCP_PROJECT_ID", matches = ".+")
class VertexAiAnthropicCustomCredentialsIT {

    @Test
    void should_build_with_null_credentials() {
        // given/when - should not throw exception with null credentials
        VertexAiAnthropicChatModel model = VertexAiAnthropicChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(DEFAULT_MODEL_NAME)
                .credentials(null) // explicitly set null credentials
                .build();

        // then
        assertNotNull(model);
        assertThat(model.provider()).isEqualTo(GOOGLE_VERTEX_AI_ANTHROPIC);
    }

    @RetryingTest(3)
    void should_work_with_application_default_credentials() throws Exception {
        // given - use Application Default Credentials
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();

        ChatModel model = VertexAiAnthropicChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(DEFAULT_MODEL_NAME)
                .credentials(credentials)
                .maxTokens(100)
                .temperature(0.1)
                .build();

        UserMessage userMessage = UserMessage.from("Say 'Hello from custom credentials!' exactly");

        // when
        ChatResponse response =
                model.chat(ChatRequest.builder().messages(List.of(userMessage)).build());

        // then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
        assertThat(response.aiMessage().text()).containsIgnoringCase("Hello from custom credentials");
    }

    @Test
    void should_build_streaming_model_with_custom_credentials() throws Exception {
        // given
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();

        // when
        VertexAiAnthropicStreamingChatModel model = VertexAiAnthropicStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(DEFAULT_MODEL_NAME)
                .credentials(credentials)
                .maxTokens(100)
                .build();

        // then
        assertNotNull(model);
        assertThat(model.provider()).isEqualTo(GOOGLE_VERTEX_AI_ANTHROPIC);
    }

    @Test
    void should_handle_credentials_that_need_scoping() throws Exception {
        // given - get credentials that might not have the right scopes
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();

        // when - the client should automatically scope the credentials
        VertexAiAnthropicChatModel model = VertexAiAnthropicChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(DEFAULT_MODEL_NAME)
                .credentials(credentials)
                .build();

        // then - should build successfully without throwing exceptions
        assertNotNull(model);
        assertThat(model.provider()).isEqualTo(GOOGLE_VERTEX_AI_ANTHROPIC);
    }
}
