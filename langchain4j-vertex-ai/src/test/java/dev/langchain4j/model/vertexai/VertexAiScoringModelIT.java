package dev.langchain4j.model.vertexai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class VertexAiScoringModelIT {
    @Test
    void should_rank_multiple() {
        // given
        VertexAiScoringModel scoringModel = VertexAiScoringModel.builder()
                .projectId(System.getenv("GCP_PROJECT_ID"))
                .projectNumber(System.getenv("GCP_PROJECT_NUM"))
                .location(System.getenv("GCP_LOCATION"))
                .model("semantic-ranker-512")
                .build();

        // when
        Response<List<Double>> score = scoringModel.scoreAll(
                Stream.of(
                                "The sky appears blue due to a phenomenon called Rayleigh scattering. "
                                        + "Sunlight is comprised of all the colors of the rainbow. Blue light has shorter "
                                        + "wavelengths than other colors, and is thus scattered more easily.",
                                "A canvas stretched across the day,\n" + "Where sunlight learns to dance and play.\n"
                                        + "Blue, a hue of scattered light,\n"
                                        + "A gentle whisper, soft and bright.")
                        .map(TextSegment::from)
                        .collect(Collectors.toList()),
                "Why is the sky blue?");

        // then
        assertThat(score.content().get(0)).isGreaterThan(score.content().get(1));
    }

    @Test
    void should_rank_single() {
        // given
        VertexAiScoringModel scoringModel = VertexAiScoringModel.builder()
                .projectId(System.getenv("GCP_PROJECT_ID"))
                .projectNumber(System.getenv("GCP_PROJECT_NUM"))
                .location(System.getenv("GCP_LOCATION"))
                .model("semantic-ranker-512")
                .build();

        // when
        Response<Double> score = scoringModel.score(
                "The sky appears blue due to a phenomenon called Rayleigh scattering. "
                        + "Sunlight is comprised of all the colors of the rainbow. Blue light has shorter "
                        + "wavelengths than other colors, and is thus scattered more easily.",
                "Why is the sky blue?");

        // then
        assertThat(score.content()).isPositive();
    }

    @Test
    void should_use_text_segment_titles_into_account() {
        // given
        String customTitleKey = "customTitle";

        VertexAiScoringModel scoringModel = VertexAiScoringModel.builder()
                .projectId(System.getenv("GCP_PROJECT_ID"))
                .projectNumber(System.getenv("GCP_PROJECT_NUM"))
                .location(System.getenv("GCP_LOCATION"))
                .model("semantic-ranker-512")
                .titleMetadataKey(customTitleKey)
                .build();

        List<TextSegment> segments = Arrays.asList(
                new TextSegment(
                        "Your Cymbal Starlight 2024 is not equipped to tow a trailer.",
                        new Metadata().put(customTitleKey, "trailer")),
                new TextSegment(
                        "The Cymbal Starlight 2024 has a cargo capacity of 13.5 cubic feet.",
                        new Metadata().put(customTitleKey, "capacity")),
                new TextSegment(
                        "The cargo area is located in the trunk of the vehicle.",
                        new Metadata().put(customTitleKey, "trunk")),
                new TextSegment(
                        "To access the cargo area, open the trunk lid using the trunk release lever located in the driver's footwell.",
                        new Metadata().put(customTitleKey, "lever")),
                new TextSegment(
                        "When loading cargo into the trunk, be sure to distribute the weight evenly.",
                        new Metadata().put(customTitleKey, "weight")),
                new TextSegment(
                        "Do not overload the trunk, as this could affect the vehicle's handling and stability.",
                        new Metadata().put(customTitleKey, "overload")));

        // when
        Response<List<Double>> score = scoringModel.scoreAll(segments, "What is the cargo capacity of the car?");

        // then
        double maxScore =
                score.content().stream().mapToDouble(Double::doubleValue).max().getAsDouble();

        assertThat(score.content().get(1)).isEqualTo(maxScore);
    }
}
