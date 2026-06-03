package dev.langchain4j.agent.tool;

import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.output.structured.Description;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class ToolSpecificationsTest implements WithAssertions {

    public record Person(
            @Description("Name of the person") String name,
            List<String> aliases,
            boolean active,
            Person parent,
            Address currentAddress,
            List<Address> previousAddresses) {}

    public static class Address {
        private String street;
        private String city;
    }

    public enum E {
        A,
        B,
        C
    }

    @SuppressWarnings("unused")
    public static class Wrapper {
        @Tool({"line1", "line2"})
        public int f(
                @P("foo") String p0,
                boolean p1,
                @P("b2") Boolean p2,
                byte p3,
                Byte p4,
                short p5,
                Short p6,
                int p7,
                Integer p8,
                long p9,
                Long p10,
                @P("biggy") BigInteger p11,
                float p12,
                Float p13,
                double p14,
                Double p15,
                @P("bigger") BigDecimal p16,
                String[] p17,
                Integer[] p18,
                Boolean[] p19,
                int[] p20,
                boolean[] p21,
                List<Integer> p22,
                Set<BigDecimal> p23,
                Collection<String> p24,
                E p25,
                Person p26,
                @P(description = "optional", required = false) int p27,
                @P(description = "required") int p28) {
            return 42;
        }

        @Tool(name = "func_name")
        public int g(@ToolMemoryId String memoryId) {
            return 42;
        }

        public int unused(int i) {
            return 42;
        }
    }

    @SuppressWarnings("unused")
    public static class InvalidToolsWithDuplicateMethodNames {

        @Tool
        public int duplicateMethod(String typeString) {
            return 42;
        }

        @Tool
        public int duplicateMethod(int typeInt) {
            return 42;
        }
    }

    @SuppressWarnings("unused")
    public static class InvalidToolsWithDuplicateNames {

        @Tool(name = "duplicate_name")
        public int oneMethod(String typeString) {
            return 42;
        }

        @Tool(name = "duplicate_name")
        public int aDifferentMethod(int typeInt) {
            return 42;
        }
    }

    public static class ToolWithSameCustomParametersButDifferentDescriptions {
        @Tool
        public void toolMethod(@P("first person") Person p0, @P("second person") Person p1) {}
    }

    private static Method getF() throws NoSuchMethodException {
        return Wrapper.class.getMethod(
                "f",
                String.class, // 0
                boolean.class,
                Boolean.class,
                byte.class,
                Byte.class,
                short.class, // 5
                Short.class,
                int.class,
                Integer.class,
                long.class,
                Long.class, // 10
                BigInteger.class,
                float.class,
                Float.class,
                double.class,
                Double.class, // 15
                BigDecimal.class,
                String[].class,
                Integer[].class,
                Boolean[].class,
                int[].class, // 20
                boolean[].class,
                List.class,
                Set.class,
                Collection.class,
                E.class, // 25
                Person.class,
                int.class,
                int.class);
    }

    @Test
    void tool_specifications_from() {
        List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(new Wrapper());
        assertThat(specs).hasSize(2);

        assertThat(specs).extracting(ToolSpecification::name).containsExactlyInAnyOrder("f", "func_name");
    }

    @Test
    void tool_specifications_from_with_duplicate_method_names() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ToolSpecifications.toolSpecificationsFrom(new InvalidToolsWithDuplicateMethodNames()))
                .withMessage("Tool names must be unique. The tool 'duplicateMethod' appears several times")
                .withNoCause();
    }

    @Test
    void tool_specifications_from_with_duplicate_names() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ToolSpecifications.toolSpecificationsFrom(new InvalidToolsWithDuplicateNames()))
                .withMessage("Tool names must be unique. The tool 'duplicate_name' appears several times")
                .withNoCause();
    }

    @Test
    void tool_name_memory_id() throws NoSuchMethodException {
        Method method = Wrapper.class.getMethod("g", String.class);
        ToolSpecification ts = ToolSpecifications.toolSpecificationFrom(method);

        assertThat(ts.name()).isEqualTo("func_name");
        assertThat(ts.description()).isNull();
        assertThat(ts.parameters()).isNull();
    }

    @Test
    void tool_specification_metadata() {
        ToolSpecification ts = ToolSpecification.builder()
                .name("some_tool")
                .addMetadata("foo", "bar")
                .build();
        assertThat(ts.metadata().get("foo")).isEqualTo("bar");
    }

    @Test
    void tool_specification_from() throws NoSuchMethodException {
        Method method = getF();

        ToolSpecification ts = ToolSpecifications.toolSpecificationFrom(method);

        assertThat(ts.name()).isEqualTo("f");
        assertThat(ts.description()).isEqualTo("line1\nline2");
        assertThat(ts.parameters()).isInstanceOf(JsonObjectSchema.class);
        assertThat(ts.metadata()).isEmpty();

        Map<String, JsonSchemaElement> properties = ts.parameters().properties();

        assertThat(properties).hasSize(29);
        assertThat(properties)
                .containsEntry(
                        "arg0", JsonStringSchema.builder().description("foo").build())
                .containsEntry("arg1", new JsonBooleanSchema())
                .containsEntry(
                        "arg2", JsonBooleanSchema.builder().description("b2").build())
                .containsEntry("arg3", new JsonIntegerSchema())
                .containsEntry("arg4", new JsonIntegerSchema())
                .containsEntry("arg5", new JsonIntegerSchema())
                .containsEntry("arg6", new JsonIntegerSchema())
                .containsEntry("arg7", new JsonIntegerSchema())
                .containsEntry("arg8", new JsonIntegerSchema())
                .containsEntry("arg9", new JsonIntegerSchema())
                .containsEntry("arg10", new JsonIntegerSchema())
                .containsEntry(
                        "arg11",
                        JsonIntegerSchema.builder().description("biggy").build())
                .containsEntry("arg12", new JsonNumberSchema())
                .containsEntry("arg13", new JsonNumberSchema())
                .containsEntry("arg14", new JsonNumberSchema())
                .containsEntry("arg15", new JsonNumberSchema())
                .containsEntry(
                        "arg16",
                        JsonNumberSchema.builder().description("bigger").build())
                .containsEntry(
                        "arg17",
                        JsonArraySchema.builder().items(new JsonStringSchema()).build())
                .containsEntry(
                        "arg18",
                        JsonArraySchema.builder().items(new JsonIntegerSchema()).build())
                .containsEntry(
                        "arg19",
                        JsonArraySchema.builder().items(new JsonBooleanSchema()).build())
                .containsEntry(
                        "arg20",
                        JsonArraySchema.builder().items(new JsonIntegerSchema()).build())
                .containsEntry(
                        "arg21",
                        JsonArraySchema.builder().items(new JsonBooleanSchema()).build())
                .containsEntry(
                        "arg22",
                        JsonArraySchema.builder().items(new JsonIntegerSchema()).build())
                .containsEntry(
                        "arg23",
                        JsonArraySchema.builder().items(new JsonNumberSchema()).build())
                .containsEntry(
                        "arg24",
                        JsonArraySchema.builder().items(new JsonStringSchema()).build())
                .containsEntry(
                        "arg25",
                        JsonEnumSchema.builder().enumValues("A", "B", "C").build())
                .containsEntry(
                        "arg27",
                        JsonIntegerSchema.builder().description("optional").build())
                .containsEntry(
                        "arg28",
                        JsonIntegerSchema.builder().description("required").build());

        assertThat(ts.parameters().required())
                .containsExactly(
                        "arg0",
                        "arg1",
                        "arg2",
                        "arg3",
                        "arg4",
                        "arg5",
                        "arg6",
                        "arg7",
                        "arg8",
                        "arg9",
                        "arg10",
                        "arg11",
                        "arg12",
                        "arg13",
                        "arg14",
                        "arg15",
                        "arg16",
                        "arg17",
                        "arg18",
                        "arg19",
                        "arg20",
                        "arg21",
                        "arg22",
                        "arg23",
                        "arg24",
                        "arg25",
                        "arg26",
                        // "arg27", params with @P(required = false) are optional
                        "arg28");
    }

    record Customer(String name, Address billingAddress, Address shippingAddress) {}

    public static class CustomerRegistration {
        @Tool("register a new customer")
        boolean registerCustomer(Customer customer) {
            return true;
        }
    }

    @Test
    void object_used_multiple_times() {

        // when
        List<ToolSpecification> toolSpecifications =
                ToolSpecifications.toolSpecificationsFrom(CustomerRegistration.class);

        // then
        assertThat(toolSpecifications).hasSize(1);
        ToolSpecification toolSpecification = toolSpecifications.get(0);
        assertThat(toolSpecification.name()).isEqualTo("registerCustomer");
        assertThat(toolSpecification.description()).isEqualTo("register a new customer");
        assertThat(toolSpecification.parameters())
                .isEqualTo(JsonObjectSchema.builder()
                        .addProperty(
                                "arg0",
                                JsonObjectSchema.builder()
                                        .addStringProperty("name")
                                        .addProperty(
                                                "billingAddress",
                                                JsonObjectSchema.builder()
                                                        .addStringProperty("street")
                                                        .addStringProperty("city")
                                                        .required("street", "city")
                                                        .build())
                                        .addProperty(
                                                "shippingAddress",
                                                JsonObjectSchema.builder()
                                                        .addStringProperty("street")
                                                        .addStringProperty("city")
                                                        .required("street", "city")
                                                        .build())
                                        .required("name", "billingAddress", "shippingAddress")
                                        .build())
                        .required("arg0")
                        .build());
    }

    @Test
    void two_custom_params_same_type_have_distinct_descriptions() throws NoSuchMethodException {
        Method method = ToolWithSameCustomParametersButDifferentDescriptions.class.getMethod(
                "toolMethod", Person.class, Person.class);
        ToolSpecification ts = ToolSpecifications.toolSpecificationFrom(method);

        Map<String, JsonSchemaElement> props = ts.parameters().properties();
        JsonObjectSchema p0Schema = (JsonObjectSchema) props.get("arg0");
        JsonObjectSchema p1Schema = (JsonObjectSchema) props.get("arg1");

        assertThat(p0Schema.description()).isEqualTo("first person");
        assertThat(p1Schema.description()).isEqualTo("second person");
        assertThat(p0Schema.properties().keySet())
                .containsExactlyInAnyOrder(
                        "name", "aliases", "active", "parent", "currentAddress", "previousAddresses");
        assertThat(p1Schema.properties().keySet())
                .containsExactlyInAnyOrder(
                        "name", "aliases", "active", "parent", "currentAddress", "previousAddresses");
    }

    @Test
    void parses_tool_metadata_correctly() throws NoSuchMethodException {

        // given
        class Tools {

            @Tool(metadata = "{\"one\": \"one\", \"two\": 2}")
            public void tool() {}
        }

        Method method = Tools.class.getMethod("tool");

        // when
        ToolSpecification toolSpecification = ToolSpecifications.toolSpecificationFrom(method);

        // then
        assertThat(toolSpecification.metadata()).containsExactly(Map.entry("one", "one"), Map.entry("two", 2));
    }

    @Test
    void optional_parameter_is_not_required() throws NoSuchMethodException {
        class Tools {
            @Tool
            public void tool(@P("foo") String foo, @P("bar") Optional<String> bar) {}
        }
        Method method = Tools.class.getMethod("tool", String.class, Optional.class);
        ToolSpecification ts = ToolSpecifications.toolSpecificationFrom(method);

        assertThat(ts.parameters()).isInstanceOf(JsonObjectSchema.class);
        JsonObjectSchema schema = ts.parameters();
        assertThat(schema.required()).containsExactly("arg0"); // only 'foo' is required
        assertThat(schema.properties().keySet()).containsExactly("arg0", "arg1");
    }

    @Test
    void non_optional_parameter_is_required_by_default() throws NoSuchMethodException {
        class Tools {
            @Tool
            public void tool(@P("foo") String foo, @P("bar") String bar) {}
        }
        Method method = Tools.class.getMethod("tool", String.class, String.class);
        ToolSpecification ts = ToolSpecifications.toolSpecificationFrom(method);

        assertThat(ts.parameters()).isInstanceOf(JsonObjectSchema.class);
        JsonObjectSchema schema = ts.parameters();
        assertThat(schema.required()).containsExactly("arg0", "arg1"); // both required
        assertThat(schema.properties().keySet()).containsExactly("arg0", "arg1");
    }

    @Test
    void parameter_explicitly_required_true_is_required() throws NoSuchMethodException {
        class Tools {
            @Tool
            public void tool(@P(description = "foo", required = true) String foo, @P("bar") String bar) {}
        }
        Method method = Tools.class.getMethod("tool", String.class, String.class);
        ToolSpecification ts = ToolSpecifications.toolSpecificationFrom(method);

        assertThat(ts.parameters()).isInstanceOf(JsonObjectSchema.class);
        JsonObjectSchema schema = ts.parameters();
        assertThat(schema.required()).containsExactly("arg0", "arg1"); // both required
        assertThat(schema.properties().keySet()).containsExactly("arg0", "arg1");
    }

    @Test
    void parameter_with_default_value_is_not_required() throws NoSuchMethodException {
        class Tools {
            @Tool
            public void tool(String foo, @P(defaultValue = "10") int bar) {}
        }
        Method method = Tools.class.getMethod("tool", String.class, int.class);
        ToolSpecification ts = ToolSpecifications.toolSpecificationFrom(method);

        assertThat(ts.parameters()).isInstanceOf(JsonObjectSchema.class);
        JsonObjectSchema schema = ts.parameters();
        assertThat(schema.required()).containsExactly("arg0"); // 'bar' is optional because it has a default
        assertThat(schema.properties().keySet()).containsExactly("arg0", "arg1");
    }

    @Test
    void parameter_with_default_value_is_not_required_even_when_required_is_true() throws NoSuchMethodException {
        // defaultValue takes precedence over required = true for the schema's required array.
        class Tools {
            @Tool
            public void tool(String foo, @P(required = true, defaultValue = "10") int bar) {}
        }
        Method method = Tools.class.getMethod("tool", String.class, int.class);
        ToolSpecification ts = ToolSpecifications.toolSpecificationFrom(method);

        assertThat(ts.parameters()).isInstanceOf(JsonObjectSchema.class);
        JsonObjectSchema schema = ts.parameters();
        assertThat(schema.required()).containsExactly("arg0");
        assertThat(schema.properties().keySet()).containsExactly("arg0", "arg1");
    }

    @Test
    void should_fail_when_both_value_and_description_are_set_in_P() throws NoSuchMethodException {
        class Tools {
            @Tool
            public void tool(@P(value = "desc via value", description = "desc via description") String param) {}
        }
        Method method = Tools.class.getMethod("tool", String.class);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ToolSpecifications.toolSpecificationFrom(method))
                .withMessageContaining("has both 'value' and 'description' set in @P");
    }

    @Test
    void should_use_description_when_only_description_is_set_in_P() throws NoSuchMethodException {
        class Tools {
            @Tool
            public void tool(@P(description = "desc via description") String param) {}
        }
        Method method = Tools.class.getMethod("tool", String.class);
        ToolSpecification ts = ToolSpecifications.toolSpecificationFrom(method);

        JsonSchemaElement element = ts.parameters().properties().get("arg0");
        assertThat(element).isEqualTo(JsonStringSchema.builder().description("desc via description").build());
    }

    @Test
    void should_use_value_when_only_value_is_set_in_P() throws NoSuchMethodException {
        class Tools {
            @Tool
            public void tool(@P(value = "desc via value") String param) {}
        }
        Method method = Tools.class.getMethod("tool", String.class);
        ToolSpecification ts = ToolSpecifications.toolSpecificationFrom(method);

        JsonSchemaElement element = ts.parameters().properties().get("arg0");
        assertThat(element).isEqualTo(JsonStringSchema.builder().description("desc via value").build());
    }

    @Test
    void should_have_null_description_when_only_name_is_set_in_P() throws NoSuchMethodException {
        class Tools {
            @Tool
            public void tool(@P(name = "myParam") String param) {}
        }
        Method method = Tools.class.getMethod("tool", String.class);
        ToolSpecification ts = ToolSpecifications.toolSpecificationFrom(method);

        assertThat(ts.parameters().properties()).containsKey("myParam");
        JsonStringSchema element = (JsonStringSchema) ts.parameters().properties().get("myParam");
        assertThat(element.description()).isNull();
    }

    // --- Inheritance tests ---

    @SuppressWarnings("unused")
    public static class ParentTool {
        @Tool("parent tool")
        public int parentMethod(int a) {
            return a;
        }
    }

    @SuppressWarnings("unused")
    public static class ChildTool extends ParentTool {
        @Tool("child tool")
        public int childMethod(int b) {
            return b;
        }
    }

    @Test
    void should_discover_tool_from_superclass() {
        List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(new ChildTool());

        assertThat(specs).hasSize(2);
        assertThat(specs).extracting(ToolSpecification::name)
                .containsExactlyInAnyOrder("childMethod", "parentMethod");
        assertThat(specs).extracting(ToolSpecification::description)
                .containsExactlyInAnyOrder("child tool", "parent tool");
    }

    @SuppressWarnings("unused")
    public static class ParentWithNamedTool {
        @Tool(name = "shared_name", value = "parent version")
        public int doSomething(int a) {
            return a;
        }
    }

    @SuppressWarnings("unused")
    public static class ChildWithSameToolName extends ParentWithNamedTool {
        @Tool(name = "shared_name", value = "child version")
        public int doSomethingElse(int b) {
            return b;
        }
    }

    @Test
    void should_fail_when_parent_and_child_have_same_tool_name() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ToolSpecifications.toolSpecificationsFrom(new ChildWithSameToolName()))
                .withMessage("Tool names must be unique. The tool 'shared_name' appears several times");
    }

    @SuppressWarnings("unused")
    public static class ParentWithOverridableTool {
        @Tool("parent description")
        public int compute(int a) {
            return a;
        }
    }

    @SuppressWarnings("unused")
    public static class ChildOverridingTool extends ParentWithOverridableTool {
        @Override
        @Tool("child description")
        public int compute(int a) {
            return a * 2;
        }
    }

    @Test
    void should_use_overriding_method_from_child_class() {
        List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(new ChildOverridingTool());

        assertThat(specs).hasSize(1);
        assertThat(specs.get(0).name()).isEqualTo("compute");
        assertThat(specs.get(0).description()).isEqualTo("child description");
    }

    @SuppressWarnings("unused")
    public static class ChildOverridingToolWithNewAnnotation extends ParentWithOverridableTool {
        @Override
        @Tool(name = "renamed_compute", value = "updated description")
        public int compute(int a) {
            return a * 3;
        }
    }

    @Test
    void should_use_updated_tool_annotation_from_overriding_child() {
        List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(new ChildOverridingToolWithNewAnnotation());

        assertThat(specs).hasSize(1);
        assertThat(specs.get(0).name()).isEqualTo("renamed_compute");
        assertThat(specs.get(0).description()).isEqualTo("updated description");
    }

    @SuppressWarnings("unused")
    public static class ParentWithProcess {
        @Tool("process a string")
        public String process(String input) {
            return input;
        }
    }

    @SuppressWarnings("unused")
    public static class ChildWithOverloadedProcess extends ParentWithProcess {
        @Tool("process an int")
        public int process(int input) {
            return input;
        }
    }

    @Test
    void should_fail_when_overloaded_methods_in_parent_and_child_default_to_same_tool_name() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ToolSpecifications.toolSpecificationsFrom(new ChildWithOverloadedProcess()))
                .withMessage("Tool names must be unique. The tool 'process' appears several times");
    }

    @SuppressWarnings("unused")
    public static class ParentWithNamedProcess {
        @Tool(name = "process", value = "process a string")
        public String process(String input) {
            return input;
        }
    }

    @SuppressWarnings("unused")
    public static class ChildWithOverloadedSameToolName extends ParentWithNamedProcess {
        @Tool(name = "process", value = "process an int")
        public int process(int input) {
            return input;
        }
    }

    @Test
    void should_fail_when_overloaded_methods_in_parent_and_child_have_same_tool_name() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ToolSpecifications.toolSpecificationsFrom(new ChildWithOverloadedSameToolName()))
                .withMessage("Tool names must be unique. The tool 'process' appears several times");
    }

    @SuppressWarnings("unused")
    public static class ChildWithOverloadedDifferentToolNames extends ParentWithNamedProcess {
        @Tool(name = "process_int", value = "process an int")
        public static int process(int input) {
            return input;
        }
    }

    @Test
    void should_discover_both_when_overloaded_methods_in_parent_and_child_have_different_tool_names() {
        List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(new ChildWithOverloadedDifferentToolNames());

        assertThat(specs).hasSize(2);
        assertThat(specs).extracting(ToolSpecification::name)
                .containsExactlyInAnyOrder("process", "process_int");
        assertThat(specs).extracting(ToolSpecification::description)
                .containsExactlyInAnyOrder("process a string", "process an int");
    }

    // --- Interface default method tests ---

    public interface ToolInterface {
        @Tool("interface tool")
        default int interfaceMethod(int a) {
            return a;
        }
    }

    @SuppressWarnings("unused")
    public static class ImplementsToolInterface implements ToolInterface {
    }

    @Test
    void should_discover_tool_from_interface_default_method() {
        List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(new ImplementsToolInterface());

        assertThat(specs).hasSize(1);
        assertThat(specs.get(0).name()).isEqualTo("interfaceMethod");
        assertThat(specs.get(0).description()).isEqualTo("interface tool");
    }

    @SuppressWarnings("unused")
    public static class ClassWithOwnToolAndInterface implements ToolInterface {
        @Tool("class tool")
        public int classMethod(int b) {
            return b;
        }
    }

    @Test
    void should_discover_tools_from_both_class_and_interface() {
        List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(new ClassWithOwnToolAndInterface());

        assertThat(specs).hasSize(2);
        assertThat(specs).extracting(ToolSpecification::name)
                .containsExactlyInAnyOrder("classMethod", "interfaceMethod");
        assertThat(specs).extracting(ToolSpecification::description)
                .containsExactlyInAnyOrder("class tool", "interface tool");
    }

    @SuppressWarnings("unused")
    public static class ClassOverridingInterfaceDefault implements ToolInterface {
        @Override
        @Tool("overridden tool")
        public int interfaceMethod(int a) {
            return a * 2;
        }
    }

    @Test
    void should_use_class_method_when_overriding_interface_default() {
        List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(new ClassOverridingInterfaceDefault());

        assertThat(specs).hasSize(1);
        assertThat(specs.get(0).name()).isEqualTo("interfaceMethod");
        assertThat(specs.get(0).description()).isEqualTo("overridden tool");
    }

    public interface ToolInterfaceWithAbstractMethod {
        @Tool("abstract tool")
        int abstractMethod(int a);
    }

    @SuppressWarnings("unused")
    public static class ImplementsAbstractToolMethod implements ToolInterfaceWithAbstractMethod {
        @Override
        public int abstractMethod(int a) {
            return a;
        }
    }

    @Test
    void should_not_discover_abstract_interface_method_without_tool_on_implementation() {
        List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(new ImplementsAbstractToolMethod());

        assertThat(specs).isEmpty();
    }

    public interface ToolInterfaceWithStaticMethod {
        @Tool("static tool")
        static int staticMethod(int a) {
            return a;
        }
    }

    @SuppressWarnings("unused")
    public static class ImplementsInterfaceWithStaticTool implements ToolInterfaceWithStaticMethod {
        @Tool("instance tool")
        public int instanceMethod(int b) {
            return b;
        }
    }

    @Test
    void should_discover_static_tool_from_interface() {
        List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(new ImplementsInterfaceWithStaticTool());

        assertThat(specs).hasSize(2);
        assertThat(specs).extracting(ToolSpecification::name)
                .containsExactlyInAnyOrder("staticMethod", "instanceMethod");
        assertThat(specs).extracting(ToolSpecification::description)
                .containsExactlyInAnyOrder("static tool", "instance tool");
    }
}
