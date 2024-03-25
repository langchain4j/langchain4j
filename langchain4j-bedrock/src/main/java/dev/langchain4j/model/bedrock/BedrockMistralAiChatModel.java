package dev.langchain4j.model.bedrock;

import static dev.langchain4j.internal.RetryUtils.withRetry;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.bedrock.internal.AbstractBedrockChatModel;
import dev.langchain4j.model.output.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

@Getter
@SuperBuilder
public class BedrockMistralAiChatModel extends AbstractBedrockChatModel<BedrockMistralAiChatModelResponse> {

    @Builder.Default
    private final int topK = 200;
    @Builder.Default
    private final String model = Types.Mistral7bInstructV0_2.getValue();

    @Override
    protected String getModelId() {
        return model;
    }

    @Override
    protected Map<String, Object> getRequestParameters(String prompt) {
        final Map<String, Object> parameters = new HashMap<>(7);

        parameters.put("prompt", prompt);
        parameters.put("max_tokens", getMaxTokens());
        parameters.put("temperature", getTemperature());
        parameters.put("top_p", getTopP());
        parameters.put("top_k", topK);
        parameters.put("stop", getStopSequences());

        return parameters;
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        String prompt = buildPrompt(messages);

        final Map<String, Object> requestParameters = getRequestParameters(prompt);
        final String body = Json.toJson(requestParameters);

        InvokeModelResponse invokeModelResponse = withRetry(() -> invoke(body), getMaxRetries());
        final String response = invokeModelResponse.body().asUtf8String().trim();
        final BedrockMistralAiChatModelResponse result = Json.fromJson(response, getResponseClassType());

        return new Response<>(new AiMessage(result.getOutputText()),
            result.getTokenUsage(),
            result.getFinishReason());
    }

    private String buildPrompt(List<ChatMessage> messages) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("<s>");

        for (ChatMessage message : messages) {
          switch (message.type()) {
            case USER:
              promptBuilder.append("[INST] ").append(message.text()).append(" [/INST]");
              break;
            case AI:
              promptBuilder.append(" ").append(message.text()).append(" ");
              break;
            default:
              throw new IllegalArgumentException("Bedrock Mistral AI does not support the message type: " + message.type());
          }
        }

        promptBuilder.append("</s>");
        return promptBuilder.toString();
    }

    @Override
    public Class<BedrockMistralAiChatModelResponse> getResponseClassType() {
        return BedrockMistralAiChatModelResponse.class;
    }

    /**
     * Bedrock Mistral model ids
     */
    @Getter
    public enum Types {
        Mistral7bInstructV0_2("mistral.mistral-7b-instruct-v0:2"),
        MistralMixtral8x7bInstructV0_1("mistral.mixtral-8x7b-instruct-v0:1");

        private final String value;

        Types(String modelID) {
            this.value = modelID;
        }
    }
}
