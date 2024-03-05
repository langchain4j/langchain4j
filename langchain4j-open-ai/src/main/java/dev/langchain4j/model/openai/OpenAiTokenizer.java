package dev.langchain4j.model.openai;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.Tokenizer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Json.fromJson;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.openai.OpenAiChatModelName.*;
import static java.util.Collections.singletonList;

/**
 * This class can be used to estimate the cost (in tokens) before calling OpenAI or when using streaming.
 * Magic numbers present in this class were found empirically while testing.
 * There are integration tests in place that are making sure that the calculations here are very close to that of OpenAI.
 */
public class OpenAiTokenizer implements Tokenizer {

    private final String modelName;
    private final Optional<Encoding> encoding;

    public OpenAiTokenizer(String modelName) {
        this.modelName = ensureNotBlank(modelName, "modelName");
        // If the model is unknown, we should NOT fail fast during the creation of OpenAiTokenizer.
        // Doing so would cause the failure of every OpenAI***Model that uses this tokenizer.
        // This is done to account for situations when a new OpenAI model is available,
        // but JTokkit does not yet support it.
        this.encoding = Encodings.newLazyEncodingRegistry().getEncodingForModel(modelName);
    }

    public int estimateTokenCountInText(String text) {
        return encoding.orElseThrow(unknownModelException())
                .countTokensOrdinary(text);
    }

    @Override
    public int estimateTokenCountInMessage(ChatMessage message) {
        int tokenCount = 1; // 1 token for role
        tokenCount += extraTokensPerMessage();

        if (message instanceof SystemMessage) {
            tokenCount += estimateTokenCountIn((SystemMessage) message);
        } else if (message instanceof UserMessage) {
            tokenCount += estimateTokenCountIn((UserMessage) message);
        } else if (message instanceof AiMessage) {
            tokenCount += estimateTokenCountIn((AiMessage) message);
        } else if (message instanceof ToolExecutionResultMessage) {
            tokenCount += estimateTokenCountIn((ToolExecutionResultMessage) message);
        } else {
            throw new IllegalArgumentException("Unknown message type: " + message);
        }

        return tokenCount;
    }

    private int estimateTokenCountIn(SystemMessage systemMessage) {
        return estimateTokenCountInText(systemMessage.text());
    }

    private int estimateTokenCountIn(UserMessage userMessage) {
        int tokenCount = 0;

        for (Content content : userMessage.contents()) {
            if (content instanceof TextContent) {
                tokenCount += estimateTokenCountInText(((TextContent) content).text());
            } else if (content instanceof ImageContent) {
                tokenCount += 85; // TODO implement for HIGH/AUTO detail level
            } else {
                throw illegalArgument("Unknown content type: " + content);
            }
        }

        if (userMessage.name() != null && !modelName.equals(GPT_4_VISION_PREVIEW)) {
            tokenCount += extraTokensPerName();
            tokenCount += estimateTokenCountInText(userMessage.name());
        }

        return tokenCount;
    }

    private int estimateTokenCountIn(AiMessage aiMessage) {
        int tokenCount = 0;

        if (aiMessage.text() != null) {
            tokenCount += estimateTokenCountInText(aiMessage.text());
        }

        if (aiMessage.toolExecutionRequests() != null) {
            if (isOneOfLatestModels()) {
                tokenCount += 6;
            } else {
                tokenCount += 3;
            }
            if (aiMessage.toolExecutionRequests().size() == 1) {
                tokenCount -= 1;
                ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
                tokenCount += estimateTokenCountInText(toolExecutionRequest.name()) * 2;
                tokenCount += estimateTokenCountInText(toolExecutionRequest.arguments());
            } else {
                tokenCount += 15;
                for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
                    tokenCount += 7;
                    tokenCount += estimateTokenCountInText(toolExecutionRequest.name());

                    Map<?, ?> arguments = fromJson(toolExecutionRequest.arguments(), Map.class);
                    for (Map.Entry<?, ?> argument : arguments.entrySet()) {
                        tokenCount += 2;
                        tokenCount += estimateTokenCountInText(argument.getKey().toString());
                        tokenCount += estimateTokenCountInText(argument.getValue().toString());
                    }
                }
            }
        }

        return tokenCount;
    }

    private int estimateTokenCountIn(ToolExecutionResultMessage toolExecutionResultMessage) {
        return estimateTokenCountInText(toolExecutionResultMessage.text());
    }

    private int extraTokensPerMessage() {
        if (modelName.equals("gpt-3.5-turbo-0301")) {
            return 4;
        } else {
            return 3;
        }
    }

    private int extraTokensPerName() {
        if (modelName.equals("gpt-3.5-turbo-0301")) {
            return -1; // if there's a name, the role is omitted
        } else {
            return 1;
        }
    }

    @Override
    public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
        // see https://github.com/openai/openai-cookbook/blob/main/examples/How_to_count_tokens_with_tiktoken.ipynb

        int tokenCount = 3; // every reply is primed with <|start|>assistant<|message|>
        for (ChatMessage message : messages) {
            tokenCount += estimateTokenCountInMessage(message);
        }
        return tokenCount;
    }

    @Override
    public int estimateTokenCountInToolSpecifications(Iterable<ToolSpecification> toolSpecifications) {
        int tokenCount = 16;
        for (ToolSpecification toolSpecification : toolSpecifications) {
            tokenCount += 6;
            tokenCount += estimateTokenCountInText(toolSpecification.name());
            if (toolSpecification.description() != null) {
                tokenCount += 2;
                tokenCount += estimateTokenCountInText(toolSpecification.description());
            }
            tokenCount += estimateTokenCountInToolParameters(toolSpecification.parameters());
        }
        return tokenCount;
    }

    private int estimateTokenCountInToolParameters(ToolParameters parameters) {
        if (parameters == null) {
            return 0;
        }

        int tokenCount = 3;
        Map<String, Map<String, Object>> properties = parameters.properties();
        if (isOneOfLatestModels()) {
            tokenCount += properties.size() - 1;
        }
        for (String property : properties.keySet()) {
            if (isOneOfLatestModels()) {
                tokenCount += 2;
            } else {
                tokenCount += 3;
            }
            tokenCount += estimateTokenCountInText(property);
            for (Map.Entry<String, Object> entry : properties.get(property).entrySet()) {
                if ("type".equals(entry.getKey())) {
                    if ("array".equals(entry.getValue()) && isOneOfLatestModels()) {
                        tokenCount += 1;
                    }
                    // TODO object
                } else if ("description".equals(entry.getKey())) {
                    tokenCount += 2;
                    tokenCount += estimateTokenCountInText(entry.getValue().toString());
                    if (isOneOfLatestModels() && parameters.required().contains(property)) {
                        tokenCount += 1;
                    }
                } else if ("enum".equals(entry.getKey())) {
                    if (isOneOfLatestModels()) {
                        tokenCount -= 2;
                    } else {
                        tokenCount -= 3;
                    }
                    for (Object enumValue : (Object[]) entry.getValue()) {
                        tokenCount += 3;
                        tokenCount += estimateTokenCountInText(enumValue.toString());
                    }
                }
            }
        }
        return tokenCount;
    }

    @Override
    public int estimateTokenCountInForcefulToolSpecification(ToolSpecification toolSpecification) {
        int tokenCount = estimateTokenCountInToolSpecifications(singletonList(toolSpecification));
        tokenCount += 4;
        tokenCount += estimateTokenCountInText(toolSpecification.name());
        if (isOneOfLatestModels()) {
            tokenCount += 3;
        }
        return tokenCount;
    }

    public List<Integer> encode(String text) {
        return encoding.orElseThrow(unknownModelException())
                .encodeOrdinary(text);
    }

    public List<Integer> encode(String text, int maxTokensToEncode) {
        return encoding.orElseThrow(unknownModelException())
                .encodeOrdinary(text, maxTokensToEncode).getTokens();
    }

    public String decode(List<Integer> tokens) {
        return encoding.orElseThrow(unknownModelException())
                .decode(tokens);
    }

    private Supplier<IllegalArgumentException> unknownModelException() {
        return () -> illegalArgument("Model '%s' is unknown to jtokkit", modelName);
    }

    @Override
    public int estimateTokenCountInToolExecutionRequests(Iterable<ToolExecutionRequest> toolExecutionRequests) {

        int tokenCount = 0;

        int toolsCount = 0;
        int toolsWithArgumentsCount = 0;
        int toolsWithoutArgumentsCount = 0;

        int totalArgumentsCount = 0;

        for (ToolExecutionRequest toolExecutionRequest : toolExecutionRequests) {
            tokenCount += 4;
            tokenCount += estimateTokenCountInText(toolExecutionRequest.name());
            tokenCount += estimateTokenCountInText(toolExecutionRequest.arguments());

            int argumentCount = countArguments(toolExecutionRequest.arguments());
            if (argumentCount == 0) {
                toolsWithoutArgumentsCount++;
            } else {
                toolsWithArgumentsCount++;
            }
            totalArgumentsCount += argumentCount;

            toolsCount++;
        }

        if (modelName.equals(GPT_3_5_TURBO_1106.toString()) || isOneOfLatestGpt4Models()) {
            tokenCount += 16;
            tokenCount += 3 * toolsWithoutArgumentsCount;
            tokenCount += toolsCount;
            if (totalArgumentsCount > 0) {
                tokenCount -= 1;
                tokenCount -= 2 * totalArgumentsCount;
                tokenCount += 2 * toolsWithArgumentsCount;
                tokenCount += toolsCount;
            }
        }

        if (modelName.equals(GPT_4_1106_PREVIEW.toString())) {
            tokenCount += 3;
            if (toolsCount > 1) {
                tokenCount += 18;
                tokenCount += 15 * toolsCount;
                tokenCount += totalArgumentsCount;
                tokenCount -= 3 * toolsWithoutArgumentsCount;
            }
        }

        return tokenCount;
    }

    @Override
    public int estimateTokenCountInForcefulToolExecutionRequest(ToolExecutionRequest toolExecutionRequest) {

        if (isOneOfLatestGpt4Models()) {
            int argumentsCount = countArguments(toolExecutionRequest.arguments());
            if (argumentsCount == 0) {
                return 1;
            } else {
                return estimateTokenCountInText(toolExecutionRequest.arguments());
            }
        }

        int tokenCount = estimateTokenCountInToolExecutionRequests(singletonList(toolExecutionRequest));
        tokenCount -= 4;
        tokenCount -= estimateTokenCountInText(toolExecutionRequest.name());

        if (modelName.equals(GPT_3_5_TURBO_1106.toString())) {
            int argumentsCount = countArguments(toolExecutionRequest.arguments());
            if (argumentsCount == 0) {
                return 1;
            }
            tokenCount -= 19;
            tokenCount += 2 * argumentsCount;
        }

        return tokenCount;
    }

    static int countArguments(String arguments) {
        if (isNullOrBlank(arguments)) {
            return 0;
        }
        Map<?, ?> argumentsMap = fromJson(arguments, Map.class);
        return argumentsMap.size();
    }

    private boolean isOneOfLatestModels() {
        return isOneOfLatestGpt3Models() || isOneOfLatestGpt4Models();
    }

    private boolean isOneOfLatestGpt3Models() {
        // TODO add GPT_3_5_TURBO once it points to GPT_3_5_TURBO_1106
        return modelName.equals(GPT_3_5_TURBO_1106.toString())
                || modelName.equals(GPT_3_5_TURBO_0125.toString());
    }

    private boolean isOneOfLatestGpt4Models() {
        return modelName.equals(GPT_4_TURBO_PREVIEW.toString())
                || modelName.equals(GPT_4_1106_PREVIEW.toString())
                || modelName.equals(GPT_4_0125_PREVIEW.toString());
    }
}
