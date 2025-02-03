package dev.langchain4j.data.document.splitter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.ValidationUtils;
import org.commonmark.Extension;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.markdown.CoreMarkdownNodeRenderer;
import org.commonmark.renderer.markdown.MarkdownNodeRendererContext;
import org.commonmark.renderer.markdown.MarkdownNodeRendererFactory;
import org.commonmark.renderer.markdown.MarkdownRenderer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A {@link DocumentSplitter} that takes a Markdown as input.
 *
 * <p>The class is instantiated via the {@link Builder} returned from the {@link #builder()} method.</p>
 *
 * <p>Internally it splits the document into sections, with metadata entries to identify the location of each section
 * in the document. It then optionally splits each section with the {@link DocumentSplitter} passed in to the Builder.
 *
 */
public class MarkdownSectionSplitter implements DocumentSplitter {

    private static final Header NO_HEADER = new Header(null, 1);
    private static final Set<Extension> EXTENSIONS = Set.of(TablesExtension.create());


    public static final String SECTION_LEVEL = "md_section_level";
    public static final String SECTION_HEADER = "md_section_header";
    public static final String SECTION_INDEX_WITHIN_PARENT = "md_section_index_in_parent";
    public static final String SECTION_PARENT_HEADER = "md_parent_header";
    public static final String SEGMENT_LINKS = "segmen_links";
    private static final DocumentSplitter NO_SPLIT = document -> Collections.singletonList(document.toTextSegment());

    private final DocumentSplitter sectionSplitter;

    private final String documentTitle;

    private final String emptySectionPlaceholderText;

    private final LinkHandling linkHandling;

    private final DocumentAdjuster documentAdjuster;
    private final TextSegmentsAdjuster textSegmentsAdjuster;
    private final YamlFrontMatterConsumer yamlFrontMatterConsumer;

    protected MarkdownSectionSplitter(Builder builder) {
        ValidationUtils.ensureNotNull(builder.getSectionSplitter(), "sectionSplitter");
        ValidationUtils.ensureNotNull(builder.getLinkHandling(), "linkHandling");
        ValidationUtils.ensureNotNull(builder.getSectionSplitter(), "sectionSplitter");
        ValidationUtils.ensureNotNull(builder.getLinkHandling(), "linkHandling");
        ValidationUtils.ensureNotNull(builder.getDocumentAdjuster(), "documentAdjuster");
        ValidationUtils.ensureNotNull(builder.getTextSegmentAdjuster(), "textSegmentAdjuster");
        this.sectionSplitter = builder.getSectionSplitter();
        this.documentTitle = builder.getDocumentTitle();
        this.emptySectionPlaceholderText = builder.getEmptySectionPlaceholderText();
        this.linkHandling = builder.getLinkHandling();
        this.documentAdjuster = builder.getDocumentAdjuster();
        this.textSegmentsAdjuster = builder.getTextSegmentAdjuster();
        this.yamlFrontMatterConsumer = builder.getYamlFrontMatterConsumer();
    }

    /**
     * Creates a new Builder used to instantiate this class.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<TextSegment> split(Document document) {
        Set<Extension> parserExtensions = new HashSet<>(EXTENSIONS);
        if (yamlFrontMatterConsumer != null) {
            parserExtensions.add(YamlFrontMatterExtension.create());
        }
        Node node = Parser.builder().extensions(parserExtensions).build().parse(document.text());
        if (yamlFrontMatterConsumer != null) {
            YamlFrontMatterVisitor visitor = new YamlFrontMatterVisitor();
            node.accept(visitor);
            yamlFrontMatterConsumer.consumeFrontMatter(visitor.getData());
        }

        MarkdownSplitterContext context = new MarkdownSplitterContext(document.metadata());
        MarkdownRenderer renderer = MarkdownRenderer.builder()
                .nodeRendererFactory(new MarkdownSectionSplitterNodeRendererFactory(context))
                .extensions(EXTENSIONS)
                .build();
        // We use the Appendable allowed by the renderer as the hook in.
        // I tried a few other approaches, but this is the only one I can find that works...
        renderer.render(node, context.getBuffer());
        context.endSection();

        return context.getSegments();
    }

    public static class Builder {
        private DocumentSplitter sectionSplitter = NO_SPLIT;
        private String documentTitle;
        private String emptySectionPlaceholderText;

        private LinkHandling linkHandling = LinkHandling.STANDARD;

        private DocumentAdjuster documentAdjuster = new DocumentAdjuster() {};
        private TextSegmentsAdjuster textSegmentsAdjuster = new TextSegmentsAdjuster() {};
        private YamlFrontMatterConsumer yamlFrontMatterConsumer;

        /**
         * <p>Sets the {@link DocumentSplitter} to further split each section.</p>
         *
         * <p>If not specified, the {@link MarkdownSectionSplitter} created by this builder will not
         * attempt to split the sections further.</p>
         *
         * @param sectionSplitter the {@link DocumentSplitter} used to further split the sections.
         * @return this Builder instance
         */
        public Builder setSectionSplitter(DocumentSplitter sectionSplitter) {
            this.sectionSplitter = sectionSplitter;
            return this;
        }

        /**
         * <p>Sets the title of the source document</p>
         *
         * <p>This is for the corner case where a Markdown document does not start with a header. If the document title
         * is set, it is used as the header for the first section.</p>
         *
         * @param title the document title
         * @return this Builder instance
         */
        public Builder setDocumentTitle(String title) {
            this.documentTitle = title;
            return this;
        }

        /**
         * <p>Set placeholder text to be used for empty sections, i.e. ones that just have a header.</p>
         *
         * <p>This is needed because the {@link Document} constructor throws an error if the constructor is empty.</p>
         * @param text placeholder text
         * @return this Builder instance
         */
        public Builder setEmptySectionPlaceholderText(String text) {
            this.emptySectionPlaceholderText = text;
            return this;
        }

        /**
         * Sets the link handling for the splitter. The default is {@link LinkHandling#STRIP}.
         *
         * @param linkHandling the link handling
         * @return this builder
         */
        public Builder setLinkHandling(LinkHandling linkHandling) {
            this.linkHandling = linkHandling;
            return this;
        }

        /**
         * Sets an {@code DocumentAdjuster} to use to add/remove metadata in, or otherwise, adjust a {@link Document}
         * from an extracted section before further splitting it into {@link TextSegment}s.
         *
         * @param documentAdjuster the adjuster
         * @return this builder
         */
        public Builder setDocumentAdjuster(DocumentAdjuster documentAdjuster) {
            this.documentAdjuster = documentAdjuster;
            return this;
        }

        /**
         * Sets an {@code TextSegmentsAdjuster} to use to add/remove metadata in, or otherwise, adjust {@link TextSegment}s
         * split from a section
         *
         * @param textSegmentsAdjuster the adjuster
         * @return this builder
         */
        public Builder setTextSegmentsAdjuster(TextSegmentsAdjuster textSegmentsAdjuster) {
            this.textSegmentsAdjuster = textSegmentsAdjuster;
            return this;
        }

        /**
         * Sets a {@link YamlFrontMatterConsumer} to handle parsed YAML front matter
         * @param yamlFrontMatterConsumer
         * @return
         */
        public Builder setYamlFrontMatterConsumer(YamlFrontMatterConsumer yamlFrontMatterConsumer) {
            this.yamlFrontMatterConsumer = yamlFrontMatterConsumer;
            return this;
        }


        /**
         * Constructs the {@link MarkdownSectionSplitter} instance
         * @return the MarkdownSectionSplitter
         */
        public MarkdownSectionSplitter build() {
            return new MarkdownSectionSplitter(this);
        }

        public DocumentSplitter getSectionSplitter() {
            return sectionSplitter;
        }

        public String getDocumentTitle() {
            return documentTitle;
        }

        public String getEmptySectionPlaceholderText() {
            return emptySectionPlaceholderText;
        }

        public LinkHandling getLinkHandling() {
            return linkHandling;
        }

        public DocumentAdjuster getDocumentAdjuster() {
            return documentAdjuster;
        }

        public TextSegmentsAdjuster getTextSegmentAdjuster() {
            return textSegmentsAdjuster;
        }

        public YamlFrontMatterConsumer getYamlFrontMatterConsumer() {
            return yamlFrontMatterConsumer;
        }
    }

    /**
     * Callback to adjust a {@link  Document} representing a section before splitting it further.
     */
    public interface DocumentAdjuster {
        /**
         * Adjust a document representing a section.
         * @param original the document
         * @return the adjusted document
         */
        default Document adjust(Document original) {
            return original;
        }
    }

    public interface TextSegmentsAdjuster {
        /**
         * Adjust the TextSegments representing a section.
         *
         * @param originalSegments the TextSegments
         * @return the adjusted TextSegments
         */
        default List<TextSegment> adjust(List<TextSegment> originalSegments) {
            return originalSegments;
        }
    }

    /**
     * Sets a consumer to handle parsed YAML from the front-matter of a {@code Document}
     */
    public interface YamlFrontMatterConsumer {
        void consumeFrontMatter(Map<String, List<String>> frontMatterYaml);
    }

    public enum LinkHandling {
        /**
         * Doesn't do anything to the links. The markdown for links in the resulting {@code TextSegment}s is the same
         * as in the original.
         */
        STANDARD,
        /**
         * Strips any links out, just leaving the text. So {@code [A Link](https://z.com)} becomes just {@code A Link}.
         */
        STRIP,
        /**
         * The original link text is left in place, and the links are added to the metadata. So e.g. the following
         * markdown is left in place in the markdown:
         * <pre>
         *     Click [here](https://a.com) and [here](https://b.com)
         * </pre>
         * <p>
         * The above will result in the following Json string in the the metadata under the key "segment-links":
         * <pre>
         *     "{
         *          "[here](https://a.com)":"https://a.com",
         *          "[here](https://b.com)":"https://b.com"
         *      }"
         * </pre>
         * </p>
         */
        METADATA
    }

    private class MarkdownSplitterContext {
        private final MarkdownSplitterBuffer buffer = new MarkdownSplitterBuffer();

        private final Metadata originalMetadata;
        private final List<TextSegment> segments = new ArrayList<>();
        private final List<Header> headers = new ArrayList<>();
        private Header currentHeader;
        private int nullHeaderIndex = 0;
        private Map<String, String> currentSectionLinks = new HashMap<>();

        MarkdownSplitterContext(Metadata metadata) {
            originalMetadata = metadata;
        }

        MarkdownSplitterBuffer getBuffer() {
            return buffer;
        }

        LinkHandling getLinkHandling() {
            return linkHandling;
        }

        public void endSection() {
            String currentSection = buffer.rollover();
            // This should be the section
            if (currentHeader == null && headers.isEmpty() && !currentSection.isEmpty() && documentTitle != null) {
                currentHeader = new Header(documentTitle, 1);
            }
            if (currentHeader != null || !currentSection.isEmpty()) {
                Header header = currentHeader != null ? currentHeader : NO_HEADER;
                addHeaderToHierarchy(header);
                addSectionSegments(header, currentSection);
                currentSectionLinks.clear();
            }
        }

        public void newSectionHeaderFound(Heading heading) {
            // The buffer will contain the header, clean it out and read it directly from the Heading instead to
            // remove any markup characters.
            buffer.rollover();
            currentHeader = new Header(heading);
        }

        private void addSectionSegments(Header header, String sectionText) {
            // Set metadata about this particular section. Work on a copy
            Metadata metadata = new Metadata(originalMetadata.toMap());
            metadata.put(SECTION_LEVEL, header.level);
            if (header.text != null) {
                metadata.put(SECTION_HEADER, header.text);
            }
            metadata.put(SECTION_INDEX_WITHIN_PARENT, header.indexInParent);
            if (header.parent != null && header.parent.text != null) {
                metadata.put(SECTION_PARENT_HEADER, header.parent.text);
            }

            if (sectionText.isBlank() && emptySectionPlaceholderText != null) {
                // Document constructor does not like blank text
                sectionText = emptySectionPlaceholderText;
            }
            Document document = new Document(sectionText, metadata);
            document = documentAdjuster.adjust(document);

            List<TextSegment> segments = sectionSplitter.split(document);
            if (!currentSectionLinks.isEmpty()) {

                for (TextSegment segment : segments) {

                    Map<String, String> segmentLinks = null;
                    String segmentText = segment.text();

                    for (Map.Entry<String, String> entry : currentSectionLinks.entrySet()) {
                        if (segmentText.contains(entry.getKey())) {
                            if (segmentLinks == null) {
                                segmentLinks = new HashMap<>();
                            }
                            segmentLinks.put(entry.getKey(), entry.getValue());
                        }
                    }
                    if (segmentLinks != null) {
                        Gson gson = new GsonBuilder().create();
                        String serializedLinks = gson.toJson(segmentLinks);
                        segment.metadata().put(SEGMENT_LINKS, serializedLinks);
                    }
                }

            }
            segments = textSegmentsAdjuster.adjust(segments);
            this.segments.addAll(segments);
        }


        private void addHeaderToHierarchy(Header header) {
            if (!headers.isEmpty()) {
                for (ListIterator<Header> it = headers.listIterator(headers.size()); it.hasPrevious() ; ) {
                    Header curr = it.previous();
                    if (curr.level < header.level) {
                        curr.addChild(header);
                        break;
                    }
                }
            }

            headers.add(header);
            if (header.parent == null) {
                header.indexInParent = nullHeaderIndex++;
            }

        }

        public List<TextSegment> getSegments() {
            return segments;
        }

        public void addLink(String markdownLink, String destination) {
            currentSectionLinks.put(markdownLink, destination);
        }
    }

    private static class MarkdownSplitterBuffer implements Appendable {
        // Temporary for testing
        private StringBuilder current = new StringBuilder();

        @Override
        public Appendable append(final CharSequence csq) {
            current.append(csq);
            return this;
        }

        @Override
        public Appendable append(final CharSequence csq, final int start, final int end) {
            current.append(csq, start, end);
            return this;
        }

        @Override
        public Appendable append(final char c) {
            current.append(c);
            return this;
        }

        public String rollover() {
            String temp = current.toString();
            current = new StringBuilder();
            return temp;
        }

        public String current() {
            return current.toString();
        }

        @Override
        public String toString() {
            return current.toString();
        }

        int length() {
            return current.length();
        }
    }

    private static class MarkdownSectionSplitterNodeRendererFactory implements MarkdownNodeRendererFactory {
        private final MarkdownSplitterContext splitterContext;

        public MarkdownSectionSplitterNodeRendererFactory(MarkdownSplitterContext splitterContext) {
            this.splitterContext = splitterContext;
        }

        @Override
        public NodeRenderer create(final MarkdownNodeRendererContext context) {
            return new HeaderSplittingCoreNodeRenderer(context, splitterContext);
        }

        @Override
        public Set<Character> getSpecialCharacters() {
            return Set.of();
        }

    }

    private static class HeaderSplittingCoreNodeRenderer extends CoreMarkdownNodeRenderer {
        private final MarkdownSplitterContext context;

        public HeaderSplittingCoreNodeRenderer(MarkdownNodeRendererContext context, MarkdownSplitterContext splitterContext) {
            super(context);
            this.context = splitterContext;
        }

        @Override
        public void visit(Heading heading) {
            boolean isSectionHeader = !isHeadingInBlock(heading);

            if (isSectionHeader) {
                context.endSection();
            }

            super.visit(heading);

            if (isSectionHeader) {
                context.newSectionHeaderFound(heading);
            }
        }

        @Override
        public void visit(final Image image) {
            // Don't handle images
        }

        public void visit(final IndentedCodeBlock indentedCodeBlock) {
            // Translate from indented to fenced code blocks for consistency
            FencedCodeBlock fencedCodeBlock = new FencedCodeBlock();
            String literal = indentedCodeBlock.getLiteral();
            fencedCodeBlock.setLiteral(literal);
            super.visit(fencedCodeBlock);
        }

        @Override
        public void visit(final Link link) {
            switch (context.getLinkHandling()) {
                case STANDARD: {
                    super.visit(link);
                    break;
                }
                case STRIP: {
                    visitChildren(link);
                    return;
                }
                case METADATA: {
                    int before = context.getBuffer().length();
                    String destination = link.getDestination();
                    super.visit(link);
                    int after = context.getBuffer().length();

                    String contents = context.getBuffer().current();
                    String markdownLink = contents.substring(before, after);
                    context.addLink(markdownLink, destination);
                    break;
                }
                default:
                    throw new IllegalStateException("Unknown: " + context.getLinkHandling());
            }
        }

        private boolean isHeadingInBlock(Heading heading) {
            Node parent = heading.getParent();
            if (parent != null) {
                if (!(parent instanceof org.commonmark.node.Document) && !(parent instanceof Heading)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static class Header {
        private final String text;
        private final int level;
        private Header parent;
        private final List<Header> children = new ArrayList<>();
        private int indexInParent;

        Header(Heading heading) {
            this(
                    heading.getFirstChild() != null ? ((Text) heading.getFirstChild()).getLiteral() : null,
                    heading.getLevel());
        }

        private Header(final String text, final int level) {
            this.text = text;
            this.level = level - 1;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Header header = (Header) o;
            return level == header.level && Objects.equals(text, header.text);
        }

        @Override
        public int hashCode() {
            return Objects.hash(text, level);
        }

        private void addChild(Header child) {
            children.add(child);
            child.indexInParent = children.size() - 1;
            child.parent = this;
        }
    }

}
