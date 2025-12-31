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
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextBoundsType;

import java.util.*;

/**
 * @author antipro
 */
public class CodeArea extends CodeInputControl {

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

    {
        textProperty().addListener((observable, oldValue, newValue) -> {
            errorPosList.clear();
            highlightedRange.set(null);
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
