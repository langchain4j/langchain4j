package dev.langchain4j.data.document.splitter;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;

public class MarkdownRebuilder extends AbstractVisitor {
    private final StringBuilder markdownBuilder = new StringBuilder();

    public static void main(String[] args) {
        String markdown = """
                # Header 1
                Some content under header 1.
                
                Another paragraph.
                A new line

                ## Header 1.1
                Content under header 1.1.

                - List item 1
                  - Iten 2.1
                  - Item 2.2
                    - Item 2.2.1
                  - Item 2.3
                - List **item** 2

                `Inline code`

                > Blockquote

                """;

        Parser parser = Parser.builder().build();
        Node document = parser.parse(markdown);

        MarkdownOutputVisitor visitor = new MarkdownOutputVisitor();
        document.accept(visitor);
        System.out.println(visitor.getOutput());
        System.out.println("----");
    }

    static class MarkdownOutputVisitor extends AbstractVisitor {
        private StringBuilder output = new StringBuilder();

        public String getOutput() {
            return output.toString();
        }

        @Override
        public void visit(Heading heading) {
            output.append("#".repeat(heading.getLevel())).append(" ");
            visitChildren(heading); // Visit children to get the text content
            output.append("\n");
        }

        @Override
        public void visit(Paragraph paragraph) {
            visitChildren(paragraph); // Visit children to get the text content
            if (!(paragraph.getParent() instanceof ListItem)) { // Don't add extra lines for paragraphs in lists
               output.append("\n\n");
            }
        }

        @Override
        public void visit(Text text) {
            output.append(text.getLiteral());
        }

        @Override
        public void visit(StrongEmphasis strongEmphasis) {
            output.append("**");
            visitChildren(strongEmphasis); // Visit children to get the text content
            output.append("**");
        }

        @Override
        public void visit(Emphasis emphasis) {
            output.append("*");
            visitChildren(emphasis); // Visit children to get the text content
            output.append("*");
        }

        @Override
        public void visit(FencedCodeBlock fencedCodeBlock) {
            output.append("```").append(fencedCodeBlock.getInfo()).append("\n")
                  .append(fencedCodeBlock.getLiteral())
                  .append("```\n\n");
        }


        @Override
        public void visit(OrderedList orderedList) {
//            if (orderedList.getParent() instanceof ListItem) {
//                output.append("\n");
//            }
            visitChildren(orderedList); // Visit children (list items)
            if (!(orderedList.getParent() instanceof ListItem)) {
                output.append("\n");
            }
        }

        @Override
        public void visit(BulletList bulletList) {
//            if (bulletList.getParent() instanceof ListItem) {
//                output.append("\n");
//            }
            visitChildren(bulletList); // Visit children (list items)
            if (!(bulletList.getParent() instanceof ListItem)) {
                output.append("\n\n");
            }
        }

        @Override
        public void visit(ListItem listItem) {
            if (output.length() == 0 || output.charAt(output.length() - 1) != '\n') {
                output.append("\n");
            }

            output.append("- ");
            visitChildren(listItem); // Visit children to get the text content

        }

        @Override
        public void visit(final Code code) {
            output.append("`");
            output.append(code.getLiteral());
            output.append("`");
        }



        @Override
        public void visit(final HardLineBreak hardLineBreak) {
            output.append("\n\n");
            visitChildren(hardLineBreak);
        }

        @Override
        public void visit(final SoftLineBreak softLineBreak) {
            // Don't break softly
            //output.append("\n")
            if (!Character.isSpaceChar(output.charAt(output.length() - 1))) {
                output.append(" ");
            }
            super.visit(softLineBreak);
        }
    }
}
