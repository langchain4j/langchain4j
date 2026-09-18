package dev.langchain4j.model.watsonx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class WatsonxCrossServiceParametersTest {

    private static final WatsonxChatRequestParameters NATIVE_PARAMETERS = WatsonxChatRequestParameters.builder()
            .temperature(0.7)
            .seed(42)
            .projectId("project-id")
            .build();

    private static final WatsonxGatewayChatRequestParameters GATEWAY_PARAMETERS =
            WatsonxGatewayChatRequestParameters.builder()
                    .temperature(0.3)
                    .seed(7)
                    .user("user")
                    .build();

    @Test
    void overrideWith_should_ignore_the_parameters_of_the_other_service() {

        var merged = (WatsonxChatRequestParameters) NATIVE_PARAMETERS.overrideWith(GATEWAY_PARAMETERS);

        assertThat(merged.temperature()).isEqualTo(0.3);
        assertThat(merged.seed()).isEqualTo(42);
        assertThat(merged.projectId()).isEqualTo("project-id");
    }

    @Test
    void overrideWith_should_ignore_the_parameters_of_the_other_service_in_both_directions() {

        var merged = (WatsonxGatewayChatRequestParameters) GATEWAY_PARAMETERS.overrideWith(NATIVE_PARAMETERS);

        assertThat(merged.temperature()).isEqualTo(0.7);
        assertThat(merged.seed()).isEqualTo(7);
        assertThat(merged.user()).isEqualTo("user");
    }

    @Test
    void defaultedBy_should_ignore_the_parameters_of_the_other_service() {

        var merged = NATIVE_PARAMETERS.defaultedBy(GATEWAY_PARAMETERS);
        assertThat(merged.temperature()).isEqualTo(0.7);
        assertThat(merged.seed()).isEqualTo(42);
        assertThat(merged.projectId()).isEqualTo("project-id");
    }

    @Test
    void overrideWith_should_accept_plain_parameters() {

        var plain = DefaultChatRequestParameters.builder().temperature(0.5).build();
        assertThat(NATIVE_PARAMETERS.overrideWith(plain).temperature()).isEqualTo(0.5);
        assertThat(GATEWAY_PARAMETERS.overrideWith(plain).temperature()).isEqualTo(0.5);
    }

    @Test
    void builder_should_ignore_the_default_parameters_of_the_other_service() {

        var defaults = (WatsonxChatRequestParameters)
                nativeChatModel(GATEWAY_PARAMETERS).defaultRequestParameters();

        assertThat(defaults.temperature()).isEqualTo(0.3);
        assertThat(defaults.seed()).isNull();

        var gatewayDefaults = (WatsonxGatewayChatRequestParameters)
                gatewayChatModel(NATIVE_PARAMETERS).defaultRequestParameters();

        assertThat(gatewayDefaults.temperature()).isEqualTo(0.7);
        assertThat(gatewayDefaults.seed()).isNull();
    }

    @Test
    void builder_should_accept_the_default_parameters_of_the_other_service() {

        assertThatCode(() -> Stream.<Runnable>of(
                                () -> nativeChatModel(GATEWAY_PARAMETERS),
                                () -> nativeStreamingChatModel(GATEWAY_PARAMETERS),
                                () -> deploymentChatModel(GATEWAY_PARAMETERS),
                                () -> deploymentStreamingChatModel(GATEWAY_PARAMETERS),
                                () -> gatewayChatModel(NATIVE_PARAMETERS),
                                () -> gatewayStreamingChatModel(NATIVE_PARAMETERS))
                        .forEach(Runnable::run))
                .doesNotThrowAnyException();
    }

    @Test
    void builder_should_accept_plain_default_parameters() {

        var plain = DefaultChatRequestParameters.builder().temperature(0.5).build();

        assertThatCode(() -> {
                    nativeChatModel(plain);
                    nativeStreamingChatModel(plain);
                    deploymentChatModel(plain);
                    deploymentStreamingChatModel(plain);
                    gatewayChatModel(plain);
                    gatewayStreamingChatModel(plain);
                })
                .doesNotThrowAnyException();
    }

    private static WatsonxChatModel nativeChatModel(ChatRequestParameters defaults) {
        return WatsonxChatModel.builder()
                .baseUrl("https://test.com")
                .apiKey("api-key")
                .modelName("modelId")
                .projectId("project-id")
                .defaultRequestParameters(defaults)
                .build();
    }

    private static WatsonxStreamingChatModel nativeStreamingChatModel(ChatRequestParameters defaults) {
        return WatsonxStreamingChatModel.builder()
                .baseUrl("https://test.com")
                .apiKey("api-key")
                .modelName("modelId")
                .projectId("project-id")
                .defaultRequestParameters(defaults)
                .build();
    }

    private static WatsonxDeploymentChatModel deploymentChatModel(ChatRequestParameters defaults) {
        return WatsonxDeploymentChatModel.builder()
                .baseUrl("https://test.com")
                .apiKey("api-key")
                .deploymentId("deployment-id")
                .defaultRequestParameters(defaults)
                .build();
    }

    private static WatsonxDeploymentStreamingChatModel deploymentStreamingChatModel(ChatRequestParameters defaults) {
        return WatsonxDeploymentStreamingChatModel.builder()
                .baseUrl("https://test.com")
                .apiKey("api-key")
                .deploymentId("deployment-id")
                .defaultRequestParameters(defaults)
                .build();
    }

    private static WatsonxGatewayChatModel gatewayChatModel(ChatRequestParameters defaults) {
        return WatsonxGatewayChatModel.builder()
                .baseUrl("https://test.com")
                .apiKey("api-key")
                .modelName("gpt-4o")
                .defaultRequestParameters(defaults)
                .build();
    }

    private static WatsonxGatewayStreamingChatModel gatewayStreamingChatModel(ChatRequestParameters defaults) {
        return WatsonxGatewayStreamingChatModel.builder()
                .baseUrl("https://test.com")
                .apiKey("api-key")
                .modelName("gpt-4o")
                .defaultRequestParameters(defaults)
                .build();
    }
}
