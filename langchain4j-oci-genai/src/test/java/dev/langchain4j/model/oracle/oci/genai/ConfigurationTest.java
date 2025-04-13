package dev.langchain4j.model.oracle.oci.genai;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

import com.oracle.bmc.Region;
import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceClient;
import com.oracle.bmc.generativeaiinference.model.ChatChoice;
import com.oracle.bmc.generativeaiinference.model.ChatResult;
import com.oracle.bmc.generativeaiinference.model.CohereChatRequest;
import com.oracle.bmc.generativeaiinference.model.CohereChatResponse;
import com.oracle.bmc.generativeaiinference.model.GenericChatRequest;
import com.oracle.bmc.generativeaiinference.model.GenericChatResponse;
import com.oracle.bmc.generativeaiinference.model.TextContent;
import com.oracle.bmc.generativeaiinference.model.UserMessage;
import com.oracle.bmc.generativeaiinference.requests.ChatRequest;
import com.oracle.bmc.generativeaiinference.responses.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class ConfigurationTest {

    @Test
    void requiredProperties() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            try (var model = OciGenAiChatModel.builder().build()) {}
        });
    }

    @Test
    void cohere() throws IOException {
        GenerativeAiInferenceClient client = Mockito.mock(GenerativeAiInferenceClient.class);

        var builder = OciGenAiCohereChatModel.builder()
                .genAiClient(client)
                .chatModelId("chatModelId")
                .compartmentId("compartmentId")
                .citationQuality(CohereChatRequest.CitationQuality.Fast)
                .documents(List.of("documents"))
                .frequencyPenalty(0.007)
                .isRawPrompting(true)
                .isSearchQueriesOnly(true)
                .maxInputTokens(999)
                .preambleOverride("preambleOverride")
                .promptTruncation(CohereChatRequest.PromptTruncation.Off)
                .maxTokens(666)
                .presencePenalty(0.006)
                .region(Region.AP_TOKYO_1)
                .seed(123456789)
                .stop(List.of("stop"))
                .temperature(1.1)
                .topK(11)
                .topP(11.11);

        Mockito.when(client.chat(Mockito.any())).thenAnswer(mock -> {
            ChatRequest req = mock.getArgument(0);
            var chatDetails = req.getChatDetails();
            CohereChatRequest cohereReq = (CohereChatRequest) chatDetails.getChatRequest();

            assertThat(chatDetails.getCompartmentId(), is("compartmentId"));
            assertThat(cohereReq.getCitationQuality(), is(CohereChatRequest.CitationQuality.Fast));
            assertThat(cohereReq.getDocuments(), contains("documents"));
            assertThat(cohereReq.getFrequencyPenalty(), is(0.007));
            assertThat(cohereReq.getIsRawPrompting(), is(true));
            assertThat(cohereReq.getIsSearchQueriesOnly(), is(true));
            assertThat(cohereReq.getMaxInputTokens(), is(999));
            assertThat(cohereReq.getPreambleOverride(), is("preambleOverride"));
            assertThat(cohereReq.getPromptTruncation(), is(CohereChatRequest.PromptTruncation.Off));
            assertThat(cohereReq.getMaxTokens(), is(666));
            assertThat(cohereReq.getPresencePenalty(), is(0.006));
            assertThat(cohereReq.getSeed(), is(123456789));
            assertThat(cohereReq.getStopSequences(), contains("stop"));
            assertThat(cohereReq.getTemperature(), is(1.1));
            assertThat(cohereReq.getTopK(), is(11));
            assertThat(cohereReq.getTopP(), is(11.11));

            return ChatResponse.builder()
                    .chatResult(ChatResult.builder()
                            .chatResponse(
                                    CohereChatResponse.builder().text("Huh!").build())
                            .build())
                    .build();
        });

        try (var model = builder.build()) {
            assertThat(model.chat("BAF!"), is("Huh!"));
        }
    }

    @Test
    void generic() throws IOException {
        GenerativeAiInferenceClient client = Mockito.mock(GenerativeAiInferenceClient.class);

        var builder = OciGenAiChatModel.builder()
                .genAiClient(client)
                .chatModelId("chatModelId")
                .compartmentId("compartmentId")
                .frequencyPenalty(0.007)
                .logitBias(0.008)
                .numGenerations(33)
                .maxTokens(666)
                .presencePenalty(0.006)
                .region(Region.AP_TOKYO_1)
                .seed(123456789)
                .stop(List.of("stop"))
                .temperature(1.1)
                .topK(11)
                .topP(11.11);

        Mockito.when(client.chat(Mockito.any())).thenAnswer(mock -> {
            ChatRequest req = mock.getArgument(0);
            var chatDetails = req.getChatDetails();
            GenericChatRequest cohereReq = (GenericChatRequest) chatDetails.getChatRequest();

            assertThat(chatDetails.getCompartmentId(), is("compartmentId"));
            assertThat(cohereReq.getLogitBias(), is(0.008));
            assertThat(cohereReq.getNumGenerations(), is(33));
            assertThat(cohereReq.getFrequencyPenalty(), is(0.007));
            assertThat(cohereReq.getMaxTokens(), is(666));
            assertThat(cohereReq.getPresencePenalty(), is(0.006));
            assertThat(cohereReq.getSeed(), is(123456789));
            assertThat(cohereReq.getStop(), contains("stop"));
            assertThat(cohereReq.getTemperature(), is(1.1));
            assertThat(cohereReq.getTopK(), is(11));
            assertThat(cohereReq.getTopP(), is(11.11));

            return ChatResponse.builder()
                    .chatResult(ChatResult.builder()
                            .chatResponse(GenericChatResponse.builder()
                                    .choices(List.of(ChatChoice.builder()
                                            .message(UserMessage.builder()
                                                    .content(List.of(TextContent.builder()
                                                            .text("Huh!")
                                                            .build()))
                                                    .name("test-name")
                                                    .build())
                                            .finishReason("stop")
                                            .build()))
                                    .build())
                            .build())
                    .build();
        });

        try (var model = builder.build()) {
            assertThat(model.chat("BAF!"), is("Huh!"));
            var userMessage = dev.langchain4j.data.message.UserMessage.userMessage("BAF!");
            var response = model.chat(userMessage);
            assertThat(response.finishReason(), is(FinishReason.STOP));
        }
    }
}
