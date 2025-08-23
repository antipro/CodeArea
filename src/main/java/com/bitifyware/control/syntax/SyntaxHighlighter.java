package com.bitifyware.control.syntax;

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
//
//    /**
//     * Highlight the text This method will be called when layoutChildren
//     * @param text The text to highlight
//     */
//    public abstract void highlight(Text text);
}
