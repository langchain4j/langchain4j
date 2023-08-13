package dev.langchain4j.common.utils;

/**
 * Assert Utility
 */
public class AssertUtil {

    private AssertUtil() throws InstantiationException {
        throw new InstantiationException("can't instantiate this class");
    }

    public static void notNull(Object object) {
        notNull(object, "[Assertion failed] - this argument is required; it must not be null");
    }

    public static void notNull(Object object, String msg) {
        if (object == null) {
            throw new IllegalArgumentException(msg);
        }
    }

    public static void isNull(Object object) {
        isNull(object, "[Assertion failed] - the object argument must be null");
    }

    public static void isNull(Object object, String msg) {
        if (object != null) {
            throw new IllegalArgumentException(msg);
        }
    }

    public static void isTrue(boolean expression) {
        isTrue(expression, "[Assertion failed] - the expression argument must be true");
    }

    public static void isTrue(boolean expression, String msg) {
        if (!expression) {
            throw new IllegalArgumentException(msg);
        }
    }

    public static void hasLength(CharSequence charSequence) {
        hasLength(charSequence, "[Assertion failed] - the charSequence argument must have length");
    }

    public static void hasLength(CharSequence charSequence, String msg) {
        if (charSequence == null || charSequence.length() == 0) {
            throw new IllegalArgumentException(msg);
        }
    }
}
