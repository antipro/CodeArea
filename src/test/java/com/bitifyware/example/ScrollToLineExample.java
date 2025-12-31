package com.bitifyware.example;

import com.bitifyware.control.CodeArea;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

/**
 * Example demonstrating the scrollToLine method.
 * @author antipro
 */
public class ScrollToLineExample extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        CodeArea codeArea = new CodeArea();
        codeArea.setFont(Font.font("Monospace", FontWeight.NORMAL, 14));
        
        // Create a text with many lines
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 100; i++) {
            sb.append("Line ").append(i).append(": This is some sample text on line ").append(i).append("\n");
        }
        codeArea.setText(sb.toString());
        
        codeArea.setPrefWidth(700);
        codeArea.setPrefHeight(400);

        // Create a toolbar with controls
        ToolBar toolBar = new ToolBar();
        
        Label label = new Label("Go to line:");
        toolBar.getItems().add(label);
        
        Spinner<Integer> lineSpinner = new Spinner<>(0, 99, 0);
        lineSpinner.setPrefWidth(100);
        toolBar.getItems().add(lineSpinner);
        
        Button scrollButton = new Button("Scroll to Line");
        scrollButton.setOnAction(event -> {
            int line = lineSpinner.getValue();
            codeArea.scrollToLine(line);
        });
        toolBar.getItems().add(scrollButton);
        
        Button scrollToLine50Button = new Button("Go to Line 50");
        scrollToLine50Button.setOnAction(event -> {
            lineSpinner.getValueFactory().setValue(50);
            codeArea.scrollToLine(50);
        });
        toolBar.getItems().add(scrollToLine50Button);
        
        Button scrollToEndButton = new Button("Go to End");
        scrollToEndButton.setOnAction(event -> {
            lineSpinner.getValueFactory().setValue(99);
            codeArea.scrollToLine(99);
        });
        toolBar.getItems().add(scrollToEndButton);
        
        Button scrollToStartButton = new Button("Go to Start");
        scrollToStartButton.setOnAction(event -> {
            lineSpinner.getValueFactory().setValue(0);
            codeArea.scrollToLine(0);
        });
        toolBar.getItems().add(scrollToStartButton);

        VBox root = new VBox(10);
        root.setPadding(new Insets(10));
        root.getChildren().addAll(toolBar, codeArea);

        Scene scene = new Scene(root, 800, 600);
        primaryStage.setTitle("CodeArea ScrollToLine Example");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}
