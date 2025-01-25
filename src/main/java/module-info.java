module com.antipro.codearea {
    requires javafx.controls;
    requires javafx.graphics;

    exports com.antipro.control;
    exports com.antipro.control.skin;
    opens com.antipro.control.skin to javafx.fxml;
    opens com.antipro.control to javafx.fxml;
}