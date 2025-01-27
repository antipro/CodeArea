package com.antipro.control.syntax;

import com.antipro.control.CodeArea;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.VPos;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextBoundsType;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SQLSyntax extends SyntaxHighlighter {

    private static final Map<String, Color> SYNTAX_COLORS = new HashMap<>() {{
        put("keyword", Color.PURPLE);
        put("string", Color.GREEN);
        put("number", Color.BLUE);
        put("comment", Color.GRAY);
        put("operator", Color.RED);
    }};

    public static final List<String> KEYWORDS = Arrays.asList(
            "with", "as",
            "select", "update", "delete", "from", "to", "where", "order", "asc", "desc", "group", "by",
            "and", "or",
            "call", "merge", "for", "loop",

            "replace", "alter", "create", "drop", "grant", "revoke", "insert", "into",
            "each", "row", "values", "on", "rename", "set",

            "view", "index", "table", "tablespace", "column", "procedure", "function", "directory",
            "trigger", "sequence", "package", "after", "before",

            "temporary", "global", "using", "default", "add", "alias",
            "constraint", "check", "unique", "primary", "foreign", "key", "references",
            "materialized", "refresh", "fast",

            "not", "null", "is", "in", "like", "between", "exists", "any", "all",

            "union", "intersect", "minus", "join", "inner", "outer", "left", "right", "full",
            "cross", "partition", "over", "distinct",
            "if", "elsif", "case", "when", "then", "else",
            "begin", "end", "commit", "rollback", "declare", "comment", "execute", "immediate",

            "varchar2", "varchar", "char", "number", "date", "timestamp", "clob", "blob",

            "pctfree", "initrans", "maxtrans", "storage", "initial", "next", "minextents", "maxextents", "unlimited"
            );

    private static final Pattern PATTERNS = Pattern.compile(
            "(?<KEYWORD>\\b(" + String.join("|", KEYWORDS) + ")\\b)|" +
                    "(?<STRING>'.*?')|" +
                    "(?<NUMBER>\\b\\d+\\b)|" +
                    "(?<COMMENT>(--.*)|(/\\*)|(\\*/))|" +
                    "(?<OPERATOR>[+\\-*/=<>!&|])",
            Pattern.CASE_INSENSITIVE
    );

    private boolean inBlockComment = false;

    @Override
    public List<Text> decompose(String sourceCode,
                                IntegerProperty tabSizeProperty,
                                ChangeListener<TextBoundsType> callback,
                                ObjectProperty<Font> fontProperty,
                                ObjectProperty<Paint> selectionFillProperty) {
        if (sourceCode.isBlank()) {
            Text textNode = new Text(sourceCode);
            textNode.getStyleClass().add("default");
            textNode.setTextOrigin(VPos.TOP);
            textNode.setManaged(false);
            textNode.tabSizeProperty().bind(tabSizeProperty);
            textNode.boundsTypeProperty().addListener(callback);
            textNode.fontProperty().bind(fontProperty);
            textNode.selectionFillProperty().bind(selectionFillProperty);
            return List.of(textNode);
        }
        List<Text> textNodes = new ArrayList<>();

        Matcher matcher = PATTERNS.matcher(sourceCode);
        int lastKwEnd = 0;

        while (matcher.find()) {
            // Add unstyled text before the match
            if (matcher.start() > lastKwEnd) {
                Text textNode = new Text(sourceCode.substring(lastKwEnd, matcher.start()));
                textNode.getStyleClass().add("default");
                textNode.setTextOrigin(VPos.TOP);
                textNode.setManaged(false);
                textNode.tabSizeProperty().bind(tabSizeProperty);
                textNode.boundsTypeProperty().addListener(callback);
                textNode.fontProperty().bind(fontProperty);
                textNode.selectionFillProperty().bind(selectionFillProperty);
                textNodes.add(textNode);
            }

            // Get the styled text
            String styleGroup = getStyleGroup(matcher);
            if (styleGroup != null) {
                Text textNode = new Text(matcher.group());
                textNode.getStyleClass().add(styleGroup);
                textNode.setTextOrigin(VPos.TOP);
                textNode.setManaged(false);
                textNode.tabSizeProperty().bind(tabSizeProperty);
                textNode.boundsTypeProperty().addListener(callback);
                textNode.fontProperty().bind(fontProperty);
                textNode.selectionFillProperty().bind(selectionFillProperty);
                textNodes.add(textNode);

                // Check for block comment start/end
                // FIXME should remember the state if the block comment is broken.
                //  the context should be represented by the SyntaxHighlighter instance
                //  if the text is no in the block . it should not be impacted.
                if (styleGroup.equals("comment")) {
                    if (matcher.group().startsWith("/*")) {
                        inBlockComment = true;
                    }
                    if (matcher.group().endsWith("*/")) {
                        inBlockComment = false;
                    }
                }
            }

            lastKwEnd = matcher.end();
        }

        // Add remaining unstyled text
        if (lastKwEnd < sourceCode.length()) {
            Text textNode = new Text(sourceCode.substring(lastKwEnd));
            textNode.setTextOrigin(VPos.TOP);
            textNode.getStyleClass().add("default");
            textNode.setManaged(false);
            textNode.tabSizeProperty().bind(tabSizeProperty);
            textNode.boundsTypeProperty().addListener(callback);
            textNode.fontProperty().bind(fontProperty);
            textNode.selectionFillProperty().bind(selectionFillProperty);
            textNodes.add(textNode);
        }

        return textNodes;
    }

    private String getStyleGroup(Matcher matcher) {
        for (String group : new String[]{"KEYWORD", "STRING", "NUMBER", "COMMENT", "OPERATOR"}) {
            if (matcher.group(group) != null) {
                return group.toLowerCase();
            }
        }
        return null;
    }

    @Override
    public void highlight(Text textNode) {
        if (textNode.getStyleClass().isEmpty()) {
            return;
        }
        String styleGroup = textNode.getStyleClass().getFirst();
        if (styleGroup.equals("comment")) {
            if (textNode.getText().startsWith("/*")) {
                inBlockComment = true;
            }
            if (textNode.getText().endsWith("*/")) {
                inBlockComment = false;
            }
        }
        textNode.setFill(inBlockComment ? Color.GRAY : SYNTAX_COLORS.getOrDefault(styleGroup, Color.BLACK));
    }

}