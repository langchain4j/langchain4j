package dev.langchain4j.model.azure;

import com.azure.ai.openai.OpenAIServiceVersion;
import com.azure.ai.openai.models.*;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

import java.util.*;

import static com.azure.ai.openai.models.ChatRole.*;
import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.model.output.FinishReason.*;
import static java.util.stream.Collectors.toList;

public class InternalAzureOpenAiHelper {

    public static OpenAIServiceVersion getOpenAIServiceVersion(String serviceVersion) {
        for (OpenAIServiceVersion version : OpenAIServiceVersion.values()) {
            if (version.getVersion().equals(serviceVersion)) {
                return version;
            }
        }
        return OpenAIServiceVersion.getLatest();
    }

    public static List<com.azure.ai.openai.models.ChatMessage> toOpenAiMessages(List<ChatMessage> messages) {

        return messages.stream()
                .map(InternalAzureOpenAiHelper::toOpenAiMessage)
                .collect(toList());
    }

    public static com.azure.ai.openai.models.ChatMessage toOpenAiMessage(ChatMessage message) {
        com.azure.ai.openai.models.ChatMessage chatMessage =
                new com.azure.ai.openai.models.ChatMessage(roleFrom(message), message.text());

        chatMessage.setName(nameFrom(message));
        chatMessage.setFunctionCall(functionCallFrom(message));

        return chatMessage;
    }

    private static String nameFrom(ChatMessage message) {
        if (message instanceof UserMessage) {
            return ((UserMessage) message).name();
        }

        if (message instanceof ToolExecutionResultMessage) {
            return ((ToolExecutionResultMessage) message).toolName();
        }

        return null;
    }

    private static FunctionCall functionCallFrom(ChatMessage message) {
        if (message instanceof AiMessage) {
            AiMessage aiMessage = (AiMessage) message;
            if (aiMessage.toolExecutionRequest() != null) {
                return new FunctionCall(aiMessage.toolExecutionRequest().name(),
                        aiMessage.toolExecutionRequest().arguments());
            }
        }

        return null;
    }

    public static ChatRole roleFrom(ChatMessage message) {
        if (message instanceof AiMessage) {
            return ASSISTANT;
        } else if (message instanceof ToolExecutionResultMessage) {
            return FUNCTION;
        } else if (message instanceof SystemMessage) {
            return SYSTEM;
        } else {
            return USER;
        }
    }

    public static List<FunctionDefinition> toFunctions(Collection<ToolSpecification> toolSpecifications) {
        return toolSpecifications.stream()
                .map(InternalAzureOpenAiHelper::toFunction)
                .collect(toList());
    }

    private static FunctionDefinition toFunction(ToolSpecification toolSpecification) {
        FunctionDefinition functionDefinition = new FunctionDefinition(toolSpecification.name());
        functionDefinition.setDescription(toolSpecification.description());
        functionDefinition.setParameters(toOpenAiParameters(toolSpecification.parameters()));
        return functionDefinition;
    }

    private static Object toOpenAiParameters(ToolParameters toolParameters) {
        Parameters parameters = new Parameters();
        if (toolParameters == null) {
            return parameters;
        }
        parameters.setProperties(toolParameters.properties());
        parameters.setRequired(toolParameters.required());
        return parameters;
    }

    private static class Parameters {

        private String type = "object";

        private Map<String, Map<String, Object>> properties = new HashMap<>();
        private List<String> required = new ArrayList<>();

        public String getType() {
            return this.type;
        }

        public Map<String, Map<String, Object>> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, Map<String, Object>> properties) {
            this.properties = properties;
        }

        public List<String> getRequired() {
            return required;
        }

        public void setRequired(List<String> required) {
            this.required = required;
        }
    }

    public static AiMessage aiMessageFrom(com.azure.ai.openai.models.ChatMessage chatMessage) {
        if (chatMessage.getContent() != null) {
            return aiMessage(chatMessage.getContent());
        } else {
            FunctionCall functionCall = chatMessage.getFunctionCall();

            ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                    .name(functionCall.getName())
                    .arguments(functionCall.getArguments())
                    .build();

            return aiMessage(toolExecutionRequest);
        }
    }

    public static TokenUsage tokenUsageFrom(CompletionsUsage openAiUsage) {
        if (openAiUsage == null) {
            return null;
        }
        return new TokenUsage(
                openAiUsage.getPromptTokens(),
                openAiUsage.getCompletionTokens(),
                openAiUsage.getTotalTokens()
        );
    }

    public static FinishReason finishReasonFrom(CompletionsFinishReason openAiFinishReason) {
        if (openAiFinishReason == null) {
            return null;
        } else if (openAiFinishReason == CompletionsFinishReason.STOPPED) {
            return STOP;
        } else if (openAiFinishReason == CompletionsFinishReason.TOKEN_LIMIT_REACHED) {
            return LENGTH;
        } else if (openAiFinishReason == CompletionsFinishReason.CONTENT_FILTERED) {
            return CONTENT_FILTER;
        } else if (openAiFinishReason == CompletionsFinishReason.FUNCTION_CALL) {
            return TOOL_EXECUTION;
        } else {
            return null;
        }
    }
}
