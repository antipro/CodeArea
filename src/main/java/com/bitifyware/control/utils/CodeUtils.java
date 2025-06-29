package com.bitifyware.control.utils;

import com.sun.javafx.scene.control.skin.Utils;
import javafx.scene.text.Font;

/**
 * @author antipro
 */
public class CodeUtils {

    public static double computeTextWidth(Font font, String string) {
        return Utils.computeTextWidth(font, string, Double.POSITIVE_INFINITY);
    }
}
