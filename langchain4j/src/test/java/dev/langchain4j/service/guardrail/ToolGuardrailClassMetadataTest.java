package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolInputGuardrailExecutor;
import dev.langchain4j.guardrail.ToolInputGuardrailRequest;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.service.tool.ToolService;
import dev.langchain4j.spi.classloading.ClassMetadataProviderFactory;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Where the default tool guardrail service finds the tool guardrail annotations: the annotations of the classes of the
 * tools come from the {@link ClassMetadataProviderFactory} only, and the annotations of the tool methods from the
 * {@link Method}.
 */
class ToolGuardrailClassMetadataTest {

    interface Assistant {}

    public static class ClassGuardrail implements ToolInputGuardrail {

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            return success();
        }
    }

    public static class MethodGuardrail implements ToolInputGuardrail {

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            return success();
        }
    }

    /** Only here to provide an instance of the annotation that a factory can return. */
    @ToolInputGuardrails(ClassGuardrail.class)
    static class AnnotationHolder {}

    static class NotAnnotatedTools {

        @Tool
        String search(String query) {
            return "found";
        }
    }

    @ToolInputGuardrails(ClassGuardrail.class)
    static class AnnotatedTools {

        @Tool
        String search(String query) {
            return "found";
        }
    }

    static class MethodAnnotatedTools {

        @Tool
        @ToolInputGuardrails(MethodGuardrail.class)
        String search(String query) {
            return "found";
        }
    }

    /**
     * A factory that answers the class annotations from a fixed map, whatever the classes carry, and whose method keys
     * are not {@link Method}s, like a framework that reads annotations from a build-time index.
     */
    private static final class IndexedFactory implements ClassMetadataProviderFactory<String> {

        private final Map<Class<?>, Annotation> classAnnotations;

        private IndexedFactory(Map<Class<?>, Annotation> classAnnotations) {
            this.classAnnotations = classAnnotations;
        }

        @Override
        public <T extends Annotation> Optional<T> getAnnotation(String method, Class<T> annotationClass) {
            return Optional.empty();
        }

        @Override
        public <T extends Annotation> Optional<T> getAnnotation(Class<?> clazz, Class<T> annotationClass) {
            return Optional.ofNullable(this.classAnnotations.get(clazz))
                    .filter(annotationClass::isInstance)
                    .map(annotationClass::cast);
        }

        @Override
        public Iterable<String> getNonStaticMethodsOnClass(Class<?> clazz) {
            return Stream.of(clazz.getMethods()).map(Method::getName).toList();
        }
    }

    @SuppressWarnings("unchecked")
    private static ToolGuardrailService serviceWith(IndexedFactory factory) {
        var resolver = new ToolGuardrailChainResolver(
                List.of(),
                List.of(),
                null,
                null,
                (ClassMetadataProviderFactory<Object>) (ClassMetadataProviderFactory<?>) factory);
        return new DefaultToolGuardrailService(Assistant.class, resolver);
    }

    private static List<Class<?>> inputChain(ToolGuardrailService.ToolKey key) {
        return ((DefaultToolKey) key)
                .inputExecutor()
                .map(ToolInputGuardrailExecutor::guardrails)
                .orElseGet(List::of)
                .stream()
                .<Class<?>>map(Object::getClass)
                .toList();
    }

    @Test
    void the_answer_of_the_factory_for_the_class_of_a_tool_is_used() {
        var annotation = AnnotationHolder.class.getAnnotation(ToolInputGuardrails.class);
        var service = serviceWith(new IndexedFactory(Map.of(NotAnnotatedTools.class, annotation)));
        var tool = ToolService.findTools(new NotAnnotatedTools()).get(0);

        var key = service.bind(NotAnnotatedTools.class, tool);

        assertThat(inputChain(key)).containsExactly(ClassGuardrail.class);
    }

    @Test
    void a_class_the_factory_has_no_annotation_for_has_no_class_level_guardrails_even_if_it_carries_one() {
        var service = serviceWith(new IndexedFactory(Map.of()));
        var tool = ToolService.findTools(new AnnotatedTools()).get(0);

        var key = service.bind(AnnotatedTools.class, tool);

        assertThat(inputChain(key)).isEmpty();
        assertThat(service.hasInputGuardrails(key)).isFalse();
    }

    @Test
    void the_annotation_of_a_tool_method_is_read_from_the_method_whatever_the_keys_of_the_factory() {
        var service = serviceWith(new IndexedFactory(Map.of()));
        var tool = ToolService.findTools(new MethodAnnotatedTools()).get(0);

        var key = service.bind(MethodAnnotatedTools.class, tool);

        assertThat(inputChain(key)).containsExactly(MethodGuardrail.class);
    }

    @Test
    void a_tool_bound_again_gets_the_guardrails_created_for_its_annotations_the_first_time() {
        var service = ToolGuardrailService.builder(Assistant.class).build();
        var tool = ToolService.findTools(new AnnotatedTools()).get(0);

        var first = (DefaultToolKey) service.bind(null, tool);
        var second = (DefaultToolKey) service.bind(null, tool);

        assertThat(second.inputExecutor().orElseThrow().guardrails())
                .singleElement()
                .isSameAs(first.inputExecutor().orElseThrow().guardrails().get(0));
    }
}
