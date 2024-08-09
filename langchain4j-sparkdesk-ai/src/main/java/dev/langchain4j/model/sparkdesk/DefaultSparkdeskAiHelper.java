package dev.langchain4j.model.sparkdesk;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.sparkdesk.client.chat.http.HttpChatCompletionResponse;
import dev.langchain4j.model.sparkdesk.client.chat.http.HttpChoice;
import dev.langchain4j.model.sparkdesk.client.chat.wss.WssChatCompletionResponse;
import dev.langchain4j.model.sparkdesk.client.chat.wss.function.Function;
import dev.langchain4j.model.sparkdesk.client.chat.wss.function.FunctionCall;
import dev.langchain4j.model.sparkdesk.client.chat.wss.function.Parameters;
import dev.langchain4j.model.sparkdesk.client.embedding.EmbeddingResponse;
import dev.langchain4j.model.sparkdesk.client.message.AssistantMessage;
import dev.langchain4j.model.sparkdesk.client.message.Message;
import dev.langchain4j.model.sparkdesk.shared.ErrorResponse;
import dev.langchain4j.model.sparkdesk.shared.Usage;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Exceptions.illegalArgument;

@Slf4j
abstract class DefaultSparkdeskAiHelper {

    public static List<Embedding> toEmbed(List<EmbeddingResponse> responses) {
        return responses.stream()
                .map(response -> {
                    byte[] textData = Base64.getDecoder().decode(response.getPayload().getFeature().getText());
                    ByteBuffer byteBuffer = ByteBuffer.wrap(textData).order(ByteOrder.LITTLE_ENDIAN);
                    FloatBuffer floatBuffer = byteBuffer.asFloatBuffer();
                    float[] vector = new float[floatBuffer.remaining()];
                    floatBuffer.get(vector);
                    return Embedding.from(vector);
                })
                .collect(Collectors.toList());
    }


    public static List<Message> toSparkdeskiMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(DefaultSparkdeskAiHelper::toSparkdeskAiMessage)
                .collect(Collectors.toList());
    }

    public static List<Function> toFunctions(List<ToolSpecification> toolSpecifications) {
        return toolSpecifications.stream()
                .map(DefaultSparkdeskAiHelper::toFunction)
                .collect(Collectors.toList());
    }


    private static Function toFunction(ToolSpecification toolSpecification) {
        return Function.builder()
                .name(toolSpecification.name())
                .description(toolSpecification.description())
                .parameters(toFunctionParameters(toolSpecification.parameters()))
                .build();
    }

    private static Parameters toFunctionParameters(ToolParameters toolParameters) {
        if (toolParameters == null) {
            return Parameters.builder().build();
        }
        return Parameters.builder()
                .properties(toolParameters.properties())
                .required(toolParameters.required())
                .build();
    }

    private static Message toSparkdeskAiMessage(ChatMessage message) {

        if (message instanceof SystemMessage) {
            SystemMessage systemMessage = (SystemMessage) message;
            return dev.langchain4j.model.sparkdesk.client.message.SystemMessage.builder()
                    .content(systemMessage.text())
                    .build();
        }

        if (message instanceof UserMessage) {
            UserMessage userMessage = (UserMessage) message;
            return dev.langchain4j.model.sparkdesk.client.message.UserMessage.builder()
                    .content(userMessage.singleText())
                    .build();
        }

        if (message instanceof AiMessage) {
            AiMessage aiMessage = (AiMessage) message;
            if (aiMessage.hasToolExecutionRequests()) {
                log.warn("ToolExecutionRequests will be ignored because the spark desk cognitive model currently does not support adding function_call in the context.");
            }
            return AssistantMessage.builder()
                    .content(aiMessage.text())
                    .build();
        }

        if (message instanceof ToolExecutionResultMessage) {
            throw illegalArgument("SparkDesk AI model currently does not support adding tool execution result responses in the context");
        }

        throw illegalArgument("Unknown Message type: " + message.type());
    }

    public static AiMessage aiMessageFrom(HttpChatCompletionResponse response) {
        AssistantMessage message = response.getChoices().get(0).getMessage();
        return AiMessage.from(message.getContent());
    }

    public static AiMessage aiMessageFrom(WssChatCompletionResponse response) {
        AssistantMessage message = response.getPayload().getChoices().getText().get(0);
        FunctionCall functionCall = message.getFunctionCall();
        if (Objects.isNull(functionCall)) {
            return AiMessage.from(message.getContent());
        }
        return AiMessage.aiMessage(ToolExecutionRequest.builder()
                .name(functionCall.getName())
                .arguments(functionCall.getArguments())
                .build());
    }


    public static TokenUsage tokenUsageFrom(Usage sparkdeskUsage) {
        if (sparkdeskUsage == null) {
            return null;
        }
        return new TokenUsage(
                sparkdeskUsage.getPromptTokens(),
                sparkdeskUsage.getCompletionTokens(),
                sparkdeskUsage.getTotalTokens()
        );
    }

    public static HttpChatCompletionResponse toChatErrorResponse(retrofit2.Response<?> retrofitResponse) throws IOException {
        try (ResponseBody errorBody = retrofitResponse.errorBody()) {
            return HttpChatCompletionResponse.builder()
                    .choices(Collections.singletonList(toChatErrorChoice(errorBody)))
                    .usage(Usage.builder().build())
                    .build();
        }
    }

    /**
     * error code see <a href="https://www.xfyun.cn/document/error-code">error codes document</a>
     */
    private static HttpChoice toChatErrorChoice(ResponseBody errorBody) throws IOException {
        if (errorBody == null) {
            return HttpChoice.builder()
                    .build();
        }
        ErrorResponse errorResponse = Json.fromJson(errorBody.string(), ErrorResponse.class);
        return HttpChoice.builder()
                .message(AssistantMessage.builder().content(errorResponse.getError().get("Message")).build())
                .build();
    }
}
