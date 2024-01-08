package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.chat.*;
import dev.ai4j.openai4j.shared.Usage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

import java.util.Collection;
import java.util.List;

import static dev.ai4j.openai4j.chat.ContentType.IMAGE_URL;
import static dev.ai4j.openai4j.chat.ContentType.TEXT;
import static dev.ai4j.openai4j.chat.ToolType.FUNCTION;
import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.output.FinishReason.*;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

public class InternalOpenAiHelper {

    static final String OPENAI_URL = "https://api.openai.com/v1";

    static final String OPENAI_DEMO_API_KEY = "demo";
    static final String OPENAI_DEMO_URL = "http://langchain4j.dev/demo/openai/v1";

    public static List<Message> toOpenAiMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(InternalOpenAiHelper::toOpenAiMessage)
                .collect(toList());
    }

    public static Message toOpenAiMessage(ChatMessage message) {
        if (message instanceof SystemMessage) {
            return dev.ai4j.openai4j.chat.SystemMessage.from(((SystemMessage) message).text());
        }

        if (message instanceof UserMessage) {
            UserMessage userMessage = (UserMessage) message;

            if (userMessage.hasSingleText()) {
                return dev.ai4j.openai4j.chat.UserMessage.builder()
                        .content(userMessage.text())
                        .name(userMessage.name())
                        .build();
            } else {
                return dev.ai4j.openai4j.chat.UserMessage.builder()
                        .content(userMessage.contents().stream()
                                .map(InternalOpenAiHelper::toOpenAiContent)
                                .collect(toList()))
                        .name(userMessage.name())
                        .build();
            }
        }

        if (message instanceof AiMessage) {
            AiMessage aiMessage = (AiMessage) message;

            if (!aiMessage.hasToolExecutionRequests()) {
                return AssistantMessage.from(aiMessage.text());
            }

            ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
            if (toolExecutionRequest.id() == null) {
                FunctionCall functionCall = FunctionCall.builder()
                        .name(toolExecutionRequest.name())
                        .arguments(toolExecutionRequest.arguments())
                        .build();

                return AssistantMessage.builder()
                        .functionCall(functionCall)
                        .build();
            }

            List<ToolCall> toolCalls = aiMessage.toolExecutionRequests().stream()
                    .map(it -> ToolCall.builder()
                            .id(it.id())
                            .type(FUNCTION)
                            .function(FunctionCall.builder()
                                    .name(it.name())
                                    .arguments(it.arguments())
                                    .build())
                            .build())
                    .collect(toList());

            return AssistantMessage.builder()
                    .toolCalls(toolCalls)
                    .build();
        }

        if (message instanceof ToolExecutionResultMessage) {
            ToolExecutionResultMessage toolExecutionResultMessage = (ToolExecutionResultMessage) message;

            if (toolExecutionResultMessage.id() == null) {
                return FunctionMessage.from(toolExecutionResultMessage.toolName(), toolExecutionResultMessage.text());
            }

            return ToolMessage.from(toolExecutionResultMessage.id(), toolExecutionResultMessage.text());
        }

        throw illegalArgument("Unknown message type: " + message.type());
    }

    private static dev.ai4j.openai4j.chat.Content toOpenAiContent(Content content) {
        if (content instanceof TextContent) {
            return toOpenAiContent((TextContent) content);
        } else if (content instanceof ImageContent) {
            return toOpenAiContent((ImageContent) content);
        } else {
            throw illegalArgument("Unknown content type: " + content);
        }
    }

    private static dev.ai4j.openai4j.chat.Content toOpenAiContent(TextContent content) {
        return dev.ai4j.openai4j.chat.Content.builder()
                .type(TEXT)
                .text(content.text())
                .build();
    }

    private static dev.ai4j.openai4j.chat.Content toOpenAiContent(ImageContent content) {
        return dev.ai4j.openai4j.chat.Content.builder()
                .type(IMAGE_URL)
                .imageUrl(ImageUrl.builder()
                        .url(toUrl(content.image()))
                        .detail(toDetail(content.detailLevel()))
                        .build())
                .build();
    }

    private static String toUrl(Image image) {
        if (image.url() != null) {
            return image.url().toString();
        }
        return format("data:%s;base64,%s", image.mimeType(), image.base64Data());
    }

    private static ImageDetail toDetail(ImageContent.DetailLevel detailLevel) {
        if (detailLevel == null) {
            return null;
        }
        return ImageDetail.valueOf(detailLevel.name());
    }

    public static List<Tool> toTools(Collection<ToolSpecification> toolSpecifications) {
        return toolSpecifications.stream()
                .map(InternalOpenAiHelper::toTool)
                .collect(toList());
    }

    private static Tool toTool(ToolSpecification toolSpecification) {
        Function function = Function.builder()
                .name(toolSpecification.name())
                .description(toolSpecification.description())
                .parameters(toOpenAiParameters(toolSpecification.parameters()))
                .build();
        return Tool.from(function);
    }

    @Deprecated
    public static List<Function> toFunctions(Collection<ToolSpecification> toolSpecifications) {
        return toolSpecifications.stream()
                .map(InternalOpenAiHelper::toFunction)
                .collect(toList());
    }

    @Deprecated
    private static Function toFunction(ToolSpecification toolSpecification) {
        return Function.builder()
                .name(toolSpecification.name())
                .description(toolSpecification.description())
                .parameters(toOpenAiParameters(toolSpecification.parameters()))
                .build();
    }

    private static dev.ai4j.openai4j.chat.Parameters toOpenAiParameters(ToolParameters toolParameters) {
        if (toolParameters == null) {
            return dev.ai4j.openai4j.chat.Parameters.builder().build();
        }
        return dev.ai4j.openai4j.chat.Parameters.builder()
                .properties(toolParameters.properties())
                .required(toolParameters.required())
                .build();
    }

    public static AiMessage aiMessageFrom(ChatCompletionResponse response) {
        AssistantMessage assistantMessage = response.choices().get(0).message();

        List<ToolCall> toolCalls = assistantMessage.toolCalls();
        if (!isNullOrEmpty(toolCalls)) {
            List<ToolExecutionRequest> toolExecutionRequests = toolCalls.stream()
                    .filter(toolCall -> toolCall.type() == FUNCTION)
                    .map(InternalOpenAiHelper::toToolExecutionRequest)
                    .collect(toList());
            return aiMessage(toolExecutionRequests);
        }

        FunctionCall functionCall = assistantMessage.functionCall();
        if (functionCall != null) {
            ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                    .name(functionCall.name())
                    .arguments(functionCall.arguments())
                    .build();
            return aiMessage(toolExecutionRequest);
        }

        return aiMessage(assistantMessage.content());
    }

    private static ToolExecutionRequest toToolExecutionRequest(ToolCall toolCall) {
        FunctionCall functionCall = toolCall.function();
        return ToolExecutionRequest.builder()
                .id(toolCall.id())
                .name(functionCall.name())
                .arguments(functionCall.arguments())
                .build();
    }

    public static TokenUsage tokenUsageFrom(Usage openAiUsage) {
        if (openAiUsage == null) {
            return null;
        }
        return new TokenUsage(
                openAiUsage.promptTokens(),
                openAiUsage.completionTokens(),
                openAiUsage.totalTokens()
        );
    }

    public static FinishReason finishReasonFrom(String openAiFinishReason) {
        if (openAiFinishReason == null) {
            return null;
        }
        switch (openAiFinishReason) {
            case "stop":
                return STOP;
            case "length":
                return LENGTH;
            case "tool_calls":
            case "function_call":
                return TOOL_EXECUTION;
            case "content_filter":
                return CONTENT_FILTER;
            default:
                return null;
        }
    }
}
