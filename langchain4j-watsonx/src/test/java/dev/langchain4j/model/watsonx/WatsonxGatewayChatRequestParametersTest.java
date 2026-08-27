package dev.langchain4j.model.watsonx;

import static org.assertj.core.api.Assertions.assertThat;

import com.ibm.watsonx.ai.gateway.chat.ModelGatewayParameters.Cache;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayParameters.ReasoningEffort;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayParameters.Router;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayParameters.ServiceTier;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WatsonxGatewayChatRequestParametersTest {

    private static WatsonxGatewayChatRequestParameters.Builder fullyPopulated() {
        return WatsonxGatewayChatRequestParameters.builder()
                // common (parent) fields
                .modelName("gpt-4o")
                .temperature(0.7)
                .maxOutputTokens(100)
                // gateway-specific fields
                .serviceTier(ServiceTier.AUTO)
                .reasoningEffort(ReasoningEffort.LOW)
                .router(new Router(new Cache(true, null, null)))
                .modalities(List.of("text"))
                .store(true)
                .parallelToolCalls(true)
                .user("user")
                .metadata(Map.of("key", "value"))
                .logitBias(Map.of("token", 1))
                .logprobs(true)
                .topLogprobs(5)
                .seed(42)
                .toolChoiceName("tool")
                .timeout(Duration.ofSeconds(10));
    }

    @Test
    void equals_and_hashCode_should_include_gateway_fields() {
        WatsonxGatewayChatRequestParameters params1 = fullyPopulated().build();
        WatsonxGatewayChatRequestParameters params2 = fullyPopulated().build();

        assertThat(params1).isEqualTo(params2);
        assertThat(params1.hashCode()).isEqualTo(params2.hashCode());
    }

    @Test
    void equals_should_distinguish_each_gateway_field() {
        WatsonxGatewayChatRequestParameters base = fullyPopulated().build();

        assertThat(base)
                .isNotEqualTo(fullyPopulated().serviceTier(ServiceTier.FLEX).build());
        assertThat(base)
                .isNotEqualTo(
                        fullyPopulated().reasoningEffort(ReasoningEffort.HIGH).build());
        assertThat(base)
                .isNotEqualTo(fullyPopulated()
                        .router(new Router(new Cache(false, null, null)))
                        .build());
        assertThat(base)
                .isNotEqualTo(fullyPopulated().modalities(List.of("audio")).build());
        assertThat(base).isNotEqualTo(fullyPopulated().store(false).build());
        assertThat(base).isNotEqualTo(fullyPopulated().parallelToolCalls(false).build());
        assertThat(base).isNotEqualTo(fullyPopulated().user("other").build());
        assertThat(base)
                .isNotEqualTo(fullyPopulated().metadata(Map.of("k", "v")).build());
        assertThat(base)
                .isNotEqualTo(fullyPopulated().logitBias(Map.of("token", 2)).build());
        assertThat(base).isNotEqualTo(fullyPopulated().logprobs(false).build());
        assertThat(base).isNotEqualTo(fullyPopulated().topLogprobs(9).build());
        assertThat(base).isNotEqualTo(fullyPopulated().seed(7).build());
        assertThat(base).isNotEqualTo(fullyPopulated().toolChoiceName("other").build());
        assertThat(base)
                .isNotEqualTo(fullyPopulated().timeout(Duration.ofSeconds(20)).build());
    }

    @Test
    void equals_should_still_honor_inherited_fields() {
        WatsonxGatewayChatRequestParameters base = fullyPopulated().build();

        assertThat(base).isNotEqualTo(fullyPopulated().modelName("other").build());
        assertThat(base).isNotEqualTo(fullyPopulated().temperature(0.1).build());
    }

    @Test
    void cache_convenience_should_be_equivalent_to_router() {
        var cache = new Cache(true, null, null);

        WatsonxGatewayChatRequestParameters viaCache =
                fullyPopulated().cache(cache).build();
        WatsonxGatewayChatRequestParameters viaRouter =
                fullyPopulated().router(new Router(cache)).build();

        assertThat(viaCache).isEqualTo(viaRouter);
        assertThat(viaCache.router()).isEqualTo(new Router(cache));
        assertThat(fullyPopulated().cache(null).build().router()).isNull();
    }

    @Test
    void equals_should_reject_null_and_other_types() {
        WatsonxGatewayChatRequestParameters base = fullyPopulated().build();

        assertThat(base).isNotEqualTo(null);
        assertThat(base).isNotEqualTo("not a parameters object");
    }

    @Test
    void overrideWith_and_defaultedBy_should_be_mirror_images() {
        WatsonxGatewayChatRequestParameters base = fullyPopulated().build();
        WatsonxGatewayChatRequestParameters other =
                WatsonxGatewayChatRequestParameters.builder().user("other").build();

        // overrideWith lets the argument win (it is declared on the interface, so it returns the interface type)
        var overridden = (WatsonxGatewayChatRequestParameters) base.overrideWith(other);
        assertThat(overridden.user()).isEqualTo("other");
        // defaultedBy lets the receiver win, and fills the gaps from the argument
        assertThat(other.defaultedBy(base).user()).isEqualTo("other");
        assertThat(other.defaultedBy(base).serviceTier()).isEqualTo(ServiceTier.AUTO);
        assertThat(other.defaultedBy(base).seed()).isEqualTo(42);
    }

    @Test
    void toString_should_include_gateway_fields() {
        String text = fullyPopulated().build().toString();

        assertThat(text)
                .contains("serviceTier=AUTO")
                .contains("reasoningEffort=LOW")
                .contains("user=user")
                .contains("seed=42")
                .contains("toolChoiceName=tool");
    }
}
