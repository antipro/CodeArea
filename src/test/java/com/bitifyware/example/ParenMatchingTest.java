package com.bitifyware.example;

import com.bitifyware.control.CodeArea;
import com.bitifyware.control.syntax.DemoSyntax;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

/**
 * Test application to verify parenthesis matching works correctly
 * even when parentheses are in different text nodes.
 * 
 * @author antipro
 */
public class ParenMatchingTest extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        CodeArea codeArea = new CodeArea();
        codeArea.setFont(Font.font("Monospace", FontWeight.NORMAL, 16));
        codeArea.setSyntaxHighlighter(new DemoSyntax());
        
        // Test code with parentheses that will be split across text nodes
        // due to syntax highlighting
        String testCode = """
                function test(arg1, arg2) {
                    if (condition) {
                        doSomething(x, y);
                    }
                    return (a + b) * c;
                }
                
                class Example {
                    public void method() {
                        System.out.println("Hello");
                    }
                }
                """;
        
        codeArea.setText(testCode);
        codeArea.setPrefWidth(600);
        codeArea.setPrefHeight(400);
        
        Label instructionLabel = new Label(
            "Instructions:\n" +
            "1. Move caret next to parentheses ( or )\n" +
            "2. The matching parenthesis should be highlighted\n" +
            "3. Test with parentheses in different syntax-highlighted regions"
        );
        instructionLabel.setStyle("-fx-padding: 10; -fx-background-color: #f0f0f0;");
        
        Button testButton = new Button("Move to position test(");
        testButton.setOnAction(e -> {
            // Move caret to position right after the opening paren in "test("
            int pos = testCode.indexOf("test(") + 5; // Right after "test("
            codeArea.positionCaret(pos);
        });
        
        VBox root = new VBox(10, instructionLabel, testButton, codeArea);
        VBox.setVgrow(codeArea, javafx.scene.layout.Priority.ALWAYS);
        root.setStyle("-fx-padding: 10;");
        
        Scene scene = new Scene(root, 800, 600);
        primaryStage.setTitle("Parenthesis Matching Test");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}
