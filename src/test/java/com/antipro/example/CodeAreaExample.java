package com.antipro.example;

import com.antipro.control.CodeArea;
import javafx.application.Application;
import javafx.scene.control.TextArea;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import org.scenicview.ScenicView;

import java.util.Objects;

/**
 * @author antipro
 */
public class CodeAreaExample extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(javafx.stage.Stage primaryStage) {
        CodeArea codeArea = new CodeArea();
//        TextArea codeArea = new TextArea();
        codeArea.setFont(Font.font("Monospace", FontWeight.NORMAL, 24));
        String text = """
                The quick brown fox jumps over the lazy dog
                敏捷的棕色狐狸跳过懒惰的狗
                すばしっこい茶色の狐は怠け者の犬を飛び越えます
                Le rapide renard brun saute par-dessus le chien paresseux
                Проворная коричневая лиса перепрыгивает через ленивую собаку
                """;
        codeArea.setText(text);
        codeArea.setWrapText(true);
        codeArea.setPrefWidth(700);
        codeArea.setPrefHeight(250);
        javafx.scene.Scene scene = new javafx.scene.Scene(codeArea);
        primaryStage.setScene(scene);
        primaryStage.setX(100);
        primaryStage.setY(100);
        primaryStage.show();
        ScenicView.show(scene);
    }
}
