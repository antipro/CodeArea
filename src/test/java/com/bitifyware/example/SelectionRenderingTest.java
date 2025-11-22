package com.bitifyware.example;

import com.bitifyware.control.CodeArea;
import com.bitifyware.control.syntax.DemoSyntax;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.scenicview.ScenicView;

/**
 * Test application to demonstrate and verify selection/highlight rendering fixes.
 * This test creates a CodeArea with some sample text and allows you to test
 * selection rendering alignment.
 * 
 * IMPORTANT: Uses DemoSyntax highlighter which creates multiple Text nodes per line
 * (one per word), properly testing the multi-text-node selection rendering issue.
 * 
 * To run: mvn compile exec:java -Dexec.mainClass="com.bitifyware.example.SelectionRenderingTest"
 */
public class SelectionRenderingTest extends Application {

    @Override
    public void start(Stage primaryStage) {
        CodeArea codeArea = new CodeArea();
        
        // Use DemoSyntax highlighter to create multiple Text nodes per line
        // This reproduces the real issue where each line has multiple Text nodes
        codeArea.setSyntaxHighlighter(new DemoSyntax());
        
        // Set a monospace font for consistent character widths
        codeArea.setFont(Font.font("Monospaced", 20));
        
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
            "4. Test MULTI-LINE selections across lines 2-3 (this is where the issue occurs)\n" +
            "5. Each line has multiple Text nodes (one per word) - tests real syntax highlighter behavior"
        );
        instructions.setWrapText(true);
        instructions.setMaxWidth(500);
        
        VBox root = new VBox(10);
        root.setPadding(new Insets(10));
        root.getChildren().addAll(instructions, codeArea);
        
        Scene scene = new Scene(root, 800, 600);
        
        // Add a simple stylesheet for better visibility
        scene.getRoot().setStyle("-fx-font-family: 'monospace';");
        
        primaryStage.setTitle("Selection Rendering Test - CodeArea (Multi-Text-Node)");
        primaryStage.setScene(scene);
        primaryStage.show();
        
        // Log text node properties for debugging
        System.out.println("=== Text Node Properties Diagnostic ===");
        System.out.println("Font: " + codeArea.getFont());
        System.out.println("Using DemoSyntax highlighter - creates multiple Text nodes per line");
        System.out.println("To test: Select text across multiple lines and observe if the highlight aligns with glyphs");
        System.out.println("Note: Each line has multiple Text nodes (one per word), which tests the real issue");
        ScenicView.show(scene);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
