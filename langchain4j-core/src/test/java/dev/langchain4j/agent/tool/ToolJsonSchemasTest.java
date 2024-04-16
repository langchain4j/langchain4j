package dev.langchain4j.agent.tool;

import static dev.langchain4j.TestReflectUtil.*;
import static java.util.Arrays.asList;

import com.google.gson.Gson;

import dev.langchain4j.exception.JsonSchemaDeserializationException;

import dev.langchain4j.exception.JsonSchemaGenerationException;
import dev.langchain4j.internal.Utils;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

public class ToolJsonSchemasTest implements WithAssertions {

    ToolJsonSchemas defaultToolJsonSchemas = new ToolJsonSchemas();

    public enum ExampleEnum {
        A,
        B,
        C
    }

    @SuppressWarnings({"unused", "rawtypes"})
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
                ExampleEnum p28,
                Object p29
        ) {
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

    @Test
    public void test_generateToolJsonSchema() throws JsonSchemaGenerationException {
        Method method = getMethodByName(Wrapper.class, "f");
        ToolJsonSchema ts = defaultToolJsonSchemas.generateToolJsonSchema(method);

        assertThat(ts.name()).isEqualTo("f");
        assertThat(ts.description()).isEqualTo("line1\nline2");

        Map<String, Map<String, Object>> properties = ts.parameters().entrySet().stream()
                .collect(
                        Collectors.toMap(Map.Entry::getKey,
                                e -> JsonSchemaProperty.toMap(e.getValue().getProperties())));

        assertThat(properties).hasSize(30);
        assertThat(properties)
                .containsEntry("arg0", Utils.mapOf(entry("type", "string"), entry("description", "foo")))
                .containsEntry("arg1", Utils.mapOf(entry("type", "boolean")))
                .containsEntry("arg2", Utils.mapOf(entry("type", "boolean"), entry("description", "b2")))
                .containsEntry("arg3", Utils.mapOf(entry("type", "integer")))
                .containsEntry("arg4", Utils.mapOf(entry("type", "integer")))
                .containsEntry("arg5", Utils.mapOf(entry("type", "integer")))
                .containsEntry("arg6", Utils.mapOf(entry("type", "integer")))
                .containsEntry("arg7", Utils.mapOf(entry("type", "integer")))
                .containsEntry("arg8", Utils.mapOf(entry("type", "integer")))
                .containsEntry("arg9", Utils.mapOf(entry("type", "integer")))
                .containsEntry("arg10", Utils.mapOf(entry("type", "integer")))
                .containsEntry("arg11", Utils.mapOf(entry("type", "integer"), entry("description", "biggy")))
                .containsEntry("arg12", Utils.mapOf(entry("type", "number")))
                .containsEntry("arg13", Utils.mapOf(entry("type", "number")))
                .containsEntry("arg14", Utils.mapOf(entry("type", "number")))
                .containsEntry("arg15", Utils.mapOf(entry("type", "number")))
                .containsEntry("arg16", Utils.mapOf(entry("type", "number"), entry("description", "bigger")))
                .containsEntry("arg17", Utils.mapOf(entry("type", "array"), entry("items", Utils.mapOf(entry("type", "string")))))
                .containsEntry("arg18", Utils.mapOf(entry("type", "array"), entry("items", Utils.mapOf(entry("type", "integer")))))
                .containsEntry("arg19", Utils.mapOf(entry("type", "array"), entry("items", Utils.mapOf(entry("type", "boolean")))))
                .containsEntry("arg20", Utils.mapOf(entry("type", "array"), entry("items", Utils.mapOf(entry("type", "integer")))))
                .containsEntry("arg21", Utils.mapOf(entry("type", "array"), entry("items", Utils.mapOf(entry("type", "boolean")))))
                .containsEntry("arg22", Utils.mapOf(entry("type", "array"), entry("items", Utils.mapOf(entry("type", "integer")))))
                .containsEntry("arg23", Utils.mapOf(entry("type", "array"), entry("items", Utils.mapOf(entry("type", "number")))))
                .containsEntry("arg24", Utils.mapOf(entry("type", "array"), entry("items", Utils.mapOf(entry("type", "string")))))
                .containsEntry("arg25", Utils.mapOf(entry("type", "array"), entry("items", Utils.mapOf(entry("type", "object")))))
                .containsEntry("arg26", Utils.mapOf(entry("type", "array"), entry("items", Utils.mapOf(entry("type", "object")))))
                .containsEntry("arg27", Utils.mapOf(entry("type", "array"), entry("items", Utils.mapOf(entry("type", "object")))))
                .containsEntry("arg29", Utils.mapOf(entry("type", "object")));

        assertThat(properties.get("arg28")).containsEntry("type", "string");
        assertThat(properties.get("arg28").get("enum")).isEqualTo(asList("A", "B", "C"));
    }

    @SuppressWarnings("unused")
    public void example(
            @ToolMemoryId UUID idA,
            int intP,
            Integer integerP,
            long longP,
            Long LongP,
            float floatP,
            Float FloatP,
            double doubleP,
            Double DoubleP,
            byte byteP,
            Byte ByteP,
            short shortP,
            Short ShortP,
            ExampleEnum enumP,
            boolean booleanP,
            Boolean BooleanP) {}

    @Test
    public void test_deserialize() throws Exception {
        Method method = getMethodByName(getClass(), "example");

        // Prepare serialized arguments
        Map<String, Object> arguments =
                Utils.mapOf(
                        entry("arg1", 1.0),
                        entry("arg2", 2.0),
                        entry("arg3", 3.0),
                        entry("arg4", 4.0),
                        entry("arg5", 5.5),
                        entry("arg6", 6.5),
                        entry("arg7", 7.5),
                        entry("arg8", 8.5),
                        entry("arg9", 9.0),
                        entry("arg10", 10.0),
                        entry("arg11", 11.0),
                        entry("arg12", 12.0),
                        entry("arg13", "A"),
                        entry("arg14", true),
                        entry("arg15", false));
        String serializedArguments = new Gson().toJson(arguments);

        ToolJsonSchemas toolJsonSchemas = new ToolJsonSchemas();
        Map<String, Object> deserializedArguments =
                toolJsonSchemas.deserialize(serializedArguments, method);

        // Sort the arguments by the order of the method parameters
        List<Object> argumentsMap =
                arguments.keySet().stream()
                        .map(deserializedArguments::get)
                        .collect(Collectors.toList());

        // Assert the arguments is deserialized correctly with respect to their values
        assertThat(argumentsMap)
                .containsExactly(
                        1,
                        2,
                        3L,
                        4L,
                        5.5f,
                        6.5f,
                        7.5,
                        8.5,
                        (byte) 9,
                        (byte) 10,
                        (short) 11,
                        (short) 12,
                        ExampleEnum.A,
                        true,
                        false);

        {
            Map<String, Object> as = new HashMap<>(deserializedArguments);
            as.put("arg1", "abc");

            assertThatExceptionOfType(JsonSchemaDeserializationException.class)
                    .isThrownBy(
                            () -> toolJsonSchemas.deserialize(
                                    toolJsonSchemas.serialize(as), method))
                    .withMessageContaining(
                            "is not convertable to int, got java.lang.String: \"abc\"")
                    .withNoCause();
        }
    }
}
