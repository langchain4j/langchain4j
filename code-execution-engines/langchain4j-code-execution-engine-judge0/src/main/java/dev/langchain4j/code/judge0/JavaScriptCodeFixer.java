package dev.langchain4j.code.judge0;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class JavaScriptCodeFixer {

    private static final Logger log = LoggerFactory.getLogger(JavaScriptCodeFixer.class);

    static String fixIfNoLogToConsole(String code) {
        if (code.contains("\n")) {
            return fixIfNoLogToConsole(code, "\n");
        }
        return fixSingleLineIfNoLogToConsole(code);
    }

    private static String fixIfNoLogToConsole(String code, String separator) {
        String[] parts = code.split(separator);
        String lastPart = parts[parts.length - 1];
        if (lastPart.startsWith("console.log")) {
            return code;
        }

        parts[parts.length - 1] = "console.log(" + lastPart.replace(";", "") + ");";
        String fixedCode = String.join(separator, parts);
        log.debug("The following code \"{}\" was fixed: \"{}\"", code, fixedCode);
        return fixedCode;
    }

    private static String fixSingleLineIfNoLogToConsole(String code) {
        int lastSemicolon = code.lastIndexOf(';');
        String trailing = code.substring(lastSemicolon + 1);
        String statement = trailing.trim();
        if (statement.isEmpty() || statement.startsWith("console.log")) {
            return code;
        }

        String prefix = code.substring(0, lastSemicolon + 1);
        String leadingWhitespace = trailing.substring(0, trailing.indexOf(statement));
        String fixedCode = prefix + leadingWhitespace + "console.log(" + statement + ");";
        log.debug("The following code \"{}\" was fixed: \"{}\"", code, fixedCode);
        return fixedCode;
    }
}
