package com.bitifyware.control;

import com.bitifyware.control.skin.CodeAreaSkin;
import com.bitifyware.control.skin.GlobalHitInfo;
import com.bitifyware.control.syntax.DemoSyntax;
import com.bitifyware.control.syntax.SyntaxHighlighter;
import com.sun.javafx.collections.ListListenerHelper;
import com.sun.javafx.collections.NonIterableChange;
import javafx.beans.InvalidationListener;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.*;
import javafx.css.converter.SizeConverter;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.AccessibleRole;
import javafx.scene.control.IndexRange;
import javafx.scene.control.Skin;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextBoundsType;

import java.util.*;

/**
 * @author antipro
 */
public class CodeArea extends CodeInputControl {

    private final ObservableList<IndexRange> columnSelectionRanges = FXCollections.observableArrayList();
    private final ObservableList<IndexRange> readOnlyColumnSelectionRanges =
            FXCollections.unmodifiableObservableList(columnSelectionRanges);
    private boolean columnSelectionActive;
    private boolean updatingColumnSelection;
    private int columnAnchorLine;
    private int columnAnchorColumn;
    private int columnCaretLine;
    private int columnCaretColumn;

    /** Returns the rectangular selection ranges, one range per line. */
    public ObservableList<IndexRange> getColumnSelectionRanges() {
        return readOnlyColumnSelectionRanges;
    }

    public boolean isColumnSelectionActive() {
        return columnSelectionActive;
    }

    public void beginColumnSelection(int position) {
        int[] location = lineAndColumn(position);
        columnSelectionActive = true;
        columnAnchorLine = location[0];
        columnAnchorColumn = location[1];
        columnCaretLine = location[0];
        columnCaretColumn = location[1];
        updateColumnSelectionState();
    }

    public void updateColumnSelection(int position) {
        if (!columnSelectionActive) {
            beginColumnSelection(getCaretPosition());
        }
        int[] location = lineAndColumn(position);
        columnCaretLine = location[0];
        columnCaretColumn = location[1];
        updateColumnSelectionState();
    }

    public void updateColumnSelection(int position, List<IndexRange> ranges) {
        if (!columnSelectionActive) {
            beginColumnSelection(getCaretPosition());
        }
        int[] location = lineAndColumn(position);
        columnCaretLine = location[0];
        columnCaretColumn = location[1];
        int caret = positionAt(columnCaretLine, columnCaretColumn);
        int anchor = positionAt(columnAnchorLine, columnAnchorColumn);
        updatingColumnSelection = true;
        try {
            super.selectRange(anchor, caret);
        } finally {
            updatingColumnSelection = false;
        }
        columnSelectionRanges.setAll(ranges);
    }

    public void moveColumnSelection(int lineDelta, int columnDelta) {
        if (!columnSelectionActive) {
            beginColumnSelection(getCaretPosition());
        }
        columnCaretLine = Math.max(0, Math.min(columnCaretLine + lineDelta, getParagraphs().size() - 1));
        columnCaretColumn = Math.max(0, columnCaretColumn + columnDelta);
        updateColumnSelectionState();
    }

    public void clearColumnSelection() {
        columnSelectionActive = false;
        columnSelectionRanges.clear();
    }

    public String getColumnSelectedText() {
        return columnSelectionRanges.stream()
                .map(range -> getText(range.getStart(), range.getEnd()))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    public void replaceColumnSelection(String replacement) {
        if (!columnSelectionActive) {
            replaceSelection(replacement);
            return;
        }
        List<IndexRange> ranges = new ArrayList<>(columnSelectionRanges);
        clearColumnSelection();
        for (int i = ranges.size() - 1; i >= 0; i--) {
            IndexRange range = ranges.get(i);
            replaceText(range.getStart(), range.getEnd(), replacement);
        }
        if (!ranges.isEmpty()) {
            int caret = ranges.getFirst().getStart() + replacement.length();
            super.selectRange(caret, caret);
        }
    }

    @Override
    public void selectRange(int anchor, int caretPosition) {
        if (!updatingColumnSelection) {
            clearColumnSelection();
        }
        super.selectRange(anchor, caretPosition);
    }

    @Override
    public void copy() {
        if (!columnSelectionActive) {
            super.copy();
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(getColumnSelectedText());
        Clipboard.getSystemClipboard().setContent(content);
    }

    @Override
    public void cut() {
        if (!columnSelectionActive) {
            super.cut();
            return;
        }
        copy();
        replaceColumnSelection("");
    }

    @Override
    public void replaceSelection(String replacement) {
        if (columnSelectionActive) {
            replaceColumnSelection(replacement);
        } else {
            super.replaceSelection(replacement);
        }
    }

    private void updateColumnSelectionState() {
        int firstLine = Math.min(columnAnchorLine, columnCaretLine);
        int lastLine = Math.max(columnAnchorLine, columnCaretLine);
        int firstColumn = Math.min(columnAnchorColumn, columnCaretColumn);
        int lastColumn = Math.max(columnAnchorColumn, columnCaretColumn);
        List<IndexRange> ranges = new ArrayList<>(lastLine - firstLine + 1);
        int offset = 0;
        for (int line = 0; line < getParagraphs().size(); line++) {
            int length = getParagraphs().get(line).length();
            if (line >= firstLine && line <= lastLine) {
                ranges.add(new IndexRange(offset + Math.min(firstColumn, length),
                        offset + Math.min(lastColumn, length)));
            }
            offset += length + 1;
        }
        int caret = positionAt(columnCaretLine, columnCaretColumn);
        int anchor = positionAt(columnAnchorLine, columnAnchorColumn);
        updatingColumnSelection = true;
        try {
            super.selectRange(anchor, caret);
        } finally {
            updatingColumnSelection = false;
        }
        columnSelectionRanges.setAll(ranges);
    }

    private int[] lineAndColumn(int position) {
        int clamped = Math.max(0, Math.min(position, getLength()));
        int offset = 0;
        for (int line = 0; line < getParagraphs().size(); line++) {
            int length = getParagraphs().get(line).length();
            if (clamped <= offset + length || line == getParagraphs().size() - 1) {
                return new int[]{line, Math.max(0, Math.min(clamped - offset, length))};
            }
            offset += length + 1;
        }
        return new int[]{0, 0};
    }

    private int positionAt(int line, int column) {
        int offset = 0;
        for (int i = 0; i < line; i++) {
            offset += getParagraphs().get(i).length() + 1;
        }
        return offset + Math.min(column, getParagraphs().get(line).length());
    }

    public void upperCase() {
        IndexRange selection = getSelection();
        if (selection.getLength() == 0) {
            return;
        }
        replaceSelection(getSelectedText().toUpperCase());
        selectRange(selection.getStart(), selection.getEnd());
    }

    public void lowerCase() {
        IndexRange selection = getSelection();
        if (selection.getLength() == 0) {
            return;
        }
        replaceSelection(getSelectedText().toLowerCase());
        selectRange(selection.getStart(), selection.getEnd());
    }

    public void camelCase() {
        // Not implemented yet
    }

    public void format() {
        // Not implemented yet
    }

    public void toggleComment() {
        // Not implemented yet
    }

    public void cutSelectionOrCurrentLine() {
        if (isDisabled() || !isEditable()) {
            return;
        }
        IndexRange selection = getSelection();
        if (selection.getLength() > 0) {
            cut();
            return;
        }
        int[] lineRange = getCurrentLineRange();
        if (lineRange == null) {
            return;
        }
        int lineStart = lineRange[0];
        int lineTextEnd = lineRange[1];
        String text = getText();
        String lineText = text.substring(lineStart, lineTextEnd);
        ClipboardContent content = new ClipboardContent();
        content.putString(lineText);
        Clipboard.getSystemClipboard().setContent(content);
        replaceText(lineStart, lineTextEnd, "");
        selectRange(lineStart, lineStart);
    }

    public void deleteCurrentLine() {
        if (isDisabled() || !isEditable()) {
            return;
        }
        int[] lineRange = getCurrentLineRange();
        if (lineRange == null) {
            return;
        }
        int lineStart = lineRange[0];
        int lineTextEnd = lineRange[1];
        replaceText(lineStart, lineTextEnd, "");
        selectRange(lineStart, lineStart);
    }

    public void cloneSelectionToNextLine() {
        IndexRange selection = getSelection();
        if (isDisabled() || !isEditable()) {
            return;
        }
        if (selection.getLength() == 0) {
            int caretPos = getCaretPosition();
            String text = getText();
            if (text.isEmpty()) {
                return;
            }
            int lineStart = findCurrentLineStart(text, caretPos);
            int lineEnd = text.indexOf("\n", lineStart);
            if (lineEnd == -1) {
                lineEnd = getLength();
            }
            String lineText = getText(lineStart, lineEnd);
            String cloneText = "\n" + lineText;
            insertText(lineEnd, cloneText);
            int newCaretPos = lineEnd + cloneText.length();
            selectRange(newCaretPos, newCaretPos);
            return;
        }
        int selectionStart = selection.getStart();
        int selectionEnd = selection.getEnd();
        String selectedText = getSelectedText();
        // Find next line position after current paragraph
        int lineEndPosition = getText().indexOf("\n", selectionEnd);
        if (lineEndPosition == -1) {
            lineEndPosition = getLength();
        }
        String cloneText = "\n" + selectedText;
        // Remove trailing newline in selected text to avoid adding extra empty line when clone
        cloneText = cloneText.replaceAll("\n+$", "\n");
        insertText(lineEndPosition, cloneText);
        int cloneTextLength = cloneText.length();
        if (lineEndPosition <= selectionStart) {
            selectionStart += cloneTextLength;
            selectionEnd += cloneTextLength;
        } else if (lineEndPosition < selectionEnd) {
            selectionEnd += cloneTextLength;
        }
        selectRange(selectionStart, selectionEnd);
    }

    public void moveCurrentLineUp() {
        if (isDisabled() || !isEditable()) {
            return;
        }
        String text = getText();
        if (text.isEmpty()) {
            return;
        }
        int caretPos = getCaretPosition();
        int currentLineStart = findCurrentLineStart(text, caretPos);
        if (currentLineStart == 0) {
            return;
        }
        int previousLineBreak = currentLineStart - 1;
        int previousLineStart = text.lastIndexOf("\n", Math.max(0, previousLineBreak - 1)) + 1;
        int currentLineEnd = text.indexOf("\n", currentLineStart);
        if (currentLineEnd == -1) {
            currentLineEnd = text.length();
        }
        int currentLineEndWithDelimiter = currentLineEnd < text.length() ? currentLineEnd + 1 : currentLineEnd;
        String previousLine = text.substring(previousLineStart, currentLineStart);
        String currentLine = text.substring(currentLineStart, currentLineEndWithDelimiter);
        int caretOffset = caretPos - currentLineStart;
        replaceText(previousLineStart, currentLineEndWithDelimiter, currentLine + previousLine);
        int maxOffset = maxCaretOffset(currentLine);
        int newCaretPos = previousLineStart + Math.min(Math.max(caretOffset, 0), maxOffset);
        selectRange(newCaretPos, newCaretPos);
    }

    public void moveCurrentLineDown() {
        if (isDisabled() || !isEditable()) {
            return;
        }
        String text = getText();
        if (text.isEmpty()) {
            return;
        }
        int caretPos = getCaretPosition();
        int currentLineStart = findCurrentLineStart(text, caretPos);
        int currentLineEnd = text.indexOf("\n", currentLineStart);
        if (currentLineEnd == -1) {
            return;
        }
        int nextLineStart = currentLineEnd + 1;
        int nextLineEnd = text.indexOf("\n", nextLineStart);
        if (nextLineEnd == -1) {
            nextLineEnd = text.length();
        }
        int nextLineEndWithDelimiter = nextLineEnd < text.length() ? nextLineEnd + 1 : nextLineEnd;
        String currentLine = text.substring(currentLineStart, nextLineStart);
        String nextLine = text.substring(nextLineStart, nextLineEndWithDelimiter);
        int caretOffset = caretPos - currentLineStart;
        boolean nextLineHasDelimiter = nextLineEnd < text.length();
        if (!nextLineHasDelimiter && currentLine.endsWith("\n")) {
            currentLine = currentLine.substring(0, currentLine.length() - 1);
            nextLine = nextLine + "\n";
        }
        replaceText(currentLineStart, nextLineEndWithDelimiter, nextLine + currentLine);
        int maxOffset = maxCaretOffset(currentLine);
        int newCaretPos = currentLineStart + nextLine.length() + Math.min(Math.max(caretOffset, 0), maxOffset);
        selectRange(newCaretPos, newCaretPos);
    }

    protected int findCurrentLineStart(String text, int caretPos) {
        if (text.isEmpty() || caretPos <= 0) {
            return 0;
        }
        int safePos = Math.min(caretPos - 1, text.length() - 1);
        return text.lastIndexOf("\n", safePos) + 1;
    }

    protected int maxCaretOffset(String lineWithDelimiter) {
        return lineWithDelimiter.endsWith("\n") ? lineWithDelimiter.length() - 1 : lineWithDelimiter.length();
    }

    protected int[] getCurrentLineRange() {
        String text = getText();
        if (text.isEmpty()) {
            return null;
        }
        int caretPos = getCaretPosition();
        int lineStart = findCurrentLineStart(text, caretPos);
        if (lineStart == text.length() && text.endsWith("\n")) {
            lineStart = text.length() - 1;
        }
        int lineBreak = text.indexOf("\n", lineStart);
        int lineTextEnd = lineBreak == -1 ? text.length() : lineBreak + 1;
        return new int[]{lineStart, lineTextEnd};
    }

    /**
     * If getSelection() is not empty, indent the selected text.
     * If getSelection() is empty, just insert a tab character at the caret position.
     */
    public void indent(boolean useSpaces) {
        int tabSize = tabSizeProperty().get();
        String tabChar = useSpaces ? " ".repeat(tabSize) : "\t";
        IndexRange selection = getSelection();
        if (selection.getLength() > 0) {
            int start = selection.getStart();
            int end = selection.getEnd();
            String text = getText();
            String[] lines = text.substring(start, end).split("\n");
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                sb.append(tabChar).append(line).append("\n");
            }
            replaceText(start, end, sb.toString());
            selectRange(start, start + sb.length());
        } else {
            insertText(getCaretPosition(), tabChar);
        }
    }

    /**
     * Un-indent the selected text or the line at the caret position.
     */
    public void unIndent() {
        int tabSize = tabSizeProperty().get();
        String tabChar = "\t";
        IndexRange selection = getSelection();
        if (selection.getLength() > 0) {
            int start = selection.getStart();
            int end = selection.getEnd();
            String text = getText();
            String[] lines = text.substring(start, end).split("\n");
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                if (line.startsWith(tabChar)) {
                    sb.append(line.substring(tabChar.length())).append("\n");
                } else if (line.startsWith(" ")) {
                    // Remove heading spaces not exceeding tab size
                    int spaceCount = 0;
                    while (spaceCount < line.length() && line.charAt(spaceCount) == ' ') {
                        spaceCount++;
                        if (spaceCount >= tabSize) {
                            break;
                        }
                    }
                    sb.append(line.substring(spaceCount)).append("\n");
                } else {
                    sb.append(line).append("\n");
                }
            }
            replaceText(start, end, sb.toString());
            selectRange(start, start + sb.length());
        } else {
            int caretPos = getCaretPosition();
            String line = getText(0, caretPos).substring(getText(0, caretPos).lastIndexOf("\n") + 1);
            if (line.startsWith(tabChar)) {
                replaceText(caretPos - tabChar.length(), caretPos, "");
            } else if (line.startsWith(" ")) {
                // Remove leading spaces not exceeding tab size
                int spaceCount = 0;
                while (spaceCount < line.length() && line.charAt(spaceCount) == ' ') {
                    spaceCount++;
                    if (spaceCount >= tabSize) {
                        break;
                    }
                }
                replaceText(caretPos - spaceCount, caretPos, "");
            }
        }
    }

    protected static abstract class CodeAreaContent extends ContentBase {
        protected List<StringBuilder> paragraphs;

        public abstract ObservableList<CharSequence> getParagraphList();
    }

    // Observable list of paragraphs
    protected static final class ParagraphList extends AbstractList<CharSequence>
            implements ObservableList<CharSequence> {

        private CodeAreaContent content;
        private ListListenerHelper<CharSequence> listenerHelper;

        @Override
        public CharSequence get(int index) {
            return content.paragraphs.get(index);
        }

        @Override
        public boolean addAll(Collection<? extends CharSequence> paragraphs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addAll(CharSequence... paragraphs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean setAll(Collection<? extends CharSequence> paragraphs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean setAll(CharSequence... paragraphs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int size() {
            return content.paragraphs.size();
        }

        @Override
        public void addListener(ListChangeListener<? super CharSequence> listener) {
            listenerHelper = ListListenerHelper.addListener(listenerHelper, listener);
        }

        @Override
        public void removeListener(ListChangeListener<? super CharSequence> listener) {
            listenerHelper = ListListenerHelper.removeListener(listenerHelper, listener);
        }

        @Override
        public boolean removeAll(CharSequence... elements) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean retainAll(CharSequence... elements) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remove(int from, int to) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addListener(InvalidationListener listener) {
            listenerHelper = ListListenerHelper.addListener(listenerHelper, listener);
        }

        @Override
        public void removeListener(InvalidationListener listener) {
            listenerHelper = ListListenerHelper.removeListener(listenerHelper, listener);
        }

        public void setContent(CodeAreaContent content) {
            this.content = content;
        }

        public ListListenerHelper<CharSequence> getListenerHelper() {
            return listenerHelper;
        }
    }

    protected static final class ParagraphListChange extends NonIterableChange<CharSequence> {

        private List<CharSequence> removed;

        protected ParagraphListChange(ObservableList<CharSequence> list, int from, int to,
                                      List<CharSequence> removed) {
            super(from, to, list);

            this.removed = removed;
        }

        @Override
        public List<CharSequence> getRemoved() {
            return removed;
        }

        @Override
        protected int[] getPermutation() {
            return new int[0];
        }
    }

    /**
     * Describes an empty line to be rendered in the CodeArea for file diff support.
     * An empty line is a visual-only line with a colored background that does not
     * contain any text content. It is rendered at the specified paragraph index
     * position in the code area.
     */
    public static final class EmptyLine {
        private final int paragraphIndex;
        private final Color color;
        private final String styleClass;

        /**
         * Creates a new EmptyLine.
         *
         * @param paragraphIndex the paragraph index before which this empty line
         *                       should be rendered (0-based). Use the paragraph count
         *                       to place it after the last paragraph.
         * @param color          the background color of the empty line
         */
        public EmptyLine(int paragraphIndex, Color color) {
            this(paragraphIndex, color, null);
        }

        /**
         * Creates a new EmptyLine.
         *
         * @param paragraphIndex the paragraph index before which this empty line
         *                       should be rendered (0-based). Use the paragraph count
         *                       to place it after the last paragraph.
         * @param styleClass     the CSS style class applied to the rendered empty line
         */
        public EmptyLine(int paragraphIndex, String styleClass) {
            this(paragraphIndex, null, styleClass);
        }

        private EmptyLine(int paragraphIndex, Color color, String styleClass) {
            if (paragraphIndex < 0) {
                throw new IllegalArgumentException("paragraphIndex cannot be negative.");
            }
            if (color == null && (styleClass == null || styleClass.isBlank())) {
                throw new IllegalArgumentException("color or styleClass cannot both be null.");
            }
            this.paragraphIndex = paragraphIndex;
            this.color = color;
            this.styleClass = styleClass;
        }

        /**
         * Gets the paragraph index before which this empty line should be rendered.
         *
         * @return the paragraph index
         */
        public int getParagraphIndex() {
            return paragraphIndex;
        }

        /**
         * Gets the background color of this empty line.
         *
         * @return the background color
         */
        public Color getColor() {
            return color;
        }

        public String getStyleClass() {
            return styleClass;
        }
    }

    /**
     * Describes a background color applied to a real text paragraph (line) for diff support.
     * Unlike {@link EmptyLine}, this decorates an existing text row rather than inserting
     * a phantom row.
     */
    public static final class LineBackground {
        private final int paragraphIndex;
        private final Color color;
        private final String styleClass;

        public LineBackground(int paragraphIndex, Color color) {
            this(paragraphIndex, color, null);
        }

        public LineBackground(int paragraphIndex, String styleClass) {
            this(paragraphIndex, null, styleClass);
        }

        private LineBackground(int paragraphIndex, Color color, String styleClass) {
            if (paragraphIndex < 0) {
                throw new IllegalArgumentException("paragraphIndex cannot be negative.");
            }
            if (color == null && (styleClass == null || styleClass.isBlank())) {
                throw new IllegalArgumentException("color or styleClass cannot both be null.");
            }
            this.paragraphIndex = paragraphIndex;
            this.color = color;
            this.styleClass = styleClass;
        }

        public int getParagraphIndex() {
            return paragraphIndex;
        }

        public Color getColor() {
            return color;
        }

        public String getStyleClass() {
            return styleClass;
        }
    }

    /**
     * Describes a colored highlight span within the text for intra-line diff support.
     * Start and end are absolute character offsets into the full text content.
     */
    public static final class IntraLineHighlight {
        private final int start;
        private final int end;
        private final Color color;

        public IntraLineHighlight(int start, int end, Color color) {
            if (start < 0) {
                throw new IllegalArgumentException("start cannot be negative.");
            }
            if (end <= start) {
                throw new IllegalArgumentException("end must be greater than start.");
            }
            if (color == null) {
                throw new IllegalArgumentException("color cannot be null.");
            }
            this.start = start;
            this.end = end;
            this.color = color;
        }

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }

        public Color getColor() {
            return color;
        }
    }

    /**
     * The default value for {@link #prefColumnCountProperty() prefColumnCount}.
     */
    public static final int DEFAULT_PREF_COLUMN_COUNT = 40;

    /**
     * The default value for {@link #prefRowCountProperty() prefRowCount}.
     */
    public static final int DEFAULT_PREF_ROW_COUNT = 10;

    /**
     * Creates a {@code CodeArea} with empty text content.
     */
    public CodeArea() {
        this("");
    }

    /**
     * Creates a {@code CodeArea} with initial text content.
     *
     * @param text A string for text content.
     */
    public CodeArea(String text) {
        super(new InMemoryContent());
        getStyleClass().addAll("text-area", "code-area");
        setAccessibleRole(AccessibleRole.TEXT_AREA);
        setText(text);
    }

    public CodeArea(String text, boolean large) {
        super(large ? new InDiskContent() : new InMemoryContent());
        getStyleClass().addAll("text-area", "code-area");
        setAccessibleRole(AccessibleRole.TEXT_AREA);
        setText(text);
    }

    @Override final void textUpdated() {
        setScrollTop(0);
        setScrollLeft(0);
    }

    /**
     * Returns an unmodifiable list of the character sequences that back the
     * text area's content.
     * @return an unmodifiable list of the character sequences that back the
     * text area's content
     */
    public ObservableList<CharSequence> getParagraphs() {
        return ((CodeAreaContent)getContent()).getParagraphList();
    }


    /* *************************************************************************
     *                                                                         *
     * Properties                                                              *
     *                                                                         *
     **************************************************************************/

    /**
     * If a run of text exceeds the width of the {@code CodeArea},
     * then this variable indicates whether the text should wrap onto
     * another line.
     */
    private BooleanProperty wrapText = new StyleableBooleanProperty(false) {
        @Override public Object getBean() {
            return CodeArea.this;
        }

        @Override public String getName() {
            return "wrapText";
        }

        @Override public CssMetaData<CodeArea,Boolean> getCssMetaData() {
            return StyleableProperties.WRAP_TEXT;
        }
    };
    public final BooleanProperty wrapTextProperty() { return wrapText; }
    public final boolean isWrapText() { return wrapText.getValue(); }
    public final void setWrapText(boolean value) { wrapText.setValue(value); }

    private final ObjectProperty<SyntaxHighlighter> syntaxHighlighter = new SimpleObjectProperty<>(this, "syntaxHighlighter");
    public final ObjectProperty<SyntaxHighlighter> syntaxHighlighterProperty() { return syntaxHighlighter; }
    public final SyntaxHighlighter getSyntaxHighlighter() {
        if (syntaxHighlighter.get() == null) {
            // Default syntax highlighter
            syntaxHighlighter.set(new DemoSyntax() {
                @Override
                public List<Text> decompose(String sentence, IntegerProperty tabSizeProperty, ChangeListener<TextBoundsType> callback, ObjectProperty<Font> fontProperty, ObjectProperty<Paint> selectionFillProperty) {
                    Text singleText = new Text(sentence);
                    singleText.setTextOrigin(javafx.geometry.VPos.TOP);
                    singleText.setManaged(false);
                    singleText.tabSizeProperty().bind(tabSizeProperty);
                    singleText.boundsTypeProperty().addListener(callback);
                    singleText.fontProperty().bind(fontProperty);
                    singleText.selectionFillProperty().bind(selectionFillProperty);
                    return Collections.singletonList(singleText);
                }
            });
        }
        return syntaxHighlighter.get(); }
    public final void setSyntaxHighlighter(SyntaxHighlighter value) {
        syntaxHighlighter.set(value);
    }

    private final IntegerProperty tabSize = new SimpleIntegerProperty(this, "tabSize", 4);

    public final IntegerProperty tabSizeProperty() {
        return tabSize;
    }

    public final void setTabSize(int tabSize) {
        if (tabSize < 1) {
            throw new IllegalArgumentException("tabSize cannot be less than 1.");
        }
        this.tabSize.set(tabSize);
    }

    private final StringProperty highlightClassProperty = new SimpleStringProperty(this, "highlightClass", "");

    public final StringProperty highlightClassProperty() {
        return highlightClassProperty;
    }

    public final String getHighlightClass() {
        return highlightClassProperty.get();
    }

    public final void setHighlightClass(String highlightClass) {
        if (highlightClass == null) {
            throw new IllegalArgumentException("identifier cannot be null.");
        }
        highlightClassProperty.set(highlightClass);
    }

    private final ObservableList<Integer> errorPosList = FXCollections.observableArrayList();


    private final ObjectProperty<IndexRange> highlightedRange = new SimpleObjectProperty<>(this, "highlightedRange", null);

    public final ObjectProperty<IndexRange> highlightedRangeProperty() {
        return highlightedRange;
    }

    public final IndexRange getHighlightedRange() {
        return highlightedRange.get();
    }

    public final void setHighlightedRange(IndexRange range) {
        highlightedRange.set(range);
        if (range == null) {
            return;
        }
        selectRange(range.getStart(), range.getStart());
    }

    public final void addErrorPos(Integer errorPos) {
        errorPosList.add(errorPos);
    }

    public ObservableList<Integer> getErrorPosList() { return errorPosList; }

    public void clearErrorPos() {
        errorPosList.clear();
    }

    private final ObservableList<EmptyLine> emptyLines = FXCollections.observableArrayList();

    /**
     * Adds an empty line at the specified paragraph index with the given background color.
     * Empty lines are visual-only lines used for file diff support. They do not contain
     * any text content and do not affect text indexing or caret positioning.
     *
     * @param paragraphIndex the paragraph index before which the empty line should appear
     * @param color          the background color of the empty line
     */
    public void addEmptyLine(int paragraphIndex, Color color) {
        emptyLines.add(new EmptyLine(paragraphIndex, color));
    }

    /**
     * Adds an empty line at the specified paragraph index with the given style class.
     * Empty lines are visual-only lines used for file diff support. They do not contain
     * any text content and do not affect text indexing or caret positioning.
     *
     * @param paragraphIndex the paragraph index before which the empty line should appear
     * @param styleClass     the CSS style class applied to the rendered empty line
     */
    public void addEmptyLine(int paragraphIndex, String styleClass) {
        emptyLines.add(new EmptyLine(paragraphIndex, styleClass));
    }

    /**
     * Returns the observable list of empty lines.
     *
     * @return the observable list of empty lines
     */
    public ObservableList<EmptyLine> getEmptyLines() {
        return emptyLines;
    }

    /**
     * Clears all empty lines.
     */
    public void clearEmptyLines() {
        emptyLines.clear();
    }

    private final ObservableList<LineBackground> lineBackgrounds = FXCollections.observableArrayList();

    /**
     * Adds a background color to the paragraph at the given index for diff highlighting.
     * The color persists across layout passes until cleared.
     *
     * @param paragraphIndex the 0-based paragraph (line) index
     * @param color          the background color to apply
     */
    public void addLineBackground(int paragraphIndex, Color color) {
        lineBackgrounds.add(new LineBackground(paragraphIndex, color));
    }

    /**
     * Adds a background style class to the paragraph at the given index for diff highlighting.
     * The style persists across layout passes until cleared.
     *
     * @param paragraphIndex the 0-based paragraph (line) index
     * @param styleClass     the CSS style class to apply
     */
    public void addLineBackground(int paragraphIndex, String styleClass) {
        lineBackgrounds.add(new LineBackground(paragraphIndex, styleClass));
    }

    public ObservableList<LineBackground> getLineBackgrounds() {
        return lineBackgrounds;
    }

    public void clearLineBackgrounds() {
        lineBackgrounds.clear();
    }

    private final ObservableList<IntraLineHighlight> intraLineHighlights = FXCollections.observableArrayList();

    /**
     * Adds a colored highlight span over an absolute character range in the text.
     * Intended for intra-line diff highlighting of changed character regions.
     *
     * @param start absolute character offset (inclusive)
     * @param end   absolute character offset (exclusive)
     * @param color the fill color of the highlight
     */
    public void addIntraLineHighlight(int start, int end, Color color) {
        intraLineHighlights.add(new IntraLineHighlight(start, end, color));
    }

    public ObservableList<IntraLineHighlight> getIntraLineHighlights() {
        return intraLineHighlights;
    }

    public void clearIntraLineHighlights() {
        intraLineHighlights.clear();
    }

    {
        textProperty().addListener((observable, oldValue, newValue) -> {
            errorPosList.clear();
            highlightedRange.set(null);
            lineBackgrounds.clear();
            intraLineHighlights.clear();
        });
    }

    public Rectangle2D getCharacterBounds(int index) {
        return ((CodeAreaSkin)getSkin()).getCharacterBounds(index);
    }

    private final ObjectProperty<EventHandler<ContextMenuEvent>> gutterEventHandlerProperty =
            new SimpleObjectProperty<>(this, "gutterEventHandler=");

    public final ObjectProperty<EventHandler<ContextMenuEvent>> gutterEventHandlerProperty() {
        return gutterEventHandlerProperty;
    }

    public final EventHandler<ContextMenuEvent> getGutterEventHandler() {
        return gutterEventHandlerProperty.get();
    }

    private final ObjectProperty<Point2D> caretPosition = new SimpleObjectProperty<>(this, "caretPosition", new Point2D(0, 0));

    public final ObjectProperty<Point2D> caretPointProperty() { return caretPosition; }

    /**
     * The preferred number of text columns. This is used for
     * calculating the {@code CodeArea}'s preferred width.
     */
    private IntegerProperty prefColumnCount = new StyleableIntegerProperty(DEFAULT_PREF_COLUMN_COUNT) {

        private int oldValue = get();

        @Override
        protected void invalidated() {
            int value = get();
            if (value < 0) {
                if (isBound()) {
                    unbind();
                }
                set(oldValue);
                throw new IllegalArgumentException("value cannot be negative.");
            }
            oldValue = value;
        }

        @Override public CssMetaData<CodeArea,Number> getCssMetaData() {
            return StyleableProperties.PREF_COLUMN_COUNT;
        }

        @Override
        public Object getBean() {
            return CodeArea.this;
        }

        @Override
        public String getName() {
            return "prefColumnCount";
        }
    };
    public final IntegerProperty prefColumnCountProperty() { return prefColumnCount; }
    public final int getPrefColumnCount() { return prefColumnCount.getValue(); }
    public final void setPrefColumnCount(int value) { prefColumnCount.setValue(value); }


    /**
     * The preferred number of text rows. This is used for calculating
     * the {@code CodeArea}'s preferred height.
     */
    private IntegerProperty prefRowCount = new StyleableIntegerProperty(DEFAULT_PREF_ROW_COUNT) {

        private int oldValue = get();

        @Override
        protected void invalidated() {
            int value = get();
            if (value < 0) {
                if (isBound()) {
                    unbind();
                }
                set(oldValue);
                throw new IllegalArgumentException("value cannot be negative.");
            }

            oldValue = value;
        }

        @Override public CssMetaData<CodeArea,Number> getCssMetaData() {
            return StyleableProperties.PREF_ROW_COUNT;
        }

        @Override
        public Object getBean() {
            return CodeArea.this;
        }

        @Override
        public String getName() {
            return "prefRowCount";
        }
    };
    public final IntegerProperty prefRowCountProperty() { return prefRowCount; }
    public final int getPrefRowCount() { return prefRowCount.getValue(); }
    public final void setPrefRowCount(int value) { prefRowCount.setValue(value); }


    /**
     * The number of pixels by which the content is vertically
     * scrolled.
     */
    private DoubleProperty scrollTop = new SimpleDoubleProperty(this, "scrollTop", 0);
    public final DoubleProperty scrollTopProperty() { return scrollTop; }
    public final double getScrollTop() { return scrollTop.getValue(); }
    public final void setScrollTop(double value) { scrollTop.setValue(value); }


    /**
     * The number of pixels by which the content is horizontally
     * scrolled.
     */
    private DoubleProperty scrollLeft = new SimpleDoubleProperty(this, "scrollLeft", 0);
    public final DoubleProperty scrollLeftProperty() { return scrollLeft; }
    public final double getScrollLeft() { return scrollLeft.getValue(); }
    public final void setScrollLeft(double value) { scrollLeft.setValue(value); }


    /* *************************************************************************
     *                                                                         *
     * Methods                                                                 *
     *                                                                         *
     **************************************************************************/

    /** {@inheritDoc} */
    @Override protected Skin<?> createDefaultSkin() {
        return new CodeAreaSkin(this);
    }

    @Override
    String filterInput(String text) {
        return CodeInputControl.filterInput(text, false, false);
    }

    /**
     * Gets the character at the specified mouse coordinates.
     * 
     * @param x the x coordinate relative to the CodeArea
     * @param y the y coordinate relative to the CodeArea
     * @return the character at the specified position, or null if the position is invalid
     */
    public String getTextAtPosition(double x, double y) {
        int charIndex = getCharacterIndexAtPosition(x, y);
        if (charIndex < 0) {
            return null;
        }
        
        String content = getText();
        if (charIndex < content.length()) {
            return String.valueOf(content.charAt(charIndex));
        }
        
        return null;
    }

    /**
     * Gets the character index at the specified mouse coordinates.
     * 
     * @param x the x coordinate relative to the CodeArea
     * @param y the y coordinate relative to the CodeArea
     * @return the character index at the specified position, or -1 if the position is invalid
     */
    public int getCharacterIndexAtPosition(double x, double y) {
        CodeAreaSkin skin = (CodeAreaSkin) getSkin();
        if (skin == null) {
            return -1;
        }
        
        GlobalHitInfo hitInfo = skin.getIndex(x, y);
        if (hitInfo == null) {
            return -1;
        }
        
        return hitInfo.getCharIndex();
    }

    /**
     * Gets the JavaFX Text node at the specified mouse coordinates.
     * 
     * @param x the x coordinate relative to the CodeArea
     * @param y the y coordinate relative to the CodeArea
     * @return the Text node at the specified position, or null if the position is invalid
     */
    public Text getTextNodeAtPosition(double x, double y) {
        CodeAreaSkin skin = (CodeAreaSkin) getSkin();
        if (skin == null) {
            return null;
        }
        x -= skin.getGutterWidth();
        GlobalHitInfo hitInfo = skin.getIndex(x, y);
        if (hitInfo == null) {
            return null;
        }
        
        return hitInfo.getTextNode();
    }

    /**
     * Adds an underline to the Text node at the specified mouse coordinates.
     * The underline will be automatically cleared during layout.
     * 
     * @param x the x coordinate relative to the CodeArea
     * @param y the y coordinate relative to the CodeArea
     */
    public void addUnderlineAtPosition(double x, double y) {
        CodeAreaSkin skin = (CodeAreaSkin) getSkin();
        if (skin == null) {
            return;
        }
        
        skin.addUnderlineAtPosition(x, y);
    }

    public void clearUnderlines() {
        CodeAreaSkin skin = (CodeAreaSkin) getSkin();
        if (skin == null) {
            return;
        }

        skin.clearUnderlines();
    }

    /**
     * Scrolls the vertical scroll bar to move the specified line to the top of the viewport
     * and resets the horizontal scroll bar to the start (left position).
     * 
     * @param line the zero-based line index to scroll to
     */
    public void scrollToLine(int line) {
        CodeAreaSkin skin = (CodeAreaSkin) getSkin();
        if (skin == null) {
            return;
        }
        
        double yPosition = skin.getLineYPosition(line);
        if (yPosition >= 0) {
            setScrollTop(yPosition);
            setScrollLeft(0);
        }
    }

    /* *************************************************************************
     *                                                                         *
     * Stylesheet Handling                                                     *
     *                                                                         *
     **************************************************************************/

    private static class StyleableProperties {
        private static final CssMetaData<CodeArea,Number> PREF_COLUMN_COUNT =
                new CssMetaData<>("-fx-pref-column-count",
                        SizeConverter.getInstance(), DEFAULT_PREF_COLUMN_COUNT) {

                    @Override
                    public boolean isSettable(CodeArea n) {
                        return !n.prefColumnCount.isBound();
                    }

                    @Override
                    public StyleableProperty<Number> getStyleableProperty(CodeArea n) {
                        return (StyleableProperty<Number>)n.prefColumnCountProperty();
                    }
                };

        private static final CssMetaData<CodeArea,Number> PREF_ROW_COUNT =
                new CssMetaData<>("-fx-pref-row-count",
                        SizeConverter.getInstance(), DEFAULT_PREF_ROW_COUNT) {

                    @Override
                    public boolean isSettable(CodeArea n) {
                        return !n.prefRowCount.isBound();
                    }

                    @Override
                    public StyleableProperty<Number> getStyleableProperty(CodeArea n) {
                        return (StyleableProperty<Number>)n.prefRowCountProperty();
                    }
                };

        private static final CssMetaData<CodeArea,Boolean> WRAP_TEXT =
                new CssMetaData<>("-fx-wrap-text",
                        StyleConverter.getBooleanConverter(), false) {

                    @Override
                    public boolean isSettable(CodeArea n) {
                        return !n.wrapText.isBound();
                    }

                    @Override
                    public StyleableProperty<Boolean> getStyleableProperty(CodeArea n) {
                        return (StyleableProperty<Boolean>)n.wrapTextProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;
        static {
            final List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(CodeInputControl.getClassCssMetaData());
            styleables.add(PREF_COLUMN_COUNT);
            styleables.add(PREF_ROW_COUNT);
            styleables.add(WRAP_TEXT);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * Gets the {@code CssMetaData} associated with this class, which may include the
     * {@code CssMetaData} of its superclasses.
     * @return the {@code CssMetaData}
     * @since JavaFX 8.0
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    /**
     * {@inheritDoc}
     * @since JavaFX 8.0
     */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }

}
