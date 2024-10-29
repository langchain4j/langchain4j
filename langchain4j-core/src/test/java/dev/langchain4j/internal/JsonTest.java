package dev.langchain4j.internal;

import com.google.gson.annotations.SerializedName;
import org.junit.jupiter.api.Test;

import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class JsonTest {

  static class TimeHolder {
    Instant time;
    ZonedDateTime zoned;
  }

  @Test
  void conversionToJsonAndFromJsonWorks() {
    TestData testData = new TestData();
    testData.setSampleDate(LocalDate.of(2023, 1, 15));
    testData.setSampleDateTime(LocalDateTime.of(2023, 1, 15, 10, 20));
    testData.setSomeValue("value");

    String json = Json.toJson(testData);

    // language=json
    assertThat(json)
      .isEqualTo(
        """
        {
          "sampleDate": "2023-01-15",
          "sampleDateTime": "2023-01-15T10:20:00",
          "some_value": "value"
        }"""
      );

    TestData deserializedData = Json.fromJson(json, TestData.class);

    assertThat(deserializedData.getSampleDate()).isEqualTo(testData.getSampleDate());
    assertThat(deserializedData.getSampleDateTime()).isEqualTo(testData.getSampleDateTime());
    assertThat(deserializedData.getSomeValue()).isEqualTo(testData.getSomeValue());
  }

  @Test
  void conversionFromJsonStringToMapWorks() {
    String json = "{" +
        "  \"name\"       : \"Foo\"," +
        "  \"description\": \"Bar\"," +
        "  \"theArray\"   : [1,2,3,4]," +
        "  \"nullable\"   : true," +
        "  \"other\"   : null" +
        "}";

    Map<String, Object> map = Json.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());

    assertThat(map)
        .hasSize(5)
        .containsEntry("name", "Foo")
        .containsEntry("description", "Bar")
        .containsEntry("theArray", Arrays.asList(1d, 2d, 3d, 4d))
        .containsEntry("nullable", true)
        .containsEntry("other", null);
  }

  @Test
  void convertsIsoStringToInstant() {
    String json = "{ \"time\": \"2024-10-19T20:19:48.192242Z\" }";

    TimeHolder timeHolder = Json.fromJson(json, TimeHolder.class);
    assertThat(timeHolder.time).isEqualTo(Instant.parse("2024-10-19T20:19:48.192242Z"));
  }

  @Test
  void convertEpochNumberToInstant() {
    String json = "{ \"time\": 1729369188192 }";

    TimeHolder timeHolder = Json.fromJson(json, TimeHolder.class);
    assertThat(timeHolder.time.toEpochMilli()).isEqualTo(1729369188192L);
  }

  @Test
  void convertsIsoStringToZonedDateTime() {
    String json = "{ \"zoned\": \"2024-10-19T22:31:50.941666+02:00[Europe/Stockholm]\" }";

    TimeHolder timeHolder = Json.fromJson(json, TimeHolder.class);
    assertThat(timeHolder.zoned).isEqualTo(ZonedDateTime.parse("2024-10-19T22:31:50.941666+02:00[Europe/Stockholm]"));
  }

  @Test
  void convertsObjectStringToZonedDateTime() {
    String json = "{ \"zoned\": {\n" +
          "  \"year\": 2024,\n" +
          "  \"month\": 10,\n" +
          "  \"day\": 13,\n" +
          "  \"hour\": 14,\n" +
          "  \"minute\": 27,\n" +
          "  \"second\": 4,\n" +
          "  \"zone\": \"Europe/Stockholm\"\n" +
          "}\n" +
        " }";

    TimeHolder timeHolder = Json.fromJson(json, TimeHolder.class);
    assertThat(timeHolder.zoned).isEqualTo(ZonedDateTime.parse("2024-10-13T14:27:04+02:00[Europe/Stockholm]"));
  }

  @Test
  void toInputStreamWorksForList() throws IOException {
    List<TestObject> testObjects = Arrays.asList(
            new TestObject("John", LocalDate.of(2021, 8, 17), LocalDateTime.of(2021, 8, 17, 14, 20)),
            new TestObject("Jane", LocalDate.of(2021, 8, 16), LocalDateTime.of(2021, 8, 16, 13, 19))
    );

    // language=json
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

  private record TestObject(String name, LocalDate date, LocalDateTime dateTime) {
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
