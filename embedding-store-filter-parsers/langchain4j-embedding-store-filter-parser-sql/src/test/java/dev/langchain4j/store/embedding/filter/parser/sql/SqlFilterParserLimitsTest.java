package dev.langchain4j.store.embedding.filter.parser.sql;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.FilterParser;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import net.sf.jsqlparser.parser.CCJSqlParserConstants;
import net.sf.jsqlparser.parser.CCJSqlParserTokenManager;
import net.sf.jsqlparser.parser.SimpleCharStream;
import net.sf.jsqlparser.parser.StringProvider;
import net.sf.jsqlparser.parser.Token;
import net.sf.jsqlparser.parser.TokenMgrException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class SqlFilterParserLimitsTest {

    private static final String CONDITIONS_LIMIT_MESSAGE =
            "The SQL filter has %s conditions, more than the maximum of %s. "
                    + "Increase it with SqlFilterParser.builder().maxConditions(...)";

    private static final String NESTING_DEPTH_LIMIT_MESSAGE =
            "The SQL filter has parentheses nested %s levels deep, more than the maximum of %s. "
                    + "Increase it with SqlFilterParser.builder().maxNestingDepth(...)";

    private static final String PARSE_TIMEOUT_MESSAGE = "Parsing the SQL filter took longer than the maximum of %s ms. "
            + "Increase it with SqlFilterParser.builder().parseTimeout(...)";

    FilterParser parser = new SqlFilterParser();

    // conditions

    @Test
    void should_parse_filter_with_default_maximum_of_conditions() {

        Filter filter = parser.parse(conditions(100));

        assertThat(filter).isInstanceOf(And.class);
        assertThat(filter.test(new Metadata().put("id", 1))).isTrue();
        assertThat(filter.test(new Metadata().put("id", 0))).isFalse();
    }

    @Test
    void should_reject_filter_with_more_than_default_maximum_of_conditions() {

        assertThatThrownBy(() -> parser.parse(conditions(101)))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage(CONDITIONS_LIMIT_MESSAGE, 101, 100);
    }

    @Test
    void should_count_conditions_before_mapping_them() {

        String sql = "id LIKE 'unsupported' AND " + conditions(100);

        assertThatThrownBy(() -> parser.parse(sql))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage(CONDITIONS_LIMIT_MESSAGE, 101, 100);
    }

    @Test
    void should_count_each_comparison_IN_and_BETWEEN_as_one_condition() {

        SqlFilterParser parser = SqlFilterParser.builder()
                .maxConditions(3)
                .parseTimeout(Duration.ofMinutes(1))
                .build();
        SqlFilterParser stricterParser = SqlFilterParser.builder()
                .maxConditions(2)
                .parseTimeout(Duration.ofMinutes(1))
                .build();

        String javadocExample = "name = 'Klaus' AND (age >= 18 OR city IN ('Berlin', 'Munich'))";
        assertThat(parser.parse(javadocExample)).isNotNull();
        assertThatThrownBy(() -> stricterParser.parse(javadocExample))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage(CONDITIONS_LIMIT_MESSAGE, 3, 2);

        String betweenAndIn = "NOT (id = 1) AND year BETWEEN 2000 AND 2020 AND id IN (" + values(10_000) + ")";
        assertThat(parser.parse(betweenAndIn)).isNotNull();
        assertThatThrownBy(() -> stricterParser.parse(betweenAndIn))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage(CONDITIONS_LIMIT_MESSAGE, 3, 2);

        assertThatThrownBy(() -> parser.parse("a = 1 AND b = 2 OR NOT (c = 3 AND d IN (4, 5))"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage(CONDITIONS_LIMIT_MESSAGE, 4, 3);
    }

    @Test
    void should_evaluate_filter_with_default_maximum_of_conditions_in_embedding_store() {

        // given
        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
        Embedding embedding = Embedding.from(new float[] {1, 0});
        embeddingStore.add(embedding, TextSegment.from("2024", new Metadata().put("year", 2024)));
        embeddingStore.add(embedding, TextSegment.from("2023", new Metadata().put("year", 2023)));

        Filter filter = parser.parse("year = 2024" + " AND year > 0".repeat(99));

        // when
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding)
                        .filter(filter)
                        .maxResults(10)
                        .build())
                .matches();

        // then
        assertThat(matches).extracting(match -> match.embedded().text()).containsExactly("2024");
    }

    @Test
    void should_evaluate_and_compare_filter_with_default_maximum_of_conditions_on_small_stack() throws Exception {

        Filter filter = parser.parse(conditions(100));
        Filter equalFilter = parser.parse(conditions(100));
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread thread = new Thread(
                null,
                () -> {
                    try {
                        assertThat(filter.test(new Metadata().put("id", 1))).isTrue();
                        assertThat(filter).isEqualTo(equalFilter).hasSameHashCodeAs(equalFilter);
                        assertThat(filter.toString()).isEqualTo(equalFilter.toString());
                    } catch (Throwable t) {
                        failure.set(t);
                    }
                },
                "small-stack",
                256 * 1024);
        thread.start();
        thread.join();

        assertThat(failure.get()).isNull();
    }

    // nesting depth

    @Test
    void should_parse_filter_with_default_maximum_nesting_depth() {

        assertThat(parser.parse(nested(50, "id = 1"))).isEqualTo(metadataKey("id").isEqualTo(1L));
    }

    @Test
    void should_reject_filter_nested_deeper_than_default_maximum() {

        assertThatThrownBy(() -> parser.parse(nested(51, "id = 1")))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage(NESTING_DEPTH_LIMIT_MESSAGE, 51, 50);
    }

    @Test
    void should_reject_filter_nested_too_deep_before_parsing_it() {

        assertThatThrownBy(() -> parser.parse(nested(51, "this is not SQL")))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage(NESTING_DEPTH_LIMIT_MESSAGE, 51, 50);

        assertTimeout(Duration.ofSeconds(1), () -> assertThatThrownBy(() -> parser.parse(nested(100_000, "id = 1")))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage(NESTING_DEPTH_LIMIT_MESSAGE, 100_000, 50));
    }

    @Test
    void should_count_parentheses_of_function_calls_and_IN_lists() {

        SqlFilterParser parser = SqlFilterParser.builder().maxNestingDepth(3).build();
        String sql = "(name = 'Klaus' OR (age >= 18 AND city IN ('Berlin', 'Munich')))";
        assertThat(parser.parse(sql)).isNotNull();
        assertThat(parser.parse("(year = YEAR(CURDATE()))")).isNotNull();

        SqlFilterParser stricterParser = SqlFilterParser.builder().maxNestingDepth(2).build();
        assertThatThrownBy(() -> stricterParser.parse(sql))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage(NESTING_DEPTH_LIMIT_MESSAGE, 3, 2);
        assertThatThrownBy(() -> stricterParser.parse("(year = YEAR(CURDATE()))"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage(NESTING_DEPTH_LIMIT_MESSAGE, 3, 2);
    }

    @Test
    void should_not_count_parentheses_in_string_literals() {

        String parentheses = "(".repeat(100) + ")".repeat(30) + "(".repeat(100);

        assertThat(parser.parse("name = '" + parentheses + "'"))
                .isEqualTo(metadataKey("name").isEqualTo(parentheses));
        assertThat(parser.parse("name = 'it''s " + parentheses + "'")).isInstanceOf(IsEqualTo.class);
        assertThat(parser.parse("name IN ('" + parentheses + "', '''" + parentheses + "''')"))
                .isInstanceOf(IsIn.class);
    }

    @Test
    void should_not_count_parentheses_in_quoted_identifiers() {

        String parentheses = "(".repeat(100);

        assertThat(parser.parse("\"name" + parentheses + "\" = 'Klaus'")).isInstanceOf(IsEqualTo.class);
        assertThat(parser.parse("\"na\"\"me" + parentheses + "\" = 'Klaus'")).isInstanceOf(IsEqualTo.class);
        assertThat(parser.parse("`name" + parentheses + "` = 'Klaus'")).isInstanceOf(IsEqualTo.class);
    }

    @Test
    void should_not_count_parentheses_in_comments() {

        String parentheses = "(".repeat(100);

        assertThat(parser.parse("id = 1 -- " + parentheses)).isEqualTo(metadataKey("id").isEqualTo(1L));
        assertThat(parser.parse("id = 1 // " + parentheses + "\n AND name = 'Klaus'"))
                .isEqualTo(metadataKey("id").isEqualTo(1L).and(metadataKey("name").isEqualTo("Klaus")));
        assertThat(parser.parse("id = 1 /* " + parentheses + " */ AND name = 'Klaus'"))
                .isEqualTo(metadataKey("id").isEqualTo(1L).and(metadataKey("name").isEqualTo("Klaus")));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                // a backslash does not escape a quote
                "name = 'Klaus\\' AND %s AND name = 'Klaus'",
                // a quote inside a comment does not start a string literal
                "id = 1 -- '\n AND %s \n -- '",
                "id = 1 /* ' */ AND %s /* ' */",
                // a quote inside a quoted identifier does not start a string literal
                "\"it's\" = 1 AND %s AND \"it's\" = 1",
                "`it's` = 1 AND %s AND `it's` = 1",
                // a block comment that is not closed is not a comment
                "id = 1 /* AND %s",
                // the end of Oracle q'...' quoted text and of $$...$$ quoted text is not looked for
                "name = q'(Klaus)' Klaus)' AND %s",
                "name = $$ ' $$ AND %s AND name = '",
            })
    void should_count_parentheses_outside_string_literals_quoted_identifiers_and_comments(String template) {

        String sql = String.format(template, nested(51, "id = 1"));

        assertThatThrownBy(() -> parser.parse(sql))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("The SQL filter has parentheses nested")
                .hasMessageContaining("more than the maximum of 50");
    }

    @Test
    void should_count_every_opening_parenthesis_after_Oracle_q_quoted_text() {

        SqlFilterParser parser = SqlFilterParser.builder().maxNestingDepth(2).build();

        assertThat(parser.parse("(id = 1) AND (id = 2) AND (id = 3) AND name = q'[Klaus]'"))
                .isNotNull();
        assertThatThrownBy(() -> parser.parse("name = q'[Klaus]' AND (id = 1) AND (id = 2) AND (id = 3)"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage(NESTING_DEPTH_LIMIT_MESSAGE, 3, 2);
    }

    @Test
    void should_count_parentheses_normally_after_dollar_sign_in_identifier() {

        String sql = "price$usd = 1" + " AND id IN (1, 2)".repeat(51);

        assertThat(parser.parse(sql)).isInstanceOf(And.class);
    }

    @Test
    void should_never_count_fewer_parentheses_than_JSqlParser_reads() {

        String[] characters = {
            "(", ")", "'", "\"", "`", "\\", "-", "/", "*", "\n", "\r", " ", "q", "Q", "N", "E", "X", "$", "a", "1",
            "[", "]", "{", "#", "."
        };
        String[] tokens = {
            "(", ")", "'", "''", "\\'", "\"", "\"\"", "`", "--", "//", "/*", "*/", "\n", " a ", " 1 ", "q'(", ")'",
            "q'[", "]'", "q'{", "}'", "q''", "$$", "$1", "N'", "E'", "X'", "\\\\", "[", "]"
        };
        Random random = new Random(20261124);
        List<String> undercounted = new ArrayList<>();
        List<String> overcounted = new ArrayList<>();

        for (int i = 0; i < 40_000; i++) {
            String[] pieces = i % 2 == 0 ? characters : tokens;
            StringBuilder sql = new StringBuilder();
            int length = 1 + random.nextInt(20);
            for (int j = 0; j < length; j++) {
                sql.append(pieces[random.nextInt(pieces.length)]);
            }

            String text = sql.toString();
            JSqlParserDepth expected = nestingDepthAsReadByJSqlParser(text);
            int actual = SqlFilterParser.preScan(text).nestingDepth();

            if (actual < expected.depth()) {
                undercounted.add(text);
            }
            boolean exactCountExpected = !expected.lexicalError()
                    && !text.contains("$$")
                    && !text.contains("q'")
                    && !text.contains("Q'");
            if (exactCountExpected && actual != expected.depth()) {
                overcounted.add(text);
            }
        }

        assertThat(undercounted).isEmpty();
        assertThat(overcounted).isEmpty();
    }

    // unsupported constructs

    static Stream<Arguments> should_reject_unsupported_construct_before_parsing() {
        return Stream.of(
                Arguments.of("a = " + "ARRAY[".repeat(14) + "1" + "]".repeat(14), "an array"),
                Arguments.of("a = " + "ARRAY[".repeat(20) + "1" + "]".repeat(20), "an array"),
                Arguments.of("a = array [1, 2]", "an array"),
                Arguments.of("a = [1, 2]", "an array"),
                Arguments.of("a = b" + "[c".repeat(20) + "]".repeat(20), "an array"),
                Arguments.of("a[1][2] = 1", "an array"),
                Arguments.of("a = " + "(SELECT ".repeat(12) + "1" + ")".repeat(12), "a nested SELECT"),
                Arguments.of("a IN (select b FROM t)", "a nested SELECT"),
                Arguments.of("SELECT * FROM t WHERE a IN (SELECT b FROM u)", "a nested SELECT"),
                Arguments.of("a = " + "CAST(".repeat(22) + "1" + " AS INT)".repeat(22), "a CAST expression"),
                Arguments.of("a = cast (1 AS INT)", "a CAST expression"),
                Arguments.of(
                        "a = " + "CASE WHEN (".repeat(7) + "1 = 1" + ") THEN 1 END = 1".repeat(6) + ") THEN 1 END",
                        "a CASE expression"),
                Arguments.of("a = Case b When 1 Then 2 End", "a CASE expression"));
    }

    @ParameterizedTest
    @MethodSource
    void should_reject_unsupported_construct_before_parsing(String sql, String construct) {

        assertTimeout(Duration.ofSeconds(1), () -> assertThatThrownBy(() -> parser.parse(sql))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("The SQL filter contains %s, which is not supported", construct));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "cases = 1",
                "selection = 1",
                "whenever = 1",
                "my_when = 1",
                "id = 1 AND selected = 2 AND preselect = 3",
                "my_case = 1 AND casting = 2 AND arrays = 3",
                "case = 1",
                "cast = 1",
                "t.select = 1",
                "t.cast = 1",
                "array = 1",
                "\"when\" = 1",
                "`select` = 1",
                "name = 'CASE WHEN CAST( SELECT ARRAY[1]'",
                "id = 1 -- CASE WHEN CAST( SELECT ARRAY[1]",
                "id = 1 /* CASE WHEN CAST( SELECT ARRAY[1] */"
            })
    void should_not_reject_column_names_strings_and_comments_that_look_like_unsupported_constructs(String sql) {

        assertThat(parser.parse(sql)).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a[1] = 1", "a [1] = 1", "\"a\"[1] = 1", "a[1:2] = 1", "a = 1 AND b[1] = 2"})
    void should_not_reject_single_subscript_of_column_before_parsing(String sql) {

        assertThat(SqlFilterParser.preScan(sql).unsupportedConstruct()).isNull();
    }

    @Test
    void should_reject_filter_that_takes_longer_than_parse_timeout() {

        SqlFilterParser parser = SqlFilterParser.builder()
                .parseTimeout(Duration.ofMillis(1))
                .build();

        assertThatThrownBy(() -> parser.parse(nested(50, "id = 1")))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage(PARSE_TIMEOUT_MESSAGE, 1);
    }

    @Test
    void should_stop_parsing_slow_filter_after_default_parse_timeout() {

        String sql = "id IN (" + values(1_000_000) + ")";

        assertTimeout(Duration.ofSeconds(5), () -> assertThatThrownBy(() -> parser.parse(sql))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage(PARSE_TIMEOUT_MESSAGE, 1000));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "PT-1S", "PT-0.001S"})
    void should_reject_parse_timeout_that_is_not_greater_than_zero(String parseTimeout) {

        assertThatThrownBy(() -> SqlFilterParser.builder()
                        .parseTimeout(Duration.parse(parseTimeout))
                        .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("parseTimeout must be greater than zero, but is: " + parseTimeout);
    }

    // inputs that once ended in a StackOverflowError

    static Stream<String> should_reject_long_unsupported_expression_with_IllegalArgumentException() {
        return Stream.of(
                "a = 1" + " XOR a = 1".repeat(20_000),
                "a = -(1" + " + 1".repeat(20_000) + ")",
                "a = 1" + "::int".repeat(5_000),
                "a = 1" + " || 1".repeat(20_000),
                "a = 1" + " + 1".repeat(20_000) + " + 'x'");
    }

    @ParameterizedTest
    @MethodSource
    void should_reject_long_unsupported_expression_with_IllegalArgumentException(String sql) {

        assertThatThrownBy(() -> parser.parse(sql)).isExactlyInstanceOf(IllegalArgumentException.class);

        SqlFilterParser parserWithLongTimeout =
                SqlFilterParser.builder().parseTimeout(Duration.ofMinutes(1)).build();
        assertThatThrownBy(() -> parserWithLongTimeout.parse(sql))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Unsupported expression of type ");
    }

    @Test
    void should_reject_comparison_of_long_parenthesized_condition_with_exception() {

        String sql = "(a = 1" + " AND a = 1".repeat(20_000) + ") = 1";

        assertThatThrownBy(() -> parser.parse(sql)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void should_reject_filter_that_is_too_complex_to_parse() {

        SqlFilterParser parser =
                SqlFilterParser.builder().maxNestingDepth(Integer.MAX_VALUE).build();
        String sql = "a = " + "f(".repeat(100_000) + "1" + ")".repeat(100_000);

        assertThatThrownBy(() -> parser.parse(sql))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("The SQL filter is too complex to parse")
                .hasRootCauseInstanceOf(StackOverflowError.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NOT ", "! "})
    void should_reject_long_chain_of_negations_without_parentheses_with_exception(String negation) {

        String sql = negation.repeat(10_000) + "id = 1";

        assertThatThrownBy(() -> parser.parse(sql)).isInstanceOf(RuntimeException.class);
    }

    // builder and constructors

    @Test
    void should_use_custom_limits() {

        SqlFilterParser parser = SqlFilterParser.builder()
                .maxConditions(2)
                .maxNestingDepth(1)
                .build();

        assertThat(parser.parse("(id = 1) AND (id = 2)")).isNotNull();
        assertThatThrownBy(() -> parser.parse("id = 1 AND id = 2 AND id = 3"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage(CONDITIONS_LIMIT_MESSAGE, 3, 2);
        assertThatThrownBy(() -> parser.parse("((id = 1))"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage(NESTING_DEPTH_LIMIT_MESSAGE, 2, 1);
    }

    @Test
    void should_effectively_disable_limits_with_Integer_MAX_VALUE() {

        SqlFilterParser parser = SqlFilterParser.builder()
                .maxConditions(Integer.MAX_VALUE)
                .maxNestingDepth(Integer.MAX_VALUE)
                .parseTimeout(Duration.ofMinutes(1))
                .build();

        assertThat(parser.parse(conditions(5000))).isInstanceOf(And.class);
        assertThat(parser.parse(nested(60, "id = 1"))).isEqualTo(metadataKey("id").isEqualTo(1L));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void should_reject_max_conditions_below_1(int maxConditions) {

        assertThatThrownBy(() ->
                        SqlFilterParser.builder().maxConditions(maxConditions).build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxConditions must be greater than zero, but is: " + maxConditions);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void should_reject_max_nesting_depth_below_1(int maxNestingDepth) {

        assertThatThrownBy(() -> SqlFilterParser.builder()
                        .maxNestingDepth(maxNestingDepth)
                        .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxNestingDepth must be greater than zero, but is: " + maxNestingDepth);
    }

    @Test
    void should_use_defaults_when_builder_values_are_null() {

        SqlFilterParser parser = SqlFilterParser.builder()
                .clock(null)
                .maxConditions(null)
                .maxNestingDepth(null)
                .parseTimeout(null)
                .build();

        assertDefaultLimits(parser);
    }

    @Test
    void should_use_default_limits_in_constructors() {

        assertDefaultLimits(new SqlFilterParser());
        assertDefaultLimits(new SqlFilterParser(Clock.systemUTC()));
    }

    @Test
    void should_let_subclass_set_limits() {

        class ParserWithMoreConditions extends SqlFilterParser {

            ParserWithMoreConditions() {
                super(SqlFilterParser.builder().maxConditions(5000).parseTimeout(Duration.ofMinutes(1)));
            }
        }
        SqlFilterParser parser = new ParserWithMoreConditions();

        assertThat(parser.parse(conditions(5000))).isInstanceOf(And.class);
        assertThatThrownBy(() -> parser.parse(conditions(5001)))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage(CONDITIONS_LIMIT_MESSAGE, 5001, 5000);
        assertThatThrownBy(() -> parser.parse(nested(51, "id = 1")))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage(NESTING_DEPTH_LIMIT_MESSAGE, 51, 50);
    }

    @Test
    void should_still_reject_null_clock_in_constructor() {

        assertThatThrownBy(() -> new SqlFilterParser((Clock) null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("clock cannot be null");
    }

    @Test
    void should_use_clock_from_builder() {

        Clock clock = Clock.fixed(Instant.parse("2001-02-03T04:05:06Z"), UTC);

        SqlFilterParser parser = SqlFilterParser.builder().clock(clock).build();

        assertThat(parser.parse("year = YEAR(CURDATE())")).isEqualTo(metadataKey("year").isEqualTo(2001L));
    }

    private static void assertDefaultLimits(SqlFilterParser parser) {
        assertThat(parser.parse(conditions(100))).isInstanceOf(And.class);
        assertThatThrownBy(() -> parser.parse(conditions(101)))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage(CONDITIONS_LIMIT_MESSAGE, 101, 100);

        assertThat(parser.parse(nested(50, "id = 1"))).isEqualTo(metadataKey("id").isEqualTo(1L));
        assertThatThrownBy(() -> parser.parse(nested(51, "id = 1")))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage(NESTING_DEPTH_LIMIT_MESSAGE, 51, 50);
    }

    private static String conditions(int count) {
        return "id >= 0" + " AND id > 0".repeat(count - 1);
    }

    private static String values(int count) {
        return "0" + ", 1".repeat(count - 1);
    }

    private static String nested(int depth, String sql) {
        return "(".repeat(depth) + sql + ")".repeat(depth);
    }

    private record JSqlParserDepth(int depth, boolean lexicalError) {}

    private static JSqlParserDepth nestingDepthAsReadByJSqlParser(String sql) {
        CCJSqlParserTokenManager tokenManager = new CCJSqlParserTokenManager(new SimpleCharStream(new StringProvider(sql)));
        int depth = 0;
        int maxDepth = 0;
        try {
            for (Token token = tokenManager.getNextToken();
                    token.kind != CCJSqlParserConstants.EOF;
                    token = tokenManager.getNextToken()) {
                if (token.image.equals("(")) {
                    maxDepth = Math.max(maxDepth, ++depth);
                } else if (token.image.equals(")")) {
                    depth = Math.max(0, depth - 1);
                }
            }
        } catch (TokenMgrException e) {
            // JSqlParser cannot read past a lexical error, so whatever follows is never parsed
            return new JSqlParserDepth(maxDepth, true);
        }
        return new JSqlParserDepth(maxDepth, false);
    }
}
