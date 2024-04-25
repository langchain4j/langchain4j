package dev.langchain4j.store.embedding.pgvector;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;

import java.util.Collection;
import java.util.stream.Collectors;


/**
 * @author nottyjay
 */
public class PgVectorMetadataFilterMapper {

  public static String map(Filter filter) {
    if (filter instanceof IsEqualTo) {
      return isEqualToMap((IsEqualTo) filter);
    } else if (filter instanceof IsNotEqualTo) {
      return isNotEqualToMap((IsNotEqualTo) filter);
    } else if (filter instanceof IsGreaterThan) {
      return isGreaterThanMap((IsGreaterThan) filter);
    } else if (filter instanceof IsGreaterThanOrEqualTo) {
      return isGreaterThanOrEqualToMap((IsGreaterThanOrEqualTo) filter);
    } else if (filter instanceof IsLessThan) {
      return isLessThanMap((IsLessThan) filter);
    } else if (filter instanceof IsLessThanOrEqualTo) {
      return isLessThanOrEqualToMap((IsLessThanOrEqualTo) filter);
    } else if (filter instanceof IsIn) {
      return isInMap((IsIn) filter);
    } else if (filter instanceof IsNotIn) {
      return isNotInMap((IsNotIn) filter);
    } else if (filter instanceof And) {
      return andMap((And) filter);
    } else if (filter instanceof Or) {
      return orMap((Or) filter);
    } else if (filter instanceof Not) {
      return notMap((Not) filter);
    } else {
      throw new UnsupportedOperationException("Unsupported filter type: " + filter.getClass().getName());
    }
  }

  private static String isInMap(IsIn filter) {
    return String.format("%s IN (%s)", formatKey(filter.key(), filter.comparisonValues()), formatValues(filter.comparisonValues()));
  }

  private static String isLessThanOrEqualToMap(IsLessThanOrEqualTo filter) {
    return String.format("%s <= %s", formatKey(filter.key(), filter.comparisonValue()), formatValue(filter.comparisonValue()));
  }

  private static String isNotInMap(IsNotIn filter) {
    return String.format("%s NOT IN (%s)", formatKey(filter.key(), filter.comparisonValues()), formatValues(filter.comparisonValues()));
  }

  private static String andMap(And filter) {
    return String.format("(%s AND %s)", map(filter.left()), map(filter.right()));
  }

  private static String orMap(Or filter) {
    return String.format("(%s OR %s)", map(filter.left()), map(filter.right()));
  }

  private static String isLessThanMap(IsLessThan filter) {
    return String.format("%s < %s", formatKey(filter.key(), filter.comparisonValue()), formatValue(filter.comparisonValue()));
  }

  private static String isGreaterThanOrEqualToMap(IsGreaterThanOrEqualTo filter) {
    return String.format("%s >= %s", formatKey(filter.key(), filter.comparisonValue()), formatValue(filter.comparisonValue()));
  }

  private static String notMap(Not filter) {
    return String.format("NOT (%s)", map(filter.expression()));
  }

  private static String isGreaterThanMap(IsGreaterThan filter) {
    return String.format("%s > %s", formatKey(filter.key(), filter.comparisonValue()), formatValue(filter.comparisonValue()));
  }

  private static String isEqualToMap(IsEqualTo filter) {
    return String.format("%s = %s", formatKey(filter.key(), filter.comparisonValue()), formatValue(filter.comparisonValue()));
  }

  private static String isNotEqualToMap(IsNotEqualTo filter) {
    return String.format("%s != %s", formatKey(filter.key(), filter.comparisonValue()), formatValue(filter.comparisonValue()));
  }

  private static String formatKey(String key, Object value) {
    String keyName = "metadata->>'" + key + "'";
    if (value instanceof Number) {
      return "CAST ( " + keyName + " AS NUMERIC )";
    } else if (value instanceof Collection) {
      Object valueTmp = ((Collection) value).stream().findFirst().get();
      if (valueTmp instanceof Number) {
        return "CAST ( " + keyName + " AS NUMERIC )";
      }
    }
    return keyName;
  }

  private static String formatValue(Object value) {
    if (value instanceof String) {
      return "\"" + value + "\"";
    } else {
      return value.toString();
    }
  }

  private static String formatValues(Collection<?> values) {
    return values.stream().map(value -> {
      if (value instanceof String) {
        return "\"" + value + "\"";
      } else {
        return value.toString();
      }
    }).collect(Collectors.joining(","));
  }
}
