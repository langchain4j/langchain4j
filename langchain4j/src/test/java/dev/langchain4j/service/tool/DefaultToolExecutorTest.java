package dev.langchain4j.service.tool;

import com.google.gson.reflect.TypeToken;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolMemoryId;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.langchain4j.service.tool.DefaultToolExecutor.coerceArgument;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;

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
            Double Double2P,
            List<Integer> integerList,
            Set<ExampleEnum> enumSet,
            Map<String, Integer> map
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
                Double.class,
                List.class,
                Set.class,
                Map.class
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
        arguments.put("arg18", asList(1.0, 2.0, 3.0));
        arguments.put("arg19", new HashSet<>(asList(ExampleEnum.A, ExampleEnum.B)));
        arguments.put("arg20", singletonMap("A", 1.0));

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
                2.2,
                asList(1, 2, 3),
                new HashSet<>(asList(ExampleEnum.A, ExampleEnum.B)),
                singletonMap("A", 1)
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

    record Person(

            String name,
            int age) {
    }

    @Test
    public void test_coerceArgument() {

        Map<String, Object> personMap = new HashMap<>();
        personMap.put("name", "Klaus");
        personMap.put("age", 42);
        assertThat(coerceArgument(personMap, "arg", Person.class, null)).isEqualTo(new Person("Klaus", 42));

        assertThat(coerceArgument("abc", "arg", String.class, null)).isEqualTo("abc");

        assertThat(coerceArgument("A", "arg", ExampleEnum.class, null)).isEqualTo(ExampleEnum.A);
        assertThat(coerceArgument(ExampleEnum.A, "arg", ExampleEnum.class, null)).isEqualTo(ExampleEnum.A);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument("D", "arg", ExampleEnum.class, null))
                .withMessageContaining("Argument \"arg\" is not a valid enum value for");

        assertThat(coerceArgument(true, "arg", boolean.class, null)).isEqualTo(true);
        assertThat(coerceArgument(Boolean.FALSE, "arg", boolean.class, null)).isEqualTo(false);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument("true", "arg", boolean.class, null))
                .withMessageContaining("Argument \"arg\" is not convertable to boolean, got java.lang.String: <true>");

        assertThat(coerceArgument(1.5, "arg", double.class, null)).isEqualTo(1.5);
        assertThat(coerceArgument(1.5, "arg", Double.class, null)).isEqualTo(1.5);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument("abc", "arg", double.class, null))
                .withMessageContaining("Argument \"arg\" is not convertable to double, got java.lang.String: <abc>");

        assertThat(coerceArgument(1.5, "arg", float.class, null)).isEqualTo(1.5f);
        assertThat(coerceArgument(1.5, "arg", Float.class, null)).isEqualTo(1.5f);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument(1.5 * ((double) Float.MAX_VALUE), "arg", float.class, null))
                .withMessageContaining("Argument \"arg\" is out of range for float:");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument(-1.5 * ((double) Float.MAX_VALUE), "arg", float.class, null))
                .withMessageContaining("Argument \"arg\" is out of range for float:");

        assertThat(coerceArgument(1.0, "arg", int.class, null)).isEqualTo(1);
        assertThat(coerceArgument(Integer.MAX_VALUE, "arg", int.class, null)).isEqualTo(Integer.MAX_VALUE);
        assertThat(coerceArgument(Integer.MIN_VALUE, "arg", Integer.class, null)).isEqualTo(Integer.MIN_VALUE);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument(1.5, "arg", int.class, null))
                .withMessageContaining("has non-integer value");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument(Integer.MAX_VALUE + 1.0, "arg", int.class, null))
                .withMessageContaining("Argument \"arg\" is out of range for int:");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument(Integer.MIN_VALUE - 1.0, "arg", Integer.class, null))
                .withMessageContaining("Argument \"arg\" is out of range for java.lang.Integer:");

        assertThat(coerceArgument(1.0, "arg", long.class, null)).isEqualTo(1L);
        assertThat(coerceArgument(Long.MAX_VALUE, "arg", long.class, null)).isEqualTo(Long.MAX_VALUE);
        assertThat(coerceArgument(Long.MIN_VALUE, "arg", Long.class, null)).isEqualTo(Long.MIN_VALUE);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument(1.5, "arg", long.class, null))
                .withMessageContaining("has non-integer value");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument(1.5 * ((double) Long.MAX_VALUE), "arg", long.class, null))
                .withMessageContaining("Argument \"arg\" is out of range for long:");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument(1.5 * ((double) Long.MIN_VALUE), "arg", Long.class, null))
                .withMessageContaining("Argument \"arg\" is out of range for java.lang.Long:");

        assertThat(coerceArgument(1.0, "arg", short.class, null)).isEqualTo((short) 1);
        assertThat(coerceArgument(Short.MAX_VALUE, "arg", short.class, null)).isEqualTo(Short.MAX_VALUE);
        assertThat(coerceArgument(Short.MIN_VALUE, "arg", Short.class, null)).isEqualTo(Short.MIN_VALUE);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument(1.5, "arg", short.class, null))
                .withMessageContaining("has non-integer value");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument(Short.MAX_VALUE + 1.0, "arg", short.class, null))
                .withMessageContaining("Argument \"arg\" is out of range for short:");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument(Short.MIN_VALUE - 1.0, "arg", Short.class, null))
                .withMessageContaining("Argument \"arg\" is out of range for java.lang.Short:");

        assertThat(coerceArgument(1.0, "arg", byte.class, null)).isEqualTo((byte) 1);
        assertThat(coerceArgument(Byte.MAX_VALUE, "arg", byte.class, null)).isEqualTo(Byte.MAX_VALUE);
        assertThat(coerceArgument(Byte.MIN_VALUE, "arg", Byte.class, null)).isEqualTo(Byte.MIN_VALUE);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument(1.5, "arg", byte.class, null))
                .withMessageContaining("has non-integer value");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument(Byte.MAX_VALUE + 1.0, "arg", byte.class, null))
                .withMessageContaining("Argument \"arg\" is out of range for byte:");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument(Byte.MIN_VALUE - 1.0, "arg", Byte.class, null))
                .withMessageContaining("Argument \"arg\" is out of range for java.lang.Byte:");

        assertThat(coerceArgument(1.5, "arg", BigDecimal.class, null)).isEqualTo(BigDecimal.valueOf(1.5));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument("abc", "arg", BigDecimal.class, null))
                .withMessageContaining("Argument \"arg\" is not convertable to java.math.BigDecimal, got java.lang.String: <abc>");

        assertThat(coerceArgument(1, "arg", BigInteger.class, null)).isEqualTo(BigInteger.valueOf(1));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument(1.5, "arg", BigInteger.class, null))
                .withMessageContaining("has non-integer value");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> coerceArgument("abc", "arg", BigInteger.class, null))
                .withMessageContaining("Argument \"arg\" is not convertable to java.math.BigInteger, got java.lang.String: <abc>");

        assertThat(coerceArgument(asList(1.0, 2.0, 3.0), "arg", List.class, new TypeToken<List<Integer>>() {
        }.getType())).isEqualTo(asList(1, 2, 3));

        assertThat(coerceArgument(new HashSet<>(asList("A", "B")), "arg", List.class, new TypeToken<Set<ExampleEnum>>() {
        }.getType())).isEqualTo(new HashSet<>(asList(ExampleEnum.A, ExampleEnum.B)));

        assertThat(coerceArgument(singletonMap("A", 1.0), "arg", List.class, new TypeToken<Map<String, Integer>>() {
        }.getType())).isEqualTo(singletonMap("A", 1));
    }

    private static class TestTool {

        @Tool
        public int addOne(int num) {
            return num + 1;
        }
    }

    private static class TestToolReturnDirectly {

        @Tool(returnDirectly = true)
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
    public void test_get_return_directly_true() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("1")
                .name("addOne")
                .arguments("{ \"arg0\": 2 }")
                .build();

        DefaultToolExecutor toolExecutor = new DefaultToolExecutor(new TestToolReturnDirectly(), request);

        assertThat(toolExecutor.isReturnDirectly()).isTrue();
    }

    @Test
    public void test_get_return_directly_false() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("1")
                .name("addOne")
                .arguments("{ \"arg0\": 2 }")
                .build();

        DefaultToolExecutor toolExecutor = new DefaultToolExecutor(new TestTool(), request);

        assertThat(toolExecutor.isReturnDirectly()).isFalse();
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
    public void should_not_execute_tool_with_null_execution_request() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new DefaultToolExecutor(new TestTool(), (ToolExecutionRequest) null));

    }

    private static class PersonTool {

        @Tool
        public Person save(Person arg) {
            assert arg != null;
            return arg;
        }

        @Tool
        public Person[] saveArray(Person[] arg) {
            assert arg != null;
            assert arg.length == 2;
            assert arg[0].getClass() == Person.class;
            assert arg[0].name.equals("Klaus");
            assert arg[0].age == 42;
            assert arg[1].getClass() == Person.class;
            assert arg[1].name.equals("Peter");
            assert arg[1].age == 43;
            return arg;
        }

        @Tool
        public List<Person> saveList(List<Person> personList) {
            assert personList != null;
            assert personList.size() == 2;
            personList.forEach(person -> {
                assert person.getClass() == Person.class;
            });
            assert personList.get(0).name.equals("Klaus");
            assert personList.get(0).age == 42;
            assert personList.get(1).name.equals("Peter");
            assert personList.get(1).age == 43;
            return personList;
        }

        @Tool
        public Set<Person> saveSet(Set<Person> personSet) {
            assert personSet != null;
            assert personSet.size() == 2;
            personSet.forEach(person -> {
                assert person.getClass() == Person.class;
            });
            assert personSet.stream().anyMatch(person -> person.name.equals("Klaus") && person.age == 42);
            assert personSet.stream().anyMatch(person -> person.name.equals("Peter") && person.age == 43);
            return personSet;
        }

        @Tool
        public Map<String, Person> saveMap(Map<String, Person> idPersonMap) {
            assert idPersonMap != null;
            assert idPersonMap.size() == 2;
            idPersonMap.forEach((id, person) -> {
                assert id.getClass() == String.class;
                assert person.getClass() == Person.class;
            });
            assert idPersonMap.get("p1").name.equals("Klaus");
            assert idPersonMap.get("p1").age == 42;
            assert idPersonMap.get("p2").name.equals("Peter");
            assert idPersonMap.get("p2").age == 43;
            return idPersonMap;
        }
    }

    @Test
    public void should_execute_tools_with_collection() {

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("1")
                .name("save")
                .arguments("{ \"arg0\": {\"name\": \"Klaus\", \"age\": 42} }")
                .build();

        DefaultToolExecutor toolExecutor = new DefaultToolExecutor(new PersonTool(), request);

        String result = toolExecutor.execute(request, "DEFAULT");
        assertThat(result).isEqualTo("{\n" +
                "  \"name\": \"Klaus\",\n" +
                "  \"age\": 42\n" +
                "}");

        ToolExecutionRequest request2 = ToolExecutionRequest.builder()
                .id("2")
                .name("saveList")
                .arguments("{ \"arg0\": [ {\"name\": \"Klaus\", \"age\": 42}, {\"name\": \"Peter\", \"age\": 43} ] }")
                .build();
        DefaultToolExecutor toolExecutor2 = new DefaultToolExecutor(new PersonTool(), request2);
        String result2 = toolExecutor2.execute(request2, "DEFAULT");
        assertThat(result2).isEqualTo("[\n" +
                "  {\n" +
                "    \"name\": \"Klaus\",\n" +
                "    \"age\": 42\n" +
                "  },\n" +
                "  {\n" +
                "    \"name\": \"Peter\",\n" +
                "    \"age\": 43\n" +
                "  }\n" +
                "]");

        ToolExecutionRequest request3 = ToolExecutionRequest.builder()
                .id("3")
                .name("saveSet")
                .arguments("{ \"arg0\": [ {\"name\": \"Klaus\", \"age\": 42}, {\"name\": \"Peter\", \"age\": 43} ] }")
                .build();
        DefaultToolExecutor toolExecutor3 = new DefaultToolExecutor(new PersonTool(), request3);
        String result3 = toolExecutor3.execute(request3, "DEFAULT");
        assertThat(result3).isEqualTo("[\n" +
                "  {\n" +
                "    \"name\": \"Klaus\",\n" +
                "    \"age\": 42\n" +
                "  },\n" +
                "  {\n" +
                "    \"name\": \"Peter\",\n" +
                "    \"age\": 43\n" +
                "  }\n" +
                "]");

        ToolExecutionRequest request4 = ToolExecutionRequest.builder()
                .id("4")
                .name("saveMap")
                .arguments("{ \"arg0\": { \"p1\" : {\"name\": \"Klaus\", \"age\": 42}, \"p2\" : {\"name\": \"Peter\", \"age\": 43} } }")
                .build();
        DefaultToolExecutor toolExecutor4 = new DefaultToolExecutor(new PersonTool(), request4);
        String result4 = toolExecutor4.execute(request4, "DEFAULT");
        assertThat(result4).isEqualTo("{\n" +
                "  \"p1\": {\n" +
                "    \"name\": \"Klaus\",\n" +
                "    \"age\": 42\n" +
                "  },\n" +
                "  \"p2\": {\n" +
                "    \"name\": \"Peter\",\n" +
                "    \"age\": 43\n" +
                "  }\n" +
                "}");

        ToolExecutionRequest request5 = ToolExecutionRequest.builder()
                .id("5")
                .name("saveArray")
                .arguments("{ \"arg0\": [ {\"name\": \"Klaus\", \"age\": 42}, {\"name\": \"Peter\", \"age\": 43} ] }")
                .build();
        DefaultToolExecutor toolExecutor5 = new DefaultToolExecutor(new PersonTool(), request5);
        String result5 = toolExecutor5.execute(request5, "DEFAULT");
        assertThat(result5).isEqualTo("[\n" +
                "  {\n" +
                "    \"name\": \"Klaus\",\n" +
                "    \"age\": 42\n" +
                "  },\n" +
                "  {\n" +
                "    \"name\": \"Peter\",\n" +
                "    \"age\": 43\n" +
                "  }\n" +
                "]");
    }
}
