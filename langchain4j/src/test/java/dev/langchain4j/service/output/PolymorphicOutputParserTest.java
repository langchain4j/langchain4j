package dev.langchain4j.service.output;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.output.structured.Description;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolymorphicOutputParserTest {

    sealed interface Animal permits Dog, Cat {}

    record Dog(String name, String breed) implements Animal {}

    record Cat(String name, boolean indoor) implements Animal {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Square.class, name = "square"),
        @JsonSubTypes.Type(value = Circle.class, name = "circle")
    })
    interface Shape {}

    static class Square implements Shape {
        double side;

        Square() {}

        Square(double side) {
            this.side = side;
        }
    }

    static class Circle implements Shape {
        double radius;

        Circle() {}

        Circle(double radius) {
            this.radius = radius;
        }
    }

    @Description("A pet that lives in your home")
    sealed interface Pet permits Hamster, Parrot {}

    sealed interface Vehicle permits Truck {}

    record Truck(String type, int wheels) implements Vehicle {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS)
    @JsonSubTypes(@JsonSubTypes.Type(Coffee.class))
    interface Beverage {}

    static class Coffee implements Beverage {
        String origin;
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
    @JsonSubTypes(@JsonSubTypes.Type(value = Apple.class, name = "apple"))
    interface Fruit {}

    static class Apple implements Fruit {
        String variety;
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME)
    @JsonSubTypes(@JsonSubTypes.Type(Pizza.class))
    interface Food {}

    static class Pizza implements Food {
        String topping;
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, defaultImpl = Wrench.class)
    @JsonSubTypes({@JsonSubTypes.Type(value = Hammer.class, name = "hammer")})
    interface Tool {}

    static class Hammer implements Tool {
        double weightKg;
    }

    static class Wrench implements Tool {
        int sizeMm;
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind", visible = true)
    @JsonSubTypes({@JsonSubTypes.Type(value = Sword.class, name = "sword")})
    interface Weapon {}

    static class Sword implements Weapon {
        String kind; // intentionally collides; visible=true makes it OK
        int blade_length_cm;
    }

    @Description("A small caged rodent kept as a pet")
    record Hamster(String name, double weightGrams) implements Pet {}

    @Description("A talking bird that can mimic human speech")
    record Parrot(String name, int vocabulary) implements Pet {}

    record Owner(String name, Animal pet) {}

    sealed interface Bird permits Eagle, Sparrow {}

    @JsonTypeName("eagle")
    record Eagle(double wingspanMeters) implements Bird {}

    @JsonTypeName("sparrow")
    record Sparrow(boolean migratory) implements Bird {}

    sealed interface Tree permits Oak, Pine {}

    // No annotation — should fall back to simpleName "Oak"
    record Oak(int rings) implements Tree {}

    @JsonTypeName("pine")
    record Pine(int needles) implements Tree {}

    @JsonSubTypes(@JsonSubTypes.Type(value = ElectricCar.class, name = "tesla"))
    sealed interface Car permits ElectricCar {}

    // @JsonSubTypes.Type(name="tesla") on the base should win over @JsonTypeName here.
    @JsonTypeName("losing-name")
    record ElectricCar(int range) implements Car {}

    @Test
    void JsonTypeName_on_subtype_is_used_as_discriminator_value() {

        JsonObjectSchema root = (JsonObjectSchema)
                new PojoOutputParser<>(Bird.class).jsonSchema().get().rootElement();

        JsonAnyOfSchema anyOf = (JsonAnyOfSchema) root.properties().get("value");

        JsonObjectSchema eagleOption = (JsonObjectSchema) anyOf.anyOf().get(0);
        JsonEnumSchema eagleDiscriminator = (JsonEnumSchema) eagleOption.properties().get("type");
        assertThat(eagleDiscriminator.enumValues()).containsExactly("eagle");

        JsonObjectSchema sparrowOption = (JsonObjectSchema) anyOf.anyOf().get(1);
        JsonEnumSchema sparrowDiscriminator = (JsonEnumSchema) sparrowOption.properties().get("type");
        assertThat(sparrowDiscriminator.enumValues()).containsExactly("sparrow");
    }

    @Test
    void JsonTypeName_drives_dispatch_during_parsing() {

        Bird bird = new PojoOutputParser<>(Bird.class)
                .parse("{\"value\":{\"type\":\"eagle\",\"wingspanMeters\":2.5}}");

        assertThat(bird).isInstanceOf(Eagle.class);
        assertThat(((Eagle) bird).wingspanMeters()).isEqualTo(2.5);
    }

    @Test
    void JsonTypeName_falls_back_to_simple_name_for_subtypes_without_the_annotation() {

        JsonObjectSchema root = (JsonObjectSchema)
                new PojoOutputParser<>(Tree.class).jsonSchema().get().rootElement();

        JsonAnyOfSchema anyOf = (JsonAnyOfSchema) root.properties().get("value");

        JsonObjectSchema oakOption = (JsonObjectSchema) anyOf.anyOf().get(0);
        JsonEnumSchema oakDiscriminator = (JsonEnumSchema) oakOption.properties().get("type");
        // Oak has no @JsonTypeName — falls back to simple name
        assertThat(oakDiscriminator.enumValues()).containsExactly("Oak");

        JsonObjectSchema pineOption = (JsonObjectSchema) anyOf.anyOf().get(1);
        JsonEnumSchema pineDiscriminator = (JsonEnumSchema) pineOption.properties().get("type");
        // Pine has @JsonTypeName("pine") — wins over simple name
        assertThat(pineDiscriminator.enumValues()).containsExactly("pine");
    }

    @Test
    void JsonSubTypes_name_wins_over_JsonTypeName_when_both_are_set() {

        JsonObjectSchema root = (JsonObjectSchema)
                new PojoOutputParser<>(Car.class).jsonSchema().get().rootElement();

        JsonAnyOfSchema anyOf = (JsonAnyOfSchema) root.properties().get("value");
        JsonObjectSchema option = (JsonObjectSchema) anyOf.anyOf().get(0);
        JsonEnumSchema discriminator = (JsonEnumSchema) option.properties().get("type");
        // @JsonSubTypes.Type(name = "tesla") wins over @JsonTypeName("losing-name")
        assertThat(discriminator.enumValues()).containsExactly("tesla");
    }

    record Adoption(Owner owner, List<Animal> petsAdopted) {}

    @Test
    void nested_sealed_field_produces_anyOf_in_outer_schema() {

        Optional<JsonSchema> schemaOpt = new PojoOutputParser<>(Owner.class).jsonSchema();

        assertThat(schemaOpt).isPresent();
        JsonObjectSchema root = (JsonObjectSchema) schemaOpt.get().rootElement();
        // Outer object is non-polymorphic — no `value` envelope
        assertThat(root.properties()).containsOnlyKeys("name", "pet");

        JsonAnyOfSchema petAnyOf = (JsonAnyOfSchema) root.properties().get("pet");
        assertThat(petAnyOf.anyOf()).hasSize(2);

        JsonObjectSchema dogOption = (JsonObjectSchema) petAnyOf.anyOf().get(0);
        JsonEnumSchema discriminator = (JsonEnumSchema) dogOption.properties().get("type");
        assertThat(discriminator.enumValues()).containsExactly("Dog");
    }

    @Test
    void nested_sealed_field_parses_correctly() {

        Owner owner = new PojoOutputParser<>(Owner.class)
                .parse("{\"name\":\"Alice\",\"pet\":{\"type\":\"Dog\",\"name\":\"Rex\",\"breed\":\"Labrador\"}}");

        assertThat(owner.name()).isEqualTo("Alice");
        assertThat(owner.pet()).isInstanceOf(Dog.class);
        assertThat(((Dog) owner.pet()).name()).isEqualTo("Rex");
        assertThat(((Dog) owner.pet()).breed()).isEqualTo("Labrador");
    }

    @Test
    void deeply_nested_sealed_collection_produces_array_of_anyOf() {

        JsonObjectSchema root = (JsonObjectSchema)
                new PojoOutputParser<>(Adoption.class).jsonSchema().get().rootElement();

        JsonArraySchema petsArray = (JsonArraySchema) root.properties().get("petsAdopted");
        assertThat(petsArray.items()).isInstanceOf(JsonAnyOfSchema.class);

        JsonObjectSchema ownerObj = (JsonObjectSchema) root.properties().get("owner");
        assertThat(ownerObj.properties().get("pet")).isInstanceOf(JsonAnyOfSchema.class);
    }

    @Test
    void deeply_nested_sealed_collection_parses_correctly() {

        Adoption adoption = new PojoOutputParser<>(Adoption.class)
                .parse("{\"owner\":{\"name\":\"Alice\",\"pet\":{\"type\":\"Cat\",\"name\":\"Whiskers\",\"indoor\":true}},"
                        + "\"petsAdopted\":["
                        + "{\"type\":\"Dog\",\"name\":\"Rex\",\"breed\":\"Lab\"},"
                        + "{\"type\":\"Cat\",\"name\":\"Mittens\",\"indoor\":false}"
                        + "]}");

        assertThat(adoption.owner().name()).isEqualTo("Alice");
        assertThat(adoption.owner().pet()).isInstanceOf(Cat.class);
        assertThat(adoption.petsAdopted()).hasSize(2);
        assertThat(adoption.petsAdopted().get(0)).isInstanceOf(Dog.class);
        assertThat(adoption.petsAdopted().get(1)).isInstanceOf(Cat.class);
    }

    record Reservation(String customer, Shape preferredShape) {}

    @Test
    void nested_jackson_annotated_field_produces_anyOf_with_jackson_discriminator() {

        JsonObjectSchema root = (JsonObjectSchema)
                new PojoOutputParser<>(Reservation.class).jsonSchema().get().rootElement();

        JsonAnyOfSchema shapeAnyOf = (JsonAnyOfSchema) root.properties().get("preferredShape");
        JsonObjectSchema firstOption = (JsonObjectSchema) shapeAnyOf.anyOf().get(0);
        // Jackson @JsonTypeInfo(property = "kind") is honored at field level too
        assertThat(firstOption.properties()).containsKey("kind");
    }

    @Test
    void nested_jackson_annotated_field_parses_correctly() {

        Reservation reservation = new PojoOutputParser<>(Reservation.class)
                .parse("{\"customer\":\"Bob\",\"preferredShape\":{\"kind\":\"circle\",\"radius\":3.14}}");

        assertThat(reservation.customer()).isEqualTo("Bob");
        assertThat(reservation.preferredShape()).isInstanceOf(Circle.class);
        assertThat(((Circle) reservation.preferredShape()).radius).isEqualTo(3.14);
    }

    @Test
    void schema_generation_fails_for_unsupported_JsonTypeInfo_use() {

        assertThatThrownBy(() -> new PojoOutputParser<>(Beverage.class).jsonSchema())
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("Id.MINIMAL_CLASS")
                .hasMessageContaining("Id.NAME")
                .hasMessageContaining("Beverage");
    }

    @Test
    void Id_SIMPLE_NAME_falls_back_to_simple_class_name_when_no_explicit_name() {

        JsonObjectSchema root = (JsonObjectSchema)
                new PojoOutputParser<>(Food.class).jsonSchema().get().rootElement();

        JsonAnyOfSchema anyOf = (JsonAnyOfSchema) root.properties().get("value");
        JsonObjectSchema pizzaOption = (JsonObjectSchema) anyOf.anyOf().get(0);
        // Jackson default property name for Id.SIMPLE_NAME is "@type"
        JsonEnumSchema discriminator = (JsonEnumSchema) pizzaOption.properties().get("@type");
        assertThat(discriminator.enumValues()).containsExactly("Pizza");
    }

    @Test
    void Id_SIMPLE_NAME_parses_simple_name() {

        Food food = new PojoOutputParser<>(Food.class).parse("{\"value\":{\"@type\":\"Pizza\",\"topping\":\"margherita\"}}");

        assertThat(food).isInstanceOf(Pizza.class);
        assertThat(((Pizza) food).topping).isEqualTo("margherita");
    }

    @Test
    void defaultImpl_is_used_when_discriminator_is_missing() {

        Tool tool = new PojoOutputParser<>(Tool.class).parse("{\"value\":{\"sizeMm\":17}}");

        assertThat(tool).isInstanceOf(Wrench.class);
        assertThat(((Wrench) tool).sizeMm).isEqualTo(17);
    }

    @Test
    void defaultImpl_is_used_when_discriminator_is_unknown() {

        // Jackson default property name for Id.NAME is "@type"
        Tool tool = new PojoOutputParser<>(Tool.class).parse("{\"value\":{\"@type\":\"unknown\",\"sizeMm\":17}}");

        assertThat(tool).isInstanceOf(Wrench.class);
        assertThat(((Wrench) tool).sizeMm).isEqualTo(17);
    }

    @Test
    void visible_true_allows_subtype_to_have_discriminator_field() {

        // schema generation succeeds despite the field collision
        Optional<JsonSchema> schema = new PojoOutputParser<>(Weapon.class).jsonSchema();
        assertThat(schema).isPresent();

        // and parsing populates the field
        Weapon weapon = new PojoOutputParser<>(Weapon.class)
                .parse("{\"value\":{\"kind\":\"sword\",\"blade_length_cm\":85}}");

        assertThat(weapon).isInstanceOf(Sword.class);
        assertThat(((Sword) weapon).kind).isEqualTo("sword");
        assertThat(((Sword) weapon).blade_length_cm).isEqualTo(85);
    }

    @Test
    void schema_generation_fails_for_unsupported_JsonTypeInfo_include() {

        assertThatThrownBy(() -> new PojoOutputParser<>(Fruit.class).jsonSchema())
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("As.WRAPPER_OBJECT")
                .hasMessageContaining("As.PROPERTY")
                .hasMessageContaining("Fruit");
    }

    @Test
    void schema_generation_fails_when_subtype_field_collides_with_discriminator() {

        assertThatThrownBy(() -> new PojoOutputParser<>(Vehicle.class).jsonSchema())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Truck")
                .hasMessageContaining("'type'")
                .hasMessageContaining("@JsonTypeInfo(property")
                .hasMessageContaining("Vehicle");
    }

    @Test
    void base_type_description_uses_Description_annotation_when_present() {

        JsonObjectSchema root = (JsonObjectSchema)
                new PojoOutputParser<>(Pet.class).jsonSchema().get().rootElement();

        JsonAnyOfSchema anyOf = (JsonAnyOfSchema) root.properties().get("value");

        assertThat(anyOf.description()).isEqualTo("A pet that lives in your home");
    }

    @Test
    void base_type_description_falls_back_to_simple_name_when_no_annotation() {

        JsonObjectSchema root = (JsonObjectSchema)
                new PojoOutputParser<>(Animal.class).jsonSchema().get().rootElement();

        JsonAnyOfSchema anyOf = (JsonAnyOfSchema) root.properties().get("value");

        assertThat(anyOf.description()).isEqualTo("Animal");
    }

    @Test
    void subtype_description_uses_Description_annotation_when_present() {

        JsonObjectSchema root = (JsonObjectSchema)
                new PojoOutputParser<>(Pet.class).jsonSchema().get().rootElement();

        JsonAnyOfSchema anyOf = (JsonAnyOfSchema) root.properties().get("value");

        JsonObjectSchema hamsterOption = (JsonObjectSchema) anyOf.anyOf().get(0);
        assertThat(hamsterOption.description()).isEqualTo("A small caged rodent kept as a pet");

        JsonObjectSchema parrotOption = (JsonObjectSchema) anyOf.anyOf().get(1);
        assertThat(parrotOption.description()).isEqualTo("A talking bird that can mimic human speech");
    }

    @Test
    void subtype_description_falls_back_to_simple_name_when_no_annotation() {

        JsonObjectSchema root = (JsonObjectSchema)
                new PojoOutputParser<>(Animal.class).jsonSchema().get().rootElement();

        JsonAnyOfSchema anyOf = (JsonAnyOfSchema) root.properties().get("value");

        assertThat(((JsonObjectSchema) anyOf.anyOf().get(0)).description()).isEqualTo("Dog");
        assertThat(((JsonObjectSchema) anyOf.anyOf().get(1)).description()).isEqualTo("Cat");
    }

    @Test
    void schema_for_sealed_interface_wraps_anyOf_under_value() {

        Optional<JsonSchema> schemaOpt = new PojoOutputParser<>(Animal.class).jsonSchema();

        assertThat(schemaOpt).isPresent();
        JsonSchema schema = schemaOpt.get();
        assertThat(schema.name()).isEqualTo("Animal");

        JsonObjectSchema root = (JsonObjectSchema) schema.rootElement();
        assertThat(root.required()).containsExactly("value");

        JsonAnyOfSchema anyOf = (JsonAnyOfSchema) root.properties().get("value");
        assertThat(anyOf.anyOf()).hasSize(2);

        // Dog option includes synthesized type discriminator with simple-name enum value
        JsonObjectSchema dogOption = (JsonObjectSchema) anyOf.anyOf().get(0);
        assertThat(dogOption.required()).startsWith("type");
        JsonEnumSchema dogDiscriminator = (JsonEnumSchema) dogOption.properties().get("type");
        assertThat(dogDiscriminator.enumValues()).containsExactly("Dog");
        assertThat(dogOption.properties()).containsKeys("name", "breed");

        JsonObjectSchema catOption = (JsonObjectSchema) anyOf.anyOf().get(1);
        JsonEnumSchema catDiscriminator = (JsonEnumSchema) catOption.properties().get("type");
        assertThat(catDiscriminator.enumValues()).containsExactly("Cat");
        assertThat(catOption.properties()).containsKeys("name", "indoor");
    }

    @Test
    void schema_honors_jackson_discriminator_property_and_subtype_names() {

        JsonObjectSchema root = (JsonObjectSchema)
                new PojoOutputParser<>(Shape.class).jsonSchema().get().rootElement();

        JsonAnyOfSchema anyOf = (JsonAnyOfSchema) root.properties().get("value");
        JsonObjectSchema firstOption = (JsonObjectSchema) anyOf.anyOf().get(0);

        // Discriminator property name comes from @JsonTypeInfo
        assertThat(firstOption.properties()).containsKey("kind");
        // Discriminator value comes from @JsonSubTypes.Type(name=...)
        JsonEnumSchema kind = (JsonEnumSchema) firstOption.properties().get("kind");
        assertThat(kind.enumValues()).containsExactly("square");
    }

    @Test
    void schema_for_collection_of_polymorphic_uses_array_of_anyOf() {

        Optional<JsonSchema> schemaOpt = new PojoListOutputParser<>(Animal.class).jsonSchema();

        assertThat(schemaOpt).isPresent();
        JsonObjectSchema root = (JsonObjectSchema) schemaOpt.get().rootElement();
        assertThat(root.required()).containsExactly("values");

        JsonArraySchema array = (JsonArraySchema) root.properties().get("values");
        JsonSchemaElement items = array.items();
        assertThat(items).isInstanceOf(JsonAnyOfSchema.class);
        assertThat(((JsonAnyOfSchema) items).anyOf()).hasSize(2);
    }

    @Test
    void parse_unwraps_value_envelope_for_single_polymorphic() {

        Animal animal = new PojoOutputParser<>(Animal.class)
                .parse("{\"value\":{\"type\":\"Dog\",\"name\":\"Rex\",\"breed\":\"Labrador\"}}");

        assertThat(animal).isInstanceOf(Dog.class);
        assertThat(((Dog) animal).name()).isEqualTo("Rex");
        assertThat(((Dog) animal).breed()).isEqualTo("Labrador");
    }

    @Test
    void parse_accepts_unwrapped_polymorphic_payload() {

        Animal animal = new PojoOutputParser<>(Animal.class)
                .parse("{\"type\":\"Cat\",\"name\":\"Whiskers\",\"indoor\":true}");

        assertThat(animal).isInstanceOf(Cat.class);
        assertThat(((Cat) animal).name()).isEqualTo("Whiskers");
        assertThat(((Cat) animal).indoor()).isTrue();
    }

    @Test
    void parse_collection_of_polymorphic() {

        List<Animal> animals = new PojoListOutputParser<>(Animal.class)
                .parse("{\"values\":["
                        + "{\"type\":\"Dog\",\"name\":\"Rex\",\"breed\":\"Labrador\"},"
                        + "{\"type\":\"Cat\",\"name\":\"Whiskers\",\"indoor\":true}"
                        + "]}");

        assertThat(animals).hasSize(2);
        assertThat(animals.get(0)).isInstanceOf(Dog.class);
        assertThat(animals.get(1)).isInstanceOf(Cat.class);
    }

    @Test
    void parse_set_of_polymorphic() {

        Set<Animal> animals = new PojoSetOutputParser<>(Animal.class)
                .parse("{\"values\":["
                        + "{\"type\":\"Dog\",\"name\":\"Rex\",\"breed\":\"Labrador\"}"
                        + "]}");

        assertThat(animals).hasSize(1);
        assertThat(animals.iterator().next()).isInstanceOf(Dog.class);
    }

    @Test
    void parse_polymorphic_with_jackson_discriminator() {

        Shape shape = new PojoOutputParser<>(Shape.class)
                .parse("{\"value\":{\"kind\":\"circle\",\"radius\":2.5}}");

        assertThat(shape).isInstanceOf(Circle.class);
        assertThat(((Circle) shape).radius).isEqualTo(2.5);
    }

    @Test
    void parse_fails_for_unknown_discriminator_value() {

        assertThatThrownBy(() -> new PojoOutputParser<>(Animal.class)
                .parse("{\"type\":\"Hamster\",\"name\":\"Nibbles\"}"))
                .isInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse");
    }

    @Test
    void parse_fails_when_discriminator_missing() {

        assertThatThrownBy(() -> new PojoOutputParser<>(Animal.class)
                .parse("{\"value\":{\"name\":\"Rex\",\"breed\":\"Labrador\"}}"))
                .isInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse");
    }
}
