package dev.langchain4j.data.document.splitter;

import static dev.langchain4j.internal.Utils.getOrDefault;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import dev.langchain4j.data.document.DefaultDocument;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.ValidationUtils;
import org.commonmark.Extension;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.markdown.CoreMarkdownNodeRenderer;
import org.commonmark.renderer.markdown.MarkdownNodeRendererContext;
import org.commonmark.renderer.markdown.MarkdownNodeRendererFactory;
import org.commonmark.renderer.markdown.MarkdownRenderer;

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

    private static final Set<Extension> EXTENSIONS = Set.of(TablesExtension.create());


    public static final String SECTION_LEVEL = "md_section_level";
    public static final String SECTION_HEADER = "md_section_header";
    public static final String SECTION_INDEX_WITHIN_PARENT = "md_section_index_in_parent";
    public static final String SECTION_PARENT_HEADER = "md_parent_header";

    private static final DocumentSplitter NO_SPLIT = document -> Collections.singletonList(document.toTextSegment());

    private static final String DEFAULT_EMPTY_SECTION_PLACEHOLDER_TEXT = ".";

    private final DocumentSplitter sectionSplitter;

    private final String documentTitle;

    private final String emptySectionPlaceholderText;

    private final YamlFrontMatterConsumer yamlFrontMatterConsumer;

    protected MarkdownSectionSplitter(Builder builder) {
        ValidationUtils.ensureNotNull(builder.getSectionSplitter(), "sectionSplitter");
        this.sectionSplitter = builder.getSectionSplitter();
        this.documentTitle = builder.getDocumentTitle();
        this.emptySectionPlaceholderText = getOrDefault(builder.getEmptySectionPlaceholderText(), DEFAULT_EMPTY_SECTION_PLACEHOLDER_TEXT);
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
        // Strip BOM (Byte Order Mark) if present at the beginning of the text.
        // The BOM character (\uFEFF) is commonly found in UTF-8 files created on Windows
        // and can prevent CommonMark from correctly parsing the first heading.
        String text = document.text();
        if (text != null && !text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }

        Set<Extension> parserExtensions = new HashSet<>(EXTENSIONS);
        if (yamlFrontMatterConsumer != null) {
            parserExtensions.add(YamlFrontMatterExtension.create());
        }
        Node node = Parser.builder().extensions(parserExtensions).build().parse(text);
        if (yamlFrontMatterConsumer != null) {
            YamlFrontMatterVisitor visitor = new YamlFrontMatterVisitor();
            node.accept(visitor);
            yamlFrontMatterConsumer.consumeFrontMatter(visitor.getData());
        }

        MarkdownSplitterContext context = new MarkdownSplitterContext(document.metadata(),
                emptySectionPlaceholderText,
                sectionSplitter,
                documentTitle);

        MarkdownRenderer renderer = MarkdownRenderer.builder()
                .nodeRendererFactory(new MarkdownSectionSplitterNodeRendererFactory(context))
                .extensions(EXTENSIONS)
                .build();
        // We use the Appendable allowed by the renderer as the hook in.
        renderer.render(node, context.getBuffer());
        context.endSection();

        return context.getSegments();
    }

    public static class Builder {
        private DocumentSplitter sectionSplitter = NO_SPLIT;
        private String documentTitle;
        private String emptySectionPlaceholderText;

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

        public YamlFrontMatterConsumer getYamlFrontMatterConsumer() {
            return yamlFrontMatterConsumer;
        }
    }

    /**
     * Sets a consumer to handle parsed YAML from the front-matter of a {@code Document}
     */
    public interface YamlFrontMatterConsumer {
        void consumeFrontMatter(Map<String, List<String>> frontMatterYaml);
    }

    /**
     * Context object that maintains state during markdown parsing and section extraction.
     * <p>
     * This class orchestrates the splitting process by:
     * <ul>
     *   <li>Buffering markdown content as the CommonMark renderer processes the document</li>
     *   <li>Tracking section boundaries based on heading locations</li>
     *   <li>Building a hierarchy of headers to capture document structure</li>
     *   <li>Creating text segments with appropriate metadata for each section</li>
     *   <li>Applying the configured sectionSplitter to further split each section</li>
     * </ul>
     * <p>
     * Instances are created during {@link #split(Document)} and used by
     * {@link HeaderSplittingCoreNodeRenderer} to signal section boundaries.
     */
    private class MarkdownSplitterContext {
        private final MarkdownSplitterBuffer buffer = new MarkdownSplitterBuffer();

        private final Metadata originalMetadata;
        private final List<TextSegment> segments = new ArrayList<>();
        private final List<Header> headers = new ArrayList<>();
        private final String emptySectionPlaceholderText;
        private final DocumentSplitter sectionSplitter;
        private final String documentTitle;
        private Header currentHeader;
        private int nullHeaderIndex = 0;

        MarkdownSplitterContext(Metadata metadata,
                                final String emptySectionPlaceholderText,
                                final DocumentSplitter sectionSplitter,
                                final String documentTitle) {
            this.originalMetadata = metadata;
            this.emptySectionPlaceholderText = emptySectionPlaceholderText;
            this.sectionSplitter = sectionSplitter;
            this.documentTitle = documentTitle;
        }

        MarkdownSplitterBuffer getBuffer() {
            return buffer;
        }

        /**
         * Finalizes the current section and processes it into text segments.
         * <p>
         * Called when a new section header is encountered or when the document ends.
         * Extracts the buffered content, associates it with the current header,
         * and converts it into text segments. Handles the special case where a
         * document doesn't start with a header by using the documentTitle if configured.
         */
        public void endSection() {
            String currentSection = buffer.rollover();
            // This should be the section
            if (currentHeader == null && headers.isEmpty() && !currentSection.isEmpty() && documentTitle != null) {
                currentHeader = new Header(documentTitle, 1);
            }
            if (currentHeader != null || !currentSection.isEmpty()) {
                Header header = currentHeader != null ? currentHeader : new Header(null, 1);
                addHeaderToHierarchy(header);
                addSectionSegments(header, currentSection);
            }
        }

        /**
         * Registers a new section header that was encountered during parsing.
         * <p>
         * Called by {@link HeaderSplittingCoreNodeRenderer} after rendering a heading.
         * The buffer is cleared because it contains the heading markup, which we don't
         * need since we extract the clean header text directly from the {@link Heading} node.
         *
         * @param heading the CommonMark heading node
         */
        public void newSectionHeaderFound(Heading heading) {
            // The buffer will contain the header, clean it out and read it directly from the Heading instead to
            // remove any markup characters.
            buffer.rollover();
            currentHeader = Header.create(heading);
        }

        /**
         * Processes a markdown section by creating text segments with appropriate metadata.
         * <p>
         * This method:
         * <ul>
         *   <li>Creates metadata for the section (level, header text, parent information)</li>
         *   <li>Handles empty sections by using placeholder text if configured</li>
         *   <li>Constructs a Document from the section text</li>
         *   <li>Splits the section using the configured sectionSplitter</li>
         *   <li>Adds the resulting segments to the overall segments list</li>
         * </ul>
         *
         * @param header the header information for this section
         * @param sectionText the text content of the section
         */
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
            Document document = new DefaultDocument(sectionText, metadata);

            List<TextSegment> sectionSegments = sectionSplitter.split(document);
            this.segments.addAll(sectionSegments);
        }


        /**
         * Adds a header to the document hierarchy and determines its parent-child relationships.
         * <p>
         * Traverses backwards through existing headers to find the nearest header with a lower
         * level (i.e., the most recent parent). For example, when processing an H3 header,
         * this finds the most recent H2 or H1 to set as its parent. If no parent is found,
         * the header becomes a top-level section.
         *
         * @param header the header to add to the hierarchy
         */
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
    }

    /**
     * An {@link Appendable} implementation that buffers markdown content during rendering.
     * <p>
     * This buffer is used as the output target for the CommonMark {@link MarkdownRenderer},
     * allowing us to intercept section boundaries and extract section content via the
     * {@link #rollover()} method.
     */
    private static class MarkdownSplitterBuffer implements Appendable {
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

        /**
         * Extracts the current buffer content and resets the buffer.
         * <p>
         * This method is called at section boundaries to extract the markdown text
         * for the current section before starting to accumulate the next section.
         *
         * @return the buffered content as a string
         */
        public String rollover() {
            String temp = current.toString();
            current = new StringBuilder();
            return temp;
        }

        @Override
        public String toString() {
            return current.toString();
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

    /**
     * Custom markdown renderer that intercepts heading nodes to detect section boundaries.
     * <p>
     * Extends {@link CoreMarkdownNodeRenderer} to leverage the standard CommonMark rendering
     * logic while adding section splitting capabilities. When a section-level heading is
     * encountered, this renderer signals the context to finalize the previous section and
     * start accumulating content for the new section.
     * <p>
     * Also customizes rendering for images (ignored) and indented code blocks (converted
     * to fenced code blocks for consistency).
     */
    private static class HeaderSplittingCoreNodeRenderer extends CoreMarkdownNodeRenderer {
        private final MarkdownSplitterContext splitterContext;

        public HeaderSplittingCoreNodeRenderer(MarkdownNodeRendererContext context,
                                               MarkdownSplitterContext splitterContext) {
            super(context);
            this.splitterContext = splitterContext;
        }

        /**
         * Handles heading nodes and determines if they represent section boundaries.
         * <p>
         * Section-level headings (those not nested inside other blocks like lists or blockquotes)
         * trigger section finalization. The sequence is:
         * <ol>
         *   <li>Finalize the previous section (extract buffered content)</li>
         *   <li>Render the heading normally (writes to buffer)</li>
         *   <li>Register the new section header (clear buffer, store header)</li>
         * </ol>
         * <p>
         * Headings nested inside other blocks are rendered normally without triggering
         * section boundaries.
         *
         * @param heading the heading node to process
         */
        @Override
        public void visit(Heading heading) {
            boolean isSectionHeader = !isHeadingInBlock(heading);

            if (isSectionHeader) {
                splitterContext.endSection();
            }

            super.visit(heading);

            if (isSectionHeader) {
                splitterContext.newSectionHeaderFound(heading);
            }
        }

        @Override
        public void visit(final Image image) {
            // Don't handle images
        }

        /**
         * Handles indented code blocks by converting them to fenced code blocks.
         * <p>
         * This normalization ensures consistent representation of code blocks in the
         * output segments, regardless of whether the original markdown used indentation
         * or fences to denote code blocks.
         *
         * @param indentedCodeBlock the indented code block node to process
         */
        @Override
        public void visit(final IndentedCodeBlock indentedCodeBlock) {
            // Translate from indented to fenced code blocks for consistency
            FencedCodeBlock fencedCodeBlock = new FencedCodeBlock();
            String literal = indentedCodeBlock.getLiteral();
            fencedCodeBlock.setLiteral(literal);
            super.visit(fencedCodeBlock);
        }

        /**
         * Determines if a heading is nested inside a block element.
         * <p>
         * Returns {@code true} if the heading's parent is something other than the
         * Document root or another Heading. This identifies headings that appear
         * within lists, blockquotes, or other container elements, which should not
         * trigger section boundaries.
         * <p>
         * Example of heading in a list item:
         * <pre>
         * - List item
         *
         *   ## Heading inside the list item
         *
         *   More content
         * </pre>
         *
         * @param heading the heading to check
         * @return {@code true} if the heading is nested in a block, {@code false} otherwise
         */
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


    /**
     * Represents a markdown header and its position in the document hierarchy.
     * <p>
     * Each Header maintains:
     * <ul>
     *   <li>The header text (extracted from the markdown heading)</li>
     *   <li>The header level (0-based: H1=0, H2=1, etc.)</li>
     *   <li>Parent-child relationships for building the document structure</li>
     *   <li>The index within its parent's children list</li>
     * </ul>
     */
    private static class Header {
        private final String text;
        private final int level;
        private Header parent;
        private final List<Header> children = new ArrayList<>();
        private int indexInParent;

        /**
         * Visitor that extracts clean text from a heading node.
         * <p>
         * Traverses the heading's children to extract text content, including
         * both regular text and HTML inline elements, while stripping out
         * markdown formatting characters.
         */
        private static class HeaderVisitor extends AbstractVisitor {
            private final StringBuilder buffer = new StringBuilder();

            @Override
            public void visit(final HtmlInline htmlInline) {
                buffer.append(htmlInline.getLiteral());
                super.visit(htmlInline);
            }

            @Override
            public void visit(final Text text) {
                buffer.append(text.getLiteral());
                super.visit(text);
            }
        }

        /**
         * Constructs a Header with the specified text and level.
         * <p>
         * Note: The level is stored as 0-based (the provided level is decremented by 1),
         * so H1 becomes 0, H2 becomes 1, etc.
         *
         * @param text the header text
         * @param level the 1-based header level from the markdown (1-6)
         */
        private Header(final String text, final int level) {
            this.text = text;
            this.level = level - 1;
        }

        /**
         * Creates a Header from a CommonMark {@link Heading} node.
         * <p>
         * Extracts the clean text content from the heading by visiting its child nodes,
         * which removes any markdown formatting characters.
         *
         * @param heading the CommonMark heading node
         * @return a new Header with the extracted text and level
         */
        private static Header create(Heading heading) {
            HeaderVisitor visitor = new HeaderVisitor();
            heading.accept(visitor);
            return new Header(visitor.buffer.toString(), heading.getLevel());
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

        /**
         * Adds a child header and establishes the parent-child relationship.
         * <p>
         * Sets the child's parent reference to this header and assigns it an index
         * based on its position in the children list. This is used to build the
         * document hierarchy and populate metadata like {@code md_section_index_in_parent}.
         *
         * @param child the child header to add
         */
        private void addChild(Header child) {
            children.add(child);
            child.indexInParent = children.size() - 1;
            child.parent = this;
        }
    }

}
