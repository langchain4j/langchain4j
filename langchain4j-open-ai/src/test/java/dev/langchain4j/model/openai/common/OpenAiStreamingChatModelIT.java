package dev.langchain4j.model.openai.common;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatResponseMetadata;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import org.mockito.InOrder;

import java.util.List;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_1_NANO;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;

class OpenAiStreamingChatModelIT extends AbstractStreamingChatModelIT {

    public static OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder defaultStreamingModelBuilder() {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_1_NANO)
                .logRequests(false) // base64-encoded images are huge in logs
                .logResponses(true);
    }

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(
                defaultStreamingModelBuilder()
                        .build(),
                defaultStreamingModelBuilder()
                        .strictTools(true)
                        .build(),
                defaultStreamingModelBuilder()
                        .responseFormat("json_schema")
                        .strictJsonSchema(true)
                        .build()
                // TODO json_object?
        );
    }

    @Override
    protected StreamingChatModel createModelWith(ChatRequestParameters parameters) {
        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder openAiStreamingChatModelBuilder = OpenAiStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .defaultRequestParameters(parameters)
                .logRequests(true)
                .logResponses(true);
        if (parameters.modelName() == null) {
            openAiStreamingChatModelBuilder.modelName(GPT_4_1_NANO);
        }
        return openAiStreamingChatModelBuilder
                .build();
    }

    @Override
    protected String customModelName() {
        return "gpt-4o-2024-11-20";
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return OpenAiChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(StreamingChatModel streamingChatModel) {
        return OpenAiChatResponseMetadata.class;
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(StreamingChatModel streamingChatModel) {
        return OpenAiTokenUsage.class;
    }

    @Override
    public StreamingChatModel createModelWith(ChatModelListener listener) {
        return defaultStreamingModelBuilder()
                .listeners(List.of(listener))
                .build();
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id) {
        io.verify(handler, atLeast(1)).onPartialToolCall(argThat(toolCall ->
                toolCall.index() == 0
                        && toolCall.id().equals(id)
                        && toolCall.name().equals("getWeather")
                        && !toolCall.partialArguments().isBlank()
        ));
        io.verify(handler).onCompleteToolCall(argThat(toolCall ->
                {
                    ToolExecutionRequest request = toolCall.toolExecutionRequest();
                    return toolCall.index() == 0
                            && request.id().equals(id)
                            && request.name().equals("getWeather")
                            && request.arguments().replace(" ", "").equals("{\"city\":\"Munich\"}");
                }
        ));
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id1, String id2) {
        io.verify(handler, atLeast(1)).onPartialToolCall(argThat(toolCall ->
                toolCall.index() == 0
                        && toolCall.id().equals(id1)
                        && toolCall.name().equals("getWeather")
                        && !toolCall.partialArguments().isBlank()
        ));
        io.verify(handler).onCompleteToolCall(argThat(toolCall ->
                {
                    ToolExecutionRequest request = toolCall.toolExecutionRequest();
                    return toolCall.index() == 0
                            && request.id().equals(id1)
                            && request.name().equals("getWeather")
                            && request.arguments().replace(" ", "").equals("{\"city\":\"Munich\"}");
                }
        ));

        io.verify(handler, atLeast(1)).onPartialToolCall(argThat(toolCall ->
                toolCall.index() == 1
                        && toolCall.id().equals(id2)
                        && toolCall.name().equals("getTime")
                        && !toolCall.partialArguments().isBlank()
        ));
        io.verify(handler).onCompleteToolCall(argThat(toolCall ->
                {
                    ToolExecutionRequest request = toolCall.toolExecutionRequest();
                    return toolCall.index() == 1
                            && request.id().equals(id2)
                            && request.name().equals("getTime")
                            && request.arguments().replace(" ", "").equals("{\"country\":\"France\"}");
                }
        ));
    }

    // TODO OpenAI-specific tests
}
