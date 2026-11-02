package dev.langchain4j.store.embedding.milvus.v2;

import io.milvus.v2.client.MilvusClientV2;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The version of the Milvus server, read once when an embedding store is created.
 */
record MilvusServerVersion(int majorVersion) {

    /**
     * Used when the server version cannot be read or parsed: it behaves like Milvus 2.
     */
    static final MilvusServerVersion DEFAULT = new MilvusServerVersion(2);

    private static final Logger log = LoggerFactory.getLogger(MilvusServerVersion.class);

    // e.g. "2.5.10", "v2.5.10", "pkg/v2.5.4", "3.0.4" or "2.6-20261023-b8d2ca4240"
    private static final Pattern MAJOR_VERSION = Pattern.compile("(?:pkg/)?v?(\\d{1,4})(?:[.-].*)?");

    static MilvusServerVersion from(MilvusClientV2 milvusClient) {
        String version = null;
        try {
            version = milvusClient.getServerVersion();
        } catch (RuntimeException e) {
            log.debug("Could not read the Milvus server version", e);
        }
        return from(version);
    }

    static MilvusServerVersion from(String version) {
        Matcher matcher = MAJOR_VERSION.matcher(version == null ? "" : version.trim());
        if (matcher.matches()) {
            return new MilvusServerVersion(Integer.parseInt(matcher.group(1)));
        }
        log.debug("Unknown Milvus server version '{}', using the behavior of Milvus 2", version);
        return DEFAULT;
    }

    /**
     * Milvus 3 resolves a backslash before any character of a LIKE pattern, while Milvus 2.5.10 to 2.6 resolve it
     * only before {@code %} and {@code _}. A backslash that is part of a value therefore needs one more level of
     * escaping on Milvus 3.
     */
    boolean likePatternNeedsExtraBackslash() {
        return majorVersion >= 3;
    }
}
