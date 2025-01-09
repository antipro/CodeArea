package com.antipro.example;

import com.antipro.control.CodeArea;
import javafx.application.Application;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.scenicview.ScenicView;

/**
 * @author antipro
 */
public class CodeAreaExample extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(javafx.stage.Stage primaryStage) {
        CodeArea textArea = new CodeArea();
        textArea.setFont(Font.font("Monospace", FontWeight.NORMAL, 24));
        String text = """
                The quick brown fox jumps over the lazy dog
                The quick brown fox jumps over the lazy dog
                The quick brown fox jumps over the lazy dog
                The quick brown fox jumps over the lazy dog
                The quick brown fox jumps over the lazy dog
                """;
        textArea.setText(text);
        textArea.setWrapText(true);
        textArea.setPrefWidth(300);
        textArea.setPrefHeight(250);

        javafx.scene.Scene scene = new javafx.scene.Scene(textArea);
        primaryStage.setScene(scene);
        primaryStage.show();
        ScenicView.show(scene);
    }
}
