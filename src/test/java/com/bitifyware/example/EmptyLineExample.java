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
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

/**
 * Example demonstrating the empty line feature for file diff display.
 * Empty lines are visual-only lines with a colored background that do not
 * contain any text content. They can be used to represent added or removed
 * lines in a side-by-side diff view.
 *
 * @author antipro
 */
public class EmptyLineExample extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        CodeArea codeArea = new CodeArea();
        codeArea.setFont(Font.font("Monospace", FontWeight.NORMAL, 14));

        // Sample code text
        String text = "public class HelloWorld {\n" +
                "    public static void main(String[] args) {\n" +
                "        System.out.println(\"Hello, World!\");\n" +
                "        int x = 42;\n" +
                "        String message = \"Test\";\n" +
                "        System.out.println(message);\n" +
                "        System.out.println(x);\n" +
                "    }\n" +
                "}";
        codeArea.setText(text);
        codeArea.setPrefWidth(700);
        codeArea.setPrefHeight(400);

        int paragraphCount = codeArea.getParagraphs().size();

        // Create toolbar with controls
        ToolBar toolBar = new ToolBar();

        Label label = new Label("Paragraph index:");
        toolBar.getItems().add(label);

        Spinner<Integer> indexSpinner = new Spinner<>(0, paragraphCount, 0);
        indexSpinner.setPrefWidth(80);
        toolBar.getItems().add(indexSpinner);

        Button addGreenButton = new Button("Add Green Empty Line");
        addGreenButton.setOnAction(event -> {
            int index = indexSpinner.getValue();
            codeArea.addEmptyLine(index, Color.LIGHTGREEN);
        });
        toolBar.getItems().add(addGreenButton);

        Button addRedButton = new Button("Add Red Empty Line");
        addRedButton.setOnAction(event -> {
            int index = indexSpinner.getValue();
            codeArea.addEmptyLine(index, Color.LIGHTCORAL);
        });
        toolBar.getItems().add(addRedButton);

        Button clearButton = new Button("Clear Empty Lines");
        clearButton.setOnAction(event -> {
            codeArea.clearEmptyLines();
        });
        toolBar.getItems().add(clearButton);

        Button diffDemoButton = new Button("Diff Demo");
        diffDemoButton.setOnAction(event -> {
            codeArea.clearEmptyLines();
            // Simulate a diff view: green lines represent additions in the other file
            codeArea.addEmptyLine(2, Color.LIGHTGREEN);
            codeArea.addEmptyLine(5, Color.LIGHTGREEN);
            codeArea.addEmptyLine(5, Color.LIGHTGREEN);
        });
        toolBar.getItems().add(diffDemoButton);

        Label instructions = new Label(
                "Use the controls to add colored empty lines at specific paragraph positions.\n" +
                "Green lines typically represent additions, red lines represent deletions in a diff view.\n" +
                "Click 'Diff Demo' to see a simulated diff display."
        );
        instructions.setWrapText(true);

        VBox root = new VBox(10);
        root.setPadding(new Insets(10));
        root.getChildren().addAll(toolBar, instructions, codeArea);
        VBox.setVgrow(codeArea, javafx.scene.layout.Priority.ALWAYS);

        Scene scene = new Scene(root, 800, 600);
        primaryStage.setTitle("CodeArea Empty Line Example - Diff Display");
        primaryStage.setScene(scene);
        primaryStage.show();

        System.out.println("Empty Line Example");
        System.out.println("==================");
        System.out.println("This example demonstrates the empty line feature for diff display.");
        System.out.println("Empty lines are visual-only colored lines without text content.");
        System.out.println("Use the toolbar controls to add or clear empty lines.");
    }
}
