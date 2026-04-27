package dev.langchain4j.model.watsonx;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.moderation.ModerationResponseMetadata;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class WatsonxModerationResponseMetadata implements ModerationResponseMetadata {

    private final String detection;

    @JsonProperty("detection_type")
    private final String detectionType;

    private final Integer start;
    private final Integer end;
    private final Double score;

    private WatsonxModerationResponseMetadata(Builder builder) {
        this.detection = builder.detection;
        this.detectionType = builder.detectionType;
        this.start = builder.start;
        this.end = builder.end;
        this.score = builder.score;
    }

    public String detection() {
        return detection;
    }

    public String detectionType() {
        return detectionType;
    }

    public Integer start() {
        return start;
    }

    public Integer end() {
        return end;
    }

    public Double score() {
        return score;
    }

    public Builder toBuilder() {
        return builder()
                .detection(detection)
                .detectionType(detectionType)
                .start(start)
                .end(end)
                .score(score);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (detection != null) {
            map.put("detection", detection);
        }
        if (detectionType != null) {
            map.put("detection_type", detectionType);
        }
        if (start != null) {
            map.put("start", start);
        }
        if (end != null) {
            map.put("end", end);
        }
        if (score != null) {
            map.put("score", score);
        }
        return map;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WatsonxModerationResponseMetadata that = (WatsonxModerationResponseMetadata) o;
        return Objects.equals(detection, that.detection)
                && Objects.equals(detectionType, that.detectionType)
                && Objects.equals(start, that.start)
                && Objects.equals(end, that.end)
                && Objects.equals(score, that.score);
    }

    @Override
    public int hashCode() {
        return Objects.hash(detection, detectionType, start, end, score);
    }

    @Override
    public String toString() {
        return "WatsonxModerationResponseMetadata{"
                + "detection='" + detection + '\''
                + ", detectionType='" + detectionType + '\''
                + ", start=" + start
                + ", end=" + end
                + ", score=" + score
                + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String detection;
        private String detectionType;
        private Integer start;
        private Integer end;
        private Double score;

        public Builder detection(String detection) {
            this.detection = detection;
            return this;
        }

        public Builder detectionType(String detectionType) {
            this.detectionType = detectionType;
            return this;
        }

        public Builder start(Integer start) {
            this.start = start;
            return this;
        }

        public Builder end(Integer end) {
            this.end = end;
            return this;
        }

        public Builder score(Double score) {
            this.score = score;
            return this;
        }

        public WatsonxModerationResponseMetadata build() {
            return new WatsonxModerationResponseMetadata(this);
        }
    }
}
