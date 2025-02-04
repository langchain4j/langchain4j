package dev.langchain4j.data.document.splitter;

import org.commonmark.Extension;
import org.commonmark.internal.renderer.NodeRendererMap;
import org.commonmark.node.Node;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.Renderer;
import org.commonmark.renderer.markdown.CoreMarkdownNodeRenderer;
import org.commonmark.renderer.markdown.MarkdownNodeRendererContext;
import org.commonmark.renderer.markdown.MarkdownNodeRendererFactory;
import org.commonmark.renderer.markdown.MarkdownRenderer;
import org.commonmark.renderer.markdown.MarkdownWriter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Temporary replacement for MarkdownRenderer, including the fix in
 * https://github.com/commonmark/commonmark-java/pull/361.
 *
 * It contains that fix, and adjustments needed to be able to use this replacement.
 */
class TempMarkdownRenderer implements Renderer {

    private final List<MarkdownNodeRendererFactory> nodeRendererFactories;

    private TempMarkdownRenderer(Builder builder) {
        this.nodeRendererFactories = new ArrayList<>(builder.nodeRendererFactories.size() + 1);
        this.nodeRendererFactories.addAll(builder.nodeRendererFactories);
        // Add as last. This means clients can override the rendering of core nodes if they want.
        this.nodeRendererFactories.add(new MarkdownNodeRendererFactory() {
            @Override
            public NodeRenderer create(MarkdownNodeRendererContext context) {
                return new CoreMarkdownNodeRenderer(context);
            }

            @Override
            public Set<Character> getSpecialCharacters() {
                return Set.of();
            }
        });
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void render(Node node, Appendable output) {
        RendererContext context = new RendererContext(new MarkdownWriter(output));
        context.render(node);
    }

    @Override
    public String render(Node node) {
        StringBuilder sb = new StringBuilder();
        render(node, sb);
        return sb.toString();
    }

    static class Builder extends MarkdownRenderer.Builder {

        private final List<MarkdownNodeRendererFactory> nodeRendererFactories = new ArrayList<>();

        public TempMarkdownRenderer tempBuild() {
            return new TempMarkdownRenderer(this);
        }

        public Builder nodeRendererFactory(MarkdownNodeRendererFactory nodeRendererFactory) {
            this.nodeRendererFactories.add(nodeRendererFactory);
            return this;
        }

        public Builder extensions(Iterable<? extends Extension> extensions) {
            for (Extension extension : extensions) {
                if (extension instanceof MarkdownRenderer.MarkdownRendererExtension) {
                    MarkdownRenderer.MarkdownRendererExtension markdownRendererExtension = (MarkdownRenderer.MarkdownRendererExtension) extension;
                    markdownRendererExtension.extend(this);
                }
            }
            return this;
        }
    }

    private class RendererContext implements MarkdownNodeRendererContext {
        private final MarkdownWriter writer;
        private final NodeRendererMap nodeRendererMap = new NodeRendererMap();
        private final Set<Character> additionalTextEscapes;

        private RendererContext(MarkdownWriter writer) {
            // Set fields that are used by interface
            this.writer = writer;
            Set<Character> escapes = new HashSet<>();
            for (MarkdownNodeRendererFactory factory : nodeRendererFactories) {
                escapes.addAll(factory.getSpecialCharacters());
            }
            additionalTextEscapes = Collections.unmodifiableSet(escapes);

            // The first node renderer for a node type "wins". The NodeRendererMap
            // disallows overwriting.
            for (MarkdownNodeRendererFactory nodeRendererFactory : nodeRendererFactories) {
                // Pass in this as context here, which uses the fields set above
                NodeRenderer nodeRenderer = nodeRendererFactory.create(this);
                nodeRendererMap.add(nodeRenderer);
            }
        }

        @Override
        public MarkdownWriter getWriter() {
            return writer;
        }

        @Override
        public void render(Node node) {
            nodeRendererMap.render(node);
        }

        @Override
        public Set<Character> getSpecialCharacters() {
            return additionalTextEscapes;
        }
    }
}
