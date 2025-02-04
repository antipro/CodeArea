package com.bitifyware.example;

import com.bitifyware.control.CodeArea;
import com.bitifyware.control.syntax.DemoSyntax;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Spinner;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.scenicview.ScenicView;

import java.util.Objects;

/**
 * @author antipro
 */
public class CodeAreaExample extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(javafx.stage.Stage primaryStage) {
        CodeArea codeArea = new CodeArea();
//        TextArea codeArea = new TextArea();
        codeArea.setFont(Font.font("Monospace", FontWeight.NORMAL, 24));
        String text = """
                The quick brown fox jumps over the lazy dog""";
        codeArea.setText(text);
        codeArea.setWrapText(true);
        codeArea.setPrefWidth(700);
        codeArea.setPrefHeight(250);


        ToolBar toolBar = new ToolBar();
        Spinner<Integer> fontSizeSpinner = new Spinner<>(8, 72, 24);
        fontSizeSpinner.valueProperty().addListener((observable, oldValue, newValue) -> {
            codeArea.setFont(Font.font("Monospace", FontWeight.NORMAL, newValue));
        });
        toolBar.getItems().add(fontSizeSpinner);
        Spinner<Integer> tabSizeSpinner = new Spinner<>(2, 8, 4);
        tabSizeSpinner.valueProperty().addListener((observable, oldValue, newValue) -> {
            codeArea.setTabSize(newValue);
        });
        toolBar.getItems().add(tabSizeSpinner);

        codeArea.syntaxHighlighterProperty().addListener((observable, oldValue, newValue) -> {
            String originalText = codeArea.getText();
            codeArea.appendText(" ");
            Platform.runLater(() -> {
                codeArea.setText(originalText);
            });
        });
        Button noSyntaxButton = new Button("No Syntax");
        noSyntaxButton.setOnAction(event -> {
            codeArea.setSyntaxHighlighter(null);
        });
        toolBar.getItems().add(noSyntaxButton);
        Button demoSyntaxButton = new Button("Demo Syntax");
        demoSyntaxButton.setOnAction(event -> {
            codeArea.setSyntaxHighlighter(new DemoSyntax());
        });
        toolBar.getItems().add(demoSyntaxButton);
        Button wrapTextButton = new Button("Wrap Text");
        wrapTextButton.setOnAction(event -> {
            codeArea.setWrapText(!codeArea.isWrapText());
        });
        toolBar.getItems().add(wrapTextButton);
        Spinner<Integer> errorLineSpinner = new Spinner<>(-1, Integer.MAX_VALUE, -1);
        errorLineSpinner.setEditable(true);
        toolBar.getItems().add(errorLineSpinner);
        Button btnAddErrorPos = new Button("Add Error Pos");
        btnAddErrorPos.setOnAction(event -> {
            Integer errorLine = errorLineSpinner.getValue();
            if (!Objects.equals(errorLine, -1)) {
                codeArea.addErrorPos(errorLine);
            }
        });
        toolBar.getItems().add(btnAddErrorPos);
        Button clearErrorPosButton = new Button("Clear Error Pos");
        clearErrorPosButton.setOnAction(event -> {
            codeArea.clearErrorPos();
        });
        toolBar.getItems().add(clearErrorPosButton);

        Button printButton = new Button("Print");
        printButton.setOnAction(event -> {
            System.out.println(codeArea.getText());
        });
        toolBar.getItems().add(printButton);

        VBox codeAreaHBox = new VBox(toolBar, codeArea);
        VBox.setVgrow(codeArea, javafx.scene.layout.Priority.ALWAYS);
        javafx.scene.Scene scene = new javafx.scene.Scene(codeAreaHBox);
        primaryStage.setScene(scene);
        primaryStage.setX(100);
        primaryStage.setY(100);
        primaryStage.show();
        ScenicView.show(scene);
    }
}
