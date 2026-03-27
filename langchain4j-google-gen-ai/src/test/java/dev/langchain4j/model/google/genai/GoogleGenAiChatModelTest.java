package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.SafetySetting;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoogleGenAiChatModelTest {

    // --- Builder tests ---

    @Test
    void should_build_with_api_key_and_model_name() {
        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .apiKey("test-key")
                .modelName("gemini-2.0-flash")
                .build();

        assertThat(model).isNotNull();
        assertThat(model.provider()).isEqualTo(ModelProvider.GOOGLE_AI_GEMINI);
    }

    @Test
    void should_build_with_custom_client() {
        Client client = Client.builder().apiKey("test").build();

        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .client(client)
                .modelName("gemini-2.0-flash")
                .build();

        assertThat(model).isNotNull();
    }

    @Test
    void should_throw_when_model_name_is_blank() {
        assertThatThrownBy(
                        () -> GoogleGenAiChatModel.builder().apiKey("test-key").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_set_default_request_parameters() {
        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .apiKey("test-key")
                .modelName("gemini-2.0-flash")
                .temperature(0.7)
                .topP(0.9)
                .topK(40)
                .maxOutputTokens(512)
                .stopSequences(List.of("END"))
                .build();

        assertThat(model.defaultRequestParameters().temperature()).isEqualTo(0.7);
        assertThat(model.defaultRequestParameters().topP()).isEqualTo(0.9);
        assertThat(model.defaultRequestParameters().topK()).isEqualTo(40);
        assertThat(model.defaultRequestParameters().maxOutputTokens()).isEqualTo(512);
        assertThat(model.defaultRequestParameters().stopSequences()).containsExactly("END");
    }

    @Test
    void should_return_empty_listeners_by_default() {
        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .apiKey("test-key")
                .modelName("gemini-2.0-flash")
                .build();

        assertThat(model.listeners()).isEmpty();
    }

    @Test
    void should_return_listeners_when_configured() {
        ChatModelListener listener = mock(ChatModelListener.class);

        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .apiKey("test-key")
                .modelName("gemini-2.0-flash")
                .listeners(List.of(listener))
                .build();

        assertThat(model.listeners()).hasSize(1);
    }

    @Test
    void should_return_supported_capabilities() {
        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .apiKey("test-key")
                .modelName("gemini-2.0-flash")
                .build();

        assertThat(model.supportedCapabilities()).containsExactly(Capability.RESPONSE_FORMAT_JSON_SCHEMA);
    }

    // --- Builder with all options ---

    @Test
    void should_build_with_all_builder_options() {
        Client client = Client.builder().apiKey("test").build();

        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .client(client)
                .modelName("gemini-2.0-flash")
                .temperature(0.5)
                .topP(0.8)
                .topK(30)
                .maxOutputTokens(1024)
                .thinkingBudget(500)
                .seed(42)
                .stopSequences(List.of("STOP"))
                .maxRetries(5)
                .safetySettings(List.of(SafetySetting.builder().build()))
                .responseSchema(Schema.builder().type(Type.Known.OBJECT).build())
                .responseMimeType("application/json")
                .enableGoogleSearch(true)
                .enableGoogleMaps(true)
                .enableUrlContext(true)
                .allowedFunctionNames(List.of("fn1"))
                .logRequests(true)
                .logResponses(true)
                .listeners(List.of(mock(ChatModelListener.class)))
                .timeout(Duration.ofSeconds(30))
                .build();

        assertThat(model).isNotNull();
    }

    @Test
    void should_build_with_null_optional_fields() {
        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .apiKey("test-key")
                .modelName("gemini-2.0-flash")
                .maxRetries(null)
                .logRequests(null)
                .logResponses(null)
                .listeners(null)
                .safetySettings(null)
                .build();

        assertThat(model).isNotNull();
        assertThat(model.listeners()).isEmpty();
    }

    // --- Builder method chain ---

    @Test
    void should_support_all_builder_setters() {
        GoogleGenAiChatModel.Builder builder = GoogleGenAiChatModel.builder();

        assertThat(builder.apiKey("key")).isSameAs(builder);
        assertThat(builder.modelName("model")).isSameAs(builder);
        assertThat(builder.temperature(0.5)).isSameAs(builder);
        assertThat(builder.topP(0.8)).isSameAs(builder);
        assertThat(builder.topK(40)).isSameAs(builder);
        assertThat(builder.maxOutputTokens(100)).isSameAs(builder);
        assertThat(builder.thinkingBudget(500)).isSameAs(builder);
        assertThat(builder.seed(42)).isSameAs(builder);
        assertThat(builder.stopSequences(List.of("STOP"))).isSameAs(builder);
        assertThat(builder.maxRetries(3)).isSameAs(builder);
        assertThat(builder.timeout(Duration.ofSeconds(10))).isSameAs(builder);
        assertThat(builder.enableGoogleSearch(true)).isSameAs(builder);
        assertThat(builder.enableGoogleMaps(true)).isSameAs(builder);
        assertThat(builder.enableUrlContext(true)).isSameAs(builder);
        assertThat(builder.generateContentConfig(GenerateContentConfig.builder().build())).isSameAs(builder);
        assertThat(builder.logRequests(true)).isSameAs(builder);
        assertThat(builder.logResponses(true)).isSameAs(builder);
        assertThat(builder.safetySettings(List.of())).isSameAs(builder);
        assertThat(builder.responseSchema(Schema.builder().build())).isSameAs(builder);
        assertThat(builder.responseMimeType("text/plain")).isSameAs(builder);
        assertThat(builder.allowedFunctionNames(List.of("fn1"))).isSameAs(builder);
        assertThat(builder.listeners(List.of())).isSameAs(builder);
    }

    @Test
    void should_build_with_generate_content_config() {
        GenerateContentConfig config = GenerateContentConfig.builder()
                .temperature(0.5f)
                .topP(0.9f)
                .build();

        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .apiKey("test-key")
                .modelName("gemini-2.0-flash")
                .generateContentConfig(config)
                .build();

        assertThat(model).isNotNull();
    }

    @Test
    void should_build_with_google_credentials_builder_setters() {
        GoogleGenAiChatModel.Builder builder = GoogleGenAiChatModel.builder();

        assertThat(builder.googleCredentials(null)).isSameAs(builder);
        assertThat(builder.projectId("project")).isSameAs(builder);
        assertThat(builder.location("us-central1")).isSameAs(builder);
        assertThat(builder.toolConfig(null)).isSameAs(builder);
    }
}
