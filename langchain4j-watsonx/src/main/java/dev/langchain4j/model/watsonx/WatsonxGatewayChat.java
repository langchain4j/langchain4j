package dev.langchain4j.model.watsonx;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.util.Objects.nonNull;

import com.ibm.watsonx.ai.chat.ChatProvider;
import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.Tool;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayChatRequest;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayChatResponse;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayParameters.Cache;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayParameters.ReasoningEffort;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayParameters.Router;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayParameters.ServiceTier;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayService;
import dev.langchain4j.Internal;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import java.util.List;
import java.util.Map;

@Internal
abstract class WatsonxGatewayChat extends WatsonxChatBase<ModelGatewayChatRequest> {

    protected final ModelGatewayService modelGatewayService;

    protected WatsonxGatewayChat(Builder<?> builder) {
        super(builder, mergeParameters(builder));

        var parameters = (WatsonxGatewayChatRequestParameters) defaultRequestParameters;

        var serviceBuilder = nonNull(builder.authenticator)
                ? ModelGatewayService.builder().authenticator(builder.authenticator)
                : ModelGatewayService.builder().apiKey(builder.apiKey);

        modelGatewayService = serviceBuilder
                .baseUrl(builder.baseUrl)
                .version(builder.version)
                .modelId(parameters.modelName())
                .timeout(parameters.timeout())
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .httpClient(builder.httpClient)
                .verifySsl(builder.verifySsl)
                .build();
    }

    @Override
    protected ChatProvider<ModelGatewayChatRequest, ModelGatewayChatResponse> chatProvider() {
        return modelGatewayService;
    }

    @Override
    protected ModelGatewayChatRequest buildChatRequest(
            List<ChatMessage> messages, List<Tool> tools, ChatRequestParameters parameters) {

        return ModelGatewayChatRequest.builder()
                .messages(messages)
                .tools(tools)
                .parameters(Converter.toModelGatewayParameters(parameters, strictJsonSchema))
                .build();
    }

    private static WatsonxGatewayChatRequestParameters mergeParameters(Builder<?> builder) {

        var gatewayParameters = builder.defaultRequestParameters instanceof WatsonxGatewayChatRequestParameters p
                ? p
                : WatsonxGatewayChatRequestParameters.EMPTY;

        var target = WatsonxGatewayChatRequestParameters.builder();
        applyCommonParameters(target, builder);

        return target.serviceTier(getOrDefault(builder.serviceTier, gatewayParameters.serviceTier()))
                .reasoningEffort(getOrDefault(builder.reasoningEffort, gatewayParameters.reasoningEffort()))
                .router(getOrDefault(builder.router, gatewayParameters.router()))
                .modalities(getOrDefault(builder.modalities, gatewayParameters.modalities()))
                .store(getOrDefault(builder.store, gatewayParameters.store()))
                .parallelToolCalls(getOrDefault(builder.parallelToolCalls, gatewayParameters.parallelToolCalls()))
                .user(getOrDefault(builder.user, gatewayParameters.user()))
                .metadata(getOrDefault(builder.metadata, gatewayParameters.metadata()))
                .logitBias(getOrDefault(builder.logitBias, gatewayParameters.logitBias()))
                .logprobs(getOrDefault(builder.logprobs, gatewayParameters.logprobs()))
                .topLogprobs(getOrDefault(builder.topLogprobs, gatewayParameters.topLogprobs()))
                .seed(getOrDefault(builder.seed, gatewayParameters.seed()))
                .toolChoiceName(getOrDefault(builder.toolChoiceName, gatewayParameters.toolChoiceName()))
                .timeout(getOrDefault(builder.timeout, gatewayParameters.timeout()))
                .build();
    }

    @SuppressWarnings("unchecked")
    abstract static class Builder<T extends Builder<T>> extends WatsonxChatBase.Builder<T> {
        protected ServiceTier serviceTier;
        protected ReasoningEffort reasoningEffort;
        protected Router router;
        protected List<String> modalities;
        protected Boolean store;
        protected Boolean parallelToolCalls;
        protected String user;
        protected Map<String, String> metadata;

        /**
         * Sets the gateway model id, e.g. {@code "gpt-4o"}.
         *
         * @param modelName the model id
         * @return {@code this}
         */
        public T modelName(String modelName) {
            this.modelName = modelName;
            return (T) this;
        }

        /**
         * Sets the service tier used to process the request.
         *
         * @param serviceTier the {@link ServiceTier}
         * @return {@code this}
         */
        public T serviceTier(ServiceTier serviceTier) {
            this.serviceTier = serviceTier;
            return (T) this;
        }

        /**
         * Sets the reasoning effort constraint for reasoning models.
         *
         * @param reasoningEffort the {@link ReasoningEffort}
         * @return {@code this}
         */
        public T reasoningEffort(ReasoningEffort reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
            return (T) this;
        }

        /**
         * Sets the model routing configuration for the request.
         *
         * @param router the {@link Router}
         * @return {@code this}
         */
        public T router(Router router) {
            this.router = router;
            return (T) this;
        }

        /**
         * Convenience for {@code router(new Router(cache))}. Caching is only honored on non-streaming requests.
         *
         * @param cache the {@link Cache} configuration
         * @return {@code this}
         */
        public T cache(Cache cache) {
            this.router = cache == null ? null : new Router(cache);
            return (T) this;
        }

        /**
         * Sets the requested output modalities (e.g. {@code ["text"]}).
         *
         * @param modalities the modalities
         * @return {@code this}
         */
        public T modalities(List<String> modalities) {
            this.modalities = modalities;
            return (T) this;
        }

        /**
         * Sets whether the output should be stored for model distillation or evals.
         *
         * @param store {@code true} to store the output
         * @return {@code this}
         */
        public T store(Boolean store) {
            this.store = store;
            return (T) this;
        }

        /**
         * Enables or disables parallel function calling during tool use.
         *
         * @param parallelToolCalls {@code true} to enable, {@code false} to disable
         * @return {@code this}
         */
        public T parallelToolCalls(Boolean parallelToolCalls) {
            this.parallelToolCalls = parallelToolCalls;
            return (T) this;
        }

        /**
         * Sets the end-user identifier for abuse monitoring.
         *
         * @param user the user identifier
         * @return {@code this}
         */
        public T user(String user) {
            this.user = user;
            return (T) this;
        }

        /**
         * Sets developer-defined tags and values used for filtering completions.
         *
         * @param metadata the metadata map
         * @return {@code this}
         */
        public T metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return (T) this;
        }
    }
}
