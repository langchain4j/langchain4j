package dev.langchain4j.service.guardrail;

import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolInputGuardrailRequest;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.guardrail.ToolOutputGuardrail;
import dev.langchain4j.guardrail.ToolOutputGuardrailRequest;
import dev.langchain4j.guardrail.ToolOutputGuardrailResult;
import dev.langchain4j.guardrail.config.ToolInputGuardrailsConfig;
import dev.langchain4j.guardrail.config.ToolOutputGuardrailsConfig;
import dev.langchain4j.service.guardrail.spi.ToolGuardrailServiceBuilderFactory;
import dev.langchain4j.service.tool.AiServiceTool;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registered in {@code META-INF/services}, so every AI service of this module's tests gets its tool guardrail service
 * builder from here. Only {@link ToolGuardrailServiceBuilderFactoryTest.SpiAssistant} gets a recording builder; every
 * other AI service gets the default one.
 */
public class TestToolGuardrailServiceBuilderFactory implements ToolGuardrailServiceBuilderFactory {

    private static final Map<Class<?>, RecordingBuilder> RECORDING_BUILDERS = new ConcurrentHashMap<>();

    @Override
    public ToolGuardrailService.Builder getBuilder(Class<?> aiServiceClass) {
        return (aiServiceClass == ToolGuardrailServiceBuilderFactoryTest.SpiAssistant.class)
                ? recordingBuilder(aiServiceClass)
                : new ToolGuardrailServiceBuilder(aiServiceClass);
    }

    private static RecordingBuilder recordingBuilder(Class<?> aiServiceClass) {
        var builder = new RecordingBuilder(aiServiceClass);
        RECORDING_BUILDERS.put(aiServiceClass, builder);
        return builder;
    }

    /**
     * The last recording builder created for the AI service class.
     */
    static Optional<RecordingBuilder> recordingBuilderFor(Class<?> aiServiceClass) {
        return Optional.ofNullable(RECORDING_BUILDERS.get(aiServiceClass));
    }

    /**
     * A builder that records what it is given, and builds a {@link RecordingService} around the default service.
     */
    static final class RecordingBuilder implements ToolGuardrailService.Builder {
        private final ToolGuardrailService.Builder delegate;
        private final List<ToolOutputGuardrail> outputGuardrails = new CopyOnWriteArrayList<>();
        private final List<RecordingService> builtServices = new CopyOnWriteArrayList<>();
        private volatile ToolOutputGuardrailsConfig outputConfig;

        private RecordingBuilder(Class<?> aiServiceClass) {
            this.delegate = new ToolGuardrailServiceBuilder(aiServiceClass);
        }

        List<ToolOutputGuardrail> outputGuardrails() {
            return List.copyOf(this.outputGuardrails);
        }

        List<RecordingService> builtServices() {
            return List.copyOf(this.builtServices);
        }

        Optional<ToolOutputGuardrailsConfig> outputConfig() {
            return Optional.ofNullable(this.outputConfig);
        }

        @Override
        public ToolGuardrailService.Builder inputGuardrailsConfig(ToolInputGuardrailsConfig config) {
            this.delegate.inputGuardrailsConfig(config);
            return this;
        }

        @Override
        public ToolGuardrailService.Builder outputGuardrailsConfig(ToolOutputGuardrailsConfig config) {
            this.outputConfig = config;
            this.delegate.outputGuardrailsConfig(config);
            return this;
        }

        @Override
        public <G extends ToolInputGuardrail> ToolGuardrailService.Builder inputGuardrailClasses(
                List<Class<? extends G>> guardrailClasses) {
            this.delegate.inputGuardrailClasses(guardrailClasses);
            return this;
        }

        @Override
        public <G extends ToolOutputGuardrail> ToolGuardrailService.Builder outputGuardrailClasses(
                List<Class<? extends G>> guardrailClasses) {
            this.delegate.outputGuardrailClasses(guardrailClasses);
            return this;
        }

        @Override
        public <G extends ToolInputGuardrail> ToolGuardrailService.Builder inputGuardrails(List<G> guardrails) {
            this.delegate.inputGuardrails(guardrails);
            return this;
        }

        @Override
        public <G extends ToolOutputGuardrail> ToolGuardrailService.Builder outputGuardrails(List<G> guardrails) {
            this.outputGuardrails.clear();
            Optional.ofNullable(guardrails).ifPresent(this.outputGuardrails::addAll);
            this.delegate.outputGuardrails(guardrails);
            return this;
        }

        @Override
        public ToolGuardrailService build() {
            var service = new RecordingService(this.delegate.build());
            this.builtServices.add(service);
            return service;
        }
    }

    /**
     * A service that records the tools it binds, and delegates everything else.
     */
    static final class RecordingService implements ToolGuardrailService {
        private final ToolGuardrailService delegate;
        private final List<String> boundToolNames = new CopyOnWriteArrayList<>();

        private RecordingService(ToolGuardrailService delegate) {
            this.delegate = delegate;
        }

        List<String> boundToolNames() {
            return List.copyOf(this.boundToolNames);
        }

        @Override
        public ToolKey bind(Class<?> toolClass, AiServiceTool tool) {
            this.boundToolNames.add(tool.name());
            return this.delegate.bind(toolClass, tool);
        }

        @Override
        public Class<?> aiServiceClass() {
            return this.delegate.aiServiceClass();
        }

        @Override
        public ToolInputGuardrailsConfig toolInputGuardrailsConfig() {
            return this.delegate.toolInputGuardrailsConfig();
        }

        @Override
        public ToolInputGuardrailResult executeInputGuardrails(ToolKey key, ToolInputGuardrailRequest request) {
            return this.delegate.executeInputGuardrails(key, request);
        }

        @Override
        public CompletableFuture<ToolInputGuardrailResult> executeInputGuardrailsAsync(
                ToolKey key, ToolInputGuardrailRequest request) {
            return this.delegate.executeInputGuardrailsAsync(key, request);
        }

        @Override
        public ToolOutputGuardrailResult executeOutputGuardrails(ToolKey key, ToolOutputGuardrailRequest request) {
            return this.delegate.executeOutputGuardrails(key, request);
        }

        @Override
        public CompletableFuture<ToolOutputGuardrailResult> executeOutputGuardrailsAsync(
                ToolKey key, ToolOutputGuardrailRequest request) {
            return this.delegate.executeOutputGuardrailsAsync(key, request);
        }

        @Override
        public boolean hasInputGuardrails(ToolKey key) {
            return this.delegate.hasInputGuardrails(key);
        }

        @Override
        public boolean hasOutputGuardrails(ToolKey key) {
            return this.delegate.hasOutputGuardrails(key);
        }
    }
}
