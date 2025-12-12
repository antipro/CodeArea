package com.bitifyware.control;

import java.lang.reflect.Field;

/**
 * Reflection-based utility for swapping the content implementation of a CodeArea or CodeInputControl.
 * 
 * <p>This utility enables runtime replacement of the private {@code content} field in CodeInputControl
 * with a different implementation (e.g., {@link DiskContent} instead of {@link InMemoryContent}).
 * This allows using disk-backed content for large files without modifying the CodeArea source code.
 * 
 * <h3>Why Reflection?</h3>
 * <p>The {@code content} field in CodeInputControl is declared as {@code private final}, and is set
 * only in the constructor. To enable swapping content implementations at runtime without modifying
 * CodeArea source, we use reflection to bypass the final modifier and replace the field value.
 * 
 * <h3>Risks and Limitations</h3>
 * <ul>
 *   <li><b>Fragile:</b> This approach depends on CodeInputControl's internal implementation.
 *       Changes to field names or structure in future versions will break this utility.</li>
 *   <li><b>JVM Security:</b> May fail if SecurityManager or module access restrictions prevent
 *       reflection. Requires {@code --add-opens} for Java modules.</li>
 *   <li><b>Not Thread-Safe:</b> Do not call while the CodeArea is being actively used.</li>
 *   <li><b>State Loss:</b> The old content object is replaced. Ensure you don't need its state.</li>
 * </ul>
 * 
 * <h3>Future Alternative</h3>
 * <p>A cleaner approach would be to modify CodeInputControl to accept a content factory or allow
 * content replacement. This reflection-based utility is a workaround to avoid source modifications.
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * CodeArea codeArea = new CodeArea();
 * DiskContent diskContent = new DiskContent("Large file content...");
 * 
 * try {
 *     ContentSwapper.swapContent(codeArea, diskContent);
 *     // Now codeArea uses disk-backed storage
 * } catch (ReflectiveOperationException e) {
 *     // Handle reflection failure
 *     e.printStackTrace();
 * }
 * }</pre>
 * 
 * @see DiskContent
 * @see CodeArea
 * @see CodeInputControl
 */
public final class ContentSwapper {
    
    private ContentSwapper() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Swaps the content implementation of a CodeArea with a new content instance.
     * 
     * <p>This method uses reflection to replace the private {@code content} field in the
     * CodeInputControl superclass. The operation is performed in the following steps:
     * <ol>
     *   <li>Locate the {@code content} field via reflection</li>
     *   <li>Make the field accessible (bypassing private and final modifiers)</li>
     *   <li>Replace the field value with the new content</li>
     * </ol>
     * 
     * <p><b>Important:</b> After swapping, call {@code codeArea.setText(newContent.get())} or
     * similar to ensure the UI is updated with the new content.
     * 
     * @param codeArea the CodeArea whose content should be swapped
     * @param newContent the new content implementation (e.g., DiskContent)
     * @throws ReflectiveOperationException if reflection fails (field not found, access denied, etc.)
     * @throws IllegalArgumentException if codeArea or newContent is null
     */
    public static void swapContent(CodeArea codeArea, CodeInputControl.Content newContent) 
            throws ReflectiveOperationException {
        if (codeArea == null) {
            throw new IllegalArgumentException("codeArea cannot be null");
        }
        if (newContent == null) {
            throw new IllegalArgumentException("newContent cannot be null");
        }
        
        // The content field is declared in CodeInputControl
        Field contentField = CodeInputControl.class.getDeclaredField("content");
        
        // Make the field accessible (bypasses private and final)
        contentField.setAccessible(true);
        
        // Replace the content field value
        contentField.set(codeArea, newContent);
        
        // Note: Caller should update the text property or trigger UI refresh
        // For example: codeArea.setText(newContent.get());
    }
    
    /**
     * Swaps the content implementation of a CodeInputControl with a new content instance.
     * 
     * <p>This is a more generic version that works with any CodeInputControl subclass,
     * not just CodeArea.
     * 
     * @param control the CodeInputControl whose content should be swapped
     * @param newContent the new content implementation
     * @throws ReflectiveOperationException if reflection fails
     * @throws IllegalArgumentException if control or newContent is null
     */
    public static void swapContent(CodeInputControl control, CodeInputControl.Content newContent) 
            throws ReflectiveOperationException {
        if (control == null) {
            throw new IllegalArgumentException("control cannot be null");
        }
        if (newContent == null) {
            throw new IllegalArgumentException("newContent cannot be null");
        }
        
        Field contentField = CodeInputControl.class.getDeclaredField("content");
        contentField.setAccessible(true);
        contentField.set(control, newContent);
    }
}
