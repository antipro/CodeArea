package com.bitifyware.example;

import com.bitifyware.control.CodeArea;
import com.bitifyware.control.ContentSwapper;
import com.bitifyware.control.DiskContent;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

/**
 * Example JavaFX application demonstrating the use of DiskContent with CodeArea.
 * 
 * <p>This example shows how to:
 * <ol>
 *   <li>Create a CodeArea with standard in-memory content</li>
 *   <li>Create a DiskContent instance with large text content</li>
 *   <li>Use ContentSwapper to replace the CodeArea's content at runtime</li>
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
 * @see DiskContent
 * @see ContentSwapper
 * @see CodeArea
 */
public class DiskContentExample extends Application {
    
    private CodeArea codeArea;
    private DiskContent diskContent;
    private Label statusLabel;
    
    @Override
    public void start(Stage primaryStage) {
        // Create the main UI components
        BorderPane root = new BorderPane();
        
        // Create CodeArea with initial content
        codeArea = new CodeArea("Initial in-memory content.\nThis will be replaced.", true);
        
        // Status label
        statusLabel = new Label("Status: Using in-memory content");
        
        // Buttons for testing
        HBox buttonBar = new HBox(10);
        
        Button loadDiskContentBtn = new Button("Load Disk Content (Large)");
        loadDiskContentBtn.setOnAction(e -> loadLargeDiskContent());
        
        Button loadSmallDiskContentBtn = new Button("Load Disk Content (Small)");
        loadSmallDiskContentBtn.setOnAction(e -> loadSmallDiskContent());
        
        Button showStatsBtn = new Button("Show Stats");
        showStatsBtn.setOnAction(e -> showStats());
        
        buttonBar.getChildren().addAll(loadDiskContentBtn, loadSmallDiskContentBtn, showStatsBtn);
        
        // Layout
        root.setCenter(codeArea);
        root.setTop(buttonBar);
        root.setBottom(statusLabel);
        
        // Create and show the scene
        Scene scene = new Scene(root, 800, 600);
        primaryStage.setTitle("DiskContent Example - CodeArea with Disk-Backed Storage");
        primaryStage.setScene(scene);
        primaryStage.show();
        
        // Cleanup on close
        primaryStage.setOnCloseRequest(e -> cleanup());
    }
    
    /**
     * Loads a large text file into disk content and swaps it into the CodeArea.
     * This demonstrates handling files too large for memory.
     */
    private void loadLargeDiskContent() {
        try {
            statusLabel.setText("Status: Generating large content...");
            
            // Generate a large text (simulating a large file)
            StringBuilder largeText = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                largeText.append("Line ").append(i + 1)
                        .append(": This is a sample line of text that will be stored on disk. ")
                        .append("Lorem ipsum dolor sit amet, consectetur adipiscing elit.\n");
            }
            
            // Create DiskContent with the large text
            if (diskContent != null) {
                diskContent.close(); // Clean up old content
            }
            diskContent = new DiskContent(largeText.toString());
            
            statusLabel.setText("Status: Swapping to disk content...");
            
            // Swap the content using reflection
            ContentSwapper.swapContent(codeArea, diskContent);
            
            // Update the CodeArea to reflect the new content
            codeArea.setText(diskContent.get());
            
            statusLabel.setText("Status: Using disk-backed content (10,000 lines, " + 
                              diskContent.length() + " characters)");
            
            System.out.println("Successfully loaded large disk content");
            
        } catch (Exception e) {
            statusLabel.setText("Status: Error - " + e.getMessage());
            e.printStackTrace();
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
            
            // Create DiskContent with the small text
            if (diskContent != null) {
                diskContent.close();
            }
            diskContent = new DiskContent(smallText);
            
            // Swap the content
            ContentSwapper.swapContent(codeArea, diskContent);
            codeArea.setText(diskContent.get());
            
            statusLabel.setText("Status: Using disk-backed content (5 lines, " + 
                              diskContent.length() + " characters)");
            
            System.out.println("Successfully loaded small disk content");
            
        } catch (Exception e) {
            statusLabel.setText("Status: Error - " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Shows statistics about the current content.
     */
    private void showStats() {
        if (diskContent != null) {
            int charCount = diskContent.length();
            int lineCount = diskContent.getParagraphList().size();
            statusLabel.setText(String.format("Status: %d lines, %d characters (disk-backed)", 
                                             lineCount, charCount));
        } else {
            int charCount = codeArea.getLength();
            statusLabel.setText(String.format("Status: %d characters (in-memory)", charCount));
        }
    }
    
    /**
     * Cleanup resources on application exit.
     */
    private void cleanup() {
        if (diskContent != null) {
            diskContent.close();
            System.out.println("DiskContent cleaned up");
        }
    }
    
    @Override
    public void stop() {
        cleanup();
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}
