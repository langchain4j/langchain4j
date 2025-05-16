package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
class GoogleAiEmbeddingRequest {
    String model;
    GeminiContent content;
    GoogleAiEmbeddingModel.TaskType taskType;
    String title;
    Integer outputDimensionality;

    @JsonCreator
    GoogleAiEmbeddingRequest(
            @JsonProperty("model") String model,
            @JsonProperty("content") GeminiContent content,
            @JsonProperty("taskType") GoogleAiEmbeddingModel.TaskType taskType,
            @JsonProperty("title") String title,
            @JsonProperty("outputDimensionality") Integer outputDimensionality) {
        this.model = model;
        this.content = content;
        this.taskType = taskType;
        this.title = title;
        this.outputDimensionality = outputDimensionality;
    }

    public static GoogleAiEmbeddingRequestBuilder builder() {
        return new GoogleAiEmbeddingRequestBuilder();
    }

    public String getModel() {
        return this.model;
    }

    public GeminiContent getContent() {
        return this.content;
    }

    public GoogleAiEmbeddingModel.TaskType getTaskType() {
        return this.taskType;
    }

    public String getTitle() {
        return this.title;
    }

    public Integer getOutputDimensionality() {
        return this.outputDimensionality;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setContent(GeminiContent content) {
        this.content = content;
    }

    public void setTaskType(GoogleAiEmbeddingModel.TaskType taskType) {
        this.taskType = taskType;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setOutputDimensionality(Integer outputDimensionality) {
        this.outputDimensionality = outputDimensionality;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GoogleAiEmbeddingRequest)) return false;
        final GoogleAiEmbeddingRequest other = (GoogleAiEmbeddingRequest) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$model = this.getModel();
        final Object other$model = other.getModel();
        if (this$model == null ? other$model != null : !this$model.equals(other$model)) return false;
        final Object this$content = this.getContent();
        final Object other$content = other.getContent();
        if (this$content == null ? other$content != null : !this$content.equals(other$content)) return false;
        final Object this$taskType = this.getTaskType();
        final Object other$taskType = other.getTaskType();
        if (this$taskType == null ? other$taskType != null : !this$taskType.equals(other$taskType)) return false;
        final Object this$title = this.getTitle();
        final Object other$title = other.getTitle();
        if (this$title == null ? other$title != null : !this$title.equals(other$title)) return false;
        final Object this$outputDimensionality = this.getOutputDimensionality();
        final Object other$outputDimensionality = other.getOutputDimensionality();
        if (this$outputDimensionality == null ? other$outputDimensionality != null : !this$outputDimensionality.equals(other$outputDimensionality))
            return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GoogleAiEmbeddingRequest;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $model = this.getModel();
        result = result * PRIME + ($model == null ? 43 : $model.hashCode());
        final Object $content = this.getContent();
        result = result * PRIME + ($content == null ? 43 : $content.hashCode());
        final Object $taskType = this.getTaskType();
        result = result * PRIME + ($taskType == null ? 43 : $taskType.hashCode());
        final Object $title = this.getTitle();
        result = result * PRIME + ($title == null ? 43 : $title.hashCode());
        final Object $outputDimensionality = this.getOutputDimensionality();
        result = result * PRIME + ($outputDimensionality == null ? 43 : $outputDimensionality.hashCode());
        return result;
    }

    public String toString() {
        return "GoogleAiEmbeddingRequest(model=" + this.getModel() + ", content=" + this.getContent() + ", taskType=" + this.getTaskType() + ", title=" + this.getTitle() + ", outputDimensionality=" + this.getOutputDimensionality() + ")";
    }

    public static class GoogleAiEmbeddingRequestBuilder {
        private String model;
        private GeminiContent content;
        private GoogleAiEmbeddingModel.TaskType taskType;
        private String title;
        private Integer outputDimensionality;

        GoogleAiEmbeddingRequestBuilder() {
        }

        public GoogleAiEmbeddingRequestBuilder model(String model) {
            this.model = model;
            return this;
        }

        public GoogleAiEmbeddingRequestBuilder content(GeminiContent content) {
            this.content = content;
            return this;
        }

        public GoogleAiEmbeddingRequestBuilder taskType(GoogleAiEmbeddingModel.TaskType taskType) {
            this.taskType = taskType;
            return this;
        }

        public GoogleAiEmbeddingRequestBuilder title(String title) {
            this.title = title;
            return this;
        }

        public GoogleAiEmbeddingRequestBuilder outputDimensionality(Integer outputDimensionality) {
            this.outputDimensionality = outputDimensionality;
            return this;
        }

        public GoogleAiEmbeddingRequest build() {
            return new GoogleAiEmbeddingRequest(this.model, this.content, this.taskType, this.title, this.outputDimensionality);
        }

        public String toString() {
            return "GoogleAiEmbeddingRequest.GoogleAiEmbeddingRequestBuilder(model=" + this.model + ", content=" + this.content + ", taskType=" + this.taskType + ", title=" + this.title + ", outputDimensionality=" + this.outputDimensionality + ")";
        }
    }
}
