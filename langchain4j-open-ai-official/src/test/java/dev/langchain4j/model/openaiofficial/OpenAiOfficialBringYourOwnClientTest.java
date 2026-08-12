package dev.langchain4j.model.openaiofficial;

import static org.assertj.core.api.Assertions.assertThat;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import dev.langchain4j.model.ModelProvider;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OpenAiOfficialBringYourOwnClientTest {

    @Test
    void should_initialize_default_request_parameters_and_provider_when_openAIClient_is_supplied() {
        OpenAIClient openAIClient = Mockito.mock(OpenAIClient.class);

        OpenAiOfficialChatModel model = OpenAiOfficialChatModel.builder()
                .openAIClient(openAIClient)
                .modelName("gpt-4o-mini")
                .build();

        assertThat(model.defaultRequestParameters()).isNotNull();
        assertThat(model.defaultRequestParameters().modelName()).isEqualTo("gpt-4o-mini");
        assertThat(model.provider()).isEqualTo(ModelProvider.OPEN_AI);
        assertThat(model.supportedCapabilities()).isNotNull();
    }

    @Test
    void should_initialize_default_request_parameters_and_provider_when_openAIClientAsync_is_supplied() {
        OpenAIClientAsync openAIClientAsync = Mockito.mock(OpenAIClientAsync.class);

        OpenAiOfficialStreamingChatModel model = OpenAiOfficialStreamingChatModel.builder()
                .openAIClientAsync(openAIClientAsync)
                .modelName("gpt-4o-mini")
                .build();

        assertThat(model.defaultRequestParameters()).isNotNull();
        assertThat(model.defaultRequestParameters().modelName()).isEqualTo("gpt-4o-mini");
        assertThat(model.provider()).isEqualTo(ModelProvider.OPEN_AI);
        assertThat(model.supportedCapabilities()).isNotNull();
    }
}
