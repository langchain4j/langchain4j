package dev.langchain4j.agent.tool;

import dev.langchain4j.agent.tool.func.ToolBiFunction;
import dev.langchain4j.agent.tool.func.ToolCompanionBiFunction;
import dev.langchain4j.agent.tool.lambda.ToolSerializedCompanionFunction;
import dev.langchain4j.agent.tool.lambda.ToolSerializedFunction;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolExecutorTest {

    TestTool testTool = new TestTool();

    private static class TestTool {

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
        static double staticDoubles(double arg0, Double arg1) {
            return arg0 + arg1;
        }

        @Tool
        static float staticFloats(float arg0, Float arg1) {
            return arg0 + arg1;
        }

        @Tool
        static BigDecimal staticBigDecimals(BigDecimal arg0, BigDecimal arg1) {
            return arg0.add(arg1);
        }

        @Tool
        static long staticLongs(long arg0, Long arg1) {
            return arg0 + arg1;
        }

        @Tool
        static int staticInts(int arg0, Integer arg1) {
            return arg0 + arg1;
        }

        @Tool
        static short staticShorts(short arg0, Short arg1) {
            return (short) (arg0 + arg1);
        }

        @Tool
        static byte staticBytes(byte arg0, Byte arg1) {
            return (byte) (arg0 + arg1);
        }

        @Tool
        BigInteger staticBigIntegers(BigInteger arg0, BigInteger arg1) {
            return arg0.add(arg1);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": 2}",
            "{\"arg0\": 2.0, \"arg1\": 2.0}",
            "{\"arg0\": 1.9, \"arg1\": 2.1}",
    })
    void should_execute_tool_with_parameters_of_type_double(String arguments) throws NoSuchMethodException {
        executeAndAssert(arguments, "doubles", double.class, Double.class, "4.0");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": 2}",
            "{\"arg0\": 2.0, \"arg1\": 2.0}",
            "{\"arg0\": 1.9, \"arg1\": 2.1}",
    })
    void should_execute_tool_with_parameters_of_type_float(String arguments) throws NoSuchMethodException {
        executeAndAssert(arguments, "floats", float.class, Float.class, "4.0");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": " + Float.MAX_VALUE + "}",
            "{\"arg0\": 2, \"arg1\": " + -Double.MAX_VALUE + "}"
    })
    void should_fail_when_argument_does_not_fit_into_float_type(String arguments) throws NoSuchMethodException {
        executeAndExpectFailure(arguments, "floats", float.class, Float.class, "is out of range for the float type");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": 2}",
            "{\"arg0\": 2.0, \"arg1\": 2.0}",
            "{\"arg0\": 1.9, \"arg1\": 2.1}",
    })
    void should_execute_tool_with_parameters_of_type_BigDecimal(String arguments) throws NoSuchMethodException {
        executeAndAssert(arguments, "bigDecimals", BigDecimal.class, BigDecimal.class, "4.0");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": 2}",
            "{\"arg0\": 2.0, \"arg1\": 2.0}"
    })
    void should_execute_tool_with_parameters_of_type_long(String arguments) throws NoSuchMethodException {
        executeAndAssert(arguments, "longs", long.class, Long.class, "4");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": " + Double.MAX_VALUE + "}",
            "{\"arg0\": 2, \"arg1\": " + -Double.MAX_VALUE + "}"
    })
    void should_fail_when_argument_does_not_fit_into_long_type(String arguments) throws NoSuchMethodException {
        executeAndExpectFailure(arguments, "longs", long.class, Long.class, "is out of range for the long type");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": 2}",
            "{\"arg0\": 2.0, \"arg1\": 2.0}"
    })
    void should_execute_tool_with_parameters_of_type_int(String arguments) throws NoSuchMethodException {
        executeAndAssert(arguments, "ints", int.class, Integer.class, "4");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": " + Double.MAX_VALUE + "}",
            "{\"arg0\": 2, \"arg1\": " + -Double.MAX_VALUE + "}"
    })
    void should_fail_when_argument_does_not_fit_into_int_type(String arguments) throws NoSuchMethodException {
        executeAndExpectFailure(arguments, "ints", int.class, Integer.class, "is out of range for the integer type");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": 2}",
            "{\"arg0\": 2.0, \"arg1\": 2.0}"
    })
    void should_execute_tool_with_parameters_of_type_short(String arguments) throws NoSuchMethodException {
        executeAndAssert(arguments, "shorts", short.class, Short.class, "4");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": " + Double.MAX_VALUE + "}",
            "{\"arg0\": 2, \"arg1\": " + -Double.MAX_VALUE + "}"
    })
    void should_fail_when_argument_does_not_fit_into_short_type(String arguments) throws NoSuchMethodException {
        executeAndExpectFailure(arguments, "shorts", short.class, Short.class, "is out of range for the short type");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": 2}",
            "{\"arg0\": 2.0, \"arg1\": 2.0}"
    })
    void should_execute_tool_with_parameters_of_type_byte(String arguments) throws NoSuchMethodException {
        executeAndAssert(arguments, "bytes", byte.class, Byte.class, "4");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": " + Double.MAX_VALUE + "}",
            "{\"arg0\": 2, \"arg1\": " + -Double.MAX_VALUE + "}"
    })
    void should_fail_when_argument_does_not_fit_into_byte_type(String arguments) throws NoSuchMethodException {
        executeAndExpectFailure(arguments, "bytes", byte.class, Byte.class, "is out of range for the byte type");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": 2}",
            "{\"arg0\": 2.0, \"arg1\": 2.0}"
    })
    void should_execute_tool_with_parameters_of_type_BigInteger(String arguments) throws NoSuchMethodException {
        executeAndAssert(arguments, "bigIntegers", BigInteger.class, BigInteger.class, "4");
    }

    private void executeAndAssert(String arguments, String methodName, Class<?> arg0Type, Class<?> arg1Type, String expectedResult) throws NoSuchMethodException {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .arguments(arguments)
                .build();

        ToolExecutor toolExecutor = new ToolExecutor(testTool, TestTool.class.getDeclaredMethod(methodName, arg0Type, arg1Type));

        String result = toolExecutor.execute(request);

        assertThat(result).isEqualTo(expectedResult);
    }

    private void executeAndExpectFailure(String arguments, String methodName, Class<?> arg0Type, Class<?> arg1Type, String expectedError) throws NoSuchMethodException {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .arguments(arguments)
                .build();

        ToolExecutor toolExecutor = new ToolExecutor(testTool, TestTool.class.getDeclaredMethod(methodName, arg0Type, arg1Type));

        assertThatThrownBy(() -> toolExecutor.execute(request))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedError);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": 2}",
            "{\"arg0\": 2.0, \"arg1\": 2.0}",
            "{\"arg0\": 1.9, \"arg1\": 2.1}",
    })
    void should_execute_tool_function_with_parameters_of_type_double(String arguments) throws NoSuchMethodException {
        executeAndAssert(arguments, TestTool::doubles, "4.0");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": 2}",
            "{\"arg0\": 2.0, \"arg1\": 2.0}",
            "{\"arg0\": 1.9, \"arg1\": 2.1}",
    })
    void should_execute_tool_function_with_parameters_of_type_float(String arguments) throws NoSuchMethodException {
        executeAndAssert(arguments, TestTool::floats, "4.0");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": " + Float.MAX_VALUE + "}",
            "{\"arg0\": 2, \"arg1\": " + -Double.MAX_VALUE + "}"
    })
    void should_fail_when_argument_does_not_fit_into_float_type_with_tool_function(String arguments) throws NoSuchMethodException {
        executeAndExpectFailure(arguments, TestTool::floats, "is out of range for the float type");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": 2}",
            "{\"arg0\": 2.0, \"arg1\": 2.0}",
            "{\"arg0\": 1.9, \"arg1\": 2.1}",
    })
    void should_execute_tool_function_with_parameters_of_type_BigDecimal(String arguments) throws NoSuchMethodException {
        executeAndAssert(arguments, TestTool::bigDecimals, "4.0");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": 2}",
            "{\"arg0\": 2.0, \"arg1\": 2.0}"
    })
    void should_execute_tool_function_with_parameters_of_type_long(String arguments) throws NoSuchMethodException {
        executeAndAssert(arguments, TestTool::longs, "4");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": " + Double.MAX_VALUE + "}",
            "{\"arg0\": 2, \"arg1\": " + -Double.MAX_VALUE + "}"
    })
    void should_fail_when_argument_does_not_fit_into_long_type_with_tool_function(String arguments) throws NoSuchMethodException {
        executeAndExpectFailure(arguments, TestTool::longs, "is out of range for the long type");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": 2}",
            "{\"arg0\": 2.0, \"arg1\": 2.0}"
    })
    void should_execute_tool_function_with_parameters_of_type_int(String arguments) throws NoSuchMethodException {
        executeAndAssert(arguments, TestTool::ints, "4");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": " + Double.MAX_VALUE + "}",
            "{\"arg0\": 2, \"arg1\": " + -Double.MAX_VALUE + "}"
    })
    void should_fail_when_argument_does_not_fit_into_int_type_with_tool_function(String arguments) throws NoSuchMethodException {
        executeAndExpectFailure(arguments, TestTool::ints, "is out of range for the integer type");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": 2}",
            "{\"arg0\": 2.0, \"arg1\": 2.0}"
    })
    void should_execute_tool_function_with_parameters_of_type_short(String arguments) throws NoSuchMethodException {
        executeAndAssert(arguments, TestTool::shorts, "4");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": " + Double.MAX_VALUE + "}",
            "{\"arg0\": 2, \"arg1\": " + -Double.MAX_VALUE + "}"
    })
    void should_fail_when_argument_does_not_fit_into_short_type_with_tool_function(String arguments) throws NoSuchMethodException {
        executeAndExpectFailure(arguments, TestTool::shorts, "is out of range for the short type");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": 2}",
            "{\"arg0\": 2.0, \"arg1\": 2.0}"
    })
    void should_execute_tool_function_with_parameters_of_type_byte(String arguments) throws NoSuchMethodException {
        executeAndAssert(arguments, TestTool::bytes, "4");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": " + Double.MAX_VALUE + "}",
            "{\"arg0\": 2, \"arg1\": " + -Double.MAX_VALUE + "}"
    })
    void should_fail_when_argument_does_not_fit_into_byte_type_with_function(String arguments) throws NoSuchMethodException {
        executeAndExpectFailure(arguments, TestTool::bytes, "is out of range for the byte type");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": 2}",
            "{\"arg0\": 2.0, \"arg1\": 2.0}"
    })
    void should_execute_tool_function_with_parameters_of_type_BigInteger(String arguments) throws NoSuchMethodException {
        executeAndAssert(arguments, TestTool::bigIntegers, "4");
    }


    private <U1, U2, R> void executeAndAssert(String arguments, ToolBiFunction<TestTool, U1, U2, R> function, String expectedResult) throws NoSuchMethodException {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .arguments(arguments)
                .build();

        ToolExecutor toolExecutor = function.wrap(testTool);

        String result = toolExecutor.execute(request);

        assertThat(result).isEqualTo(expectedResult);
    }


    private <U1, U2, R> void executeAndExpectFailure(String arguments, ToolBiFunction<TestTool, U1, U2, R> function, String expectedError) throws NoSuchMethodException {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .arguments(arguments)
                .build();

        ToolExecutor toolExecutor = function.wrap(testTool);

        assertThatThrownBy(() -> toolExecutor.execute(request))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedError);
    }


    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": 2}",
            "{\"arg0\": 2.0, \"arg1\": 2.0}",
            "{\"arg0\": 1.9, \"arg1\": 2.1}",
    })
    void should_execute_tool_companion_function_with_parameters_of_type_double(String arguments) throws NoSuchMethodException {
        executeAndAssert(arguments, TestTool::staticDoubles, "4.0");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": 2}",
            "{\"arg0\": 2.0, \"arg1\": 2.0}",
            "{\"arg0\": 1.9, \"arg1\": 2.1}",
    })
    void should_execute_tool_companion_function_with_parameters_of_type_float(String arguments) throws NoSuchMethodException {
        executeAndAssert(arguments, TestTool::staticFloats, "4.0");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": " + Float.MAX_VALUE + "}",
            "{\"arg0\": 2, \"arg1\": " + -Double.MAX_VALUE + "}"
    })
    void should_fail_when_argument_does_not_fit_into_float_type_with_tool_companion_function(String arguments) throws NoSuchMethodException {
        executeAndExpectFailure(arguments, TestTool::staticFloats, "is out of range for the float type");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": 2}",
            "{\"arg0\": 2.0, \"arg1\": 2.0}",
            "{\"arg0\": 1.9, \"arg1\": 2.1}",
    })
    void should_execute_tool_companion_function_with_parameters_of_type_BigDecimal(String arguments) throws NoSuchMethodException {
        executeAndAssert(arguments, TestTool::staticBigDecimals, "4.0");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": 2}",
            "{\"arg0\": 2.0, \"arg1\": 2.0}"
    })
    void should_execute_tool_companion_function_with_parameters_of_type_long(String arguments) throws NoSuchMethodException {
        executeAndAssert(arguments, TestTool::staticLongs, "4");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": " + Double.MAX_VALUE + "}",
            "{\"arg0\": 2, \"arg1\": " + -Double.MAX_VALUE + "}"
    })
    void should_fail_when_argument_does_not_fit_into_long_type_with_tool_companion_function(String arguments) throws NoSuchMethodException {
        executeAndExpectFailure(arguments, TestTool::staticLongs, "is out of range for the long type");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": 2}",
            "{\"arg0\": 2.0, \"arg1\": 2.0}"
    })
    void should_execute_tool_companion_function_with_parameters_of_type_int(String arguments) throws NoSuchMethodException {
        executeAndAssert(arguments, TestTool::staticInts, "4");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": " + Double.MAX_VALUE + "}",
            "{\"arg0\": 2, \"arg1\": " + -Double.MAX_VALUE + "}"
    })
    void should_fail_when_argument_does_not_fit_into_int_type_with_tool_companion_function(String arguments) throws NoSuchMethodException {
        executeAndExpectFailure(arguments, TestTool::staticInts, "is out of range for the integer type");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": 2}",
            "{\"arg0\": 2.0, \"arg1\": 2.0}"
    })
    void should_execute_tool_companion_function_with_parameters_of_type_short(String arguments) throws NoSuchMethodException {
        executeAndAssert(arguments, TestTool::staticShorts, "4");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": " + Double.MAX_VALUE + "}",
            "{\"arg0\": 2, \"arg1\": " + -Double.MAX_VALUE + "}"
    })
    void should_fail_when_argument_does_not_fit_into_short_type_with_tool_companion_function(String arguments) throws NoSuchMethodException {
        executeAndExpectFailure(arguments, TestTool::staticShorts, "is out of range for the short type");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": 2}",
            "{\"arg0\": 2.0, \"arg1\": 2.0}"
    })
    void should_execute_tool_companion_function_with_parameters_of_type_byte(String arguments) throws NoSuchMethodException {
        executeAndAssert(arguments, TestTool::staticBytes, "4");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": " + Double.MAX_VALUE + "}",
            "{\"arg0\": 2, \"arg1\": " + -Double.MAX_VALUE + "}"
    })
    void should_fail_when_argument_does_not_fit_into_byte_type_with_tool_companion_function(String arguments) throws NoSuchMethodException {
        executeAndExpectFailure(arguments, TestTool::staticBytes, "is out of range for the byte type");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"arg0\": 2, \"arg1\": 2}",
            "{\"arg0\": 2.0, \"arg1\": 2.0}"
    })
    void should_execute_tool_companion_function_with_parameters_of_type_BigInteger(String arguments) throws NoSuchMethodException {
        executeAndAssert(arguments, TestTool::staticBigIntegers, "4");
    }


    private <U1, U2, R> void executeAndAssert(String arguments, ToolCompanionBiFunction<U1, U2, R> function, String expectedResult) throws NoSuchMethodException {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .arguments(arguments)
                .build();

        ToolExecutor toolExecutor = function.delegate();

        String result = toolExecutor.execute(request);

        assertThat(result).isEqualTo(expectedResult);
    }


    private <U1, U2, R> void executeAndExpectFailure(String arguments, ToolCompanionBiFunction<U1, U2, R> function, String expectedError) throws NoSuchMethodException {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .arguments(arguments)
                .build();

        ToolExecutor toolExecutor = function.delegate();

        assertThatThrownBy(() -> toolExecutor.execute(request))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedError);
    }
}