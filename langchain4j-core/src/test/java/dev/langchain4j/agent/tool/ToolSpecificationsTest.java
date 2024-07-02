package dev.langchain4j.agent.tool;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.items;
import dev.langchain4j.model.output.structured.Description;
import static java.util.Arrays.asList;
import lombok.Data;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

class ToolSpecificationsTest implements WithAssertions {

    @Test
    public void test_removeNulls() {
        assertThat(ToolSpecifications.removeNulls(null, JsonSchemaProperty.STRING, null))
                .containsExactly(JsonSchemaProperty.STRING);
    }

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
                List p25,
                Set p26,
                Collection p27,
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
                List.class,//25
                Set.class,
                Collection.class,
                E.class,
                Person.class,
                int.class, //30
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

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7 ) {
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
        assertThat(ts.description()).isEmpty();
        assertThat(ts.parameters()).isNull();
    }

    @Test
    public void test_toolSpecificationFrom() throws NoSuchMethodException {
        Method method = getF();

        ToolSpecification ts = ToolSpecifications.toolSpecificationFrom(method);

        assertThat(ts.name()).isEqualTo("f");
        assertThat(ts.description()).isEqualTo("line1\nline2");
        assertThat(ts.parameters().type()).isEqualTo("object");

        Map<String, Map<String, Object>> properties = ts.parameters().properties();

        assertThat(properties).hasSize(32);
        assertThat(properties)
                .containsEntry("arg0", mapOf("type", "string", "description", "foo"))
                .containsEntry("arg1", mapOf("type", "boolean"))
                .containsEntry("arg2", mapOf("type", "boolean", "description", "b2"))
                .containsEntry("arg3", mapOf("type", "integer"))
                .containsEntry("arg4", mapOf("type", "integer"))
                .containsEntry("arg5", mapOf("type", "integer"))
                .containsEntry("arg6", mapOf("type", "integer"))
                .containsEntry("arg7", mapOf("type", "integer"))
                .containsEntry("arg8", mapOf("type", "integer"))
                .containsEntry("arg9", mapOf("type", "integer"))
                .containsEntry("arg10", mapOf("type", "integer"))
                .containsEntry("arg11", mapOf("type", "integer", "description", "biggy"))
                .containsEntry("arg12", mapOf("type", "number"))
                .containsEntry("arg13", mapOf("type", "number"))
                .containsEntry("arg14", mapOf("type", "number"))
                .containsEntry("arg15", mapOf("type", "number"))
                .containsEntry("arg16", mapOf("type", "number", "description", "bigger"))
                .containsEntry("arg17", mapOf("type", "array", "items", mapOf("type", "string")))
                .containsEntry("arg18", mapOf("type", "array", "items", mapOf("type", "integer")))
                .containsEntry("arg19", mapOf("type", "array", "items", mapOf("type", "boolean")))
                .containsEntry("arg20", mapOf("type", "array", "items", mapOf("type", "integer")))
                .containsEntry("arg21", mapOf("type", "array", "items", mapOf("type", "boolean")))
                .containsEntry("arg22", mapOf("type", "array", "items", mapOf("type", "integer")))
                .containsEntry("arg23", mapOf("type", "array", "items", mapOf("type", "number")))
                .containsEntry("arg24", mapOf("type", "array", "items", mapOf("type", "string")))
                .containsEntry("arg25", mapOf("type", "array", "items", mapOf("type", "object")))
                .containsEntry("arg26", mapOf("type", "array", "items", mapOf("type", "object")))
                .containsEntry("arg27", mapOf("type", "array", "items", mapOf("type", "object")))
                .containsEntry("arg29", mapOf("type", "object", "properties", mapOf(
                        "name", mapOf("description", "Name of the person", "type", "string"),
                        "active", mapOf("type", "boolean"),
                        "aliases", mapOf("type", "array", "items", mapOf("type", "string")),
                        "currentAddress", mapOf("type", "object", "properties", mapOf("city", mapOf("type", "string"), "street", mapOf("type", "string"))),
                        "parent", mapOf("type", "object"),
                        "aliases", mapOf("type", "array", "items", mapOf("type", "string")),
                        "previousAddresses", mapOf("type", "array", "items", mapOf("type", "object", "properties", mapOf("city", mapOf("type", "string"), "street", mapOf("type", "string")))))))
                .containsEntry("arg30", mapOf("type", "integer", "description", "optional"))
                .containsEntry("arg31", mapOf("type", "integer", "description", "required"));



        assertThat(properties.get("arg28")).containsEntry("type", "string");
        assertThat(properties.get("arg28").get("enum")).isEqualTo(asList("A", "B", "C"));

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
                        "arg27",
                        "arg28",
                        "arg29",
                        // "arg30", params with @P(optional = true) are optional
                        "arg31"
                );
    }

    @Test
    public void test_toJsonSchemaProperties() throws NoSuchMethodException {
        Method method = getF();

        Parameter[] ps = method.getParameters();

        assertThat(ToolSpecifications.toJsonSchemaProperties(ps[0]))
                .containsExactly(JsonSchemaProperty.STRING,
                        JsonSchemaProperty.description("foo"));

        assertThat(ToolSpecifications.toJsonSchemaProperties(ps[1]))
                .containsExactly(JsonSchemaProperty.BOOLEAN);
        assertThat(ToolSpecifications.toJsonSchemaProperties(ps[2]))
                .containsExactly(JsonSchemaProperty.BOOLEAN,
                        JsonSchemaProperty.description("b2")
                );

        assertThat(ToolSpecifications.toJsonSchemaProperties(ps[3]))
                .containsExactly(JsonSchemaProperty.INTEGER);

        assertThat(ToolSpecifications.toJsonSchemaProperties(ps[4]))
                .containsExactly(JsonSchemaProperty.INTEGER);
        assertThat(ToolSpecifications.toJsonSchemaProperties(ps[5]))
                .containsExactly(JsonSchemaProperty.INTEGER);
        assertThat(ToolSpecifications.toJsonSchemaProperties(ps[6]))
                .containsExactly(JsonSchemaProperty.INTEGER);
        assertThat(ToolSpecifications.toJsonSchemaProperties(ps[7]))
                .containsExactly(JsonSchemaProperty.INTEGER);
        assertThat(ToolSpecifications.toJsonSchemaProperties(ps[8]))
                .containsExactly(JsonSchemaProperty.INTEGER);
        assertThat(ToolSpecifications.toJsonSchemaProperties(ps[9]))
                .containsExactly(JsonSchemaProperty.INTEGER);
        assertThat(ToolSpecifications.toJsonSchemaProperties(ps[10]))
                .containsExactly(JsonSchemaProperty.INTEGER);
        assertThat(ToolSpecifications.toJsonSchemaProperties(ps[11]))
                .containsExactly(JsonSchemaProperty.INTEGER,
                        JsonSchemaProperty.description("biggy"));

        assertThat(ToolSpecifications.toJsonSchemaProperties(ps[12]))
                .containsExactly(JsonSchemaProperty.NUMBER);
        assertThat(ToolSpecifications.toJsonSchemaProperties(ps[13]))
                .containsExactly(JsonSchemaProperty.NUMBER);
        assertThat(ToolSpecifications.toJsonSchemaProperties(ps[14]))
                .containsExactly(JsonSchemaProperty.NUMBER);
        assertThat(ToolSpecifications.toJsonSchemaProperties(ps[15]))
                .containsExactly(JsonSchemaProperty.NUMBER);
        assertThat(ToolSpecifications.toJsonSchemaProperties(ps[16]))
                .containsExactly(JsonSchemaProperty.NUMBER,
                        JsonSchemaProperty.description("bigger"));

        assertThat(ToolSpecifications.toJsonSchemaProperties(ps[17]))
                .containsExactly(JsonSchemaProperty.ARRAY, items(JsonSchemaProperty.STRING));
        assertThat(ToolSpecifications.toJsonSchemaProperties(ps[18]))
                .containsExactly(JsonSchemaProperty.ARRAY, items(JsonSchemaProperty.INTEGER));
        assertThat(ToolSpecifications.toJsonSchemaProperties(ps[19]))
                .containsExactly(JsonSchemaProperty.ARRAY, items(JsonSchemaProperty.BOOLEAN));

        {
            List<JsonSchemaProperty> properties = new ArrayList<>();
            ToolSpecifications.toJsonSchemaProperties(ps[28]).forEach(properties::add);

            assertThat(properties.get(0))
                    .isEqualTo(JsonSchemaProperty.STRING);

            assertThat(properties.get(1).value()).isEqualTo(asList("A", "B", "C"));
        }

        assertThat(ToolSpecifications.toJsonSchemaProperties(ps[29]))
                .containsExactly(JsonSchemaProperty.OBJECT, JsonSchemaProperty.from(
                        "properties", mapOf("name" , mapOf("description", "Name of the person", "type", "string"),
                "active", mapOf( "type","boolean" ),
                "aliases", mapOf("type", "array", "items", mapOf("type", "string")),
                "currentAddress", mapOf("type", "object", "properties", mapOf("city", mapOf("type", "string"), "street", mapOf("type", "string"))),
                "parent", mapOf("type", "object"),
                "aliases", mapOf("type", "array", "items", mapOf("type", "string")),
                "previousAddresses", mapOf("type", "array", "items", mapOf("type", "object","properties", mapOf("city", mapOf("type", "string"), "street", mapOf("type", "string")) )))));

    }
}
