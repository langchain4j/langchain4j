package dev.langchain4j.store.embedding.vespa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
class QueryResponse {

    private RootNode root;

    public QueryResponse(RootNode root) {
        this.root = root;
    }

    public QueryResponse() {}

    public RootNode getRoot() {
        return this.root;
    }

    public void setRoot(RootNode root) {
        this.root = root;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class RootNode {

        private List<Record> children;

        public RootNode(List<Record> children) {
            this.children = children;
        }

        public RootNode() {}

        public List<Record> getChildren() {
            return this.children;
        }

        public void setChildren(List<Record> children) {
            this.children = children;
        }
    }
}
