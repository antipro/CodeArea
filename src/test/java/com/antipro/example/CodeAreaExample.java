package com.antipro.example;

import com.antipro.control.CodeArea;
import com.antipro.control.syntax.DemoSyntax;
import com.antipro.control.syntax.SQLSyntax;
import javafx.application.Application;
import javafx.scene.control.Button;
import javafx.scene.control.Spinner;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
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


        ToolBar toolBar = new ToolBar();
        Spinner<Integer> fontSizeSpinner = new Spinner<>(8, 72, 24);
        fontSizeSpinner.valueProperty().addListener((observable, oldValue, newValue) -> {
            codeArea.setFont(Font.font("Monospace", FontWeight.NORMAL, newValue));
        });
        toolBar.getItems().add(fontSizeSpinner);
        Spinner<Integer> tabSizeSpinner = new Spinner<>(2, 8, 4);
        tabSizeSpinner.valueProperty().addListener((observable, oldValue, newValue) -> {
            codeArea.setTabSize(newValue);
        });
        toolBar.getItems().add(tabSizeSpinner);
        Button noSyntaxButton = new Button("No Syntax");
        noSyntaxButton.setOnAction(event -> {
            codeArea.setSyntaxHighlighter(null);
        });
        toolBar.getItems().add(noSyntaxButton);
        Button demoSyntaxButton = new Button("Demo Syntax");
        demoSyntaxButton.setOnAction(event -> {
            codeArea.setSyntaxHighlighter(new DemoSyntax());
        });
        toolBar.getItems().add(demoSyntaxButton);
        Button sqlSyntaxButton = new Button("SQL Syntax");
        sqlSyntaxButton.setOnAction(event -> {
            codeArea.setSyntaxHighlighter(new SQLSyntax());
        });
        toolBar.getItems().add(sqlSyntaxButton);
        Button wrapTextButton = new Button("Wrap Text");
        wrapTextButton.setOnAction(event -> {
            codeArea.setWrapText(!codeArea.isWrapText());
        });
        toolBar.getItems().add(wrapTextButton);

        Button printButton = new Button("Print");
        printButton.setOnAction(event -> {
            System.out.println(codeArea.getText());
        });
        toolBar.getItems().add(printButton);

        VBox codeAreaHBox = new VBox(toolBar, codeArea);
        VBox.setVgrow(codeArea, javafx.scene.layout.Priority.ALWAYS);
        javafx.scene.Scene scene = new javafx.scene.Scene(codeAreaHBox);
        primaryStage.setScene(scene);
        primaryStage.setX(100);
        primaryStage.setY(100);
        primaryStage.show();
        ScenicView.show(scene);
    }
}
