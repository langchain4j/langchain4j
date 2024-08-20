package dev.langchain4j.service.tool;

import static dev.langchain4j.service.tool.DefaultToolExecutor.coerceArgument;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

class DefaultToolExecutorTest implements WithAssertions {

    @Test
    public void tesT_hasNoFractionalPart() {
        assertThat(DefaultToolExecutor.hasNoFractionalPart(3.0)).isTrue();
        assertThat(DefaultToolExecutor.hasNoFractionalPart(-3.0)).isTrue();
        assertThat(DefaultToolExecutor.hasNoFractionalPart(3.5)).isFalse();
        assertThat(DefaultToolExecutor.hasNoFractionalPart(-3.5)).isFalse();
    }

    public enum ExampleEnum {A, B, C}

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
            Boolean BooleanP,
            double double2P,
            Double Double2P
    ) {
    }

    @Test
    public void test_prepareArguments() throws Exception {
        UUID memoryId = UUID.randomUUID();

        Method method = getClass().getMethod(
                "example",
                UUID.class,
                int.class,
                Integer.class,
                long.class,
                Long.class,
                float.class,
                Float.class,
                double.class,
                Double.class,
                byte.class,
                Byte.class,
                short.class,
                Short.class,
                ExampleEnum.class,
                boolean.class,
                Boolean.class,
                double.class,
                Double.class
        );

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("arg1", 1.0);
        arguments.put("arg2", 2.0);
        arguments.put("arg3", 3.0);
        arguments.put("arg4", 4.0);
        arguments.put("arg5", 5.5);
        arguments.put("arg6", 6.5);
        arguments.put("arg7", 7.5);
        arguments.put("arg8", 8.5);
        arguments.put("arg9", 9.0);
        arguments.put("arg10", 10.0);
        arguments.put("arg11", 11.0);
        arguments.put("arg12", 12.0);
        arguments.put("arg13", "A");
        arguments.put("arg14", true);
        arguments.put("arg15", Boolean.FALSE);
        arguments.put("arg16", "1.1");
        arguments.put("arg17", "2.2");

        Object[] args = DefaultToolExecutor.prepareArguments(method, arguments, memoryId);

        assertThat(args).containsExactly(
                memoryId,
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
                false,
                1.1,
                2.2
        );

        {
            Map<String, Object> as = new HashMap<>(arguments);
            as.put("arg1", "abc");

            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> DefaultToolExecutor.prepareArguments(method, as, memoryId))
                    .withMessage("Argument \"arg1\" is not convertable to int, got java.lang.String: <abc>")
                    .withNoCause();
        }
    }

    @AllArgsConstructor
    @EqualsAndHashCode
    static class Person {

        String name;
        int age;
    }

    @Test
    public void test_coerceArgument() {

        Map<String, Object> personMap = new HashMap<>();
        personMap.put("name", "Klaus");
        personMap.put("age", 42);
        assertThat(coerceArgument(personMap, "arg", Person.class)).isEqualTo(new Person("Klaus", 42));

        assertThat(coerceArgument("abc", "arg", String.class)).isEqualTo("abc");

        assertThat(coerceArgument("A", "arg", ExampleEnum.class)).isEqualTo(ExampleEnum.A);
        assertThat(coerceArgument(ExampleEnum.A, "arg", ExampleEnum.class)).isEqualTo(ExampleEnum.A);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument("D", "arg", ExampleEnum.class))
                .withMessageContaining("Argument \"arg\" is not a valid enum value for");

        assertThat(coerceArgument(true, "arg", boolean.class)).isEqualTo(true);
        assertThat(coerceArgument(Boolean.FALSE, "arg", boolean.class)).isEqualTo(false);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument("true", "arg", boolean.class))
                .withMessageContaining("Argument \"arg\" is not convertable to boolean, got java.lang.String: <true>");

        assertThat(coerceArgument(1.5, "arg", double.class)).isEqualTo(1.5);
        assertThat(coerceArgument(1.5, "arg", Double.class)).isEqualTo(1.5);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument("abc", "arg", double.class))
                .withMessageContaining("Argument \"arg\" is not convertable to double, got java.lang.String: <abc>");

        assertThat(coerceArgument(1.5, "arg", float.class)).isEqualTo(1.5f);
        assertThat(coerceArgument(1.5, "arg", Float.class)).isEqualTo(1.5f);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument(1.5 * ((double) Float.MAX_VALUE), "arg", float.class))
                .withMessageContaining("Argument \"arg\" is out of range for float:");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument(-1.5 * ((double) Float.MAX_VALUE), "arg", float.class))
                .withMessageContaining("Argument \"arg\" is out of range for float:");

        assertThat(coerceArgument(1.0, "arg", int.class)).isEqualTo(1);
        assertThat(coerceArgument(Integer.MAX_VALUE, "arg", int.class)).isEqualTo(Integer.MAX_VALUE);
        assertThat(coerceArgument(Integer.MIN_VALUE, "arg", Integer.class)).isEqualTo(Integer.MIN_VALUE);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument(1.5, "arg", int.class))
                .withMessageContaining("has non-integer value");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument(Integer.MAX_VALUE + 1.0, "arg", int.class))
                .withMessageContaining("Argument \"arg\" is out of range for int:");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument(Integer.MIN_VALUE - 1.0, "arg", Integer.class))
                .withMessageContaining("Argument \"arg\" is out of range for java.lang.Integer:");

        assertThat(coerceArgument(1.0, "arg", long.class)).isEqualTo(1L);
        assertThat(coerceArgument(Long.MAX_VALUE, "arg", long.class)).isEqualTo(Long.MAX_VALUE);
        assertThat(coerceArgument(Long.MIN_VALUE, "arg", Long.class)).isEqualTo(Long.MIN_VALUE);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument(1.5, "arg", long.class))
                .withMessageContaining("has non-integer value");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument(1.5 * ((double) Long.MAX_VALUE), "arg", long.class))
                .withMessageContaining("Argument \"arg\" is out of range for long:");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument(1.5 * ((double) Long.MIN_VALUE), "arg", Long.class))
                .withMessageContaining("Argument \"arg\" is out of range for java.lang.Long:");

        assertThat(coerceArgument(1.0, "arg", short.class)).isEqualTo((short) 1);
        assertThat(coerceArgument(Short.MAX_VALUE, "arg", short.class)).isEqualTo(Short.MAX_VALUE);
        assertThat(coerceArgument(Short.MIN_VALUE, "arg", Short.class)).isEqualTo(Short.MIN_VALUE);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument(1.5, "arg", short.class))
                .withMessageContaining("has non-integer value");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument(Short.MAX_VALUE + 1.0, "arg", short.class))
                .withMessageContaining("Argument \"arg\" is out of range for short:");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument(Short.MIN_VALUE - 1.0, "arg", Short.class))
                .withMessageContaining("Argument \"arg\" is out of range for java.lang.Short:");

        assertThat(coerceArgument(1.0, "arg", byte.class)).isEqualTo((byte) 1);
        assertThat(coerceArgument(Byte.MAX_VALUE, "arg", byte.class)).isEqualTo(Byte.MAX_VALUE);
        assertThat(coerceArgument(Byte.MIN_VALUE, "arg", Byte.class)).isEqualTo(Byte.MIN_VALUE);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument(1.5, "arg", byte.class))
                .withMessageContaining("has non-integer value");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument(Byte.MAX_VALUE + 1.0, "arg", byte.class))
                .withMessageContaining("Argument \"arg\" is out of range for byte:");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument(Byte.MIN_VALUE - 1.0, "arg", Byte.class))
                .withMessageContaining("Argument \"arg\" is out of range for java.lang.Byte:");

        assertThat(coerceArgument(1.5, "arg", BigDecimal.class)).isEqualTo(BigDecimal.valueOf(1.5));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument("abc", "arg", BigDecimal.class))
                .withMessageContaining("Argument \"arg\" is not convertable to java.math.BigDecimal, got java.lang.String: <abc>");

        assertThat(coerceArgument(1, "arg", BigInteger.class)).isEqualTo(BigInteger.valueOf(1));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument(1.5, "arg", BigInteger.class))
                .withMessageContaining("has non-integer value");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument("abc", "arg", BigInteger.class))
                .withMessageContaining("Argument \"arg\" is not convertable to java.math.BigInteger, got java.lang.String: <abc>");
    }

    private static class TestTool {

        @Tool
        public int addOne(int num) {
            return num + 1;
        }
    }

    @Test
    public void should_execute_tool_by_method_name() throws NoSuchMethodException {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("1")
                .name("addOne")
                .arguments("{ \"arg0\": 2 }")
                .build();

        DefaultToolExecutor toolExecutor =
                new DefaultToolExecutor(new TestTool(), TestTool.class.getDeclaredMethod("addOne", int.class));

        String result = toolExecutor.execute(request, "DEFAULT");

        assertThat(result).isEqualTo("3");
    }

    @Test
    public void should_execute_tool_with_execution_request() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("1")
                .name("addOne")
                .arguments("{ \"arg0\": 2 }")
                .build();

        DefaultToolExecutor toolExecutor = new DefaultToolExecutor(new TestTool(), request);

        String result = toolExecutor.execute(request, "DEFAULT");

        assertThat(result).isEqualTo("3");
    }

    @Test
    public void should_not_execute_tool_with_wrong_execution_request() throws NoSuchMethodException {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("1")
                .name("unknownMethod")
                .arguments("{ \"arg0\": 2 }")
                .build();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new DefaultToolExecutor(new TestTool(), request))
                .withMessageContaining("Method 'unknownMethod' is not found in object");

    }

    @Test
    public void should_not_execute_tool_with_null_execution_request() throws NoSuchMethodException {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new DefaultToolExecutor(new TestTool(), (ToolExecutionRequest) null));

    }
}