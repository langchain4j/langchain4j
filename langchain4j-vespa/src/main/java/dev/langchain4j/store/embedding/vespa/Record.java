package dev.langchain4j.store.embedding.vespa;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class Record {

    private String id;
    private Double relevance;
    private Fields fields;

    public Record(String id, Double relevance, Fields fields) {
        this.id = id;
        this.relevance = relevance;
        this.fields = fields;
    }

    public Record() {}

    public static RecordBuilder builder() {
        return new RecordBuilder();
    }

    public String getId() {
        return this.id;
    }

    public Double getRelevance() {
        return this.relevance;
    }

    public Fields getFields() {
        return this.fields;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setRelevance(Double relevance) {
        this.relevance = relevance;
    }

    public void setFields(Fields fields) {
        this.fields = fields;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(NON_NULL)
    @JsonNaming(SnakeCaseStrategy.class)
    static class Fields {

        private String documentid;
        private String textSegment;
        private Vector vector;

        public Fields(String documentid, String textSegment, Vector vector) {
            this.documentid = documentid;
            this.textSegment = textSegment;
            this.vector = vector;
        }

        public Fields() {}

        public static FieldsBuilder builder() {
            return new FieldsBuilder();
        }

        public String getDocumentid() {
            return this.documentid;
        }

        public String getTextSegment() {
            return this.textSegment;
        }

        public Vector getVector() {
            return this.vector;
        }

        public void setDocumentid(String documentid) {
            this.documentid = documentid;
        }

        public void setTextSegment(String textSegment) {
            this.textSegment = textSegment;
        }

        public void setVector(Vector vector) {
            this.vector = vector;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        static class Vector {

            private List<Float> values;

            public Vector(List<Float> values) {
                this.values = values;
            }

            public Vector() {}

            public static VectorBuilder builder() {
                return new VectorBuilder();
            }

            public List<Float> getValues() {
                return this.values;
            }

            public void setValues(List<Float> values) {
                this.values = values;
            }

            public static class VectorBuilder {

                private List<Float> values;

                VectorBuilder() {}

                public VectorBuilder values(List<Float> values) {
                    this.values = values;
                    return this;
                }

                public Vector build() {
                    return new Vector(this.values);
                }
            }
        }

        public static class FieldsBuilder {

            private String documentid;
            private String textSegment;
            private Vector vector;

            FieldsBuilder() {}

            public FieldsBuilder documentid(String documentid) {
                this.documentid = documentid;
                return this;
            }

            public FieldsBuilder textSegment(String textSegment) {
                this.textSegment = textSegment;
                return this;
            }

            public FieldsBuilder vector(Vector vector) {
                this.vector = vector;
                return this;
            }

            public Fields build() {
                return new Fields(this.documentid, this.textSegment, this.vector);
            }
        }
    }

    public static class RecordBuilder {

        private String id;
        private Double relevance;
        private Fields fields;

        RecordBuilder() {}

        public RecordBuilder id(String id) {
            this.id = id;
            return this;
        }

        public RecordBuilder relevance(Double relevance) {
            this.relevance = relevance;
            return this;
        }

        public RecordBuilder fields(Fields fields) {
            this.fields = fields;
            return this;
        }

        public Record build() {
            return new Record(this.id, this.relevance, this.fields);
        }
    }
}
