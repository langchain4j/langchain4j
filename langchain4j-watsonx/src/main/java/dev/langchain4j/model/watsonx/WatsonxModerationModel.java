package dev.langchain4j.model.watsonx;

import static dev.langchain4j.internal.Utils.copy;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.ibm.watsonx.ai.detection.DetectionService;
import com.ibm.watsonx.ai.detection.DetectionTextRequest;
import com.ibm.watsonx.ai.detection.DetectionTextResponse;
import com.ibm.watsonx.ai.detection.detector.BaseDetector;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.internal.DefaultExecutorProvider;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.moderation.ModerationRequest;
import dev.langchain4j.model.moderation.ModerationResponse;
import dev.langchain4j.model.moderation.listener.ModerationModelListener;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * A {@link ModerationModel} implementation that integrates IBM watsonx.ai with LangChain4j.
 * <p>
 * <b>Example usage:</b>
 *
 * <pre>{@code
 * ModerationModel chatModel = WatsonxModerationModel.builder()
 *     .baseUrl("https://...") // or use CloudRegion
 *     .apiKey("...")
 *     .projectId("...")
 *     .detectors(Pii.ofDefaults(), GraniteGuardian.ofDefaults())
 *     .build();
 * }</pre>
 *
 */
public class WatsonxModerationModel implements ModerationModel {
    private final List<BaseDetector> detectors;
    private final DetectionService detectionService;
    private final List<ModerationModelListener> listeners;

    public WatsonxModerationModel(Builder builder) {

        if (isNull(builder.detectors) || builder.detectors.isEmpty())
            throw new IllegalArgumentException("At least one detector must be provided");

        detectors = builder.detectors;

        var detectionServiceBuilder = nonNull(builder.authenticator)
                ? DetectionService.builder().authenticator(builder.authenticator)
                : DetectionService.builder().apiKey(builder.apiKey);

        detectionService = detectionServiceBuilder
                .baseUrl(builder.baseUrl)
                .version(builder.version)
                .projectId(builder.projectId)
                .spaceId(builder.spaceId)
                .timeout(builder.timeout)
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .httpClient(builder.httpClient)
                .verifySsl(builder.verifySsl)
                .build();

        this.listeners = copy(builder.listeners);
    }

    @Override
    public List<ModerationModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.WATSONX;
    }

    @Override
    public ModerationResponse doModerate(ModerationRequest moderationRequest) {
        List<String> inputs = ModerationModel.toInputs(moderationRequest);
        return moderateInternal(inputs);
    }

    private ModerationResponse moderateInternal(List<String> inputs) {
        var futures = inputs.stream()
                .map(input -> CompletableFuture.supplyAsync(
                        () -> moderateSingleInput(input), DefaultExecutorProvider.getDefaultExecutorService()))
                .toList();

        try {
            return futures.stream()
                    .map(CompletableFuture::join)
                    .filter(response -> response.moderation().flagged())
                    .findFirst()
                    .orElse(ModerationResponse.builder()
                            .moderation(Moderation.notFlagged())
                            .build());
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            throw cause instanceof LangChain4jException langchainException
                    ? langchainException
                    : new RuntimeException(cause);
        }
    }

    private ModerationResponse moderateSingleInput(String input) {
        var request =
                DetectionTextRequest.builder().input(input).detectors(detectors).build();

        return WatsonxExceptionMapper.INSTANCE.withExceptionMapper(
                () -> detectionService.detect(request).detections().stream()
                        .findFirst()
                        .map(this::createModerationResponse)
                        .orElse(ModerationResponse.builder()
                                .moderation(Moderation.notFlagged())
                                .build()));
    }

    /**
     * Returns a new {@link Builder} instance.
     * <p>
     * <b>Example usage:</b>
     *
     * <pre>{@code
     * ModerationModel chatModel = WatsonxModerationModel.builder()
     *     .baseUrl("https://...") // or use CloudRegion
     *     .apiKey("...")
     *     .projectId("...")
     *     .detectors(Pii.ofDefaults(), GraniteGuardian.ofDefaults())
     *     .build();
     * }</pre>
     *
     * @return {@link Builder} instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    private ModerationResponse createModerationResponse(DetectionTextResponse detectionTextResponse) {
        Moderation moderation = Moderation.flagged(detectionTextResponse.text());
        Map<String, Object> metadata = Map.of(
                "detection", detectionTextResponse.detection(),
                "detection_type", detectionTextResponse.detectionType(),
                "start", detectionTextResponse.start(),
                "end", detectionTextResponse.end(),
                "score", detectionTextResponse.score());
        return ModerationResponse.builder()
                .moderation(moderation)
                .metadata(metadata)
                .build();
    }

    /**
     * Builder class for constructing {@link WatsonxModerationModel} instances with configurable parameters.
     */
    public static class Builder extends WatsonxBuilder<Builder> {
        private List<BaseDetector> detectors;
        private List<ModerationModelListener> listeners;

        private Builder() {}

        /**
         * Sets the list of detectors to use.
         *
         * @param detectors the list of detectors
         */
        public Builder detectors(List<BaseDetector> detectors) {
            this.detectors = detectors;
            return this;
        }

        /**
         * Sets the list of detectors to use.
         *
         * @param detectors the list of detectors
         */
        public Builder detectors(BaseDetector... detectors) {
            return detectors(List.of(detectors));
        }

        /**
         * Sets the listeners for this moderation model.
         *
         * @param listeners the listeners.
         * @return {@code this}.
         */
        public Builder listeners(List<ModerationModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public WatsonxModerationModel build() {
            return new WatsonxModerationModel(this);
        }
    }
}
