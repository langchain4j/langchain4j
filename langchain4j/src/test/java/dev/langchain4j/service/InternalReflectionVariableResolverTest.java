package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.Test;

class InternalReflectionVariableResolverTest {

    // Helper method with various parameters for reflection use
    @SuppressWarnings("unused")
    public void noAnnotation(String name, int age) {
        throw new UnsupportedOperationException("not implemented");
    }

    @SuppressWarnings("unused")
    public void vAnnotation(@V("customName") String name, int age) {
        throw new UnsupportedOperationException("not implemented");
    }

    @SuppressWarnings("unused")
    public void singleItParameter(String it) {
        throw new UnsupportedOperationException("not implemented");
    }

    @SuppressWarnings("unused")
    public void withMemoryId(@MemoryId String memory, String other) {
        throw new UnsupportedOperationException("not implemented");
    }

    @SuppressWarnings("unused")
    public void withUserMessage(@UserMessage String message, String it) {
        throw new UnsupportedOperationException("not implemented");
    }

    @SuppressWarnings("unused")
    public void withUserName(@UserName String user, String value) {
        throw new UnsupportedOperationException("not implemented");
    }

    @SuppressWarnings("unused")
    public void vItName(@V("it") String special) {
        throw new UnsupportedOperationException("not implemented");
    }

    @SuppressWarnings("unused")
    public void arrayParameter(String[] arr) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Test
    void returnsParameterNamesWhenNoAnnotations() throws Exception {
        final var m = getClass().getMethod("noAnnotation", String.class, int.class);
        final var result = InternalReflectionVariableResolver.findTemplateVariables(
                "Hi {{name}}, Age {{age}}", m, new Object[] {"Alex", 42});

        // names are erased because code is compiled without the `-parameters` flag
        assertThat(result).containsEntry("arg0", "Alex").containsEntry("arg1", 42);
    }

    @Test
    void usesVAnnotationValueAsKey() throws Exception {
        final var m = getClass().getMethod("vAnnotation", String.class, int.class);
        final var result = InternalReflectionVariableResolver.findTemplateVariables(
                "Hello {{customName}} Age {{age}}", m, new Object[] {"Sam", 23});
        assertThat(result).containsEntry("customName", "Sam").containsEntry("arg1", 23);
    }

    @Test
    void injectsItWhenTemplateHasItAndArgCountIsOne() throws Exception {
        final var m = getClass().getMethod("singleItParameter", String.class);
        final var result =
                InternalReflectionVariableResolver.findTemplateVariables("Hi {{it}}", m, new Object[] {"VALUE"});
        assertThat(result).containsEntry("it", "VALUE");
    }

    @Test
    void skipsAnnotationsThatAreMemoryIdUserMessageUserNameForIt() throws Exception {
        final var m = getClass().getMethod("withMemoryId", String.class, String.class);
        Throwable thrown = catchThrowable(() ->
                InternalReflectionVariableResolver.findTemplateVariables("Hi {{it}}", m, new Object[] {"foo", "bar"}));
        assertThat(thrown)
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("cannot find the value of the prompt template variable");
    }

    @Test
    void findsItWhenParameterHasVAnnotationWithItValue() throws Exception {
        final var m = getClass().getMethod("vItName", String.class);
        final var result =
                InternalReflectionVariableResolver.findTemplateVariables("Hi {{it}}", m, new Object[] {"SOMETHING"});
        assertThat(result).containsEntry("it", "SOMETHING");
    }

    @Test
    void arrayParametersGetStringified() throws Exception {
        final var m = getClass().getMethod("arrayParameter", String[].class);
        final var result = InternalReflectionVariableResolver.findTemplateVariables(
                "values={{arg0}}", m, new Object[] {new String[] {"a", "b"}});

        assertThat(InternalReflectionVariableResolver.asString(result.get("arg0")))
                .isEqualTo("[a, b]");
    }

    @Test
    void returnsEmptyMapIfArgsNull() throws Exception {
        final var m = getClass().getMethod("noAnnotation", String.class, int.class);
        final var result = InternalReflectionVariableResolver.findTemplateVariables("test", m, null);
        assertThat(result).isEmpty();
    }

    @Test
    void asStringHandlesNullAndPrimitive() {
        assertThat(InternalReflectionVariableResolver.asString(null)).isEqualTo("null");
        assertThat(InternalReflectionVariableResolver.asString(5)).isEqualTo("5");
    }
}
