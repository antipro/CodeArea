package com.antipro.control.syntax;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextBoundsType;

import java.util.List;

public interface SyntaxHighlighter {

    List<Text> parse(String rawString,
                     IntegerProperty tabSizeProperty,
                     ChangeListener<TextBoundsType> callback,
                     ObjectProperty<Font> fontProperty,
                     ObjectProperty<Paint> selectionFillProperty);
}
