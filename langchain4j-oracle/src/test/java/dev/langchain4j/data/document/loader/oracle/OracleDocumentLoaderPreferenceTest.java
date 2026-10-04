package dev.langchain4j.data.document.loader.oracle;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.InvalidParameterException;
import org.junit.jupiter.api.Test;

/**
 * The preference JSON is validated before the loader touches the database, so these run without
 * one. A typo in a preference has to be reported rather than silently ignored, which the codec
 * would otherwise do.
 */
class OracleDocumentLoaderPreferenceTest {

    private final OracleDocumentLoader loader = new OracleDocumentLoader(null);

    @Test
    void should_reject_a_file_preference_with_an_unknown_property() {
        assertThatThrownBy(() -> loader.loadDocuments("{\"file\":\"file.txt\",\"extraProperty\":\"\"}"))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessage("Invalid file preference: unknown property specified");
    }

    @Test
    void should_reject_a_directory_preference_with_an_unknown_property() {
        assertThatThrownBy(() -> loader.loadDocuments("{\"dir\":\"/tmp\",\"extraProperty\":\"\"}"))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessage("Invalid directory preference: unknown property specified");
    }

    @Test
    void should_reject_a_table_preference_with_an_unknown_property() {
        assertThatThrownBy(() -> loader.loadDocuments(
                        "{\"owner\":\"o\",\"tablename\":\"t\",\"colname\":\"c\",\"extraProperty\":\"\"}"))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessage("Invalid table preference: unknown property specified");
    }

    @Test
    void should_reject_a_table_preference_that_is_missing_a_name() {
        assertThatThrownBy(() -> loader.loadDocuments("{\"tablename\":\"docs\",\"colname\":\"text\"}"))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessage("Invalid table preference: missing owner, table, or column name");
    }

    @Test
    void should_accept_a_table_preference_whose_owner_is_explicitly_null() {
        assertThatThrownBy(() ->
                        loader.loadDocuments("{\"owner\":null,\"tablename\":\"docs\",\"colname\":\"text\"}"))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessage("Invalid table preference: missing owner, table, or column name");
    }

    @Test
    void should_reject_a_preference_that_names_none_of_file_dir_or_table() {
        assertThatThrownBy(() -> loader.loadDocuments("{\"something\":\"else\"}"))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessage("Invalid preference: missing filename, directory, or table");
    }
}
