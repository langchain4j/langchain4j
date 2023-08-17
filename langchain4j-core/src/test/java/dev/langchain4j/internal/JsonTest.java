package dev.langchain4j.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.annotations.SerializedName;
import org.junit.jupiter.api.Test;

class JsonTest {

  @Test
  void conversionToJsonAndFromJsonWorks() {
    TestData testData = new TestData();
    testData.setSampleDate(LocalDate.of(2023, 1, 15));
    testData.setSampleDateTime(LocalDateTime.of(2023, 1, 15, 10, 20));
    testData.setSomeValue("value");

    String json = Json.toJson(testData);

    assertThat(json)
      .isEqualTo(
        "{\n" +
        "  \"sampleDate\": \"2023-01-15\",\n" +
        "  \"sampleDateTime\": \"2023-01-15T10:20:00\",\n" +
        "  \"some_value\": \"value\"\n" +
        "}"
      );

    TestData deserializedData = Json.fromJson(json, TestData.class);

    assertThat(deserializedData.getSampleDate()).isEqualTo(testData.getSampleDate());
    assertThat(deserializedData.getSampleDateTime()).isEqualTo(testData.getSampleDateTime());
    assertThat(deserializedData.getSomeValue()).isEqualTo(testData.getSomeValue());
  }

  @Test
  void toInputStreamWorksForList() throws IOException {
    List<TestObject> testObjects = Arrays.asList(
            new TestObject("John", LocalDate.of(2021, 8, 17), LocalDateTime.of(2021, 8, 17, 14, 20)),
            new TestObject("Jane", LocalDate.of(2021, 8, 16), LocalDateTime.of(2021, 8, 16, 13, 19))
    );

    String expectedJson = "[{" +
            "\"name\":\"John\"," +
            "\"date\":\"2021-08-17\"," +
            "\"dateTime\":\"2021-08-17T14:20:00\"" +
            "},{" +
            "\"name\":\"Jane\"," +
            "\"date\":\"2021-08-16\"," +
            "\"dateTime\":\"2021-08-16T13:19:00\"" +
            "}]";

    InputStream inputStream = Json.toInputStream(testObjects, List.class);
    try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
      String resultJson = bufferedReader.lines().collect(Collectors.joining());

      assertThat(resultJson).isEqualTo(expectedJson);
    }
  }

  private static class TestObject {
    private final String name;
    private final LocalDate date;
    private final LocalDateTime dateTime;

    public TestObject(String name, LocalDate date, LocalDateTime dateTime) {
      this.name = name;
      this.date = date;
      this.dateTime = dateTime;
    }
  }

  private static class TestData {

    private LocalDate sampleDate;
    private LocalDateTime sampleDateTime;
    @SerializedName("some_value")
    private String someValue;

    LocalDate getSampleDate() {
      return sampleDate;
    }

    void setSampleDate(LocalDate sampleDate) {
      this.sampleDate = sampleDate;
    }

    LocalDateTime getSampleDateTime() {
      return sampleDateTime;
    }

    void setSampleDateTime(LocalDateTime sampleDateTime) {
      this.sampleDateTime = sampleDateTime;
    }

    String getSomeValue() {
      return someValue;
    }

    void setSomeValue(String someValue) {
      this.someValue = someValue;
    }
  }
}
