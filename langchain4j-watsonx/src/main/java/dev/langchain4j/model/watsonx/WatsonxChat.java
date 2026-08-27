package dev.langchain4j.model.watsonx;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.util.Objects.nonNull;

import com.ibm.watsonx.ai.chat.ChatProvider;
import com.ibm.watsonx.ai.chat.ChatRequest;
import com.ibm.watsonx.ai.chat.ChatService;
import com.ibm.watsonx.ai.chat.TextChatResponse;
import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.Tool;
import dev.langchain4j.Internal;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import java.util.List;

@Internal
abstract class WatsonxChat extends WatsonxTextChatBase<ChatRequest> {

    protected final ChatService chatService;

    protected WatsonxChat(Builder<?> builder) {
        super(builder, mergeParameters(builder));

        var parameters = (WatsonxChatRequestParameters) defaultRequestParameters;

        var serviceBuilder = nonNull(builder.authenticator)
                ? ChatService.builder().authenticator(builder.authenticator)
                : ChatService.builder().apiKey(builder.apiKey);

        chatService = serviceBuilder
                .baseUrl(builder.baseUrl)
                .modelId(parameters.modelName())
                .version(builder.version)
                .projectId(parameters.projectId())
                .spaceId(parameters.spaceId())
                .timeout(parameters.timeout())
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .httpClient(builder.httpClient)
                .verifySsl(builder.verifySsl)
                .build();
    }

    @Override
    protected ChatRequest buildChatRequest(
            List<ChatMessage> messages, List<Tool> tools, ChatRequestParameters parameters) {

        return applyChatRequest(ChatRequest.builder(), messages, tools, parameters)
                .build();
    }

    @Override
    protected ChatProvider<ChatRequest, TextChatResponse> chatProvider() {
        return chatService;
    }

    private static WatsonxChatRequestParameters mergeParameters(Builder<?> builder) {

        var watsonxParameters = builder.defaultRequestParameters instanceof WatsonxChatRequestParameters parameters
                ? parameters
                : WatsonxChatRequestParameters.EMPTY;

        var target = WatsonxChatRequestParameters.builder();
        applyTextChatParameters(target, builder);

        return target.projectId(getOrDefault(builder.projectId, watsonxParameters.projectId()))
                .spaceId(getOrDefault(builder.spaceId, watsonxParameters.spaceId()))
                .build();
    }

    @SuppressWarnings("unchecked")
    abstract static class Builder<T extends Builder<T>> extends WatsonxTextChatBase.Builder<T> {
        protected String projectId;
        protected String spaceId;

        /**
         * Sets the foundation model id, e.g. {@code "ibm/granite-4-h-small"}.
         *
         * @param modelName the model id
         * @return {@code this}
         */
        public T modelName(String modelName) {
            this.modelName = modelName;
            return (T) this;
        }

        /**
         * Sets the IBM Cloud project ID that owns the watsonx.ai resources. Exactly one of {@code projectId} or {@code spaceId} must be set.
         *
         * @param projectId the IBM Cloud project ID
         * @return {@code this}
         */
        public T projectId(String projectId) {
            this.projectId = projectId;
            return (T) this;
        }

        /**
         * Sets the IBM Cloud deployment space ID. Exactly one of {@code projectId} or {@code spaceId} must be set.
         *
         * @param spaceId the IBM Cloud deployment space ID
         * @return {@code this}
         */
        public T spaceId(String spaceId) {
            this.spaceId = spaceId;
            return (T) this;
        }
    }
}
