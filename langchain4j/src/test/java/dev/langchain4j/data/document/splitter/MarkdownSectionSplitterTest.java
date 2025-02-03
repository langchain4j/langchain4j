package dev.langchain4j.data.document.splitter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.data.document.splitter.MarkdownSectionSplitter.SECTION_HEADER;
import static dev.langchain4j.data.document.splitter.MarkdownSectionSplitter.SECTION_INDEX_WITHIN_PARENT;
import static dev.langchain4j.data.document.splitter.MarkdownSectionSplitter.SECTION_LEVEL;
import static dev.langchain4j.data.document.splitter.MarkdownSectionSplitter.SECTION_PARENT_HEADER;
import static dev.langchain4j.data.document.splitter.MarkdownSectionSplitter.SEGMENT_LINKS;

public class MarkdownSectionSplitterTest implements WithAssertions {

    @Test
    public void testNoSubSplitter() {
        String text = "# Title\n"
                + "## Section 1\n"
                + "section 1\n"
                + "## Section 2\n"
                + "section 2\n"
                + "### Section 2.1\n"
                + "section 2.1\n"
                + "#### Section 2.1.1\n"
                + "section 2.1.1\n"
                + "#### Section 2.1.2\n"
                + "section 2.1.2\nmore\n"
                + "### Section 2.2\n"
                + "#### Section 2.2.1\n"
                + "## Section 3\n"
                + "section 3\n"
                // Jump in section levels. There is no intermediate '###' on purpose
                + "#### Section 3.1.1\n"
                + "section 3.1.1\n"
                + "## Section 4\n"
                + "section 4 \n"
                // Add another level 1 section to make sure we can support more than one
                + "# Header 1\n"
                + "header\n";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder()
                .setEmptySectionPlaceholderText(".")
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
        String text = "Intro text\n" + "## Section 1\n" + "section 1\n";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder().build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        assertThat(segments.size()).isEqualTo(2);
        checkTextSegment(source, segments.get(0), null, null, 0, 0, "Intro text");
        checkTextSegment(source, segments.get(1), "Section 1", null, 1, 0, "section 1");
    }

    @Test
    public void testIntroductoryTextNoHeaderWithDocumentTitle() {
        String text = "Intro text\n" + "## Section 1\n" + "section 1\n";

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
        String text = "# Title\n"
                + "## Section 1\n"
                + "section 1\n"
                + "## Section 2\n"
                + "section 2 split\n"
                + "### Section 2.1\n"
                + "section 2.1\n"
                + "### Section 2.2\n"
                + "section 2.2 split\n";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder()
                .setEmptySectionPlaceholderText(".")
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
        String text = "# Title\n"
                + "## Section 1\n"
                + "section 1\n"
                + "```\n"
                + "# In Code\n"
                + "```\n"
                + "## Section 2\n"
                + "section 2\n"
                + "\n"
                + "```\n"
                + "# In Code\n"
                + "```\n";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder()
                .setEmptySectionPlaceholderText(".")
                .build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        assertThat(segments.size()).isEqualTo(3);

        checkTextSegment(source, segments.get(0), "Title", null, 0, 0, ".");
        checkTextSegment(source, segments.get(1), "Section 1", "Title", 1, 0, "section 1\n\n```\n# In Code\n```");
        checkTextSegment(source, segments.get(2), "Section 2", "Title", 1, 1, "section 2\n\n```\n# In Code\n```");
    }

    @Test
    public void testCodeSpan() {
        String text = "# Title\n"
                + "## Section 1\n"
                + "section 1 is `the best` ever\n"
;

        DocumentSplitter splitter = MarkdownSectionSplitter.builder()
                .setEmptySectionPlaceholderText(".")
                .build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        assertThat(segments.size()).isEqualTo(2);

        checkTextSegment(source, segments.get(0), "Title", null, 0, 0, ".");
        checkTextSegment(source, segments.get(1), "Section 1", "Title", 1, 0, "section 1 is `the best` ever");
    }

    @Test
    public void testAdjusters() {
        String text = "# Title\n" + "intro\n" + "## Section 1\n" + "section 1\n";

        TestMarkdownAdjuster adjuster = new TestMarkdownAdjuster();
        DocumentSplitter splitter = MarkdownSectionSplitter.builder()
                .setDocumentAdjuster(adjuster)
                .setTextSegmentsAdjuster(adjuster)
                .build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(2, segments.size());

        checkTextSegment(source, segments.get(0), "Title", null, 0, 0, "intro");
        assertThat(segments.get(0).metadata().getInteger("test-doc-counter")).isEqualTo(0);
        assertThat(segments.get(0).metadata().getInteger("test-segment-counter")).isEqualTo(0);

        checkTextSegment(source, segments.get(1), "Section 1", "Title", 1, 0, "section 1");
        assertThat(segments.get(1).metadata().getInteger("test-doc-counter")).isEqualTo(1);
        assertThat(segments.get(1).metadata().getInteger("test-segment-counter")).isEqualTo(10);
    }

    @Test
    public void testFencedCodeBlock() {
        // The renderer adds empty lines around code blocks
        // Test some variations of the input.
        String text = "# Title\n" +
                "Some text\n" +
                "```\n" +
                "    function(){\n" +
                "       this.i++;\n" +
                "\t}\n" +
                "```\n" +
                "More text\n\n" +
                "```\n" +
                "    print(x);\n" +
                "```\n\n" +
                "Final text";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder().build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(1, segments.size());
        checkTextSegment(source, segments.get(0), "Title", null, 0, 0,
                "Some text\n\n```\n    function(){\n       this.i++;\n\t}\n```\n\n" +
                        "More text\n\n```\n    print(x);\n```\n\nFinal text");

    }

    @Test
    public void testIndentedCodeBlock() {
        // The renderer adds empty lines around code blocks
        // Test some variations of the input.
        // In the output Markdown, we use the fenced style always for consistency.
        String text = "# Title\n" +
                "Some text\n\n" +
                "        function(){\n" +
                "           this.i++;\n" +
                "\t    }\n" +
                "More text\n\n" +
                "        print(x);\n" +
                "Final text";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder().build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(1, segments.size());
        checkTextSegment(source, segments.get(0), "Title", null, 0, 0,
                "Some text\n\n```\n    function(){\n       this.i++;\n    }\n```\n\n" +
                        "More text\n\n```\n    print(x);\n```\n\nFinal text");

    }

    @Test
    public void testParagraphs() {
        // More than two '\n\n' gets replaced with just one.
        String text = "# Title\n" +
                "Paragraph 1\n\nParagraph2\n\n\nParagraph3";

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
        String text = "# Title\n" +
                "The *quick* brown _fox_ jumped **over** the __lazy__ dog";

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
        String text = "Title\n" +
                "=====\n" +
                "intro\n\n" +
                "Section 1\n" +
                "----\n" +
                "section 1\n";

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
        String text = "# Title\n" +
                "intro\n" +
                "- One\n" +
                "- Two `test` two\n" +
                "\n" + // Double \n is needed here to end the list
                "After text\n" +
                "\n" +
                "* First\n" +
                "* Second";

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
        String text = "# Title\n" +
                "intro\n" +
                "1. One\n" +
                "2. Two `test` two\n" +
                "\n" + // Double \n is needed here to end the list
                "After text\n" +
                "\n" +
                "1. First\n" +
                "2. Second";

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
        String body = "intro\n\n" +
                "* One\n" +
                "* Two\n" +
                "  * 2-1\n" +
                "  * 2-2\n" +
                "    1. 2-2-1\n" +
                "       1. 2-2-1-1\n" +
                "       2. 2-2-1-2\n" +
                "    2. 2-2-2\n" +
                "* Three\n" +
                "  1. 3-1\n" +
                "     * 3-1-1\n" +
                "     * 3-1-2\n" +
                "       * 3-1-2-1\n" +
                "       * 3-1-2-2\n" +
                "     * 3-1-3";


        String text = "# Title\n" +
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
        String text = "# Title\n" +
                "intro\n" +
                "> line1\n" +
                ">\n" +
                ">line2\n" +
                "line3\n" +
                "\n" +
                "Other text\n\n" +
                "> # Ignored header\n\n" +
                "Final text";

        // The renderer massages the continuing 'line3' a bit, and adds a space after '>' but it is semantically the same.
        String expected = "intro\n\n" +
                "> line1\n" +
                "> \n" +
                "> line2\n" +
                "> line3\n" +
                "\n" +
                "Other text\n\n" +
                "> # Ignored header\n\n" +
                "Final text";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder().build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(1, segments.size());
        checkTextSegment(source, segments.get(0), "Title", null, 0, 0, expected);

    }

    @Test
    public void testNestedBlockQuotes() {
        String body = "> Test\n" +
                "> \n" +
                "> > # Ignored header\n" +
                "> > \n" +
                "> > 1. One\n" +
                "> > 2. Two";

        String text = "# Title\n\n" + body;

        DocumentSplitter splitter = MarkdownSectionSplitter.builder().build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(1, segments.size());
        checkTextSegment(source, segments.get(0), "Title", null, 0, 0, body);

    }

    @Test
    public void testImagesRemoved() {
        // I don't think images are relevant at this stage so let's check they are removed
        String text = "# Title\n\n" +
                "intro\n" +
                "![link](/uri)\n" +
                "outro\n";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder().build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(1, segments.size());

        checkTextSegment(source, segments.get(0), "Title", null, 0, 0,
                "intro\n\noutro");
    }

    @Test
    public void testLinks_Standard() {
        String text = "# Title\n\n" +
                "intro [A Link](https://a.com \"testA\").";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder()
                .build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(1, segments.size());

        checkTextSegment(source, segments.get(0), "Title", null, 0, 0,
                "intro [A Link](https://a.com \"testA\").");
    }

    @Test
    public void testLinks_Stripped() {
        String text = "# Title\n\n" +
                "intro [A Link](https://a.com \"real\").";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder()
                .setLinkHandling(MarkdownSectionSplitter.LinkHandling.STRIP)
                .build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(1, segments.size());

        checkTextSegment(source, segments.get(0), "Title", null, 0, 0,
                "intro A Link.");
    }

    @Test
    public void testLinks_Metadata() {
        String text = "# Title\n\n" +
                "intro [Link A](https://a.com) [Link B](https://b.com).\n\n" +
                "---\n\n" +
                "outro [Link C](https://c.com)";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder()
                .setLinkHandling(MarkdownSectionSplitter.LinkHandling.METADATA)
                .setSectionSplitter(document -> {
                    String text1 = document.text();
                    int index = text1.indexOf("---\n\n");
                    TextSegment segmentA = TextSegment.textSegment(
                            text1.substring(0, index), new Metadata(document.metadata().toMap()));
                    TextSegment segmentB = TextSegment.textSegment(
                            text1.substring(index + 4), new Metadata(document.metadata().toMap()));
                    return List.of(segmentA, segmentB);
                })
                .build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(2, segments.size());

        Gson gson = new GsonBuilder().create();
        Type type = new TypeToken<HashMap<String, String>>(){}.getType();


        checkTextSegment(source, segments.get(0), "Title", null, 0, 0,
                "intro [Link A](https://a.com) [Link B](https://b.com).");
        Map<String, String> links = gson.fromJson(segments.get(0).metadata().getString(SEGMENT_LINKS), type);
        Assertions.assertEquals(2, links.size());
        Assertions.assertEquals("https://a.com", links.get("[Link A](https://a.com)"));
        Assertions.assertEquals("https://b.com", links.get("[Link B](https://b.com)"));

        checkTextSegment(source, segments.get(1), "Title", null, 0, 0,
                "outro [Link C](https://c.com)");
        links = gson.fromJson(segments.get(1).metadata().getString(SEGMENT_LINKS), type);
        Assertions.assertEquals(1, links.size());
        Assertions.assertEquals("https://c.com", links.get("[Link C](https://c.com)"));
    }

    @Test
    public void testTables() {
                String text = "# Title\n\n" +
                "intro\n" +
                "\n" +
                "| H1 | H2 |\n" +
                "|----|----|\n" +
                "| 1  | 2  |\n" +
                "| 3  | 4  |\n\n" +
                "outro";

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
                String text = "---\n" +
                "hello: world\n" +
                "empty:\n" +
                "---\n" +
                "# Title\n\n" +
                "intro\n";

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
                String text = "# 1 <ejb>\n\n" +
                "intro\n" +
                "## 1.1 <stateful>\n\n" +
                "stateful\n" +
                "## 1.2 <stateless>\n\n" +
                "stateless\n" +
                "## **<mdb>**\n\n" +
                "mdb\n";

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

    private static class TestMarkdownAdjuster implements MarkdownSectionSplitter.DocumentAdjuster, MarkdownSectionSplitter.TextSegmentsAdjuster {
        static int doc_counter = 0;
        static int segment_counter = 0;
        @Override
        public Document adjust(final Document original) {
            original.metadata().put("test-doc-counter", doc_counter++);
            return original;
        }

        @Override
        public List<TextSegment> adjust(final List<TextSegment> originalSegments) {
            originalSegments.forEach(ts -> ts.metadata().put("test-segment-counter", segment_counter));
            segment_counter += 10;
            return originalSegments;
        }
    }
}
