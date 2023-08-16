package dev.langchain4j.store.embedding;

import com.google.gson.Gson;
import dev.langchain4j.internal.Json;

import java.util.List;

public class QueryResponse {

    private RootNode root;

    public RootNode getRoot() {
        return root;
    }

    public void setRoot(RootNode root) {
        this.root = root;
    }

    public static class RootNode {
        private List<ChildNode> children;

        public List<ChildNode> getChildren() {
            return children;
        }

        public void setChildren(List<ChildNode> children) {
            this.children = children;
        }
    }

    public static class ChildNode {
        private String id;
        private double relevance;
        private Fields fields;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public double getRelevance() {
            return relevance;
        }

        public void setRelevance(double relevance) {
            this.relevance = relevance;
        }

        public Fields getFields() {
            return fields;
        }

        public void setFields(Fields fields) {
            this.fields = fields;
        }
    }

    public static class Fields {
        private String textSegment;
        private Vector vector;

        public String getTextSegment() {
            return textSegment;
        }

        public void setTextSegment(String textSegment) {
            this.textSegment = textSegment;
        }

        public Vector getVector() {
            return vector;
        }

        public void setVector(Vector vector) {
            this.vector = vector;
        }
    }

    public static class Vector {
        private List<Float> values;

        public List<Float> getValues() {
            return values;
        }

        public void setValues(List<Float> values) {
            this.values = values;
        }
    }
    public static void main(String[] args) {
        String jsonStr = "{ \"root\": { \"children\": [ { \"id\": \"index:src/0/c4ca423891cc3638efdf800d\", \"relevance\": 0.8444931313891128, \"fields\": { \"text_segment\": \"text1\" } }, { \"id\": \"index:src/0/e4da3b7fa4629b94af9be5b2\", \"relevance\": 0.8402529150549546, \"fields\": { \"text_segment\": \"text2\" } } ] } }";

        QueryResponse structure = Json.fromJson(jsonStr, QueryResponse.class);

        // For demonstration purposes, print the parsed id of the first child
        System.out.println(structure.getRoot().getChildren().get(0).getId());
    }
}
