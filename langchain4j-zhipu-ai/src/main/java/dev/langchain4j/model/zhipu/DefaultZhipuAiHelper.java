package dev.langchain4j.model.zhipu;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.chat.listener.ChatModelResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.zhipu.chat.Content;
import dev.langchain4j.model.zhipu.chat.*;
import dev.langchain4j.model.zhipu.embedding.EmbeddingResponse;
import dev.langchain4j.model.zhipu.shared.ErrorResponse;
import dev.langchain4j.model.zhipu.shared.Usage;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.output.FinishReason.*;

class DefaultZhipuAiHelper {

    static final String FINISH_REASON_SENSITIVE = "sensitive";
    static final String FINISH_REASON_OTHER = "other";

    static List<Embedding> toEmbed(List<EmbeddingResponse> response) {
        return response.stream()
                .map(zhipuAiEmbedding -> Embedding.from(zhipuAiEmbedding.getEmbedding()))
                .collect(Collectors.toList());
    }

    static List<Tool> toTools(List<ToolSpecification> toolSpecifications) {
        return toolSpecifications.stream()
                .map(toolSpecification -> Tool.from(toFunction(toolSpecification)))
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


    static List<Message> toZhipuAiMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(DefaultZhipuAiHelper::toZhipuAiMessage)
                .collect(Collectors.toList());
    }

    private static Message toZhipuAiMessage(ChatMessage message) {

        if (message instanceof SystemMessage) {
            SystemMessage systemMessage = (SystemMessage) message;
            return dev.langchain4j.model.zhipu.chat.SystemMessage.builder()
                    .content(systemMessage.text())
                    .build();
        }

        if (message instanceof UserMessage) {
            UserMessage userMessage = (UserMessage) message;
            if (userMessage.hasSingleText()) {
                return dev.langchain4j.model.zhipu.chat.UserMessage.from(userMessage.singleText());
            }
            List<Content> contents = new ArrayList<>(userMessage.contents().size());
            userMessage.contents().forEach(content -> {
                if (content instanceof TextContent) {
                    TextContent textContent = (TextContent) content;
                    contents.add(dev.langchain4j.model.zhipu.chat.TextContent.builder()
                            .text(textContent.text())
                            .build());
                }
                if (content instanceof ImageContent) {
                    Image image = ((ImageContent) content).image();
                    contents.add(dev.langchain4j.model.zhipu.chat.ImageContent.builder()
                            .imageUrl(dev.langchain4j.model.zhipu.chat.Image.builder()
                                    .url(image.url() != null ? image.url().toString() : image.base64Data())
                                    .build())
                            .build());
                }
            });
            return dev.langchain4j.model.zhipu.chat.UserMessage.from(contents);
        }

        if (message instanceof AiMessage) {
            AiMessage aiMessage = (AiMessage) message;
            if (!aiMessage.hasToolExecutionRequests()) {
                return AssistantMessage.builder()
                        .content(aiMessage.text())
                        .build();
            }
            List<ToolCall> toolCallArrayList = new ArrayList<>();
            for (ToolExecutionRequest executionRequest : aiMessage.toolExecutionRequests()) {
                toolCallArrayList.add(ToolCall.builder()
                        .function(
                                FunctionCall.builder()
                                        .name(executionRequest.name())
                                        .arguments(executionRequest.arguments())
                                        .build()
                        )
                        .type(ToolType.FUNCTION)
                        .id(executionRequest.id())
                        .build()
                );
            }
            return AssistantMessage.builder()
                    .content(aiMessage.text())
                    .toolCalls(toolCallArrayList)
                    .build();
        }

        if (message instanceof ToolExecutionResultMessage) {
            ToolExecutionResultMessage resultMessage = (ToolExecutionResultMessage) message;
            return ToolMessage.builder()
                    .content(resultMessage.text())
                    .build();
        }

        throw illegalArgument("Unknown message type: " + message.type());
    }

    static AiMessage aiMessageFrom(ChatCompletionResponse response) {
        AssistantMessage message = response.getChoices().get(0).getMessage();
        if (isNullOrEmpty(message.getToolCalls())) {
            return AiMessage.from(message.getContent());
        }

        return AiMessage.from(specificationsFrom(message.getToolCalls()));
    }

    static List<ToolExecutionRequest> specificationsFrom(List<ToolCall> toolCalls) {
        List<ToolExecutionRequest> specifications = new ArrayList<>(toolCalls.size());
        for (ToolCall toolCall : toolCalls) {
            specifications.add(
                    ToolExecutionRequest.builder()
                            .id(toolCall.getId())
                            .name(toolCall.getFunction().getName())
                            .arguments(toolCall.getFunction().getArguments())
                            .build()
            );
        }
        return specifications;
    }

    static Usage getEmbeddingUsage(List<EmbeddingResponse> responses) {
        Usage tokenUsage = Usage.builder()
                .completionTokens(0)
                .promptTokens(0)
                .totalTokens(0)
                .build();

        for (EmbeddingResponse response : responses) {
            tokenUsage.add(response.getUsage());
        }
        return tokenUsage;
    }


    static TokenUsage tokenUsageFrom(Usage zhipuUsage) {
        if (zhipuUsage == null) {
            return null;
        }
        return new TokenUsage(
                zhipuUsage.getPromptTokens(),
                zhipuUsage.getCompletionTokens(),
                zhipuUsage.getTotalTokens()
        );
    }

    static ChatCompletionResponse toChatErrorResponse(Object object) {
        return ChatCompletionResponse.builder()
                .choices(Collections.singletonList(toChatErrorChoice(object)))
                .usage(Usage.builder().build())
                .build();
    }

    /**
     * error code see <a href="https://open.bigmodel.cn/dev/api#error-code-v3">error codes document</a>
     */
    private static ChatCompletionChoice toChatErrorChoice(Object object) {
        if (object instanceof Throwable) {
            Throwable throwable = (Throwable) object;
            return ChatCompletionChoice.builder()
                    .message(AssistantMessage.builder().content(throwable.getMessage()).build())
                    .finishReason(FINISH_REASON_OTHER)
                    .build();
        }
        ResponseBody errorBody = ((retrofit2.Response<?>) object).errorBody();

        if (errorBody == null) {
            return ChatCompletionChoice.builder()
                    .finishReason(FINISH_REASON_OTHER)
                    .build();
        }
        ErrorResponse errorResponse;
        try {
            errorResponse = Json.fromJson(errorBody.string(), ErrorResponse.class);
            String code = errorResponse.getError().get("code");
            return ChatCompletionChoice.builder()
                    .message(AssistantMessage.builder().content(errorResponse.getError().get("message")).build())
                    .finishReason(getFinishReason(code))
                    .build();
        } catch (IOException e) {
            return ChatCompletionChoice.builder()
                    .message(AssistantMessage.builder().content(e.getMessage()).build())
                    .finishReason(FINISH_REASON_OTHER)
                    .build();
        } finally {
            errorBody.close();
        }
    }

    static String getFinishReason(Object o) {
        if (o instanceof String) {
            // 1301: 系统检测到输入或生成内容可能包含不安全或敏感内容，请您避免输入易产生敏感内容的提示语，感谢您的配合
            if ("1301".equals(o)) {
                return FINISH_REASON_SENSITIVE;
            }
        }
        if (o instanceof ZhipuAiException) {
            ZhipuAiException exception = (ZhipuAiException) o;
            if ("1301".equals(exception.getCode())) {
                return FINISH_REASON_SENSITIVE;
            }
        }
        return FINISH_REASON_OTHER;
    }


    static ChatModelRequest createModelListenerRequest(ChatCompletionRequest options,
                                                       List<ChatMessage> messages,
                                                       List<ToolSpecification> toolSpecifications) {
        return ChatModelRequest.builder()
                .model(options.getModel())
                .temperature(options.getTemperature())
                .topP(options.getTopP())
                .maxTokens(options.getMaxTokens())
                .messages(messages)
                .toolSpecifications(toolSpecifications)
                .build();
    }

    static ChatModelResponse createModelListenerResponse(String responseId,
                                                         String responseModel,
                                                         Response<AiMessage> response) {
        if (response == null) {
            return null;
        }

        return ChatModelResponse.builder()
                .id(responseId)
                .model(responseModel)
                .tokenUsage(response.tokenUsage())
                .finishReason(response.finishReason())
                .aiMessage(response.content())
                .build();
    }

    static boolean isSuccessFinishReason(FinishReason finishReason) {
        return !CONTENT_FILTER.equals(finishReason) && !OTHER.equals(finishReason);
    }

    static FinishReason finishReasonFrom(String finishReason) {
        if (finishReason == null) {
            return null;
        }
        switch (finishReason) {
            case "stop":
                return STOP;
            case "length":
                return LENGTH;
            case "tool_calls":
                return TOOL_EXECUTION;
            case "sensitive":
                return CONTENT_FILTER;
            default:
                return OTHER;
        }
    }
}
