package dev.langchain4j.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class JsonTest {

  @Test
  void conversionToJsonAndFromJsonWorks() {
    TestData testData = new TestData();
    testData.setSampleDate(LocalDate.of(2023, 1, 15));
    testData.setSampleDateTime(LocalDateTime.of(2023, 1, 15, 10, 20));
    testData.setSomeValue("value");

    String json = Json.toJson(testData);

    // language=json
    assertThat(json)
      .isEqualToIgnoringWhitespace(
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

  private static class TestData {

    private LocalDate sampleDate;
    private LocalDateTime sampleDateTime;
    @JsonProperty("some_value")
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
