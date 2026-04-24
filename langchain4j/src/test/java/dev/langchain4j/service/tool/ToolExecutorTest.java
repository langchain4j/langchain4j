package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.DayOfWeek;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ToolExecutorTest {

    TestTool testTool = new TestTool();

    private static class TestTool {

        @Tool
        String strings(String arg0, String arg1) {
            return arg0 + "_" + arg1;
        }

        @Tool
        double doubles(double arg0, Double arg1) {
            return arg0 + arg1;
        }

        @Tool
        float floats(float arg0, Float arg1) {
            return arg0 + arg1;
        }

        @Tool
        BigDecimal bigDecimals(BigDecimal arg0, BigDecimal arg1) {
            return arg0.add(arg1);
        }

        @Tool
        long longs(long arg0, Long arg1) {
            return arg0 + arg1;
        }

        @Tool
        int ints(int arg0, Integer arg1) {
            return arg0 + arg1;
        }

        @Tool
        short shorts(short arg0, Short arg1) {
            return (short) (arg0 + arg1);
        }

        @Tool
        byte bytes(byte arg0, Byte arg1) {
            return (byte) (arg0 + arg1);
        }

        @Tool
        BigInteger bigIntegers(BigInteger arg0, BigInteger arg1) {
            return arg0.add(arg1);
        }

        @Tool
        String enums(DayOfWeek arg0, CustomEnum arg1) {
            return arg0 + "_" + arg1;
        }

        enum CustomEnum {
            ONE,
            TWO
        }
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = ';',
            value = {
                "{\"arg0\": \"hello\", \"arg1\": \"world\"}; hello_world",
                "{\"arg0\": \"hello\"}; hello_null",
                "{}; null_null",
            })
    void should_execute_tool_with_parameters_of_type_string(String arguments, String expectedResult)
            throws NoSuchMethodException {
        executeAndAssert(arguments, "strings", String.class, String.class, expectedResult);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"arg0\": 2, \"arg1\": 2}",
                "{\"arg0\": 2.0, \"arg1\": 2.0}",
                "{\"arg0\": 1.9, \"arg1\": 2.1}",
            })
    void should_execute_tool_with_parameters_of_type_double(String arguments) throws NoSuchMethodException {
        executeAndAssert(arguments, "doubles", double.class, Double.class, "4.0");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"arg0\": 2, \"arg1\": 2}",
                "{\"arg0\": 2.0, \"arg1\": 2.0}",
                "{\"arg0\": 1.9, \"arg1\": 2.1}",
                "{\"arg0\": -6.0, \"arg1\": 10.0}",
            })
    void should_execute_tool_with_parameters_of_type_float(String arguments) throws NoSuchMethodException {
        executeAndAssert(arguments, "floats", float.class, Float.class, "4.0");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"arg0\": 2, \"arg1\": " + Float.MAX_VALUE + "}",
                "{\"arg0\": 2, \"arg1\": " + -Double.MAX_VALUE + "}"
            })
    void should_fail_when_argument_does_not_fit_into_float_type(String arguments) throws NoSuchMethodException {
        executeAndExpectFailure(
                arguments,
                "floats",
                float.class,
                Float.class,
                "Argument \"arg1\" is out of range for java.lang.Float:");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"arg0\": 2, \"arg1\": 2}",
                "{\"arg0\": 2.0, \"arg1\": 2.0}",
                "{\"arg0\": 1.9, \"arg1\": 2.1}",
            })
    void should_execute_tool_with_parameters_of_type_BigDecimal(String arguments) throws NoSuchMethodException {
        executeAndAssert(arguments, "bigDecimals", BigDecimal.class, BigDecimal.class, "4.0");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"arg0\": 2, \"arg1\": 2}", "{\"arg0\": 2.0, \"arg1\": 2.0}"})
    void should_execute_tool_with_parameters_of_type_long(String arguments) throws NoSuchMethodException {
        executeAndAssert(arguments, "longs", long.class, Long.class, "4");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"arg0\": 2, \"arg1\": 2.1}", "{\"arg0\": 2.1, \"arg1\": 2}"})
    void should_fail_when_argument_is_fractional_number_for_parameter_of_type_long(String arguments)
            throws NoSuchMethodException {
        executeAndExpectFailure(arguments, "longs", long.class, Long.class, "has non-integer value for");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"arg0\": 2, \"arg1\": " + Double.MAX_VALUE + "}",
                "{\"arg0\": 2, \"arg1\": " + -Double.MAX_VALUE + "}"
            })
    void should_fail_when_argument_does_not_fit_into_long_type(String arguments) throws NoSuchMethodException {
        executeAndExpectFailure(
                arguments, "longs", long.class, Long.class, "Argument \"arg1\" is out of range for java.lang.Long:");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"arg0\": 2, \"arg1\": 2}", "{\"arg0\": 2.0, \"arg1\": 2.0}"})
    void should_execute_tool_with_parameters_of_type_int(String arguments) throws NoSuchMethodException {
        executeAndAssert(arguments, "ints", int.class, Integer.class, "4");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"arg0\": 2, \"arg1\": 2.1}", "{\"arg0\": 2.1, \"arg1\": 2}"})
    void should_fail_when_argument_is_fractional_number_for_parameter_of_type_int(String arguments)
            throws NoSuchMethodException {
        executeAndExpectFailure(arguments, "ints", int.class, Integer.class, "has non-integer value for");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"arg0\": 2, \"arg1\": " + Double.MAX_VALUE + "}",
                "{\"arg0\": 2, \"arg1\": " + -Double.MAX_VALUE + "}"
            })
    void should_fail_when_argument_does_not_fit_into_int_type(String arguments) throws NoSuchMethodException {
        executeAndExpectFailure(
                arguments,
                "ints",
                int.class,
                Integer.class,
                "Argument \"arg1\" is out of range for java.lang.Integer:");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"arg0\": 2, \"arg1\": 2}", "{\"arg0\": 2.0, \"arg1\": 2.0}"})
    void should_execute_tool_with_parameters_of_type_short(String arguments) throws NoSuchMethodException {
        executeAndAssert(arguments, "shorts", short.class, Short.class, "4");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"arg0\": 2, \"arg1\": 2.1}", "{\"arg0\": 2.1, \"arg1\": 2}"})
    void should_fail_when_argument_is_fractional_number_for_parameter_of_type_short(String arguments)
            throws NoSuchMethodException {
        executeAndExpectFailure(arguments, "shorts", short.class, Short.class, "has non-integer value for");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"arg0\": 2, \"arg1\": " + Double.MAX_VALUE + "}",
                "{\"arg0\": 2, \"arg1\": " + -Double.MAX_VALUE + "}"
            })
    void should_fail_when_argument_does_not_fit_into_short_type(String arguments) throws NoSuchMethodException {
        executeAndExpectFailure(
                arguments,
                "shorts",
                short.class,
                Short.class,
                "Argument \"arg1\" is out of range for java.lang.Short:");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"arg0\": 2, \"arg1\": 2}", "{\"arg0\": 2.0, \"arg1\": 2.0}"})
    void should_execute_tool_with_parameters_of_type_byte(String arguments) throws NoSuchMethodException {
        executeAndAssert(arguments, "bytes", byte.class, Byte.class, "4");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"arg0\": 2, \"arg1\": 2.1}", "{\"arg0\": 2.1, \"arg1\": 2}"})
    void should_fail_when_argument_is_fractional_number_for_parameter_of_type_byte(String arguments)
            throws NoSuchMethodException {
        executeAndExpectFailure(arguments, "bytes", byte.class, Byte.class, "has non-integer value for");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"arg0\": 2, \"arg1\": " + Double.MAX_VALUE + "}",
                "{\"arg0\": 2, \"arg1\": " + -Double.MAX_VALUE + "}"
            })
    void should_fail_when_argument_does_not_fit_into_byte_type(String arguments) throws NoSuchMethodException {
        executeAndExpectFailure(
                arguments, "bytes", byte.class, Byte.class, "Argument \"arg1\" is out of range for java.lang.Byte:");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"arg0\": 2, \"arg1\": 2}", "{\"arg0\": 2.0, \"arg1\": 2.0}"})
    void should_execute_tool_with_parameters_of_type_BigInteger(String arguments) throws NoSuchMethodException {
        executeAndAssert(arguments, "bigIntegers", BigInteger.class, BigInteger.class, "4");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = ';',
            value = {
                "{\"arg0\": \"MONDAY\", \"arg1\": \"ONE\"}; MONDAY_ONE",
                "{\"arg0\": \"MONDAY\"}; MONDAY_null",
                "{}; null_null",
            })
    void should_execute_tool_with_parameters_of_type_enum(String arguments, String expectedResult)
            throws NoSuchMethodException {
        executeAndAssert(arguments, "enums", DayOfWeek.class, TestTool.CustomEnum.class, expectedResult);
    }

    private void executeAndAssert(
            String arguments, String methodName, Class<?> arg0Type, Class<?> arg1Type, String expectedResult)
            throws NoSuchMethodException {
        ToolExecutionRequest request =
                ToolExecutionRequest.builder().arguments(arguments).build();

        DefaultToolExecutor toolExecutor =
                new DefaultToolExecutor(testTool, TestTool.class.getDeclaredMethod(methodName, arg0Type, arg1Type));

        String result = toolExecutor.execute(request, "DEFAULT");

        assertThat(result).isEqualTo(expectedResult);
    }

    private void executeAndExpectFailure(
            String arguments, String methodName, Class<?> arg0Type, Class<?> arg1Type, String expectedError)
            throws NoSuchMethodException {
        ToolExecutionRequest request =
                ToolExecutionRequest.builder().arguments(arguments).build();

        DefaultToolExecutor toolExecutor =
                new DefaultToolExecutor(testTool, TestTool.class.getDeclaredMethod(methodName, arg0Type, arg1Type));

        assertThatThrownBy(() -> toolExecutor.execute(request, "DEFAULT"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedError);
    }

    @Test
    void should_specify_tool_executor_as_lambda() {

        ToolExecutor toolExecutor = (request, memoryId) -> "result";

        assertThat(toolExecutor.execute(null, null)).isEqualTo("result");
        assertThat(toolExecutor.executeWithContext(null, null).resultText()).isEqualTo("result");
    }

    @Test
    void should_specify_tool_executor_as_lambda_typed() {

        ToolExecutor toolExecutor = (ToolExecutionRequest request, Object memoryId) -> "result";

        assertThat(toolExecutor.execute(null, null)).isEqualTo("result");
        assertThat(toolExecutor.executeWithContext(null, null).resultText()).isEqualTo("result");
    }

    @Test
    void should_specify_tool_executor_as_anonymous_class() {

        ToolExecutor toolExecutor = new ToolExecutor() {

            @Override
            public String execute(ToolExecutionRequest toolExecutionRequest, Object memoryId) {
                return "result";
            }

            public void dummyMethod() { // ensure anonymous class cannot be converted into lambda during refactoring
            }
        };

        assertThat(toolExecutor.execute(null, null)).isEqualTo("result");
        assertThat(toolExecutor.executeWithContext(null, null).resultText()).isEqualTo("result");
    }

    @Test
    void should_specify_tool_executor_with_context_as_anonymous_class() {

        ToolExecutor toolExecutor = new ToolExecutor() {

            @Override
            public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {
                return ToolExecutionResult.builder().resultText("result").build();
            }

            @Override
            public String execute(ToolExecutionRequest request, Object memoryId) {
                return null;
            }
        };

        assertThat(toolExecutor.execute(null, null)).isNull();
        assertThat(toolExecutor.executeWithContext(null, null).resultText()).isEqualTo("result");
    }
}
