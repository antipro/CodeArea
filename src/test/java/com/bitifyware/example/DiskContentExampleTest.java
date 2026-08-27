package com.bitifyware.example;

import com.bitifyware.control.CodeArea;
import javafx.stage.Stage;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DiskContentExampleTest extends ApplicationTest {

    private DiskContentExample example;

    @BeforeClass
    public static void requireDisplay() {
        String os = System.getProperty("os.name").toLowerCase();
        Assume.assumeFalse(Boolean.parseBoolean(System.getProperty("java.awt.headless", "true")));
        Assume.assumeTrue(!os.contains("linux") || System.getenv("DISPLAY") != null);
    }

    @Override
    public void start(Stage stage) {
        example = new DiskContentExample();
        example.start(stage);
    }

    @Test(timeout = 30_000)
    public void loadsLargeContentFromExampleButton() {
        interact(example::loadLargeDiskContent);
        WaitForAsyncUtils.waitForFxEvents();

        CodeArea codeArea = lookup(".code-area").queryAs(CodeArea.class);
        assertEquals(10_001, codeArea.getParagraphs().size());
        assertTrue(codeArea.getText().startsWith("Line 1: This is a sample line"));
        assertTrue(codeArea.getText().endsWith("adipiscing elit.\n"));
    }
}
