package com.bitifyware.example;

import com.bitifyware.control.CodeArea;
import com.bitifyware.control.syntax.DemoSyntax;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;

/**
 * Test application to demonstrate and verify the CJK selection line height fix.
 * 
 * This test specifically addresses the issue where selection rectangles misalign
 * when CJK (Chinese, Japanese, Korean) characters are present in the text.
 * 
 * BEFORE FIX: Selection would misalign vertically when CJK characters were added
 * because per-Text bounds varied with different glyphs and font fallback.
 * 
 * AFTER FIX: Selection uses stable line height computed from "Hg中" measurement,
 * ensuring consistent alignment regardless of character content.
 * 
 * To run: mvn compile exec:java -Dexec.mainClass="com.bitifyware.example.TestSelectionLineHeightFix"
 */
public class TestSelectionLineHeightFix extends Application {

    @Override
    public void start(Stage primaryStage) {
        CodeArea codeArea = new CodeArea();
        
        // Use DemoSyntax highlighter to test with multiple Text nodes per line
        codeArea.setSyntaxHighlighter(new DemoSyntax());
        
        // Set a monospace font (system will fall back to appropriate CJK font as needed)
        codeArea.setFont(Font.font("Monospace", 14));
        
        // Sample text with mixed Latin and CJK characters
        // This is the key test case that would fail before the fix
        String sampleText = 
            "// Test selection with Latin characters\n" +
            "public class HelloWorld {\n" +
            "    // 测试中文字符 (Test Chinese characters)\n" +
            "    // テストの日本語 (Test Japanese characters)\n" +
            "    // 한글 테스트 (Test Korean characters)\n" +
            "    public static void main(String[] args) {\n" +
            "        System.out.println(\"Hello, World!\");\n" +
            "        System.out.println(\"你好世界\"); // Chinese\n" +
            "        System.out.println(\"こんにちは世界\"); // Japanese\n" +
            "        System.out.println(\"안녕하세요 세계\"); // Korean\n" +
            "        int count = 42;\n" +
            "        String message = \"Mixed 混合 テキスト 텍스트\";\n" +
            "    }\n" +
            "}\n";
        
        codeArea.setText(sampleText);
        codeArea.setPrefRowCount(20);
        codeArea.setPrefColumnCount(70);
        
        Label instructions = new Label(
            "CJK Selection Line Height Fix Test\n\n" +
            "Instructions:\n" +
            "1. Select text across multiple lines (click and drag)\n" +
            "2. Pay attention to lines with CJK characters (lines 3-5, 8-10, 12)\n" +
            "3. Verify that selection highlight aligns correctly on ALL lines\n" +
            "4. The selection should have NO vertical gaps or misalignment\n" +
            "5. Use the button below to add/remove CJK characters dynamically\n\n" +
            "BEFORE FIX: Selection would misalign on CJK lines\n" +
            "AFTER FIX: Selection aligns consistently on all lines\n\n" +
            "Technical: Uses stable line height from \"Hg中\" measurement"
        );
        instructions.setWrapText(true);
        instructions.setMaxWidth(700);
        instructions.setStyle("-fx-font-size: 11px; -fx-padding: 10px;");
        
        // Button to dynamically test adding/removing CJK characters
        Button toggleCJKButton = new Button("Toggle CJK Characters on Line 3");
        final boolean[] hasCJK = {true};
        toggleCJKButton.setOnAction(e -> {
            String currentText = codeArea.getText();
            if (hasCJK[0]) {
                // Remove CJK characters from line 3
                currentText = currentText.replace("// 测试中文字符 (Test Chinese characters)", 
                                                  "// Test without Chinese characters");
                toggleCJKButton.setText("Add CJK Characters on Line 3");
            } else {
                // Add CJK characters back
                currentText = currentText.replace("// Test without Chinese characters",
                                                  "// 测试中文字符 (Test Chinese characters)");
                toggleCJKButton.setText("Remove CJK Characters on Line 3");
            }
            hasCJK[0] = !hasCJK[0];
            codeArea.setText(currentText);
            System.out.println("[TEST] Toggled CJK characters. Selection should remain aligned.");
        });
        
        VBox root = new VBox(10);
        root.setPadding(new Insets(10));
        root.getChildren().addAll(instructions, toggleCJKButton, codeArea);
        
        Scene scene = new Scene(root, 900, 700);
        
        primaryStage.setTitle("CJK Selection Line Height Fix Test - CodeArea");
        primaryStage.setScene(scene);
        primaryStage.show();
        
        // Log diagnostic information
        System.out.println("=== CJK Selection Line Height Fix Test ===");
        System.out.println("Font: " + codeArea.getFont());
        System.out.println("Using DemoSyntax highlighter (multiple Text nodes per line)");
        System.out.println("\nTest steps:");
        System.out.println("1. Select text across lines 3-5 (with CJK characters)");
        System.out.println("2. Verify selection aligns vertically on all lines");
        System.out.println("3. Click button to toggle CJK - selection should remain aligned");
        System.out.println("4. Compare to lines without CJK (lines 2, 6-7) - should match");
        System.out.println("\nExpected result: Consistent selection alignment on all lines");
        System.out.println("==========================================\n");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
