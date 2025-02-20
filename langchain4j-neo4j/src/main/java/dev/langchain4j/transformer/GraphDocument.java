package dev.langchain4j.transformer;

import dev.langchain4j.data.document.Document;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
public class GraphDocument {
    private Set<Node> nodes;
    private Set<Edge> relationships;
    private Document source;

    public GraphDocument(Set<GraphDocument.Node> nodes, Set<GraphDocument.Edge> relationships, Document source) {
        this.nodes = nodes;
        this.relationships = relationships;
        this.source = source;
    }

    @Getter
    @Setter
    @EqualsAndHashCode
    @ToString // for testing purpose
    public static class Node {
        private String id;
        private String type;

        public Node(String id, String type) {
            this.id = id;
            this.type = type;
        }
    }

    @Getter
    @EqualsAndHashCode
    @ToString // for testing purpose
    public static class Edge {
        private Node sourceNode;
        private Node targetNode;
        private String type;

        public Edge(final Node sourceNode, final Node targetNode, final String type) {
            this.sourceNode = sourceNode;
            this.targetNode = targetNode;
            this.type = type;
        }
    }
}
