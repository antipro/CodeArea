package com.bitifyware.control.syntax;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.VPos;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextBoundsType;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a demo syntax highlighter that highlights every other word in blue and red.
 * @author antipro
 */
public class DemoSyntax extends SyntaxHighlighter{

    @Override
    public List<Text> decompose(String sentence,
                                IntegerProperty tabSizeProperty,
                                ChangeListener<TextBoundsType> callback,
                                ObjectProperty<Font> fontProperty,
                                ObjectProperty<Paint> selectionFillProperty) {
        List<Text> texts = new ArrayList<>();
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        if (!sentence.isEmpty()) {
            for (int j = 0; j < sentence.length(); j++) {
                char c = sentence.charAt(j);
                if (c == ' ') {
                    word.append(c);
                    words.add(word.toString());
                    word = new StringBuilder();
                } else {
                    word.append(c);
                }
            }
            words.add(word.toString());
        } else {
            words.add(sentence);
        }
        for (int j = 0; j < words.size(); j++) {
            Text textNode = new Text(words.get(j));
            textNode.setTextOrigin(VPos.TOP);
            textNode.setManaged(false);
            textNode.tabSizeProperty().bind(tabSizeProperty);
            textNode.boundsTypeProperty().addListener(callback);
            textNode.fontProperty().bind(fontProperty);
            if (j % 2 == 0) {
                textNode.setFill(Color.BLUE);
            } else {
                textNode.setFill(Color.RED);
            }
            textNode.selectionFillProperty().bind(selectionFillProperty);
            texts.add(textNode);
        }
        return texts;
    }

    @Override
    public void highlight(Text text) {
        // Do nothing
    }
}
