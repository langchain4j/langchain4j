package dev.langchain4j.store.embedding.oracle.vecdb;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.exception.UnsupportedFeatureException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checks whether a connected Oracle Database supports {@code DBMS_VECTOR_DATABASE}. */
final class VecDbSupport {

    private static final DatabaseVersion MINIMUM_VERSION = new DatabaseVersion(23, 26, 3);
    private static final Pattern VERSION_PATTERN =
            Pattern.compile("(?<!\\d)(\\d+)\\.(\\d+)\\.(\\d+)(?:\\.\\d+)*(?!\\d)");

    private VecDbSupport() {}

    static boolean isSupported(Connection connection) throws SQLException {
        return databaseVersion(connection).compareTo(MINIMUM_VERSION) >= 0;
    }

    static void requireSupported(Connection connection) throws SQLException {
        DatabaseVersion version = databaseVersion(connection);
        if (version.compareTo(MINIMUM_VERSION) < 0) {
            throw new UnsupportedFeatureException("VecDB requires Oracle Database " + MINIMUM_VERSION
                    + " or later, but the connected database reports " + version);
        }
    }

    static DatabaseVersion parseVersion(String productVersion) {
        productVersion = ensureNotBlank(productVersion, "databaseProductVersion");
        Matcher matcher = VERSION_PATTERN.matcher(productVersion);
        DatabaseVersion version = null;

        // Oracle product strings can contain both a base "Release" and a later full "Version" value.
        while (matcher.find()) {
            version = new DatabaseVersion(
                    parseComponent(matcher.group(1), productVersion),
                    parseComponent(matcher.group(2), productVersion),
                    parseComponent(matcher.group(3), productVersion));
        }

        if (version == null) {
            throw new IllegalStateException(
                    "Unable to determine Oracle Database version from JDBC product version: " + productVersion);
        }
        return version;
    }

    private static DatabaseVersion databaseVersion(Connection connection) throws SQLException {
        ensureNotNull(connection, "connection");
        return parseVersion(connection.getMetaData().getDatabaseProductVersion());
    }

    private static int parseComponent(String component, String productVersion) {
        try {
            return Integer.parseInt(component);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "Invalid numeric component in JDBC database product version: " + productVersion, exception);
        }
    }

    record DatabaseVersion(int major, int minor, int patch) implements Comparable<DatabaseVersion> {

        DatabaseVersion {
            if (major < 0 || minor < 0 || patch < 0) {
                throw new IllegalArgumentException("Database version components must not be negative");
            }
        }

        @Override
        public int compareTo(DatabaseVersion other) {
            ensureNotNull(other, "other");
            int comparison = Integer.compare(major, other.major);
            if (comparison != 0) {
                return comparison;
            }
            comparison = Integer.compare(minor, other.minor);
            return comparison != 0 ? comparison : Integer.compare(patch, other.patch);
        }

        @Override
        public String toString() {
            return major + "." + minor + "." + patch;
        }
    }
}
