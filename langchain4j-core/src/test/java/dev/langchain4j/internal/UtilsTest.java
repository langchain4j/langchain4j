package dev.langchain4j.internal;

import static dev.langchain4j.internal.Utils.quoted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UtilsTest {

  @Test
  void randomUUIDWorks() {
    String uuid1 = Utils.randomUUID();
    String uuid2 = Utils.randomUUID();

    assertThat(uuid1).isNotNull().isNotEmpty();
    assertThat(uuid2).isNotNull().isNotEmpty();

    // Checking if the two generated UUIDs are not the same
    assertThat(uuid1).isNotEqualTo(uuid2);

    // Validate if the returned string is in the UUID format
    UUID.fromString(uuid1);
    UUID.fromString(uuid2);
  }

  @Test
  void generateUUIDFromTextWorks() {
    String input1 = "Hello";
    String input2 = "World";

    String uuidFromInput1 = Utils.generateUUIDFrom(input1);
    String uuidFromInput2 = Utils.generateUUIDFrom(input2);

    assertThat(uuidFromInput1).isNotNull().isNotEmpty();
    assertThat(uuidFromInput2).isNotNull().isNotEmpty();

    // Different inputs should produce different UUIDs
    assertThat(uuidFromInput1).isNotEqualTo(uuidFromInput2);

    // Validate if the returned string is in the UUID format
    UUID.fromString(uuidFromInput1);
    UUID.fromString(uuidFromInput2);

    // Test if hashing is consistent for the same input
    assertThat(Utils.generateUUIDFrom(input1)).isEqualTo(uuidFromInput1);
  }

  @Test
  void generateUUIDFromEmptyInputWorks() {
    String uuidFromEmptyInput = Utils.generateUUIDFrom("");

    assertThat(uuidFromEmptyInput).isNotNull().isNotEmpty();

    // Validate if the returned string is in the UUID format
    UUID.fromString(uuidFromEmptyInput);
  }

  @Test
  void generateUUIDFromNullInputWorks() {
    assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> Utils.generateUUIDFrom(null));
  }

  @MethodSource
  @ParameterizedTest
  void test_quoted(String string, String expected) {
    assertThat(quoted(string)).isEqualTo(expected);
  }

  static Stream<Arguments> test_quoted() {
    return Stream.of(
      Arguments.of(null, "null"),
      Arguments.of("", "\"\""),
      Arguments.of(" ", "\" \""),
      Arguments.of("hello", "\"hello\"")
    );
  }
}
