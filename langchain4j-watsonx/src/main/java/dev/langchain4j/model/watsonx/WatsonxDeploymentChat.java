package dev.langchain4j.model.watsonx;

import static java.util.Objects.nonNull;

import com.ibm.watsonx.ai.chat.ChatProvider;
import com.ibm.watsonx.ai.chat.TextChatResponse;
import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.Tool;
import com.ibm.watsonx.ai.deployment.DeploymentChatRequest;
import com.ibm.watsonx.ai.deployment.DeploymentService;
import dev.langchain4j.Internal;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import java.util.List;

@Internal
abstract class WatsonxDeploymentChat extends WatsonxTextChatBase<DeploymentChatRequest> {

    protected final DeploymentService deploymentService;
    protected final String deploymentId;

    protected WatsonxDeploymentChat(Builder<?> builder) {
        super(builder, mergeParameters(builder));

        this.deploymentId = builder.deploymentId;

        var parameters = (WatsonxChatRequestParameters) defaultRequestParameters;

        var serviceBuilder = nonNull(builder.authenticator)
                ? DeploymentService.builder().authenticator(builder.authenticator)
                : DeploymentService.builder().apiKey(builder.apiKey);

        deploymentService = serviceBuilder
                .baseUrl(builder.baseUrl)
                .version(builder.version)
                .timeout(parameters.timeout())
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .httpClient(builder.httpClient)
                .verifySsl(builder.verifySsl)
                .build();
    }

    @Override
    protected DeploymentChatRequest buildChatRequest(
            List<ChatMessage> messages, List<Tool> tools, ChatRequestParameters parameters) {

        validateModelName(parameters);

        return applyChatRequest(DeploymentChatRequest.builder().deploymentId(deploymentId), messages, tools, parameters)
                .build();
    }

    @Override
    protected ChatProvider<DeploymentChatRequest, TextChatResponse> chatProvider() {
        return deploymentService;
    }

    private static WatsonxChatRequestParameters mergeParameters(Builder<?> builder) {

        validateModelName(builder.defaultRequestParameters);

        var target = WatsonxChatRequestParameters.builder();
        applyTextChatParameters(target, builder);

        return target.build();
    }

    private static void validateModelName(ChatRequestParameters parameters) {
        if (nonNull(parameters) && nonNull(parameters.modelName()))
            throw new UnsupportedFeatureException(
                    "The 'modelName' parameter is not supported, the deployment id defines the model to call");
    }

    @SuppressWarnings("unchecked")
    abstract static class Builder<T extends Builder<T>> extends WatsonxTextChatBase.Builder<T> {
        protected String deploymentId;

        /**
         * Sets the id of the on-demand model to call.
         *
         * @param deploymentId the deployment id
         * @return {@code this}
         */
        public T deploymentId(String deploymentId) {
            this.deploymentId = deploymentId;
            return (T) this;
        }
    }
}
