package com.bitifyware;

import com.bitifyware.control.CodeArea;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;

/**
 * @author antipro
 */
public class LargeContentTest extends ApplicationTest {

    private CodeArea codeArea;

    @Override
    public void start(Stage stage) {
        codeArea = new CodeArea("", true);
        stage.setScene(new Scene(new StackPane(codeArea), 100, 100));
        stage.show();
    }

    @Test
    public void largeText() throws InterruptedException {

        StringBuilder largeText = new StringBuilder();
        for (int i = 0; i < 100000; i++) {
            largeText.append("Line ").append(i).append(": The quick brown fox jumps over the lazy dog.\n");
        }
        codeArea.setText(largeText.toString());
        // Verify that the content was set correctly
        org.junit.Assert.assertEquals(largeText.toString(), codeArea.getText());
    }
}
