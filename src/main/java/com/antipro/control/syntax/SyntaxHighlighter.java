package com.antipro.control.syntax;

import com.antipro.control.CodeArea;
import com.antipro.control.skin.CodeAreaSkin;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextBoundsType;

import java.util.List;

public abstract class SyntaxHighlighter {

    private final CodeArea codeArea;

    public SyntaxHighlighter(CodeArea codeArea) {
        this.codeArea = codeArea;
    }

    public List<Text> parseDelegate(String rawString,
                                    IntegerProperty tabSizeProperty,
                                    ChangeListener<TextBoundsType> callback,
                                    ObjectProperty<Font> fontProperty,
                                    ObjectProperty<Paint> selectionFillProperty) {
        List<Text> texts = parse(
                rawString,
                tabSizeProperty,
                callback,
                fontProperty,
                selectionFillProperty
        );
        return texts;
    }

    abstract List<Text> parse(String rawString,
                              IntegerProperty tabSizeProperty,
                              ChangeListener<TextBoundsType> callback,
                              ObjectProperty<Font> fontProperty,
                              ObjectProperty<Paint> selectionFillProperty);
}
