package com.bitifyware.example;

import com.bitifyware.control.CodeArea;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;

/**
 * Test application to demonstrate and verify selection/highlight rendering fixes.
 * This test creates a CodeArea with some sample text and allows you to test
 * selection rendering alignment.
 * 
 * To run: mvn compile exec:java -Dexec.mainClass="com.bitifyware.example.SelectionRenderingTest"
 */
public class SelectionRenderingTest extends Application {

    @Override
    public void start(Stage primaryStage) {
        CodeArea codeArea = new CodeArea();
        
        // Set a monospace font for consistent character widths
        codeArea.setFont(Font.font("Monospace", 14));
        
        // Sample text with multiple lines
        String sampleText = "public class HelloWorld {\n" +
                           "    public static void main(String[] args) {\n" +
                           "        System.out.println(\"Hello, World!\");\n" +
                           "        int x = 42;\n" +
                           "        String message = \"Test\";\n" +
                           "    }\n" +
                           "}";
        
        codeArea.setText(sampleText);
        codeArea.setPrefRowCount(15);
        codeArea.setPrefColumnCount(60);
        
        Label instructions = new Label(
            "Instructions:\n" +
            "1. Click and drag to select text\n" +
            "2. Verify that the selection highlight aligns correctly with the text glyphs\n" +
            "3. The selection should not have vertical gaps or misalignment\n" +
            "4. Test on different lines and with different selection lengths"
        );
        instructions.setWrapText(true);
        instructions.setMaxWidth(500);
        
        VBox root = new VBox(10);
        root.setPadding(new Insets(10));
        root.getChildren().addAll(instructions, codeArea);
        
        Scene scene = new Scene(root, 800, 600);
        
        // Add a simple stylesheet for better visibility
        scene.getRoot().setStyle("-fx-font-family: 'monospace';");
        
        primaryStage.setTitle("Selection Rendering Test - CodeArea");
        primaryStage.setScene(scene);
        primaryStage.show();
        
        // Log text node properties for debugging
        System.out.println("=== Text Node Properties Diagnostic ===");
        System.out.println("Font: " + codeArea.getFont());
        System.out.println("To test: Select some text and observe if the highlight aligns with glyphs");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
