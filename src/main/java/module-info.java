module com.bitifyware.codearea {
    requires javafx.controls;
    requires javafx.graphics;
    requires java.desktop;

    exports com.bitifyware.control.skin;
    exports com.bitifyware.control.syntax;
    opens com.bitifyware.control.skin to javafx.fxml;
    exports com.bitifyware.control;
    opens com.bitifyware.control to javafx.fxml;
}