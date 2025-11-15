package com.bitifyware.example;

import com.bitifyware.control.CodeArea;
import com.bitifyware.control.syntax.DemoSyntax;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

/**
 * Test application to demonstrate bracket pair highlighting for all bracket types:
 * - Round brackets: ()
 * - Square brackets: []
 * - Curly brackets: {}
 * - Angle brackets: <>
 * 
 * @author antipro
 */
public class BracketHighlightTest extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        CodeArea codeArea = new CodeArea();
        codeArea.setFont(Font.font("Monospace", FontWeight.NORMAL, 16));
        
        // Sample text with all types of brackets
        String text = """
                // Test round brackets
                function add(a, b) {
                    return (a + b);
                }
                
                // Test square brackets
                array[0] = array[1] + array[2];
                matrix[i][j] = value;
                
                // Test curly brackets
                if (condition) {
                    while (x < 10) {
                        doSomething();
                    }
                }
                
                // Test angle brackets
                List<String> names = new ArrayList<>();
                Map<Integer, List<String>> map = new HashMap<>();
                
                // Test nested brackets
                result = function((a + b) * (c + d));
                data = array[index[0]][index[1]];
                config = { key: { nested: { value: 123 } } };
                generic = Container<List<Map<String, Integer>>>();
                
                // Test mixed bracket types
                result = function(array[index], {key: value});
                template = render(<Component prop={data[0]} />);
                """;
        
        codeArea.setText(text);
        codeArea.setSyntaxHighlighter(new DemoSyntax());
        codeArea.setPrefWidth(800);
        codeArea.setPrefHeight(600);
        
        VBox root = new VBox(codeArea);
        VBox.setVgrow(codeArea, javafx.scene.layout.Priority.ALWAYS);
        
        Scene scene = new Scene(root);
        primaryStage.setTitle("Bracket Pair Highlighting Test");
        primaryStage.setScene(scene);
        primaryStage.setWidth(900);
        primaryStage.setHeight(700);
        primaryStage.show();
        
        System.out.println("Bracket Highlighting Test");
        System.out.println("========================");
        System.out.println("Move your cursor next to any bracket to see pair highlighting:");
        System.out.println("- Round brackets: ()");
        System.out.println("- Square brackets: []");
        System.out.println("- Curly brackets: {}");
        System.out.println("- Angle brackets: <>");
        System.out.println("\nThe matching bracket should be highlighted automatically!");
    }
}
