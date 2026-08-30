package dev.langchain4j.model.watsonx;

import static dev.langchain4j.internal.Utils.getOrDefault;

import com.ibm.watsonx.ai.gateway.chat.ModelGatewayParameters.Cache;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayParameters.ReasoningEffort;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayParameters.Router;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayParameters.ServiceTier;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class WatsonxGatewayChatRequestParameters extends DefaultChatRequestParameters {

    public static final WatsonxGatewayChatRequestParameters EMPTY =
            WatsonxGatewayChatRequestParameters.builder().build();

    private final Map<String, Integer> logitBias;
    private final Boolean logprobs;
    private final Integer topLogprobs;
    private final Integer seed;
    private final String toolChoiceName;
    private final Duration timeout;
    private final ServiceTier serviceTier;
    private final ReasoningEffort reasoningEffort;
    private final Router router;
    private final List<String> modalities;
    private final Boolean store;
    private final Boolean parallelToolCalls;
    private final String user;
    private final Map<String, String> metadata;

    private WatsonxGatewayChatRequestParameters(Builder builder) {
        super(builder);
        logitBias = builder.logitBias;
        logprobs = builder.logprobs;
        topLogprobs = builder.topLogprobs;
        seed = builder.seed;
        toolChoiceName = builder.toolChoiceName;
        timeout = builder.timeout;
        serviceTier = builder.serviceTier;
        reasoningEffort = builder.reasoningEffort;
        router = builder.router;
        modalities = builder.modalities;
        store = builder.store;
        parallelToolCalls = builder.parallelToolCalls;
        user = builder.user;
        metadata = builder.metadata;
    }

    public Map<String, Integer> logitBias() {
        return logitBias;
    }

    public Boolean logprobs() {
        return logprobs;
    }

    public Integer topLogprobs() {
        return topLogprobs;
    }

    public Integer seed() {
        return seed;
    }

    public String toolChoiceName() {
        return toolChoiceName;
    }

    public Duration timeout() {
        return timeout;
    }

    public ServiceTier serviceTier() {
        return serviceTier;
    }

    public ReasoningEffort reasoningEffort() {
        return reasoningEffort;
    }

    public Router router() {
        return router;
    }

    public List<String> modalities() {
        return modalities;
    }

    public Boolean store() {
        return store;
    }

    public Boolean parallelToolCalls() {
        return parallelToolCalls;
    }

    public String user() {
        return user;
    }

    public Map<String, String> metadata() {
        return metadata;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ChatRequestParameters overrideWith(ChatRequestParameters that) {
        return WatsonxGatewayChatRequestParameters.builder()
                .overrideWith(this)
                .overrideWith(that)
                .build();
    }

    @Override
    public WatsonxGatewayChatRequestParameters defaultedBy(ChatRequestParameters that) {
        return WatsonxGatewayChatRequestParameters.builder()
                .overrideWith(that)
                .overrideWith(this)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o)) return false;
        WatsonxGatewayChatRequestParameters that = (WatsonxGatewayChatRequestParameters) o;
        return Objects.equals(logitBias, that.logitBias)
                && Objects.equals(logprobs, that.logprobs)
                && Objects.equals(topLogprobs, that.topLogprobs)
                && Objects.equals(seed, that.seed)
                && Objects.equals(toolChoiceName, that.toolChoiceName)
                && Objects.equals(timeout, that.timeout)
                && Objects.equals(serviceTier, that.serviceTier)
                && Objects.equals(reasoningEffort, that.reasoningEffort)
                && Objects.equals(router, that.router)
                && Objects.equals(modalities, that.modalities)
                && Objects.equals(store, that.store)
                && Objects.equals(parallelToolCalls, that.parallelToolCalls)
                && Objects.equals(user, that.user)
                && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                super.hashCode(),
                logitBias,
                logprobs,
                topLogprobs,
                seed,
                toolChoiceName,
                timeout,
                serviceTier,
                reasoningEffort,
                router,
                modalities,
                store,
                parallelToolCalls,
                user,
                metadata);
    }

    @Override
    public String toString() {
        return "WatsonxGatewayChatRequestParameters{" + "modelName="
                + modelName() + ", temperature="
                + temperature() + ", topP="
                + topP() + ", topK="
                + topK() + ", frequencyPenalty="
                + frequencyPenalty() + ", presencePenalty="
                + presencePenalty() + ", maxOutputTokens="
                + maxOutputTokens() + ", stopSequences="
                + stopSequences() + ", toolSpecifications="
                + toolSpecifications() + ", toolChoice="
                + toolChoice() + ", responseFormat="
                + responseFormat() + ", serviceTier="
                + serviceTier + ", reasoningEffort="
                + reasoningEffort + ", router="
                + router + ", modalities="
                + modalities + ", store="
                + store + ", parallelToolCalls="
                + parallelToolCalls + ", user="
                + user + ", metadata="
                + metadata + ", logitBias="
                + logitBias + ", logprobs="
                + logprobs + ", topLogprobs="
                + topLogprobs + ", seed="
                + seed + ", toolChoiceName="
                + toolChoiceName + ", timeout="
                + timeout + '}';
    }

    public static class Builder extends DefaultChatRequestParameters.Builder<Builder> {

        private Map<String, Integer> logitBias;
        private Boolean logprobs;
        private Integer topLogprobs;
        private Integer seed;
        private String toolChoiceName;
        private Duration timeout;
        private ServiceTier serviceTier;
        private ReasoningEffort reasoningEffort;
        private Router router;
        private List<String> modalities;
        private Boolean store;
        private Boolean parallelToolCalls;
        private String user;
        private Map<String, String> metadata;

        @Override
        public Builder overrideWith(ChatRequestParameters parameters) {
            super.overrideWith(parameters);
            if (parameters instanceof WatsonxGatewayChatRequestParameters gatewayParameters) {
                logitBias(getOrDefault(gatewayParameters.logitBias(), logitBias));
                logprobs(getOrDefault(gatewayParameters.logprobs(), logprobs));
                topLogprobs(getOrDefault(gatewayParameters.topLogprobs(), topLogprobs));
                seed(getOrDefault(gatewayParameters.seed(), seed));
                toolChoiceName(getOrDefault(gatewayParameters.toolChoiceName(), toolChoiceName));
                timeout(getOrDefault(gatewayParameters.timeout(), timeout));
                serviceTier(getOrDefault(gatewayParameters.serviceTier(), serviceTier));
                reasoningEffort(getOrDefault(gatewayParameters.reasoningEffort(), reasoningEffort));
                router(getOrDefault(gatewayParameters.router(), router));
                modalities(getOrDefault(gatewayParameters.modalities(), modalities));
                store(getOrDefault(gatewayParameters.store(), store));
                parallelToolCalls(getOrDefault(gatewayParameters.parallelToolCalls(), parallelToolCalls));
                user(getOrDefault(gatewayParameters.user(), user));
                metadata(getOrDefault(gatewayParameters.metadata(), metadata));
            }
            return this;
        }

        public Builder logitBias(Map<String, Integer> logitBias) {
            this.logitBias = logitBias;
            return this;
        }

        public Builder logprobs(Boolean logprobs) {
            this.logprobs = logprobs;
            return this;
        }

        public Builder topLogprobs(Integer topLogprobs) {
            this.topLogprobs = topLogprobs;
            return this;
        }

        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public Builder toolChoiceName(String toolChoiceName) {
            this.toolChoiceName = toolChoiceName;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder serviceTier(ServiceTier serviceTier) {
            this.serviceTier = serviceTier;
            return this;
        }

        public Builder reasoningEffort(ReasoningEffort reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
            return this;
        }

        public Builder router(Router router) {
            this.router = router;
            return this;
        }

        public Builder cache(Cache cache) {
            this.router = cache == null ? null : new Router(cache);
            return this;
        }

        public Builder modalities(List<String> modalities) {
            this.modalities = modalities;
            return this;
        }

        public Builder store(Boolean store) {
            this.store = store;
            return this;
        }

        public Builder parallelToolCalls(Boolean parallelToolCalls) {
            this.parallelToolCalls = parallelToolCalls;
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        @Override
        public WatsonxGatewayChatRequestParameters build() {
            return new WatsonxGatewayChatRequestParameters(this);
        }
    }
}
