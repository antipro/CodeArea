module com.bitifyware.codearea {
    requires javafx.controls;
    requires javafx.graphics;
    requires java.desktop;

    exports com.bitifyware.control.skin;
    exports com.bitifyware.control.syntax;
    exports com.bitifyware.control;
    opens com.bitifyware.control.skin to javafx.fxml;
    opens com.bitifyware.control to javafx.fxml;
}