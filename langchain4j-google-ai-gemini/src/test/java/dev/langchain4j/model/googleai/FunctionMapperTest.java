package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.googleai.GeminiGenerateContentRequest.GeminiTool;
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
        GeminiTool geminiTool = FunctionMapper.fromToolSpecsToGTools(
                        toolSpecifications, false, false, false, false, false)
                .get(0);
        System.out.println("\ngeminiTool = " + withoutNullValues(geminiTool.toString()));

        // then
        List<GeminiFunctionDeclaration> allGFnDecl = geminiTool.functionDeclarations();
        assertThat(allGFnDecl).hasSize(1);

        GeminiFunctionDeclaration gFnDecl = allGFnDecl.get(0);
        assertThat(gFnDecl.name()).isEqualTo("distanceBetween");

        assertThat(gFnDecl.parameters().getType()).isEqualTo(GeminiType.OBJECT);
        Map<String, GeminiSchema> props = gFnDecl.parameters().getProperties();

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
        boolean makeOrder(@P("The order to make") Order order) {
            return true;
        }
    }

    @Test
    void complexNestedGraph() {
        // given
        List<ToolSpecification> toolSpecifications = ToolSpecifications.toolSpecificationsFrom(OrderSystem.class);
        System.out.println("\ntoolSpecifications = " + toolSpecifications);

        // when
        GeminiTool geminiTool = FunctionMapper.fromToolSpecsToGTools(
                        toolSpecifications, false, false, false, false, false)
                .get(0);
        System.out.println("\ngeminiTool = " + withoutNullValues(geminiTool.toString()));

        // then
        List<GeminiFunctionDeclaration> allGFnDecl = geminiTool.functionDeclarations();
        assertThat(allGFnDecl).hasSize(1);

        GeminiFunctionDeclaration gFnDecl = allGFnDecl.get(0);
        assertThat(gFnDecl.name()).isEqualTo("makeOrder");
        assertThat(gFnDecl.parameters().getType()).isEqualTo(GeminiType.OBJECT);

        Map<String, GeminiSchema> props = gFnDecl.parameters().getProperties();
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
                        .addProperty(
                                "arrayParameter",
                                JsonArraySchema.builder()
                                        .items(new JsonStringSchema())
                                        .description("an array")
                                        .build())
                        .required("arrayParameter")
                        .build())
                .build();

        System.out.println("\nspec = " + spec);

        // when
        GeminiTool geminiTool = FunctionMapper.fromToolSpecsToGTools(
                        Arrays.asList(spec), false, false, false, false, false)
                .get(0);
        System.out.println("\ngeminiTool = " + withoutNullValues(geminiTool.toString()));

        // then
        List<GeminiFunctionDeclaration> allGFnDecl = geminiTool.functionDeclarations();
        assertThat(allGFnDecl).hasSize(1);
        GeminiFunctionDeclaration gFnDecl = allGFnDecl.get(0);
        assertThat(gFnDecl.name()).isEqualTo("toolName");
        assertThat(gFnDecl.parameters().getType()).isEqualTo(GeminiType.OBJECT);

        Map<String, GeminiSchema> props = gFnDecl.parameters().getProperties();
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

    @Test
    void should_include_url_context_tool() {
        // given
        boolean allowUrlContext = true;

        // when
        GeminiTool geminiTool = FunctionMapper.fromToolSpecsToGTools(null, false, false, allowUrlContext, false, false)
                .get(0);

        // then
        assertThat(geminiTool).isNotNull();
        assertThat(geminiTool.functionDeclarations()).isNull();
        assertThat(geminiTool.codeExecution()).isNull();
        assertThat(geminiTool.urlContext()).isNotNull();
    }

    @Test
    void should_include_google_search_retrieval() {
        // given
        boolean allowGoogleSearch = true;

        // when
        GeminiTool geminiTool = FunctionMapper.fromToolSpecsToGTools(
                        null, false, allowGoogleSearch, false, false, false)
                .get(0);

        // then
        assertThat(geminiTool).isNotNull();
        assertThat(geminiTool.functionDeclarations()).isNull();
        assertThat(geminiTool.codeExecution()).isNull();
        assertThat(geminiTool.googleSearch()).isNotNull();
    }

    @Test
    void should_include_google_maps_tool() {
        // given
        boolean allowGoogleMaps = true;
        boolean allowGoogleMapsWidget = false;

        // when
        GeminiTool geminiTool = FunctionMapper.fromToolSpecsToGTools(
                        null, false, false, false, allowGoogleMaps, allowGoogleMapsWidget)
                .get(0);

        // then
        assertThat(geminiTool).isNotNull();
        assertThat(geminiTool.functionDeclarations()).isNull();
        assertThat(geminiTool.codeExecution()).isNull();
        assertThat(geminiTool.googleMaps()).isNotNull();
        assertThat(geminiTool.googleMaps().enableWidget()).isFalse();
    }

    @Test
    void should_include_google_maps_tool_with_widget() {
        // given
        boolean allowGoogleMaps = true;
        boolean allowGoogleMapsWidget = true;

        // when
        GeminiTool geminiTool = FunctionMapper.fromToolSpecsToGTools(
                        null, false, false, false, allowGoogleMaps, allowGoogleMapsWidget)
                .get(0);

        // then
        assertThat(geminiTool).isNotNull();
        assertThat(geminiTool.functionDeclarations()).isNull();
        assertThat(geminiTool.codeExecution()).isNull();
        assertThat(geminiTool.googleMaps()).isNotNull();
        assertThat(geminiTool.googleMaps().enableWidget()).isTrue();
    }

    @Test
    void should_use_typed_parameters_when_every_element_has_a_typed_form() {
        // given
        JsonObjectSchema parameters = JsonObjectSchema.builder()
                .addStringProperty("query")
                .addIntegerProperty("maxResults")
                .required("query")
                .build();

        // when
        GeminiFunctionDeclaration declaration = declarationFor(parameters);

        // then
        assertThat(declaration.parametersJsonSchema()).isNull();
        assertThat(declaration.parameters().getType()).isEqualTo(GeminiType.OBJECT);
        assertThat(declaration.parameters().getProperties().keySet()).containsExactlyInAnyOrder("query", "maxResults");
    }

    @Test
    void should_use_json_schema_for_a_raw_element() {
        // given
        JsonObjectSchema parameters = JsonObjectSchema.builder()
                .addStringProperty("query")
                .addProperty("maxResults", JsonRawSchema.from("{\"type\":\"integer\",\"minimum\":1,\"maximum\":50}"))
                .required("query")
                .build();

        // when
        GeminiFunctionDeclaration declaration = declarationFor(parameters);

        // then
        assertThat(declaration.parameters()).isNull();

        Map<String, Object> properties = propertiesOf(declaration.parametersJsonSchema());
        assertThat(properties).containsKeys("query", "maxResults");
        // The constraints the typed model has no field for survive.
        assertThat((Map<String, Object>) properties.get("maxResults")).containsEntry("minimum", 1);
        assertThat((Map<String, Object>) properties.get("maxResults")).containsEntry("maximum", 50);
    }

    @Test
    void should_use_json_schema_for_a_reference_element() {
        // given
        JsonObjectSchema priceRange = JsonObjectSchema.builder()
                .addProperty("min", new JsonNumberSchema())
                .addProperty("max", new JsonNumberSchema())
                .build();
        JsonObjectSchema parameters = JsonObjectSchema.builder()
                .definitions(Map.of("PriceRange", priceRange))
                .addStringProperty("query")
                // A reference holds the bare definition name; toMap() writes the "#/$defs/" prefix.
                .addProperty(
                        "retailPrice",
                        JsonReferenceSchema.builder().reference("PriceRange").build())
                .required("query")
                .build();

        // when
        GeminiFunctionDeclaration declaration = declarationFor(parameters);

        // then
        assertThat(declaration.parameters()).isNull();

        Map<String, Object> jsonSchema = declaration.parametersJsonSchema();
        assertThat(jsonSchema).containsKey("$defs");
        assertThat((Map<String, Object>) propertiesOf(jsonSchema).get("retailPrice"))
                .containsEntry("$ref", "#/$defs/PriceRange");
    }

    @Test
    void should_use_json_schema_for_definitions_that_nothing_references() {
        // given
        JsonObjectSchema priceRange = JsonObjectSchema.builder()
                .addProperty("min", new JsonNumberSchema())
                .addProperty("max", new JsonNumberSchema())
                .build();
        // Every property has a typed form. Only the definitions rule out the typed field, since
        // GeminiSchema has nowhere to put them and they would be dropped without a word.
        JsonObjectSchema parameters = JsonObjectSchema.builder()
                .definitions(Map.of("PriceRange", priceRange))
                .addStringProperty("query")
                .required("query")
                .build();

        // when
        GeminiFunctionDeclaration declaration = declarationFor(parameters);

        // then
        assertThat(declaration.parameters()).isNull();

        Map<String, Object> jsonSchema = declaration.parametersJsonSchema();
        assertThat(propertiesOf(jsonSchema)).containsKey("query");
        assertThat((Map<String, Object>) jsonSchema.get("$defs")).containsKey("PriceRange");
    }

    @Test
    void should_use_json_schema_for_an_element_nested_below_the_root() {
        // given
        JsonObjectSchema parameters = JsonObjectSchema.builder()
                .addProperty(
                        "filters",
                        JsonArraySchema.builder()
                                .items(JsonObjectSchema.builder()
                                        .addProperty("size", JsonRawSchema.from("{\"type\":\"integer\",\"minimum\":1}"))
                                        .build())
                                .build())
                .build();

        // when
        GeminiFunctionDeclaration declaration = declarationFor(parameters);

        // then
        assertThat(declaration.parameters()).isNull();
        assertThat(declaration.parametersJsonSchema()).isNotNull();
    }

    @Test
    void should_omit_the_unused_parameter_field_from_the_serialized_request() {
        // given
        JsonObjectSchema typed =
                JsonObjectSchema.builder().addStringProperty("query").build();
        JsonObjectSchema raw = JsonObjectSchema.builder()
                .addProperty("query", JsonRawSchema.from("{\"type\":\"string\",\"minLength\":1}"))
                .build();

        // when
        String typedJson = Json.toJson(declarationFor(typed));
        String rawJson = Json.toJson(declarationFor(raw));

        // then
        assertThat(typedJson).contains("\"parameters\"").doesNotContain("parametersJsonSchema");
        assertThat(rawJson).contains("\"parametersJsonSchema\"").doesNotContain("\"parameters\"");
    }

    private static GeminiFunctionDeclaration declarationFor(JsonObjectSchema parameters) {
        ToolSpecification specification = ToolSpecification.builder()
                .name("search_products")
                .description("Search the catalog")
                .parameters(parameters)
                .build();

        return FunctionMapper.fromToolSpecsToGTools(List.of(specification), false, false, false, false, false)
                .get(0)
                .functionDeclarations()
                .get(0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> propertiesOf(Map<String, Object> jsonSchema) {
        return (Map<String, Object>) jsonSchema.get("properties");
    }

    private static String withoutNullValues(String toString) {
        return toString.replaceAll("(, )?(?<=(, |\\())[^\\s(]+?=null(?:, )?", " ")
                .replaceFirst(", \\)$", ")");
    }
}
