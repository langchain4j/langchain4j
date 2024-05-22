package dev.langchain4j.code.judge0;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JavaScriptCodeFixerTest {

    @Test
    void should_fix_multi_line_code_when_result_is_not_logged_to_console() {
        String code = "const startDate = new Date('Feb 21, 1988 17:00:00');\nconst endDate = new Date('Apr 12, 2014 04:00:00');\nconst diff = endDate - startDate;\nconst result = diff / (1000*60*60);\n;const startDate = new Date('Feb 21, 1988 17:00:00');\nconst endDate = new Date('Apr 12, 2014 04:00:00');\nconst diff = endDate - startDate;\nconst result = diff / (1000*60*60);\n";

        String result = JavaScriptCodeFixer.fixIfNoLogToConsole(code + "result");

        assertThat(result).isEqualTo(code + "console.log(result);");
    }

    @Test
    void should_not_fix_multi_line_code_when_result_is_logged_to_console() {
        String code = "const startDate = new Date('Feb 21, 1988 17:00:00');\nconst endDate = new Date('Apr 12, 2014 04:00:00');\nconst diff = endDate - startDate;\nconst result = diff / (1000*60*60);\nconsole.log(result);";

        String result = JavaScriptCodeFixer.fixIfNoLogToConsole(code);

        assertThat(result).isEqualTo(code);
    }

    @Test
    void should_fix_one_line_code_when_result_is_not_logged_to_console() {
        String code = "const start = new Date('1988-02-21T17:00:00'); const end = new Date('2014-04-12T04:00:00'); const hours = (end - start) / (1000 * 60 * 60); ";

        String result = JavaScriptCodeFixer.fixIfNoLogToConsole(code + "hours");

        assertThat(result).isEqualTo(code + "console.log(hours);");
    }

    @Test
    void should_not_fix_one_line_code_when_result_is_logged_to_console() {
        String code = "const start = new Date('1988-02-21T17:00:00'); const end = new Date('2014-04-12T04:00:00'); const hours = (end - start) / (1000 * 60 * 60); console.log(hours);";

        String result = JavaScriptCodeFixer.fixIfNoLogToConsole(code);

        assertThat(result).isEqualTo(code);
    }

    @Test
    void should_fix_one_statement_when_result_is_not_logged_to_console() {
        String code = "Math.sqrt(49506838032859)";

        String result = JavaScriptCodeFixer.fixIfNoLogToConsole(code);

        assertThat(result).isEqualTo("console.log(Math.sqrt(49506838032859));");
    }

    @Test
    void should_not_fix_one_statement_when_result_is_logged_to_console() {
        String code = "console.log(Math.sqrt(49506838032859));";

        String result = JavaScriptCodeFixer.fixIfNoLogToConsole(code);

        assertThat(result).isEqualTo(code);
    }
}