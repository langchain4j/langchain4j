package dev.langchain4j.store.embedding.milvus;

import dev.langchain4j.store.embedding.filter.MetadataFilter;
import dev.langchain4j.store.embedding.filter.comparison.Equal;
import dev.langchain4j.store.embedding.filter.comparison.GreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.In;
import dev.langchain4j.store.embedding.filter.comparison.LessThan;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;

class MilvusMetadataFilterMapper {

    static String map(MetadataFilter metadataFilter) {
        if (metadataFilter instanceof Equal) {
            return mapEqual((Equal) metadataFilter);
        }
//        else if (metadataFilter instanceof GreaterThan) {
//            return mapGreaterThan((GreaterThan) metadataFilter);
//        } else if (metadataFilter instanceof LessThan) {
//            return mapLessThan((LessThan) metadataFilter);
//        } else if (metadataFilter instanceof In) {
//            return mapIn((In) metadataFilter);
//        } else if (metadataFilter instanceof And) {
//            return mapAnd((And) metadataFilter);
//        } else if (metadataFilter instanceof Group) {
//            return map(((Group) metadataFilter).metadataFilter());
//        } else if (metadataFilter instanceof Not) {
//            return mapNot((Not) metadataFilter);
//        } else if (metadataFilter instanceof Or) {
//            return mapOr((Or) metadataFilter);
//        }
        else {
            throw new UnsupportedOperationException("Unsupported metadataFilter type: " + metadataFilter.getClass().getName());
        }
        // TODO map other expressions
    }

    private static String mapEqual(Equal equal) {
        String comparisonValue;
        if (equal.comparisonValue() instanceof String) {
            comparisonValue = "\"" + equal.comparisonValue() + "\"";
        } else {
            comparisonValue = equal.comparisonValue().toString();
        }
        return String.format("metadata[\"%s\"] == %s", equal.key(), comparisonValue);
    }

    private static String mapGreaterThan(GreaterThan greaterThan) {
        return null; // TODO
    }

    private static String mapLessThan(LessThan lessThan) {
        return null; // TODO
    }

    public static String mapIn(In in) {
        return null; // TODO
    }

    private static String mapAnd(And and) {
        return null; // TODO
    }

    private static String mapNot(Not not) {
        return null; // TODO
    }

    private static String mapOr(Or or) {
        return null; // TODO
    }
}

