package dev.langchain4j.store.embedding.filter.comparison;

import java.util.Collection;
import java.util.UUID;

class UUIDComparator {

    static boolean containsAsUUID(Object actualUUID, Collection<?> comparisonUUIDs) {
        UUID uuid = toUUID(actualUUID);
        return comparisonUUIDs.stream()
                .map(UUIDComparator::toUUID)
                .anyMatch(comparisonUUID ->
                        comparisonUUID.compareTo(uuid) == 0);
    }

    private static UUID toUUID(Object actualUUID) {
        if (actualUUID instanceof String) {
            return UUID.fromString(actualUUID.toString());
        } else if (actualUUID instanceof UUID) {
            return (UUID)actualUUID;
        }

        throw new IllegalArgumentException("Unsupported type: " + actualUUID.getClass().getName());
    }
}
