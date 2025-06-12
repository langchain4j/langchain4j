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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
                @P(value = "optional", required = false) int p27,
                @P(value = "required") int p28) {
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
    void tool_specification_from() throws NoSuchMethodException {
        Method method = getF();

        ToolSpecification ts = ToolSpecifications.toolSpecificationFrom(method);

        assertThat(ts.name()).isEqualTo("f");
        assertThat(ts.description()).isEqualTo("line1\nline2");
        assertThat(ts.parameters()).isInstanceOf(JsonObjectSchema.class);

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
}
