package dev.langchain4j.service.tool;

import static dev.langchain4j.service.tool.DefaultToolExecutor.coerceArgument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.internal.Json;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Values inside an object, collection, map or array argument get the same checks as top-level parameters:
 * a missing primitive is reported, and an integer accepts only integral numbers.
 */
class DefaultToolExecutorNestedArgumentsTest {

    record Order(String item, int quantity, double unitPrice, boolean express, Integer boxedQuantity) {}

    record Customer(String name, Order lastOrder, List<Order> orders) {}

    static class Optionals {
        @JsonProperty(required = false)
        int optionalCount;

        @JsonProperty("renamed_count")
        int renamedCount; // @JsonProperty without required = true: not required in the tool schema either

        @JsonProperty(value = "required_count", required = true)
        int requiredCount;

        @JsonIgnore
        int ignoredCount;

        static int staticCount;

        transient int transientCount;
    }

    static class Base {
        long id;
    }

    static class Derived extends Base {
        String label;
    }

    static class Ids {
        BigInteger big;
        short small;
        byte tiny;
        long[] longs;
    }

    static class OrderTool {
        final List<Order> placed = new ArrayList<>();

        @Tool
        String placeOrder(@P(value = "the order", name = "order") Order order) {
            placed.add(order);
            return "ok";
        }
    }

    // ---------- unchanged behaviour ----------

    @Test
    void should_bind_a_complete_object() {
        Object order = coerce("""
                {"item": "pen", "quantity": 3, "unitPrice": 1.5, "express": true, "boxedQuantity": 4}
                """, Order.class);
        assertThat(order).isEqualTo(new Order("pen", 3, 1.5, true, 4));
    }

    @ParameterizedTest
    @ValueSource(strings = {"3", "3.0", "-3", "0"})
    void should_accept_integral_numbers_for_nested_integers(String quantity) {
        Object order = coerce(
                "{\"item\": \"pen\", \"quantity\": " + quantity + ", \"unitPrice\": 1.5, \"express\": true}",
                Order.class);
        assertThat(((Order) order).quantity()).isEqualTo((int) Double.parseDouble(quantity));
    }

    @Test
    void should_keep_null_for_missing_object_fields() {
        // missing non-primitive fields stay null, as documented on @P for 1.x
        Object order = coerce("{\"quantity\": 3, \"unitPrice\": 1.5, \"express\": false}", Order.class);
        assertThat(order).isEqualTo(new Order(null, 3, 1.5, false, null));
    }

    @Test
    void should_accept_a_fraction_for_a_nested_double() {
        Object order =
                coerce("{\"item\": \"pen\", \"quantity\": 3, \"unitPrice\": 2.75, \"express\": true}", Order.class);
        assertThat(((Order) order).unitPrice()).isEqualTo(2.75);
    }

    @Test
    void should_skip_optional_ignored_static_and_transient_fields() {
        Object value = coerce("{\"required_count\": 7}", Optionals.class);
        assertThat(((Optionals) value).requiredCount).isEqualTo(7);
        assertThat(((Optionals) value).optionalCount).isZero();
        assertThat(((Optionals) value).renamedCount).isZero();
    }

    // ---------- newly reported ----------

    @Test
    void should_report_a_fraction_for_a_nested_int() {
        assertThatThrownBy(() -> coerce(
                        "{\"item\": \"pen\", \"quantity\": 2.7, \"unitPrice\": 1.5, \"express\": true}", Order.class))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Argument \"order.quantity\" has non-integer value for int: <2.7>");
    }

    @Test
    void should_report_a_fraction_for_a_nested_boxed_integer() {
        assertThatThrownBy(() -> coerce(
                        "{\"item\": \"pen\", \"quantity\": 2, \"unitPrice\": 1.5, \"express\": true, \"boxedQuantity\": 4.5}",
                        Order.class))
                .hasMessage("Argument \"order.boxedQuantity\" has non-integer value for java.lang.Integer: <4.5>");
    }

    @Test
    void should_report_a_missing_nested_primitive() {
        assertThatThrownBy(() -> coerce("{\"item\": \"pen\", \"unitPrice\": 1.5, \"express\": true}", Order.class))
                .hasMessage("Required argument \"order.quantity\" of type int is missing");
    }

    @Test
    void should_report_a_null_nested_primitive() {
        assertThatThrownBy(() -> coerce(
                        "{\"item\": \"pen\", \"quantity\": null, \"unitPrice\": 1.5, \"express\": true}", Order.class))
                .hasMessage("Required argument \"order.quantity\" of type int is missing");
    }

    @Test
    void should_report_an_empty_object() {
        assertThatThrownBy(() -> coerce("{}", Order.class))
                .hasMessage("Required argument \"order.quantity\" of type int is missing");
    }

    @Test
    void should_report_missing_renamed_and_inherited_primitives() {
        assertThatThrownBy(() -> coerce("{}", Optionals.class))
                .hasMessage("Required argument \"order.required_count\" of type int is missing");
        assertThatThrownBy(() -> coerce("{\"label\": \"x\"}", Derived.class))
                .hasMessage("Required argument \"order.id\" of type long is missing");
    }

    @Test
    void should_report_values_at_depth() {
        String customer = """
                {"name": "Ann",
                 "lastOrder": {"item": "pen", "quantity": 1, "unitPrice": 1.5, "express": true},
                 "orders": [{"item": "pen", "quantity": 1, "unitPrice": 1.5, "express": true},
                            {"item": "ink", "unitPrice": 9.0, "express": false}]}
                """;
        assertThatThrownBy(() -> coerce(customer, Customer.class))
                .hasMessage("Required argument \"order.orders[1].quantity\" of type int is missing");
    }

    @Test
    void should_check_collections_maps_and_arrays_of_objects() throws Exception {
        Method method = Holder.class.getDeclaredMethod("all", List.class, Set.class, Map.class, Order[].class);

        Object list = Json.fromJson(
                "[{\"item\": \"a\", \"quantity\": 1.5, \"unitPrice\": 1, \"express\": true}]", Object.class);
        assertThatThrownBy(() -> coerceArgument(list, "list", List.class, method.getGenericParameterTypes()[0]))
                .hasMessage("Argument \"list[0].quantity\" has non-integer value for int: <1.5>");

        Object set = Json.fromJson("[{\"item\": \"a\", \"unitPrice\": 1, \"express\": true}]", Object.class);
        assertThatThrownBy(() -> coerceArgument(set, "set", Set.class, method.getGenericParameterTypes()[1]))
                .hasMessage("Required argument \"set[0].quantity\" of type int is missing");

        Object map = Json.fromJson(
                "{\"k1\": {\"item\": \"a\", \"quantity\": 2.2, \"unitPrice\": 1, \"express\": true}}", Object.class);
        assertThatThrownBy(() -> coerceArgument(map, "map", Map.class, method.getGenericParameterTypes()[2]))
                .hasMessage("Argument \"map.k1.quantity\" has non-integer value for int: <2.2>");

        Object array = Json.fromJson("[{\"item\": \"a\", \"quantity\": 1, \"unitPrice\": 1}]", Object.class);
        assertThatThrownBy(() -> coerceArgument(array, "array", Order[].class, Order[].class))
                .hasMessage("Required argument \"array[0].express\" of type boolean is missing");
    }

    @Test
    void should_check_big_integer_short_byte_and_primitive_arrays() {
        assertThatThrownBy(() -> coerce("{\"big\": 1.5, \"small\": 1, \"tiny\": 1}", Ids.class))
                .hasMessage("Argument \"order.big\" has non-integer value for java.math.BigInteger: <1.5>");
        assertThatThrownBy(() -> coerce("{\"small\": 1.5, \"tiny\": 1}", Ids.class))
                .hasMessage("Argument \"order.small\" has non-integer value for short: <1.5>");
        assertThatThrownBy(() -> coerce("{\"small\": 1, \"tiny\": 1, \"longs\": [1, 2.5]}", Ids.class))
                .hasMessage("Argument \"order.longs[1]\" has non-integer value for long: <2.5>");
    }

    @Test
    void should_fail_the_tool_call_through_the_executor() throws Exception {
        OrderTool tool = new OrderTool();
        DefaultToolExecutor executor = DefaultToolExecutor.builder()
                .object(tool)
                .originalMethod(OrderTool.class.getDeclaredMethod("placeOrder", Order.class))
                .methodToInvoke(OrderTool.class.getDeclaredMethod("placeOrder", Order.class))
                .wrapToolArgumentsExceptions(true)
                .build();
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("1")
                .name("placeOrder")
                .arguments("{\"order\": {}}")
                .build();

        assertThatThrownBy(() -> executor.execute(request, "memory"))
                .isInstanceOf(ToolArgumentsException.class)
                .hasMessageContaining("Required argument \"order.quantity\" of type int is missing");
        assertThat(tool.placed).isEmpty();
    }

    // ---------- helpers ----------

    @SuppressWarnings("unused")
    static class Holder {
        void all(List<Order> list, Set<Order> set, Map<String, Order> map, Order[] array) {}
    }

    private static Object coerce(String json, Class<?> type) {
        return coerceArgument(Json.fromJson(json, Object.class), "order", type, type);
    }
}
