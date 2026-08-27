package com.bitifyware.example;

import com.bitifyware.control.CodeArea;
import com.bitifyware.control.InDiskContent;
import com.bitifyware.control.syntax.DemoSyntax;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
//import org.scenicview.ScenicView;
import lombok.extern.slf4j.Slf4j;

/**
 * Example JavaFX application demonstrating the use of DiskContent with CodeArea.
 * 
 * <p>This example shows how to:
 * <ol>
 *   <li>Create a CodeArea with disk-backed content</li>
 *   <li>Load large text into the disk-backed content</li>
 *   <li>Display and interact with the disk-backed content</li>
 * </ol>
 * 
 * <h3>Performance Note</h3>
 * <p>For very large files, consider loading content in a background thread to avoid
 * blocking the JavaFX Application Thread. This example loads content during initialization
 * for simplicity.
 * 
 * <h3>Running the Example</h3>
 * <pre>
 * java com.bitifyware.example.DiskContentExample
 * </pre>
 * 
 * @see InDiskContent
 * @see CodeArea
 */
@Slf4j
public class DiskContentExample extends Application {

    private CodeArea codeArea;
    private Label statusLabel;
    
    @Override
    public void start(Stage primaryStage) {
        // Create the main UI components
        BorderPane root = new BorderPane();
        
        // Passing true selects InDiskContent for this CodeArea.
        codeArea = new CodeArea("Initial disk-backed content.\nThis will be replaced.", true);
        codeArea.setFont(Font.font("Monospace", FontWeight.NORMAL, 18));
        codeArea.setSyntaxHighlighter(new DemoSyntax());
        // Status label
        statusLabel = new Label("Status: Using disk-backed content");
        
        // Buttons for testing
        HBox buttonBar = new HBox(10);
        
        Button loadDiskContentBtn = new Button("Load Disk Content (Large)");
        loadDiskContentBtn.setOnAction(e -> loadLargeDiskContent());
        
        Button loadSmallDiskContentBtn = new Button("Load Disk Content (Small)");
        loadSmallDiskContentBtn.setOnAction(e -> loadSmallDiskContent());
        
        Button showStatsBtn = new Button("Show Stats");

        buttonBar.getChildren().addAll(loadDiskContentBtn, loadSmallDiskContentBtn, showStatsBtn);
        
        // Layout
        root.setCenter(codeArea);
        root.setTop(buttonBar);
        root.setBottom(statusLabel);

        // Create and show the scene
        Scene scene = new Scene(root, 800, 600);
        // Inspecting thousands of paragraph nodes is expensive, so ScenicView is opt-in.
        if (Boolean.getBoolean("codearea.scenicView")) {
            try {
                Class<?> scenic = Class.forName("org.scenicview.ScenicView");
                java.lang.reflect.Method show = scenic.getMethod("show", javafx.scene.Scene.class);
                show.invoke(null, scene);
            } catch (ClassNotFoundException ignored) {
                log.info("ScenicView is not available");
            } catch (Exception ex) {
                log.error("Failed to show ScenicView", ex);
            }
        }
        primaryStage.setTitle("DiskContent Example - CodeArea with Disk-Backed Storage");
        primaryStage.setScene(scene);
        primaryStage.show();

    }
    
    /**
     * Loads a large text file into disk content and swaps it into the CodeArea.
     * This demonstrates handling files too large for memory.
     */
    void loadLargeDiskContent() {
        try {
            statusLabel.setText("Status: Generating large content...");
            
            // Generate a large text (simulating a large file)
            StringBuilder largeText = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                largeText.append("Line ").append(i + 1)
                        .append(": This is a sample line of text that will be stored on disk. ")
                        .append("Lorem ipsum dolor sit amet, consectetur adipiscing elit.\n");
            }
            
            statusLabel.setText("Status: Swapping to disk content...");
            
            // Update the CodeArea to reflect the new content
            codeArea.setText(largeText.toString());
            
            statusLabel.setText("Status: Using disk-backed content (10,000 lines, " +
                    largeText.length() + " characters)");
            
            System.out.println("Successfully loaded large disk content");
            
        } catch (Exception e) {
            statusLabel.setText("Status: Error - " + e.getMessage());
            log.error("Error loading large disk content", e);
        }
    }
    
    /**
     * Loads a small text into disk content for testing.
     */
    private void loadSmallDiskContent() {
        try {
            statusLabel.setText("Status: Creating small disk content...");
            
            String smallText = "Line 1: Hello from disk-backed storage!\n" +
                             "Line 2: This content is stored in a temporary file.\n" +
                             "Line 3: Try editing this text.\n" +
                             "Line 4: The changes are persisted to disk.\n" +
                             "Line 5: End of example content.";
            
            // Swap the content
            codeArea.setText(smallText);
            
            statusLabel.setText("Status: Using disk-backed content (5 lines, " +
                    smallText.length() + " characters)");
            
            System.out.println("Successfully loaded small disk content");
            
        } catch (Exception e) {
            statusLabel.setText("Status: Error - " + e.getMessage());
            log.error("Error loading small disk content", e);
        }
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}
