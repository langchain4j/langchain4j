package dev.langchain4j.agent.tool;

import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.output.structured.Description;
import lombok.Data;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

class ToolSpecificationsTest implements WithAssertions {

    @Data
    public static class Person {

        @Description("Name of the person")
        private String name;
        private List<String> aliases;
        private boolean active;
        private Person parent;
        private Address currentAddress;
        private List<Address> previousAddresses;
    }

    public static class Address {
        private String street;
        private String city;
    }

    public enum E {
        A, B, C
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
                @P("biggy")
                BigInteger p11,
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
                E p28,
                Person p29,
                @P(value = "optional", required = false) int p30,
                @P(value = "required") int p31) {
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
        return Wrapper.class.getMethod("f",
                String.class,//0
                boolean.class,
                Boolean.class,
                byte.class,
                Byte.class,
                short.class,//5
                Short.class,
                int.class,
                Integer.class,
                long.class,
                Long.class, //10
                BigInteger.class,
                float.class,
                Float.class,
                double.class,
                Double.class, //15
                BigDecimal.class,
                String[].class,
                Integer[].class,
                Boolean[].class,
                int[].class,//20
                boolean[].class,
                List.class,
                Set.class,
                Collection.class,
                E.class,// 25
                Person.class,
                int.class,
                int.class);
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1) {
        Map<K, V> map = new HashMap<>();
        map.put(k1, v1);
        return map;
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2) {
        Map<K, V> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3) {
        Map<K, V> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        return map;
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7) {
        Map<K, V> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        map.put(k5, v5);
        map.put(k6, v6);
        map.put(k7, v7);
        return map;
    }

    @Test
    public void test_toolSpecificationsFrom() {
        List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(new Wrapper());
        assertThat(specs).hasSize(2);

        assertThat(specs).extracting(ToolSpecification::name)
                .containsExactlyInAnyOrder("f", "func_name");
    }

    @Test
    public void test_toolSpecificationsFrom_with_duplicate_method_names() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ToolSpecifications.toolSpecificationsFrom(new InvalidToolsWithDuplicateMethodNames()))
                .withMessage("Tool names must be unique. The tool 'duplicateMethod' appears several times")
                .withNoCause();

    }

    @Test
    public void test_toolSpecificationsFrom_with_duplicate_names() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ToolSpecifications.toolSpecificationsFrom(new InvalidToolsWithDuplicateNames()))
                .withMessage("Tool names must be unique. The tool 'duplicate_name' appears several times")
                .withNoCause();

    }

    @Test
    public void test_toolName_memoryId() throws NoSuchMethodException {
        Method method = Wrapper.class.getMethod("g", String.class);
        ToolSpecification ts = ToolSpecifications.toolSpecificationFrom(method);

        assertThat(ts.name()).isEqualTo("func_name");
        assertThat(ts.description()).isNull();
        assertThat(ts.parameters()).isNull();
        assertThat(ts.toolParameters()).isNull();
    }

    @Test
    public void test_toolSpecificationFrom() throws NoSuchMethodException {
        Method method = getF();

        ToolSpecification ts = ToolSpecifications.toolSpecificationFrom(method);

        assertThat(ts.name()).isEqualTo("f");
        assertThat(ts.description()).isEqualTo("line1\nline2");
        assertThat(ts.parameters()).isInstanceOf(JsonObjectSchema.class);

        Map<String, JsonSchemaElement> properties = ts.parameters().properties();

        assertThat(properties).hasSize(29);
//        assertThat(properties)
//                .containsEntry("arg0", mapOf("type", "string", "description", "foo"))
//                .containsEntry("arg1", mapOf("type", "boolean"))
//                .containsEntry("arg2", mapOf("type", "boolean", "description", "b2"))
//                .containsEntry("arg3", mapOf("type", "integer"))
//                .containsEntry("arg4", mapOf("type", "integer"))
//                .containsEntry("arg5", mapOf("type", "integer"))
//                .containsEntry("arg6", mapOf("type", "integer"))
//                .containsEntry("arg7", mapOf("type", "integer"))
//                .containsEntry("arg8", mapOf("type", "integer"))
//                .containsEntry("arg9", mapOf("type", "integer"))
//                .containsEntry("arg10", mapOf("type", "integer"))
//                .containsEntry("arg11", mapOf("type", "integer", "description", "biggy"))
//                .containsEntry("arg12", mapOf("type", "number"))
//                .containsEntry("arg13", mapOf("type", "number"))
//                .containsEntry("arg14", mapOf("type", "number"))
//                .containsEntry("arg15", mapOf("type", "number"))
//                .containsEntry("arg16", mapOf("type", "number", "description", "bigger"))
//                .containsEntry("arg17", mapOf("type", "array", "items", mapOf("type", "string")))
//                .containsEntry("arg18", mapOf("type", "array", "items", mapOf("type", "integer")))
//                .containsEntry("arg19", mapOf("type", "array", "items", mapOf("type", "boolean")))
//                .containsEntry("arg20", mapOf("type", "array", "items", mapOf("type", "integer")))
//                .containsEntry("arg21", mapOf("type", "array", "items", mapOf("type", "boolean")))
//                .containsEntry("arg22", mapOf("type", "array", "items", mapOf("type", "integer")))
//                .containsEntry("arg23", mapOf("type", "array", "items", mapOf("type", "number")))
//                .containsEntry("arg24", mapOf("type", "array", "items", mapOf("type", "string")))
//                .containsEntry("arg25", mapOf("type", "object", "properties", mapOf(
//                        "name", mapOf("description", "Name of the person", "type", "string"),
//                        "active", mapOf("type", "boolean"),
//                        "aliases", mapOf("type", "array", "items", mapOf("type", "string")),
//                        "currentAddress", mapOf("type", "object", "properties", mapOf("city", mapOf("type", "string"), "street", mapOf("type", "string"))),
//                        "parent", mapOf("type", "object"),
//                        "aliases", mapOf("type", "array", "items", mapOf("type", "string")),
//                        "previousAddresses", mapOf("type", "array", "items", mapOf("type", "object", "properties", mapOf("city", mapOf("type", "string"), "street", mapOf("type", "string")))))))
//                .containsEntry("arg26", mapOf("type", "integer", "description", "optional"))
//                .containsEntry("arg27", mapOf("type", "integer", "description", "required"));


//        assertThat(properties.get("arg28")).containsEntry("type", "string");
//        assertThat(properties.get("arg28").get("enum")).isEqualTo(asList("A", "B", "C"));

        assertThat(ts.parameters().required())
                .containsExactly("arg0",
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
                        // "arg30", params with @P(optional = true) are optional
                        "arg28"
                );
    }

    @Data
    public static class Customer {
        public String name;
        public Address billingAddress;
        public Address shippingAddress;
    }

    public static class CustomerRegistration {
        @Tool("register a new customer")
        boolean registerCustomer(Customer customer) {
            return true;
        }
    }

    @Test
    void test_object_used_multiple_times() {

        // when
        List<ToolSpecification> toolSpecifications = ToolSpecifications.toolSpecificationsFrom(CustomerRegistration.class);

        // then
        assertThat(toolSpecifications).hasSize(1);
        ToolSpecification toolSpecification = toolSpecifications.get(0);
        assertThat(toolSpecification.name()).isEqualTo("registerCustomer");
        assertThat(toolSpecification.description()).isEqualTo("register a new customer");
        assertThat(toolSpecification.parameters()).isEqualTo(JsonObjectSchema.builder()
                .addProperty("arg0", JsonObjectSchema.builder()
                        .addStringProperty("name")
                        .addProperty("billingAddress", JsonObjectSchema.builder()
                                .addStringProperty("street")
                                .addStringProperty("city")
                                .required("street", "city")
                                .build())
                        .addProperty("shippingAddress", JsonObjectSchema.builder()
                                .addStringProperty("street")
                                .addStringProperty("city")
                                .required("street", "city")
                                .build())
                        .required("name", "billingAddress", "shippingAddress")
                        .build())
                .required("arg0")
                .build());
        assertThat(toolSpecification.toolParameters()).isNull();
    }
}