package dev.langchain4j.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ToolSourceTest {

    @SuppressWarnings("unused")
    static class Tools {
        void run(int a, String[] b, Inner c, long[][] d, Inner[] e) {}

        void over(String a) {}

        void over(int a) {}

        static class Inner {}
    }

    @Test
    void local_exposes_names_only() throws Exception {
        Method method = String.class.getMethod("length");

        ToolSource source = ToolSource.local(method);

        assertThat(source).isInstanceOfSatisfying(ToolSource.Local.class, local -> {
            assertThat(local.declaringClassName()).isEqualTo("java.lang.String");
            assertThat(local.methodName()).isEqualTo("length");
            assertThat(local.parameterTypeNames()).isEmpty();
        });
        assertThat(source).isEqualTo(ToolSource.local(method)).hasSameHashCodeAs(ToolSource.local(method));
    }

    @Test
    void local_uses_class_type_names_for_primitives_arrays_and_nested_classes() throws Exception {
        Method method = Tools.class.getDeclaredMethod(
                "run", int.class, String[].class, Tools.Inner.class, long[][].class, Tools.Inner[].class);

        ToolSource.Local local = (ToolSource.Local) ToolSource.local(method);

        assertThat(local.declaringClassName()).isEqualTo(Tools.class.getName());
        assertThat(local.parameterTypeNames())
                .containsExactly(
                        "int",
                        "java.lang.String[]",
                        Tools.Inner.class.getName(),
                        "long[][]",
                        Tools.Inner.class.getName() + "[]");
    }

    @Test
    void local_distinguishes_overloads() throws Exception {
        ToolSource a = ToolSource.local(Tools.class.getDeclaredMethod("over", String.class));
        ToolSource b = ToolSource.local(Tools.class.getDeclaredMethod("over", int.class));

        assertThat(a).isNotEqualTo(b);
        assertThat(a).hasToString("ToolSource.Local{" + Tools.class.getName() + "#over(java.lang.String)}");
    }

    @Test
    void local_explicit_factory_equals_method_factory_and_is_immutable() throws Exception {
        ToolSource fromMethod = ToolSource.local(String.class.getMethod("charAt", int.class));
        List<String> types = new java.util.ArrayList<>(List.of("int"));
        ToolSource.Local explicit = (ToolSource.Local) ToolSource.local("java.lang.String", "charAt", types);
        types.add("x");

        assertThat(explicit).isEqualTo(fromMethod).hasSameHashCodeAs(fromMethod);
        assertThat(explicit.parameterTypeNames()).containsExactly("int");
        assertThatThrownBy(() -> explicit.parameterTypeNames().add("y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void local_explicit_factory_rejects_invalid_values() {
        assertThatThrownBy(() -> ToolSource.local(null, "m", List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ToolSource.local(" ", "m", List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ToolSource.local("C", "", List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ToolSource.local("C", "m", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ToolSource.local("C", "m", Arrays.asList("int", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ToolSource.local("C", "m", List.of(" "))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void local_rejects_null_method() {
        assertThatThrownBy(() -> ToolSource.local(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("method");
    }

    @Test
    void mcp_single_server() {
        ToolSource source = ToolSource.mcp("github");

        assertThat(source).isInstanceOfSatisfying(ToolSource.Mcp.class, mcp -> {
            assertThat(mcp.serverKeys()).containsExactly("github");
            assertThat(mcp.involves("github")).isTrue();
            assertThat(mcp.involves("other")).isFalse();
            assertThat(mcp.involves(null)).isFalse();
        });
        assertThat(source).isEqualTo(ToolSource.mcp(List.of("github")));
    }

    @Test
    void mcp_multiple_servers() {
        ToolSource source = ToolSource.mcp(List.of("a", "b", "a"));

        assertThat(source).isInstanceOfSatisfying(ToolSource.Mcp.class, mcp -> {
            assertThat(mcp.serverKeys()).containsExactlyInAnyOrder("a", "b");
            assertThat(mcp.involves("a")).isTrue();
            assertThat(mcp.involves("b")).isTrue();
            assertThat(mcp.involves("c")).isFalse();
        });
        assertThat(source)
                .isEqualTo(ToolSource.mcp(Set.of("b", "a")))
                .hasSameHashCodeAs(ToolSource.mcp(List.of("b", "a")));
    }

    @Test
    void mcp_multiple_servers_keep_insertion_order() {
        ToolSource.Mcp source = (ToolSource.Mcp) ToolSource.mcp(List.of("b", "a", "b", "c"));

        assertThat(source.serverKeys()).containsExactly("b", "a", "c");
    }

    @Test
    void sources_of_different_origin_or_content_are_not_equal() throws Exception {
        Method length = String.class.getMethod("length");
        Method isEmpty = String.class.getMethod("isEmpty");

        assertThat(ToolSource.local(length)).isNotEqualTo(ToolSource.local(isEmpty));
        assertThat(ToolSource.mcp("a")).isNotEqualTo(ToolSource.mcp("b"));
        assertThat(ToolSource.mcp(List.of("a", "b"))).isNotEqualTo(ToolSource.mcp(List.of("a")));
        assertThat(ToolSource.mcp("a")).isNotEqualTo(ToolSource.local(length));
        assertThat(ToolSource.mcp("a")).isNotEqualTo(ToolSource.programmatic());
        assertThat(ToolSource.local(length)).isNotEqualTo(ToolSource.programmatic());
        assertThat(ToolSource.local(length)).isNotEqualTo(ToolSource.mcp("a"));
        assertThat(ToolSource.programmatic()).isNotEqualTo(ToolSource.mcp("a"));
        assertThat(ToolSource.programmatic()).isNotEqualTo(null);
        assertThat(ToolSource.mcp("a")).isNotEqualTo("a");
    }

    @Test
    void mcp_server_keys_are_immutable() {
        ToolSource.Mcp source = (ToolSource.Mcp) ToolSource.mcp(List.of("a"));

        assertThatThrownBy(() -> source.serverKeys().add("b")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void mcp_rejects_invalid_keys() {
        assertThatThrownBy(() -> ToolSource.mcp((String) null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ToolSource.mcp(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ToolSource.mcp((List<String>) null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ToolSource.mcp(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ToolSource.mcp(Arrays.asList("a", null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ToolSource.mcp(List.of("a", ""))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void programmatic_is_singleton() {
        assertThat(ToolSource.programmatic())
                .isInstanceOf(ToolSource.Programmatic.class)
                .isSameAs(ToolSource.programmatic());
    }

    @Test
    void to_string() throws Exception {
        assertThat(ToolSource.programmatic()).hasToString("ToolSource.Programmatic");
        assertThat(ToolSource.mcp("x")).hasToString("ToolSource.Mcp{serverKeys=[x]}");
        assertThat(ToolSource.local(String.class.getMethod("length")).toString())
                .isEqualTo("ToolSource.Local{java.lang.String#length()}");
    }
}
