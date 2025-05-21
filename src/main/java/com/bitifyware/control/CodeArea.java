package com.bitifyware.control;

import com.bitifyware.control.skin.CodeAreaSkin;
import com.bitifyware.control.syntax.DemoSyntax;
import com.bitifyware.control.syntax.SyntaxHighlighter;
import com.sun.javafx.collections.ListListenerHelper;
import com.sun.javafx.collections.NonIterableChange;
import javafx.beans.InvalidationListener;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.*;
import javafx.css.converter.SizeConverter;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Skin;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextBoundsType;

import java.util.*;

/**
 * @author antipro
 */
public class CodeArea extends CodeInputControl {
    // Text area content model
    private static final class CodeAreaContent extends ContentBase {
        private final List<StringBuilder> paragraphs = new ArrayList<>();
        private final ParagraphList paragraphList = new ParagraphList();

        private int contentLength = 0;

        private CodeAreaContent() {
            paragraphs.add(new StringBuilder(DEFAULT_PARAGRAPH_CAPACITY));
            paragraphList.content = this;
        }

        @Override public String get(int start, int end) {
            int length = end - start;
            StringBuilder textBuilder = new StringBuilder(length);

            int paragraphCount = paragraphs.size();

            int paragraphIndex = 0;
            int offset = start;

            while (paragraphIndex < paragraphCount) {
                StringBuilder paragraph = paragraphs.get(paragraphIndex);
                int count = paragraph.length() + 1;

                if (offset < count) {
                    break;
                }

                offset -= count;
                paragraphIndex++;
            }

            // Read characters until end is reached, appending to text builder
            // and moving to next paragraph as needed
            StringBuilder paragraph = paragraphs.get(paragraphIndex);

            int i = 0;
            while (i < length) {
                if (offset == paragraph.length()
                        && i < contentLength) {
                    textBuilder.append('\n');
                    paragraph = paragraphs.get(++paragraphIndex);
                    offset = 0;
                } else {
                    textBuilder.append(paragraph.charAt(offset++));
                }

                i++;
            }

            return textBuilder.toString();
        }

        @Override
        @SuppressWarnings("unchecked")
        public void insert(int index, String text, boolean notifyListeners) {
            if (index < 0
                    || index > contentLength) {
                throw new IndexOutOfBoundsException();
            }

            if (text == null) {
                throw new IllegalArgumentException();
            }
            text = CodeInputControl.filterInput(text, false, false);
            int length = text.length();
            if (length > 0) {
                // Split the text into lines
                ArrayList<StringBuilder> lines = new ArrayList<>();

                StringBuilder line = new StringBuilder(DEFAULT_PARAGRAPH_CAPACITY);
                for (int i = 0; i < length; i++) {
                    char c = text.charAt(i);

                    if (c == '\n') {
//                        line.append(c);
                        lines.add(line);
                        line = new StringBuilder(DEFAULT_PARAGRAPH_CAPACITY);
                    } else {
                        line.append(c);
                    }
                }

                lines.add(line);

                // Merge the text into the existing content
                // Merge the text into the existing content
                int paragraphIndex = paragraphs.size();
                int offset = contentLength + 1;

                StringBuilder paragraph = null;

                do {
                    paragraph = paragraphs.get(--paragraphIndex);
                    offset -= paragraph.length() + 1;
                } while (index < offset);

                int start = index - offset;

                int n = lines.size();
                if (n == 1) {
                    // The text contains only a single line; insert it into the
                    // intersecting paragraph
                    paragraph.insert(start, line);
                    fireParagraphListChangeEvent(paragraphIndex, paragraphIndex + 1,
                            Collections.singletonList((CharSequence)paragraph));
                } else {
                    // The text contains multiple line; split the intersecting
                    // paragraph
                    int end = paragraph.length();
                    CharSequence trailingText = paragraph.subSequence(start, end);
                    paragraph.delete(start, end);

                    // Append the first line to the intersecting paragraph and
                    // append the trailing text to the last line
                    StringBuilder first = lines.getFirst();
                    paragraph.insert(start, first);
                    line.append(trailingText);
                    fireParagraphListChangeEvent(paragraphIndex, paragraphIndex + 1,
                            Collections.singletonList((CharSequence)paragraph));

                    // Insert the remaining lines into the paragraph list
                    paragraphs.addAll(paragraphIndex + 1, lines.subList(1, n));
                    fireParagraphListChangeEvent(paragraphIndex + 1, paragraphIndex + n,
                            Collections.EMPTY_LIST);
                }

                // Update content length
                contentLength += length;
                if (notifyListeners) {
                    fireValueChangedEvent();
                }
            }
        }

        @Override public void delete(int start, int end, boolean notifyListeners) {
            if (start > end) {
                throw new IllegalArgumentException();
            }

            if (start < 0
                    || end > contentLength) {
                throw new IndexOutOfBoundsException();
            }

            int length = end - start;

            if (length > 0) {
                // Identify the trailing paragraph index
                int paragraphIndex = paragraphs.size();
                int offset = contentLength + 1;

                StringBuilder paragraph = null;

                do {
                    paragraph = paragraphs.get(--paragraphIndex);
                    offset -= paragraph.length() + 1;
                } while (end < offset);

                int trailingParagraphIndex = paragraphIndex;
                int trailingOffset = offset;
                StringBuilder trailingParagraph = paragraph;

                // Identify the leading paragraph index
                paragraphIndex++;
                offset += paragraph.length() + 1;

                do {
                    paragraph = paragraphs.get(--paragraphIndex);
                    offset -= paragraph.length() + 1;
                } while (start < offset);

                int leadingParagraphIndex = paragraphIndex;
                int leadingOffset = offset;
                StringBuilder leadingParagraph = paragraph;

                // Remove the text
                if (leadingParagraphIndex == trailingParagraphIndex) {
                    // The removal affects only a single paragraph
                    leadingParagraph.delete(start - leadingOffset,
                            end - leadingOffset);

                    fireParagraphListChangeEvent(leadingParagraphIndex, leadingParagraphIndex + 1,
                            Collections.singletonList((CharSequence)leadingParagraph));
                } else {
                    // The removal spans paragraphs; remove any intervening paragraphs and
                    // merge the leading and trailing segments
                    CharSequence leadingSegment = leadingParagraph.subSequence(0,
                            start - leadingOffset);
                    int trailingSegmentLength = (start + length) - trailingOffset;

                    trailingParagraph.delete(0, trailingSegmentLength);
                    fireParagraphListChangeEvent(trailingParagraphIndex, trailingParagraphIndex + 1,
                            Collections.singletonList((CharSequence)trailingParagraph));

                    if (trailingParagraphIndex - leadingParagraphIndex > 0) {
                        List<CharSequence> removed = new ArrayList<>(paragraphs.subList(leadingParagraphIndex,
                                trailingParagraphIndex));
                        paragraphs.subList(leadingParagraphIndex,
                                trailingParagraphIndex).clear();
                        fireParagraphListChangeEvent(leadingParagraphIndex, leadingParagraphIndex,
                                removed);
                    }

                    // Trailing paragraph is now at the former leading paragraph's index
                    trailingParagraph.insert(0, leadingSegment);
                    fireParagraphListChangeEvent(leadingParagraphIndex, leadingParagraphIndex + 1,
                            Collections.singletonList((CharSequence)leadingParagraph));
                }

                // Update content length
                contentLength -= length;
                if (notifyListeners) {
                    fireValueChangedEvent();
                }
            }
        }

        @Override public int length() {
            return contentLength;
        }

        @Override public String get() {
            return get(0, length());
        }

        @Override public String getValue() {
            return get();
        }

        private void fireParagraphListChangeEvent(int from, int to, List<CharSequence> removed) {
            ParagraphListChange change = new ParagraphListChange(paragraphList, from, to, removed);
            ListListenerHelper.fireValueChangedEvent(paragraphList.listenerHelper, change);
        }
    }

    // Observable list of paragraphs
    private static final class ParagraphList extends AbstractList<CharSequence>
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
    }

    private static final class ParagraphListChange extends NonIterableChange<CharSequence> {

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
     * The default value for {@link #prefColumnCountProperty() prefColumnCount}.
     */
    public static final int DEFAULT_PREF_COLUMN_COUNT = 40;

    /**
     * The default value for {@link #prefRowCountProperty() prefRowCount}.
     */
    public static final int DEFAULT_PREF_ROW_COUNT = 10;

    private static final int DEFAULT_PARAGRAPH_CAPACITY = 32;

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
        super(new CodeAreaContent());
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
        return ((CodeAreaContent)getContent()).paragraphList;
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

    private IntegerProperty tabSize = new SimpleIntegerProperty(this, "tabSize", 4);

    public final IntegerProperty tabSizeProperty() {
        return tabSize;
    }

    public final void setTabSize(int tabSize) {
        if (tabSize < 1) {
            throw new IllegalArgumentException("tabSize cannot be less than 1.");
        }
        this.tabSize.set(tabSize);
    }

    private final ObservableList<Integer> errorPosList = FXCollections.observableArrayList();

    {
        textProperty().addListener((observable, oldValue, newValue) -> {
            errorPosList.clear();
        });
    }

    public final void addErrorPos(Integer errorPos) {
        errorPosList.add(errorPos);
    }

    public ObservableList<Integer> getErrorPosList() { return errorPosList; }

    public void clearErrorPos() {
        errorPosList.clear();
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
