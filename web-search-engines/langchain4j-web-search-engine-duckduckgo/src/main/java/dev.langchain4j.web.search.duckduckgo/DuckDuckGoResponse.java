package dev.langchain4j.web.search.duckduckgo;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

class DuckDuckGoResponse {

    @JsonProperty("Abstract")
    private String abstractText;

    @JsonProperty("AbstractText")
    private String abstractTextPlain;

    @JsonProperty("AbstractSource")
    private String abstractSource;

    @JsonProperty("AbstractURL")
    private String abstractUrl;

    @JsonProperty("Image")
    private String image;

    @JsonProperty("Heading")
    private String heading;

    @JsonProperty("Answer")
    private String answer;

    @JsonProperty("AnswerType")
    private String answerType;

    @JsonProperty("Definition")
    private String definition;

    @JsonProperty("DefinitionSource")
    private String definitionSource;

    @JsonProperty("DefinitionURL")
    private String definitionUrl;

    @JsonProperty("RelatedTopics")
    private List<DuckDuckGoSearchResult> relatedTopics;

    @JsonProperty("Results")
    private List<DuckDuckGoSearchResult> results;

    public DuckDuckGoResponse(String abstractText, String abstractTextPlain, String abstractSource,
                              String abstractUrl, String image, String heading, String answer,
                              String answerType, String definition, String definitionSource,
                              String definitionUrl, List<DuckDuckGoSearchResult> relatedTopics,
                              List<DuckDuckGoSearchResult> results) {
        this.abstractText = abstractText;
        this.abstractTextPlain = abstractTextPlain;
        this.abstractSource = abstractSource;
        this.abstractUrl = abstractUrl;
        this.image = image;
        this.heading = heading;
        this.answer = answer;
        this.answerType = answerType;
        this.definition = definition;
        this.definitionSource = definitionSource;
        this.definitionUrl = definitionUrl;
        this.relatedTopics = relatedTopics;
        this.results = results;
    }

    public DuckDuckGoResponse() {
    }

    public static DuckDuckGoResponseBuilder builder() {
        return new DuckDuckGoResponseBuilder();
    }

    public String getAbstractText() {
        return this.abstractText;
    }

    public String getAbstractTextPlain() {
        return this.abstractTextPlain;
    }

    public String getAbstractSource() {
        return this.abstractSource;
    }

    public String getAbstractUrl() {
        return this.abstractUrl;
    }

    public String getImage() {
        return this.image;
    }

    public String getHeading() {
        return this.heading;
    }

    public String getAnswer() {
        return this.answer;
    }

    public String getAnswerType() {
        return this.answerType;
    }

    public String getDefinition() {
        return this.definition;
    }

    public String getDefinitionSource() {
        return this.definitionSource;
    }

    public String getDefinitionUrl() {
        return this.definitionUrl;
    }

    public List<DuckDuckGoSearchResult> getRelatedTopics() {
        return this.relatedTopics;
    }

    public List<DuckDuckGoSearchResult> getResults() {
        return this.results;
    }

    public static class DuckDuckGoResponseBuilder {
        private String abstractText;
        private String abstractTextPlain;
        private String abstractSource;
        private String abstractUrl;
        private String image;
        private String heading;
        private String answer;
        private String answerType;
        private String definition;
        private String definitionSource;
        private String definitionUrl;
        private List<DuckDuckGoSearchResult> relatedTopics;
        private List<DuckDuckGoSearchResult> results;

        DuckDuckGoResponseBuilder() {
        }

        public DuckDuckGoResponseBuilder abstractText(String abstractText) {
            this.abstractText = abstractText;
            return this;
        }

        public DuckDuckGoResponseBuilder abstractTextPlain(String abstractTextPlain) {
            this.abstractTextPlain = abstractTextPlain;
            return this;
        }

        public DuckDuckGoResponseBuilder abstractSource(String abstractSource) {
            this.abstractSource = abstractSource;
            return this;
        }

        public DuckDuckGoResponseBuilder abstractUrl(String abstractUrl) {
            this.abstractUrl = abstractUrl;
            return this;
        }

        public DuckDuckGoResponseBuilder image(String image) {
            this.image = image;
            return this;
        }

        public DuckDuckGoResponseBuilder heading(String heading) {
            this.heading = heading;
            return this;
        }

        public DuckDuckGoResponseBuilder answer(String answer) {
            this.answer = answer;
            return this;
        }

        public DuckDuckGoResponseBuilder answerType(String answerType) {
            this.answerType = answerType;
            return this;
        }

        public DuckDuckGoResponseBuilder definition(String definition) {
            this.definition = definition;
            return this;
        }

        public DuckDuckGoResponseBuilder definitionSource(String definitionSource) {
            this.definitionSource = definitionSource;
            return this;
        }

        public DuckDuckGoResponseBuilder definitionUrl(String definitionUrl) {
            this.definitionUrl = definitionUrl;
            return this;
        }

        public DuckDuckGoResponseBuilder relatedTopics(List<DuckDuckGoSearchResult> relatedTopics) {
            this.relatedTopics = relatedTopics;
            return this;
        }

        public DuckDuckGoResponseBuilder results(List<DuckDuckGoSearchResult> results) {
            this.results = results;
            return this;
        }

        public DuckDuckGoResponse build() {
            return new DuckDuckGoResponse(this.abstractText, this.abstractTextPlain, this.abstractSource,
                    this.abstractUrl, this.image, this.heading, this.answer,
                    this.answerType, this.definition, this.definitionSource,
                    this.definitionUrl, this.relatedTopics, this.results);
        }

        public String toString() {
            return "DuckDuckGoResponse.DuckDuckGoResponseBuilder(abstractText=" + this.abstractText +
                    ", abstractTextPlain=" + this.abstractTextPlain + ", abstractSource=" + this.abstractSource +
                    ", abstractUrl=" + this.abstractUrl + ", image=" + this.image + ", heading=" + this.heading +
                    ", answer=" + this.answer + ", answerType=" + this.answerType + ", definition=" + this.definition +
                    ", definitionSource=" + this.definitionSource + ", definitionUrl=" + this.definitionUrl +
                    ", relatedTopics=" + this.relatedTopics + ", results=" + this.results + ")";
        }
    }
}
