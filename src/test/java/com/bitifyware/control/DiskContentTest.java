package com.bitifyware.control;

/**
 * Simple test for DiskContent basic operations.
 * This is a standalone test that doesn't require JavaFX.
 */
public class DiskContentTest {
    
    public static void main(String[] args) {
        System.out.println("Testing DiskContent basic operations...\n");
        
        try {
            testBasicOperations();
            testMultiLineOperations();
            testEdgeCases();
            
            System.out.println("\n✓ All tests passed!");
        } catch (Exception e) {
            System.err.println("\n✗ Test failed!");
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void testBasicOperations() throws Exception {
        System.out.println("Test 1: Basic operations (insert, get, delete)");
        
        DiskContent content = new DiskContent();
        
        // Test empty content
        assertEquals(0, content.length(), "Initial length should be 0");
        assertEquals("", content.get(), "Initial content should be empty");
        
        // Test insert
        content.insert(0, "Hello, World!", true);
        assertEquals(13, content.length(), "Length after insert");
        assertEquals("Hello, World!", content.get(), "Content after insert");
        
        // Test get substring
        assertEquals("Hello", content.get(0, 5), "Substring 0-5");
        assertEquals("World", content.get(7, 12), "Substring 7-12");
        
        // Test delete
        content.delete(5, 7, true); // Remove ", "
        assertEquals(11, content.length(), "Length after delete");
        assertEquals("HelloWorld!", content.get(), "Content after delete");
        
        content.close();
        System.out.println("  ✓ Basic operations test passed");
    }
    
    private static void testMultiLineOperations() throws Exception {
        System.out.println("Test 2: Multi-line operations");
        
        DiskContent content = new DiskContent();
        
        // Insert multi-line content
        String multiLine = "Line 1\nLine 2\nLine 3";
        content.insert(0, multiLine, true);
        
        assertEquals(20, content.length(), "Multi-line length (6+1+6+1+6=20)");
        assertEquals(multiLine, content.get(), "Multi-line content");
        assertEquals(3, content.getParagraphList().size(), "Paragraph count");
        
        // Test individual lines
        assertEquals("Line 1", content.getParagraphList().get(0).toString(), "First line");
        assertEquals("Line 2", content.getParagraphList().get(1).toString(), "Second line");
        assertEquals("Line 3", content.getParagraphList().get(2).toString(), "Third line");
        
        // Insert in middle of line
        content.insert(7, "INSERTED", true); // Insert after "Line 1\n"
        assertEquals("Line 1\nINSERTEDLine 2\nLine 3", content.get(), "After middle insert");
        
        // Delete across lines
        content = new DiskContent("AAA\nBBB\nCCC");
        content.delete(2, 7, true); // Delete "A\nBB"
        assertEquals("AAB\nCCC", content.get(), "After cross-line delete");
        
        content.close();
        System.out.println("  ✓ Multi-line operations test passed");
    }
    
    private static void testEdgeCases() throws Exception {
        System.out.println("Test 3: Edge cases");
        
        DiskContent content = new DiskContent();
        
        // Empty line handling
        content.insert(0, "\n\n", true);
        assertEquals(2, content.length(), "Empty lines length");
        assertEquals(3, content.getParagraphList().size(), "Empty lines paragraph count");
        
        // Insert at end
        content.insert(content.length(), "END", true);
        assertEquals("\n\nEND", content.get(), "Insert at end");
        
        // Delete from start
        content.delete(0, 2, true);
        assertEquals("END", content.get(), "Delete from start");
        
        // Replace all content
        content.delete(0, content.length(), true);
        content.insert(0, "New content", true);
        assertEquals("New content", content.get(), "Replace all");
        
        content.close();
        System.out.println("  ✓ Edge cases test passed");
    }
    
    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }
    
    private static void assertEquals(String expected, String actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ":\n  expected: \"" + expected + "\"\n  but got:  \"" + actual + "\"");
        }
    }
}
