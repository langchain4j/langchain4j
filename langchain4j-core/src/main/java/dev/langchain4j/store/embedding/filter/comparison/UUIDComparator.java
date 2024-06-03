package dev.langchain4j.store.embedding.filter.comparison;

import java.util.Collection;
import java.util.UUID;

class UUIDComparator {

    static boolean containsAsUUID(Object actualNumber, Collection<?> comparisonUUIDs) {
        UUID actualUUID = toUUID(actualNumber);
        return comparisonUUIDs.stream()
                .map(UUIDComparator::toUUID)
                .anyMatch(comparisonUUID ->
                        comparisonUUID.compareTo(actualUUID) == 0);
    }

    private static UUID toUUID(Object actualNumber) {
        if (actualNumber instanceof String) {
            return UUID.fromString(actualNumber.toString());
        } else if (actualNumber instanceof UUID) {
            return (UUID)actualNumber;
        }

        throw new IllegalArgumentException("Unsupported type: " + actualNumber.getClass().getName());
    }
}
