package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolInputGuardrailRequest;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.service.tool.ToolService;
import dev.langchain4j.spi.classloading.ClassMetadataProviderFactory;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ToolGuardrailChainResolverAnnotationsTest {

    /**
     * A factory whose method keys are not {@link Method}s and that does not override
     * {@code resolveMethodKey}, like one written before tool guardrails existed.
     */
    static class StringKeyedFactory implements ClassMetadataProviderFactory<String> {

        @Override
        public <T extends Annotation> Optional<T> getAnnotation(String method, Class<T> annotationClass) {
            return Optional.empty();
        }

        @Override
        public <T extends Annotation> Optional<T> getAnnotation(Class<?> clazz, Class<T> annotationClass) {
            return Optional.ofNullable(clazz.getAnnotation(annotationClass));
        }

        @Override
        public Iterable<String> getNonStaticMethodsOnClass(Class<?> clazz) {
            return Stream.of(clazz.getMethods()).map(Method::getName).toList();
        }
    }

    public static class Reject implements ToolInputGuardrail {

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            return failure("rejected");
        }
    }

    static class Tools {

        @Tool
        @ToolInputGuardrails(Reject.class)
        String search(String query) {
            return "found";
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void a_factory_whose_method_keys_are_not_methods_binds_tools_and_their_guardrails_are_found() {
        var factory = (ClassMetadataProviderFactory<Object>) (ClassMetadataProviderFactory<?>) new StringKeyedFactory();
        var resolver = new ToolGuardrailChainResolver(List.of(), List.of(), null, null, factory);
        var tool = ToolService.findTools(new Tools()).get(0);

        var key = resolver.resolve(Tools.class, tool);

        assertThat(DefaultToolKey.inputExecutorOf(key))
                .hasValueSatisfying(executor ->
                        assertThat(executor.guardrails()).singleElement().isInstanceOf(Reject.class));
    }

    @Test
    void a_tool_bound_again_gets_the_guardrails_created_for_its_annotations_the_first_time() {
        var service = ToolGuardrailService.builder(Tools.class).build();
        var tool = ToolService.findTools(new Tools()).get(0);

        var first = service.bind(null, tool);
        var second = service.bind(null, tool);

        assertThat(DefaultToolKey.inputExecutorOf(second).orElseThrow().guardrails())
                .singleElement()
                .isSameAs(DefaultToolKey.inputExecutorOf(first).orElseThrow().guardrails().get(0));
    }
}
