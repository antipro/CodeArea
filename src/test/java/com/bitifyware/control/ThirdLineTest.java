package com.bitifyware.control;

/**
 * Test to verify the third-line input issue is fixed.
 */
public class ThirdLineTest {
    
    public static void main(String[] args) {
        System.out.println("Testing third-line input issue fix...\n");
        
        try {
            DiskContent content = new DiskContent();
            
            // Simulate typing: "Line 1\nLine 2\nLine 3"
            content.insert(0, "Line 1", true);
            System.out.println("After 'Line 1':");
            System.out.println("  Content: [" + content.get() + "]");
            System.out.println("  Line count: " + content.getParagraphList().size());
            printParagraphs(content);
            
            content.insert(6, "\n", true);
            System.out.println("\nAfter first newline:");
            System.out.println("  Content: [" + content.get() + "]");
            System.out.println("  Line count: " + content.getParagraphList().size());
            printParagraphs(content);
            
            content.insert(7, "Line 2", true);
            System.out.println("\nAfter 'Line 2':");
            System.out.println("  Content: [" + content.get() + "]");
            System.out.println("  Line count: " + content.getParagraphList().size());
            printParagraphs(content);
            
            content.insert(13, "\n", true);
            System.out.println("\nAfter second newline:");
            System.out.println("  Content: [" + content.get() + "]");
            System.out.println("  Line count: " + content.getParagraphList().size());
            printParagraphs(content);
            
            content.insert(14, "Line 3", true);
            System.out.println("\nAfter 'Line 3':");
            System.out.println("  Content: [" + content.get() + "]");
            System.out.println("  Line count: " + content.getParagraphList().size());
            printParagraphs(content);
            
            // Verify the result
            String expected = "Line 1\nLine 2\nLine 3";
            String actual = content.get();
            
            if (expected.equals(actual)) {
                System.out.println("\n✓ TEST PASSED: Content matches expected");
                System.out.println("  Expected paragraphs: [Line 1], [Line 2], [Line 3]");
                System.out.println("  Actual paragraphs:   [" + content.getParagraphList().get(0) + "], " +
                                   "[" + content.getParagraphList().get(1) + "], " +
                                   "[" + content.getParagraphList().get(2) + "]");
            } else {
                System.err.println("\n✗ TEST FAILED");
                System.err.println("  Expected: [" + expected + "]");
                System.err.println("  Actual:   [" + actual + "]");
                System.exit(1);
            }
            
            content.close();
            System.out.println("\n✓ All tests passed!");
            
        } catch (Exception e) {
            System.err.println("\n✗ Test failed with exception!");
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void printParagraphs(DiskContent content) {
        for (int i = 0; i < content.getParagraphList().size(); i++) {
            System.out.println("    Paragraph " + i + ": [" + content.getParagraphList().get(i) + "]");
        }
    }
}
