package dev.langchain4j.data.document.splitter;

import static dev.langchain4j.data.document.splitter.MarkdownSectionSplitter.SECTION_HEADER;
import static dev.langchain4j.data.document.splitter.MarkdownSectionSplitter.SECTION_INDEX_WITHIN_PARENT;
import static dev.langchain4j.data.document.splitter.MarkdownSectionSplitter.SECTION_LEVEL;
import static dev.langchain4j.data.document.splitter.MarkdownSectionSplitter.SECTION_PARENT_HEADER;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentLoader;
import dev.langchain4j.data.document.DocumentSource;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.segment.TextSegment;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MarkdownSectionSplitterTest implements WithAssertions {

    @Test
    public void testNoSubSplitter() {
        String text = """
                # Title
                ## Section 1
                section 1
                ## Section 2
                section 2
                ### Section 2.1
                section 2.1
                #### Section 2.1.1
                section 2.1.1
                #### Section 2.1.2
                section 2.1.2
                more
                ### Section 2.2
                #### Section 2.2.1
                ## Section 3
                section 3
                #### Section 3.1.1
                section 3.1.1
                ## Section 4
                section 4\s
                # Header 1
                header
                """;

        DocumentSplitter splitter = MarkdownSectionSplitter.builder()
                .build();
        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        assertThat(segments.size()).isEqualTo(12);
        checkTextSegment(source, segments.get(0), "Title", null, 0, 0, ".");
        checkTextSegment(source, segments.get(1), "Section 1", "Title", 1, 0, "section 1");
        checkTextSegment(source, segments.get(2), "Section 2", "Title", 1, 1, "section 2");
        checkTextSegment(source, segments.get(3), "Section 2.1", "Section 2", 2, 0, "section 2.1");
        checkTextSegment(source, segments.get(4), "Section 2.1.1", "Section 2.1", 3, 0, "section 2.1.1");
        checkTextSegment(source, segments.get(5), "Section 2.1.2", "Section 2.1", 3, 1, "section 2.1.2\nmore");
        checkTextSegment(source, segments.get(6), "Section 2.2", "Section 2", 2, 1, ".");
        checkTextSegment(source, segments.get(7), "Section 2.2.1", "Section 2.2", 3, 0, ".");
        checkTextSegment(source, segments.get(8), "Section 3", "Title", 1, 2, "section 3");
        checkTextSegment(source, segments.get(9), "Section 3.1.1", "Section 3", 3, 0, "section 3.1.1");
        checkTextSegment(source, segments.get(10), "Section 4", "Title", 1, 3, "section 4");
        checkTextSegment(source, segments.get(11), "Header 1", null, 0, 1, "header");
    }

    @Test
    public void testIntroductoryTextNoHeaderNoDocumentTitle() {
        String text = """
                Intro text
                ## Section 1
                section 1
                """;

        DocumentSplitter splitter = MarkdownSectionSplitter.builder().build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        assertThat(segments.size()).isEqualTo(2);
        checkTextSegment(source, segments.get(0), null, null, 0, 0, "Intro text");
        checkTextSegment(source, segments.get(1), "Section 1", null, 1, 0, "section 1");
    }

    @Test
    public void testIntroductoryTextNoHeaderWithDocumentTitle() {
        String text = """
                Intro text
                ## Section 1
                section 1
                """;

        DocumentSplitter splitter =
                MarkdownSectionSplitter.builder().setDocumentTitle("Doc Title").build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        assertThat(segments.size()).isEqualTo(2);
        checkTextSegment(source, segments.get(0), "Doc Title", null, 0, 0, "Intro text");
        checkTextSegment(source, segments.get(1), "Section 1", "Doc Title", 1, 0, "section 1");
    }

    @Test
    public void testSectionSplitter() {
        String text = """
                # Title
                ## Section 1
                section 1
                ## Section 2
                section 2 split
                ### Section 2.1
                section 2.1
                ### Section 2.2
                section 2.2 split
                """;

        DocumentSplitter splitter = MarkdownSectionSplitter.builder()
                .setDocumentTitle("Doc Title")
                .setSectionSplitter(DocumentSplitters.recursive(11, 0))
                .build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        assertThat(segments.size()).isEqualTo(7);
        checkTextSegment(source, segments.get(0), "Title", null, 0, 0, ".");
        assertThat(segments.get(0).metadata().getInteger("index")).isEqualTo(0);

        checkTextSegment(source, segments.get(1), "Section 1", "Title", 1, 0, "section 1");
        assertThat(segments.get(1).metadata().getInteger("index")).isEqualTo(0);

        checkTextSegment(source, segments.get(2), "Section 2", "Title", 1, 1, "section 2");
        assertThat(segments.get(2).metadata().getInteger("index")).isEqualTo(0);

        checkTextSegment(source, segments.get(3), "Section 2", "Title", 1, 1, "split");
        assertThat(segments.get(3).metadata().getInteger("index")).isEqualTo(1);

        checkTextSegment(source, segments.get(4), "Section 2.1", "Section 2", 2, 0, "section 2.1");
        assertThat(segments.get(4).metadata().getInteger("index")).isEqualTo(0);

        checkTextSegment(source, segments.get(5), "Section 2.2", "Section 2", 2, 1, "section 2.2");
        assertThat(segments.get(5).metadata().getInteger("index")).isEqualTo(0);

        checkTextSegment(source, segments.get(6), "Section 2.2", "Section 2", 2, 1, "split");
        assertThat(segments.get(6).metadata().getInteger("index")).isEqualTo(1);
    }

    @Test
    public void testHeaderInFencedCodeBlock() {
        // The parser adds a blank line between the previous paragraph and the code block.
        // Test input with both cases
        String text = """
                # Title
                ## Section 1
                section 1
                ```
                # In Code
                ```
                ## Section 2
                section 2

                ```
                # In Code
                ```
                """;

        DocumentSplitter splitter = MarkdownSectionSplitter.builder().build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        assertThat(segments.size()).isEqualTo(3);

        checkTextSegment(source, segments.get(0), "Title", null, 0, 0, ".");
        checkTextSegment(source, segments.get(1), "Section 1", "Title", 1, 0, "section 1\n\n```\n# In Code\n```");
        checkTextSegment(source, segments.get(2), "Section 2", "Title", 1, 1, "section 2\n\n```\n# In Code\n```");
    }

    @Test
    public void testCodeSpan() {
        String text = """
                # Title
                ## Section 1
                section 1 is `the best` ever
                """;

        DocumentSplitter splitter = MarkdownSectionSplitter.builder().build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        assertThat(segments.size()).isEqualTo(2);

        checkTextSegment(source, segments.get(0), "Title", null, 0, 0, ".");
        checkTextSegment(source, segments.get(1), "Section 1", "Title", 1, 0, "section 1 is `the best` ever");
    }

    @Test
    public void testFencedCodeBlock() {
        // The renderer adds empty lines around code blocks
        // Test some variations of the input.
        String text = """
                # Title
                Some text
                ```
                    function(){
                       this.i++;
                \t}
                ```
                More text

                ```
                    print(x);
                ```

                Final text""";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder().build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(1, segments.size());
        checkTextSegment(source, segments.get(0), "Title", null, 0, 0,
                """
                        Some text

                        ```
                            function(){
                               this.i++;
                        \t}
                        ```

                        More text

                        ```
                            print(x);
                        ```

                        Final text""");

    }

    @Test
    public void testIndentedCodeBlock() {
        // The renderer adds empty lines around code blocks
        // Test some variations of the input.
        // In the output Markdown, we use the fenced style always for consistency.
        String text = """
                # Title
                Some text

                        function(){
                           this.i++;
                \t    }
                More text

                        print(x);
                Final text""";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder().build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(1, segments.size());
        checkTextSegment(source, segments.get(0), "Title", null, 0, 0,
                """
                        Some text

                        ```
                            function(){
                               this.i++;
                            }
                        ```

                        More text

                        ```
                            print(x);
                        ```

                        Final text""");

    }

    @Test
    public void testParagraphs() {
        // More than two '\n\n' gets replaced with just one.
        String text = """
                # Title
                Paragraph 1

                Paragraph2


                Paragraph3""";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder().build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(1, segments.size());
        checkTextSegment(source, segments.get(0), "Title", null, 0, 0,
                "Paragraph 1\n\nParagraph2\n\nParagraph3");
    }

    @Test
    public void testEmphasis() {
        //The renderer replaces '__' with '**'. They have the same meaning.
        String text = """
                # Title
                The *quick* brown _fox_ jumped **over** the __lazy__ dog""";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder().build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(1, segments.size());
        checkTextSegment(source, segments.get(0), "Title", null, 0, 0,
                "The *quick* brown _fox_ jumped **over** the **lazy** dog");
    }

    @Test
    public void testSetextHeaders() {
        // We are testing ATX Headers elsewhere (they are of the format "# Header 1", "## Header 2" etc.)
        // Setext uses equals under a line for H1, and hyphens for H2
        String text = """
                Title
                =====
                intro

                Section 1
                ----
                section 1
                """;

        DocumentSplitter splitter = MarkdownSectionSplitter.builder()
                .build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(2, segments.size());

        checkTextSegment(source, segments.get(0), "Title", null, 0, 0, "intro");
        checkTextSegment(source, segments.get(1), "Section 1", "Title", 1, 0, "section 1");
    }

    @Test
    public void testBulletList() {
        // The renderer adds empty lines around the lists.
        // Test some variations of the input.
        String text = """
                # Title
                intro
                - One
                - Two `test` two

                After text

                * First
                * Second""";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder()
                .build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(1, segments.size());
        checkTextSegment(source, segments.get(0), "Title", null, 0, 0,
                "intro\n\n- One\n- Two `test` two\n\nAfter text\n\n* First\n* Second");
    }

    @Test
    public void testOrderedList() {
        // The renderer adds empty lines around the lists.
        // Test some variations of the input.
        String text = """
                # Title
                intro
                1. One
                2. Two `test` two

                After text

                1. First
                2. Second""";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder()
                .build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(1, segments.size());
        checkTextSegment(source, segments.get(0), "Title", null, 0, 0,
                "intro\n\n1. One\n2. Two `test` two\n\nAfter text\n\n1. First\n2. Second");
    }

    @Test
    public void testNestedLists() {
        // The renderer adds empty lines around the lists.
        // Test some variations of the input.
        // Note that nested lists of ordered lists need at least 3 spaces, while nested lists in
        // bullet lists can do with 2.
        String body = """
                intro

                * One
                * Two
                  * 2-1
                  * 2-2
                    1. 2-2-1
                       1. 2-2-1-1
                       2. 2-2-1-2
                    2. 2-2-2
                * Three
                  1. 3-1
                     * 3-1-1
                     * 3-1-2
                       * 3-1-2-1
                       * 3-1-2-2
                     * 3-1-3""";


        String text = """
                # Title
                """ +
                body;


        DocumentSplitter splitter = MarkdownSectionSplitter.builder()
                .build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(1, segments.size());
        checkTextSegment(source, segments.get(0), "Title", null, 0, 0, body.trim());
    }

    @Test
    public void testBlockQuotes() {
        // The renderer adds empty lines around the lists.
        // Test some variations of the input.
        String text = """
                # Title
                intro
                > line1
                >
                >line2
                line3

                Other text

                > # Ignored header

                Final text""";

        // The renderer massages the continuing 'line3' a bit, and adds a space after '>' but it is semantically the same.
        String expected = """
                intro

                > line1
                >\s
                > line2
                > line3

                Other text

                > # Ignored header

                Final text""";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder().build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(1, segments.size());
        checkTextSegment(source, segments.get(0), "Title", null, 0, 0, expected);

    }

    @Test
    public void testNestedBlockQuotes() {
        String body = """
                > Test
                >\s
                > > # Ignored header
                > >\s
                > > 1. One
                > > 2. Two""";

        String text = """
                # Title

                """ + body;

        DocumentSplitter splitter = MarkdownSectionSplitter.builder().build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(1, segments.size());
        checkTextSegment(source, segments.get(0), "Title", null, 0, 0, body);

    }

    @Test
    public void testImagesRemoved() {
        // I don't think images are relevant at this stage so let's check they are removed
        String text = """
                # Title

                intro
                ![link](/uri)
                outro
                """;

        DocumentSplitter splitter = MarkdownSectionSplitter.builder().build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(1, segments.size());

        checkTextSegment(source, segments.get(0), "Title", null, 0, 0,
                "intro\n\noutro");
    }

    @Test
    public void testLinks() {
        String text = """
                # Title

                intro [A Link](https://a.com "testA").""";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder()
                .build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(1, segments.size());

        checkTextSegment(source, segments.get(0), "Title", null, 0, 0,
                "intro [A Link](https://a.com \"testA\").");
    }

    @Test
    public void testTables() {
        String text = """
                # Title

                intro

                | H1 | H2 |
                |----|----|
                | 1  | 2  |
                | 3  | 4  |

                outro""";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder()
                .build();
        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(1, segments.size());

        checkTextSegment(source, segments.get(0), "Title", null, 0, 0,
                "intro\n\n|H1|H2|\n|---|---|\n|1|2|\n|3|4|\n\noutro");

    }

    @Test
    public void testYamlFrontMatter() {
        String text = """
                ---
                hello: world
                empty:
                ---
                # Title

                intro
                """;

        Map<String, List<String>> frontMatter = new HashMap<>();

        DocumentSplitter splitter = MarkdownSectionSplitter.builder()
                .setYamlFrontMatterConsumer(frontMatter::putAll)
                .build();
        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(1, segments.size());

        checkTextSegment(source, segments.get(0), "Title", null, 0, 0,
                "intro");
        Assertions.assertEquals(2, frontMatter.size());
        Assertions.assertEquals(1, frontMatter.get("hello").size());
        Assertions.assertEquals("world", frontMatter.get("hello").get(0));
        Assertions.assertEquals(0, frontMatter.get("empty").size());
    }

    @Test
    public void testHtmlInHeaders() {
        String text = """
                # 1 <ejb>

                intro
                ## 1.1 <stateful>

                stateful
                ## 1.2 <stateless>

                stateless
                ## **<mdb>**

                mdb
                """;

        Map<String, List<String>> frontMatter = new HashMap<>();

        DocumentSplitter splitter = MarkdownSectionSplitter.builder()
                .setYamlFrontMatterConsumer(frontMatter::putAll)
                .build();
        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(4, segments.size());

        checkTextSegment(source, segments.get(0), "1 <ejb>", null, 0, 0,
                "intro");
        checkTextSegment(source, segments.get(1), "1.1 <stateful>", "1 <ejb>", 1, 0,
                "stateful");
        checkTextSegment(source, segments.get(2), "1.2 <stateless>", "1 <ejb>", 1, 1,
                "stateless");
        // The visitor used to parse Headings doesn't care about emphasis markup
        checkTextSegment(source, segments.get(3), "<mdb>", "1 <ejb>", 1, 2,
                "mdb");


    }

    @Test
    public void testBOM() {
        // BOM (Byte Order Mark) at the beginning of the file should be handled correctly
        String text = """
                \uFEFF# Title

                ## Section 1

                section 1
                ## Section 2

                section 2
                """;

        DocumentSplitter splitter = MarkdownSectionSplitter.builder()
                .build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(3, segments.size());
        checkTextSegment(source, segments.get(0), "Title", null, 0, 0, ".");
        checkTextSegment(source, segments.get(1), "Section 1", "Title", 1, 0, "section 1");
        checkTextSegment(source, segments.get(2), "Section 2", "Title", 1, 1, "section 2");
    }

    @Test
    public void testHeadersInsideBlocks() {
        // Headers inside blocks (lists, blockquotes) should not create new sections
        String text = """
                # Section 1

                Introduction

                - List item 1

                  ## Heading in list item

                  Content under heading in list

                - List item 2

                > ## Heading in blockquote
                >
                > Content in blockquote

                ## Section 2

                Section 2 content
                """;

        DocumentSplitter splitter = MarkdownSectionSplitter.builder().build();
        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        // Should only have 2 sections: "Section 1" and "Section 2"
        // The headers inside the list and blockquote should NOT create sections
        Assertions.assertEquals(2, segments.size());

        // Section 1 should contain the list with the embedded heading and the blockquote
        // The renderer adds spaces to blank lines within lists and blockquotes to maintain structure
        String expectedSection1 = """
                Introduction

                - List item 1
                 \s
                  ## Heading in list item
                 \s
                  Content under heading in list

                - List item 2

                > ## Heading in blockquote
                >\s
                > Content in blockquote""";
        checkTextSegment(source, segments.get(0), "Section 1", null, 0, 0,
                expectedSection1);

        // Section 2 should be a separate section
        checkTextSegment(source, segments.get(1), "Section 2", "Section 1", 1, 0,
                "Section 2 content");
    }

    private Document createDocument(String text) {
        DocumentSource loader = new StringDocumentSource(text);
        Document doc = DocumentLoader.load(loader, new TextDocumentParser());
        doc.metadata().put("doc-a", "DOC-A");
        doc.metadata().put("doc-b", "DOC-B");

        return doc;
    }

    private void checkTextSegment(
            Document source,
            TextSegment ts,
            String header,
            String parentHeader,
            int level,
            int indexInParent,
            String text) {
        assertThat(ts.metadata().getString(SECTION_HEADER)).isEqualTo(header);
        assertThat(ts.metadata().getString(SECTION_PARENT_HEADER)).isEqualTo(parentHeader);
        assertThat(ts.metadata().getInteger(SECTION_LEVEL).intValue()).isEqualTo(level);
        assertThat(ts.metadata().getInteger(SECTION_INDEX_WITHIN_PARENT)).isEqualTo(indexInParent);
        assertThat(ts.text().trim()).isEqualTo(text);

        for (String key : source.metadata().toMap().keySet()) {
            assertThat(ts.metadata().getString(key)).isEqualTo(source.metadata().getString(key));
        }
    }

    private record StringDocumentSource(String text) implements DocumentSource {

        @Override
        public InputStream inputStream() throws IOException {
            return new ByteArrayInputStream(text.getBytes("UTF-8"));
        }

        @Override
        public Metadata metadata() {
            return new Metadata();
        }
    }
}
