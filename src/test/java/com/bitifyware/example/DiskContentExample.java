package com.bitifyware.example;

import com.bitifyware.control.CodeArea;
import com.bitifyware.control.InDiskContent;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import org.scenicview.ScenicView;

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
 * @see InDiskContent
 * @see CodeArea
 */
public class DiskContentExample extends Application {
    
    private CodeArea codeArea;
    private Label statusLabel;
    
    @Override
    public void start(Stage primaryStage) {
        // Create the main UI components
        BorderPane root = new BorderPane();
        
        // Create CodeArea with initial content
        codeArea = new CodeArea("Initial in-memory content.\nThis will be replaced.", true);
        codeArea.setFont(Font.font("Monospace", FontWeight.NORMAL, 18));
        // Status label
        statusLabel = new Label("Status: Using in-memory content");
        
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
        ScenicView.show(scene);
        primaryStage.setTitle("DiskContent Example - CodeArea with Disk-Backed Storage");
        primaryStage.setScene(scene);
        primaryStage.show();

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
            
            statusLabel.setText("Status: Swapping to disk content...");
            
            // Update the CodeArea to reflect the new content
            codeArea.setText(largeText.toString());
            
            statusLabel.setText("Status: Using disk-backed content (10,000 lines, " +
                    largeText.length() + " characters)");
            
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
            
            // Swap the content
            codeArea.setText(smallText);
            
            statusLabel.setText("Status: Using disk-backed content (5 lines, " +
                    smallText.length() + " characters)");
            
            System.out.println("Successfully loaded small disk content");
            
        } catch (Exception e) {
            statusLabel.setText("Status: Error - " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}
