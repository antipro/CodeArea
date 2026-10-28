package com.bitifyware.control.syntax;

import com.bitifyware.control.CodeArea;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextBoundsType;

import java.util.List;

public abstract class SyntaxHighlighter {

    /**
     * Split the raw string into a list of Text nodes that represent the decomposed string
     * this method will be called when a line of text is changed
     * @param sentence The raw string
     * @param tabSizeProperty The tab size property
     * @param callback The callback to listen for changes in bounds type
     * @param fontProperty The font property
     * @param selectionFillProperty The selection fill property
     * @return A list of Text nodes that represent the decomposed string
     */
    public abstract List<Text> decompose(String sentence,
                                         IntegerProperty tabSizeProperty,
                                         ChangeListener<TextBoundsType> callback,
                                         ObjectProperty<Font> fontProperty,
                                         ObjectProperty<Paint> selectionFillProperty);

    /**
     * Same as {@link #decompose(String, IntegerProperty, ChangeListener, ObjectProperty, ObjectProperty)},
     * but also receives the paragraph position inside the editor. Only visible
     * paragraphs are decomposed, and they can be visited in any order (for
     * example when the user drags the scrollbar upwards), so a highlighter that
     * carries state across lines (such as block comments) must derive that state
     * from the document instead of remembering the previously decomposed line.
     *
     * <p>The default implementation ignores the extra context and delegates to
     * the original method, keeping existing highlighters working unchanged.</p>
     *
     * @param sentence The raw string of the paragraph
     * @param paragraphIndex The index of the paragraph inside the editor
     * @param codeArea The editor that owns the paragraph
     * @param tabSizeProperty The tab size property
     * @param callback The callback to listen for changes in bounds type
     * @param fontProperty The font property
     * @param selectionFillProperty The selection fill property
     * @return A list of Text nodes that represent the decomposed string
     */
    public List<Text> decompose(String sentence,
                                int paragraphIndex,
                                CodeArea codeArea,
                                IntegerProperty tabSizeProperty,
                                ChangeListener<TextBoundsType> callback,
                                ObjectProperty<Font> fontProperty,
                                ObjectProperty<Paint> selectionFillProperty) {
        return decompose(sentence, tabSizeProperty, callback, fontProperty, selectionFillProperty);
    }
//
//    /**
//     * Highlight the text This method will be called when layoutChildren
//     * @param text The text to highlight
//     */
//    public abstract void highlight(Text text);
}
