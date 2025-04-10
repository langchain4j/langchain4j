package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.output.structured.Description;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FunctionMapperTest {

    enum Projection {
        WGS84,
        NAD83,
        PZ90,
        GCJ02,
        BD09
    }

    static class Coordinates {
        @Description("latitude")
        double latitude;

        @Description("latitude")
        double longitude;

        @Description("Geographic projection system used")
        Projection projection;

        public Coordinates(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.projection = Projection.WGS84;
        }
    }

    static class IssTool {
        @Tool("Get the distance between the user and the ISS.")
        int distanceBetween(
                @P("user coordinates") Coordinates userCoordinates, @P("ISS coordinates") Coordinates issCoordinates) {
            return 3456;
        }
    }

    @Test
    void should_convert_nested_structures() {
        // when
        List<ToolSpecification> toolSpecifications = ToolSpecifications.toolSpecificationsFrom(IssTool.class);
        System.out.println("\ntoolSpecifications = " + toolSpecifications);

        // then
        assertThat(toolSpecifications).hasSize(1);
        ToolSpecification toolSpecification = toolSpecifications.get(0);
        assertThat(toolSpecification.name()).isEqualTo("distanceBetween");
        assertThat(toolSpecification.description()).isEqualTo("Get the distance between the user and the ISS.");

        // when
        GeminiTool geminiTool = FunctionMapper.fromToolSepcsToGTool(toolSpecifications, false);
        System.out.println("\ngeminiTool = " + withoutNullValues(geminiTool.toString()));

        // then
        List<GeminiFunctionDeclaration> allGFnDecl = geminiTool.getFunctionDeclarations();
        assertThat(allGFnDecl).hasSize(1);

        GeminiFunctionDeclaration gFnDecl = allGFnDecl.get(0);
        assertThat(gFnDecl.getName()).isEqualTo("distanceBetween");

        assertThat(gFnDecl.getParameters().getType()).isEqualTo(GeminiType.OBJECT);
        Map<String, GeminiSchema> props = gFnDecl.getParameters().getProperties();

        assertThat(props).hasSize(2);
        assertThat(props.keySet()).containsAll(Arrays.asList("userCoordinates", "issCoordinates"));

        GeminiSchema userCoord = props.get("userCoordinates");
        assertThat(userCoord.getType()).isEqualTo(GeminiType.OBJECT);

        GeminiSchema issCoord = props.get("issCoordinates");
        assertThat(issCoord.getType()).isEqualTo(GeminiType.OBJECT);

        assertThat(userCoord.getProperties()).hasSize(3);
        assertThat(issCoord.getProperties()).hasSize(3);

        assertThat(userCoord.getProperties().keySet())
                .containsAll(Arrays.asList("latitude", "longitude", "projection"));
        assertThat(issCoord.getProperties().keySet()).containsAll(Arrays.asList("latitude", "longitude", "projection"));
    }

    static class Address {
        private final String street;
        private final String zipCode;
        private final String city;

        public Address(String street, String zipCode, String city) {
            this.street = street;
            this.zipCode = zipCode;
            this.city = city;
        }
    }

    static class Customer {
        private final String firstname;
        private final String lastname;

        private final Address shippingAddress;
        //        private final Address billingAddress;

        public Customer(String firstname, String lastname, Address shippingAddress
                //                        Address billingAddress
                ) {
            this.firstname = firstname;
            this.lastname = lastname;
            this.shippingAddress = shippingAddress;
            //            this.billingAddress = billingAddress;
        }
    }

    static class Product {
        private final String name;
        private final String description;
        private final double price;

        public Product(String name, String description, double price) {
            this.name = name;
            this.description = description;
            this.price = price;
        }
    }

    static class LineItem {
        private final Product product;
        private final int quantity;

        public LineItem(int quantity, Product product) {
            this.product = product;
            this.quantity = quantity;
        }
    }

    static class Order {
        private final Double totalAmount;
        private final List<LineItem> lineItems;
        private final Customer customer;

        public Order(Double totalAmount, List<LineItem> lineItems, Customer customer) {
            this.totalAmount = totalAmount;
            this.lineItems = lineItems;
            this.customer = customer;
        }
    }

    static class OrderSystem {
        @Tool("Make an order")
        boolean makeOrder(@P(value = "The order to make") Order order) {
            return true;
        }
    }

    @Test
    void complexNestedGraph() {
        // given
        List<ToolSpecification> toolSpecifications = ToolSpecifications.toolSpecificationsFrom(OrderSystem.class);
        System.out.println("\ntoolSpecifications = " + toolSpecifications);

        // when
        GeminiTool geminiTool = FunctionMapper.fromToolSepcsToGTool(toolSpecifications, false);
        System.out.println("\ngeminiTool = " + withoutNullValues(geminiTool.toString()));

        // then
        List<GeminiFunctionDeclaration> allGFnDecl = geminiTool.getFunctionDeclarations();
        assertThat(allGFnDecl).hasSize(1);

        GeminiFunctionDeclaration gFnDecl = allGFnDecl.get(0);
        assertThat(gFnDecl.getName()).isEqualTo("makeOrder");
        assertThat(gFnDecl.getParameters().getType()).isEqualTo(GeminiType.OBJECT);

        Map<String, GeminiSchema> props = gFnDecl.getParameters().getProperties();
        assertThat(props).hasSize(1);
        assertThat(props.keySet()).containsExactly("order");

        GeminiSchema orderSchema = props.get("order");
        assertThat(orderSchema.getType()).isEqualTo(GeminiType.OBJECT);
        assertThat(orderSchema.getProperties()).hasSize(3);
        assertThat(orderSchema.getProperties().keySet())
                .containsAll(Arrays.asList("totalAmount", "lineItems", "customer"));

        GeminiSchema totalAmount = orderSchema.getProperties().get("totalAmount");
        assertThat(totalAmount.getType()).isEqualTo(GeminiType.NUMBER);

        GeminiSchema lineItems = orderSchema.getProperties().get("lineItems");
        assertThat(lineItems.getType()).isEqualTo(GeminiType.ARRAY);

        GeminiSchema lineItemsItems = lineItems.getItems();
        assertThat(lineItemsItems.getType()).isEqualTo(GeminiType.OBJECT);
        assertThat(lineItemsItems.getProperties()).hasSize(2);
        assertThat(lineItemsItems.getProperties().keySet()).containsAll(Arrays.asList("product", "quantity"));

        GeminiSchema product = lineItemsItems.getProperties().get("product");
        assertThat(product.getType()).isEqualTo(GeminiType.OBJECT);
        assertThat(product.getProperties()).hasSize(3);
        assertThat(product.getProperties().keySet()).containsAll(Arrays.asList("name", "description", "price"));

        GeminiSchema customer = orderSchema.getProperties().get("customer");
        assertThat(customer.getType()).isEqualTo(GeminiType.OBJECT);
        assertThat(customer.getProperties()).hasSize(3);
        assertThat(customer.getProperties().keySet())
                .containsAll(Arrays.asList("firstname", "lastname", "shippingAddress"));

        GeminiSchema shippingAddress = customer.getProperties().get("shippingAddress");
        assertThat(shippingAddress.getType()).isEqualTo(GeminiType.OBJECT);
        assertThat(shippingAddress.getProperties()).hasSize(3);
        assertThat(shippingAddress.getProperties().keySet()).containsAll(Arrays.asList("street", "zipCode", "city"));
    }

    @Test
    void array() {
        // given
        ToolSpecification spec = ToolSpecification.builder()
                .name("toolName")
                .description("tool description")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("arrayParameter", JsonArraySchema.builder()
                                .items(new JsonStringSchema())
                                .description("an array")
                                .build())
                        .required("arrayParameter")
                        .build())
                .build();

        System.out.println("\nspec = " + spec);

        // when
        GeminiTool geminiTool = FunctionMapper.fromToolSepcsToGTool(Arrays.asList(spec), false);
        System.out.println("\ngeminiTool = " + withoutNullValues(geminiTool.toString()));

        // then
        List<GeminiFunctionDeclaration> allGFnDecl = geminiTool.getFunctionDeclarations();
        assertThat(allGFnDecl).hasSize(1);
        GeminiFunctionDeclaration gFnDecl = allGFnDecl.get(0);
        assertThat(gFnDecl.getName()).isEqualTo("toolName");
        assertThat(gFnDecl.getParameters().getType()).isEqualTo(GeminiType.OBJECT);

        Map<String, GeminiSchema> props = gFnDecl.getParameters().getProperties();
        System.out.println("props = " + withoutNullValues(props.toString()));
        assertThat(props).hasSize(1);
        assertThat(props.keySet()).containsExactly("arrayParameter");

        GeminiSchema arrayParameter = props.get("arrayParameter");
        assertThat(arrayParameter.getType()).isEqualTo(GeminiType.ARRAY);
        assertThat(arrayParameter.getDescription()).isEqualTo("an array");
        assertThat(arrayParameter.getItems().getType()).isEqualTo(GeminiType.STRING);
        assertThat(arrayParameter.getItems().getItems()).isNull();
        assertThat(arrayParameter.getItems().getProperties()).isNull();
    }

    private static String withoutNullValues(String toString) {
        return toString.replaceAll("(, )?(?<=(, |\\())[^\\s(]+?=null(?:, )?", " ")
                .replaceFirst(", \\)$", ")");
    }
}
