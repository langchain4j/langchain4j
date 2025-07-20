package dev.langchain4j.store.embedding.mariadb;

import java.sql.SQLException;
import org.mariadb.jdbc.Driver;

class MariaDbValidator {

    /**
     * Escaped identifier according to MariaDB requirement.
     * @param identifier identifier
     * @param alwaysQuote indicate if identifier must be quoted even if not necessary.
     * @return return escaped identifier, quoted when necessary or indicated with
     * alwaysQuote.
     * @see <a href="https://mariadb.com/kb/en/library/identifier-names/">mariadb
     * identifier name</a>
     */
    public static String validateAndEnquoteIdentifier(String identifier, boolean alwaysQuote) {
        try {
            return Driver.enquoteIdentifier(identifier, alwaysQuote);
        } catch (SQLException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
