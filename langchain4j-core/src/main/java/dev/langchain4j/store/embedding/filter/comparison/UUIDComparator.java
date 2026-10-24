package dev.langchain4j.store.embedding.filter.comparison;

import dev.langchain4j.Internal;
import java.util.Collection;
import java.util.UUID;

@Internal
class UUIDComparator {

    static boolean containsAsUUID(Object actualUUID, Collection<?> comparisonUUIDs) {
        UUID uuid;
        try {
            uuid = toUUID(actualUUID);
        } catch (IllegalArgumentException e) {
            return false; // not a UUID, so it cannot match any
        }
        return comparisonUUIDs.stream()
                .map(UUIDComparator::toUUID)
                .anyMatch(comparisonUUID -> comparisonUUID.compareTo(uuid) == 0);
    }

    private static UUID toUUID(Object actualUUID) {
        if (actualUUID instanceof String) {
            return UUID.fromString(actualUUID.toString());
        } else if (actualUUID instanceof UUID iD) {
            return iD;
        }

        throw new IllegalArgumentException(
                "Unsupported type: " + actualUUID.getClass().getName());
    }
}
