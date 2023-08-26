package dev.langchain4j.agent.tool;

import dev.langchain4j.agent.tool.lambda.ToolSerializedCompanionFunction;
import dev.langchain4j.agent.tool.lambda.ToolSerializedFunction;
import dev.langchain4j.agent.tool.lambda.LambdaMeta;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ToolFunctionTest {
    static class Subject {
        public int m1(String a) { return 0; }
        public Integer m1_boxed(String a) { return 0; }
        public Integer m1_byte(byte a) { return 0; }
        public Integer m1_char(char a) { return 0; }
        public Integer m1_short(short a) { return 0; }
        public Integer m1_int(int a) { return 0; }
        public Integer m1_long(long a) { return 0; }
        public Integer m1_float(float a) { return 0; }
        public Integer m1_double(double a) { return 0; }

        public Integer m2_byte(Byte a, byte b) { return 0; }
        public Integer m2_char(Character a, char b) { return 0; }
        public Integer m2_short(Short a, short b) { return 0; }
        public Integer m2_int(Integer a, int b) { return 0; }
        public Integer m2_long(Long a, long b) { return 0; }
        public Integer m2_float(Float a, float b) { return 0; }
        public Integer m2_double(Double a, double b) { return 0; }

        public Integer m3_byte(String a, byte b, Byte c) { return 0; }
//      Reference to 'm3_byte' is ambiguous, both 'm3_byte(String, byte, Byte)' and 'm3_byte(String, Byte, byte)' match
//      public Integer m3_byte(String a, Byte b, byte c) { return 1; }
        public Integer m3_char(String a, char b, Character c) { return 0; }
        public Integer m3_short(String a, short b, Short c) { return 0; }
        public Integer m3_int(String a, int b, Integer c) { return 0; }
        public Integer m3_long(String a, long b, Long c) { return 0; }
        public Integer m3_float(String a, float b, Float c) { return 0; }
        public Integer m3_double(String a, double b, Double c) { return 0; }
    }

    @ParameterizedTest
    @MethodSource({
            "test_function",
            "test_bi_function",
            "test_ti_function",
    })
    void test_instance_function(ToolSerializedFunction func, Method method) {
        final LambdaMeta meta = LambdaMeta.extract(func);

        assertEquals(meta.getImplClass(), Subject.class);
        assertEquals(meta.getImplMethod(), method);
    }


    static Stream<Arguments> test_function() throws NoSuchMethodException {
        return Stream.of(
                Arguments.of(
                        (ToolFunction<Subject, String, Integer>) Subject::m1,
                        Subject.class.getDeclaredMethod("m1", String.class)
                ),
                Arguments.of(
                        (ToolFunction<Subject, String, Integer>) Subject::m1_boxed,
                        Subject.class.getDeclaredMethod("m1_boxed", String.class)
                ),
                Arguments.of(
                        (ToolFunction<Subject, Byte, Integer>) Subject::m1_byte,
                        Subject.class.getDeclaredMethod("m1_byte", byte.class)
                ),
                Arguments.of(
                        (ToolFunction<Subject, Character, Integer>)  Subject::m1_char,
                        Subject.class.getDeclaredMethod("m1_char", char.class)
                ),
                Arguments.of(
                        (ToolFunction<Subject, Short, Integer>)  Subject::m1_short,
                        Subject.class.getDeclaredMethod("m1_short", short.class)
                ),
                Arguments.of(
                        (ToolFunction<Subject, Integer, Integer>)  Subject::m1_int,
                        Subject.class.getDeclaredMethod("m1_int", int.class)
                ),
                Arguments.of(
                        (ToolFunction<Subject, Long, Integer>)  Subject::m1_long,
                        Subject.class.getDeclaredMethod("m1_long", long.class)
                ),
                Arguments.of(
                        (ToolFunction<Subject, Float, Integer>)  Subject::m1_float,
                        Subject.class.getDeclaredMethod("m1_float", float.class)
                ),
                Arguments.of(
                        (ToolFunction<Subject, Double, Integer>)  Subject::m1_double,
                        Subject.class.getDeclaredMethod("m1_double", double.class)
                )
        );
    }

    static Stream<Arguments> test_bi_function() throws NoSuchMethodException {
        return Stream.of(
                Arguments.of(
                        (ToolBiFunction<Subject, Byte, Byte, Integer>) Subject::m2_byte,
                        Subject.class.getDeclaredMethod("m2_byte", Byte.class, byte.class)
                ),
                Arguments.of(
                        (ToolBiFunction<Subject, Character, Character, Integer>)  Subject::m2_char,
                        Subject.class.getDeclaredMethod("m2_char", Character.class, char.class)
                ),
                Arguments.of(
                        (ToolBiFunction<Subject, Short, Short, Integer>)  Subject::m2_short,
                        Subject.class.getDeclaredMethod("m2_short", Short.class, short.class)
                ),
                Arguments.of(
                        (ToolBiFunction<Subject, Integer, Integer, Integer>)  Subject::m2_int,
                        Subject.class.getDeclaredMethod("m2_int", Integer.class, int.class)
                ),
                Arguments.of(
                        (ToolBiFunction<Subject, Long, Long, Integer>)  Subject::m2_long,
                        Subject.class.getDeclaredMethod("m2_long", Long.class, long.class)
                ),
                Arguments.of(
                        (ToolBiFunction<Subject, Float, Float, Integer>)  Subject::m2_float,
                        Subject.class.getDeclaredMethod("m2_float", Float.class, float.class)
                ),
                Arguments.of(
                        (ToolBiFunction<Subject, Double, Double, Integer>)  Subject::m2_double,
                        Subject.class.getDeclaredMethod("m2_double", Double.class, double.class)
                )
        );
    }

    static Stream<Arguments> test_ti_function() throws NoSuchMethodException {
        return Stream.of(
                Arguments.of(
                        (ToolTiFunction<Subject, String, Byte, Byte, Integer>) Subject::m3_byte,
                        Subject.class.getDeclaredMethod("m3_byte", String.class, byte.class, Byte.class)
                ),
                Arguments.of(
                        (ToolTiFunction<Subject, String, Character, Character, Integer>) Subject::m3_char,
                        Subject.class.getDeclaredMethod("m3_char", String.class, char.class, Character.class)
                ),
                Arguments.of(
                        (ToolTiFunction<Subject, String, Short, Short, Integer>) Subject::m3_short,
                        Subject.class.getDeclaredMethod("m3_short", String.class, short.class, Short.class)
                ),
                Arguments.of(
                        (ToolTiFunction<Subject, String, Integer, Integer, Integer>) Subject::m3_int,
                        Subject.class.getDeclaredMethod("m3_int", String.class, int.class, Integer.class)
                ),
                Arguments.of(
                        (ToolTiFunction<Subject, String, Long, Long, Integer>) Subject::m3_long,
                        Subject.class.getDeclaredMethod("m3_long", String.class, long.class, Long.class)
                ),
                Arguments.of(
                        (ToolTiFunction<Subject, String, Float, Float, Integer>) Subject::m3_float,
                        Subject.class.getDeclaredMethod("m3_float", String.class, float.class, Float.class)
                ),
                Arguments.of(
                        (ToolTiFunction<Subject, String, Double, Double, Integer>) Subject::m3_double,
                        Subject.class.getDeclaredMethod("m3_double", String.class, double.class, Double.class)
                )
        );
    }

    static class StaticSubject {
        public static int m1(String a) { return 0; }
        public static Integer m1_boxed(String a) { return 0; }
        public static Integer m1_byte(byte a) { return 0; }
        public static Integer m1_char(char a) { return 0; }
        public static Integer m1_short(short a) { return 0; }
        public static Integer m1_int(int a) { return 0; }
        public static Integer m1_long(long a) { return 0; }
        public static Integer m1_float(float a) { return 0; }
        public static Integer m1_double(double a) { return 0; }

        public static Integer m2_byte(Byte a, byte b) { return 0; }
        public static Integer m2_char(Character a, char b) { return 0; }
        public static Integer m2_short(Short a, short b) { return 0; }
        public static Integer m2_int(Integer a, int b) { return 0; }
        public static Integer m2_long(Long a, long b) { return 0; }
        public static Integer m2_float(Float a, float b) { return 0; }
        public static Integer m2_double(Double a, double b) { return 0; }

        public static Integer m3_byte(String a, byte b, Byte c) { return 0; }
//      Reference to 'm3_byte' is ambiguous, both 'm3_byte(String, byte, Byte)' and 'm3_byte(String, Byte, byte)' match
//      public static Integer m3_byte(String a, Byte b, byte c) { return 1; }
        public static Integer m3_char(String a, char b, Character c) { return 0; }
        public static Integer m3_short(String a, short b, Short c) { return 0; }
        public static Integer m3_int(String a, int b, Integer c) { return 0; }
        public static Integer m3_long(String a, long b, Long c) { return 0; }
        public static Integer m3_float(String a, float b, Float c) { return 0; }
        public static Integer m3_double(String a, double b, Double c) { return 0; }
    }

    @ParameterizedTest
    @MethodSource({
            "test_static_function",
            "test_static_bi_function",
            "test_static_ti_function",
    })
    void test_static_function(ToolSerializedCompanionFunction func, Method method) {
        final LambdaMeta meta = LambdaMeta.extract(func);

        assertEquals(meta.getImplClass(), StaticSubject.class);
        assertEquals(meta.getImplMethod(), method);
    }

    static Stream<Arguments> test_static_function() throws NoSuchMethodException {
        return Stream.of(
                Arguments.of(
                        (ToolCompanionFunction<String, Integer>) StaticSubject::m1,
                        StaticSubject.class.getDeclaredMethod("m1", String.class)
                ),
                Arguments.of(
                        (ToolCompanionFunction<String, Integer>) StaticSubject::m1_boxed,
                        StaticSubject.class.getDeclaredMethod("m1_boxed", String.class)
                ),
                Arguments.of(
                        (ToolCompanionFunction<Byte, Integer>) StaticSubject::m1_byte,
                        StaticSubject.class.getDeclaredMethod("m1_byte", byte.class)
                ),
                Arguments.of(
                        (ToolCompanionFunction<Character, Integer>)  StaticSubject::m1_char,
                        StaticSubject.class.getDeclaredMethod("m1_char", char.class)
                ),
                Arguments.of(
                        (ToolCompanionFunction<Short, Integer>)  StaticSubject::m1_short,
                        StaticSubject.class.getDeclaredMethod("m1_short", short.class)
                ),
                Arguments.of(
                        (ToolCompanionFunction<Integer, Integer>)  StaticSubject::m1_int,
                        StaticSubject.class.getDeclaredMethod("m1_int", int.class)
                ),
                Arguments.of(
                        (ToolCompanionFunction<Long, Integer>)  StaticSubject::m1_long,
                        StaticSubject.class.getDeclaredMethod("m1_long", long.class)
                ),
                Arguments.of(
                        (ToolCompanionFunction<Float, Integer>)  StaticSubject::m1_float,
                        StaticSubject.class.getDeclaredMethod("m1_float", float.class)
                ),
                Arguments.of(
                        (ToolCompanionFunction<Double, Integer>)  StaticSubject::m1_double,
                        StaticSubject.class.getDeclaredMethod("m1_double", double.class)
                )
        );
    }

    static Stream<Arguments> test_static_bi_function() throws NoSuchMethodException {
        return Stream.of(
                Arguments.of(
                        (ToolCompanionBiFunction<Byte, Byte, Integer>) StaticSubject::m2_byte,
                        StaticSubject.class.getDeclaredMethod("m2_byte", Byte.class, byte.class)
                ),
                Arguments.of(
                        (ToolCompanionBiFunction<Character, Character, Integer>)  StaticSubject::m2_char,
                        StaticSubject.class.getDeclaredMethod("m2_char", Character.class, char.class)
                ),
                Arguments.of(
                        (ToolCompanionBiFunction<Short, Short, Integer>)  StaticSubject::m2_short,
                        StaticSubject.class.getDeclaredMethod("m2_short", Short.class, short.class)
                ),
                Arguments.of(
                        (ToolCompanionBiFunction<Integer, Integer, Integer>)  StaticSubject::m2_int,
                        StaticSubject.class.getDeclaredMethod("m2_int", Integer.class, int.class)
                ),
                Arguments.of(
                        (ToolCompanionBiFunction<Long, Long, Integer>)  StaticSubject::m2_long,
                        StaticSubject.class.getDeclaredMethod("m2_long", Long.class, long.class)
                ),
                Arguments.of(
                        (ToolCompanionBiFunction<Float, Float, Integer>)  StaticSubject::m2_float,
                        StaticSubject.class.getDeclaredMethod("m2_float", Float.class, float.class)
                ),
                Arguments.of(
                        (ToolCompanionBiFunction<Double, Double, Integer>)  StaticSubject::m2_double,
                        StaticSubject.class.getDeclaredMethod("m2_double", Double.class, double.class)
                )
        );
    }

    static Stream<Arguments> test_static_ti_function() throws NoSuchMethodException {
        return Stream.of(
                Arguments.of(
                        (ToolCompanionTiFunction<String, Byte, Byte, Integer>) StaticSubject::m3_byte,
                        StaticSubject.class.getDeclaredMethod("m3_byte", String.class, byte.class, Byte.class)
                ),
                Arguments.of(
                        (ToolCompanionTiFunction<String, Character, Character, Integer>) StaticSubject::m3_char,
                        StaticSubject.class.getDeclaredMethod("m3_char", String.class, char.class, Character.class)
                ),
                Arguments.of(
                        (ToolCompanionTiFunction<String, Short, Short, Integer>) StaticSubject::m3_short,
                        StaticSubject.class.getDeclaredMethod("m3_short", String.class, short.class, Short.class)
                ),
                Arguments.of(
                        (ToolCompanionTiFunction<String, Integer, Integer, Integer>) StaticSubject::m3_int,
                        StaticSubject.class.getDeclaredMethod("m3_int", String.class, int.class, Integer.class)
                ),
                Arguments.of(
                        (ToolCompanionTiFunction<String, Long, Long, Integer>) StaticSubject::m3_long,
                        StaticSubject.class.getDeclaredMethod("m3_long", String.class, long.class, Long.class)
                ),
                Arguments.of(
                        (ToolCompanionTiFunction<String, Float, Float, Integer>) StaticSubject::m3_float,
                        StaticSubject.class.getDeclaredMethod("m3_float", String.class, float.class, Float.class)
                ),
                Arguments.of(
                        (ToolCompanionTiFunction<String, Double, Double, Integer>) StaticSubject::m3_double,
                        StaticSubject.class.getDeclaredMethod("m3_double", String.class, double.class, Double.class)
                )
        );
    }

    interface ToolFunction<TOOL, U, R> extends BiFunction<TOOL, U, R>, ToolSerializedFunction {
    }

    interface ToolBiFunction<TOOL, U1, U2, R> extends ToolSerializedFunction {
        R apply(TOOL tool, U1 u1, U2 u2);
    }

    interface ToolTiFunction<TOOL, U1, U2, U3, R> extends ToolSerializedFunction {
        R apply(TOOL tool, U1 u1, U2 u2, U3 u3);
    }

    interface ToolCompanionFunction<T, R> extends Function<T, R>, ToolSerializedCompanionFunction {
    }

    interface ToolCompanionBiFunction<T, U, R> extends BiFunction<T, U, R>, ToolSerializedCompanionFunction {
    }

    interface ToolCompanionTiFunction<T, U1, U2, R> extends ToolSerializedCompanionFunction {
        R apply(T t, U1 u1, U2 u2);
    }
}
