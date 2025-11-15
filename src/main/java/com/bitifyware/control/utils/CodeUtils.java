package com.bitifyware.control.utils;

import com.sun.javafx.scene.control.skin.Utils;
import javafx.scene.shape.*;
import javafx.scene.text.Text;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;

/**
 * @author antipro
 */
public class CodeUtils {

    public static double computeTextWidth(javafx.scene.text.Font font, String string) {
        return Utils.computeTextWidth(font, string, Double.POSITIVE_INFINITY);
    }

    /**
     * FIXME
     * This is a experimental method to convert a JavaFX Text node into a Path
     * the coordination is wrong.
     * @param textNode
     * @return
     */
    public static Path textToPath(Text textNode) {
        String text = textNode.getText();
        if (text == null || text.isEmpty()) {
            return new Path();
        }

        // Get font family, style, and size
        javafx.scene.text.Font fxFont = textNode.getFont();
        String fontFamily = fxFont.getFamily();
        double fontSize = fxFont.getSize();
        int fontStyle = Font.PLAIN;
        if (fontFamily == null) fontFamily = "System";

        // Bold/Italic detection (optional)
        if (fxFont.getStyle().toLowerCase().contains("bold")) fontStyle |= Font.BOLD;
        if (fxFont.getStyle().toLowerCase().contains("italic")) fontStyle |= Font.ITALIC;

        // Create AWT Font
        Font awtFont = new Font(fontFamily, fontStyle, (int)Math.round(fontSize));

        // Create GlyphVector for provided text
        FontRenderContext frc = new FontRenderContext(null, true, true);
        GlyphVector gv = awtFont.createGlyphVector(frc, text);

        // --- Compute the offsets to align with textNode ---
        // "Text" uses baseline origin, AWT needs a translation
        float x = (float)textNode.getX();
//        float y = (float)textNode.getY();
        float y = (float) textNode.getY() + awtFont.getLineMetrics(text, frc).getAscent();

        // Get baseline offset (ascent), so characters appear at the right Y
        float baselineOffset = (float)-awtFont.getLineMetrics(text, frc).getDescent();

        // Outline at correct X, Y, and baseline
        java.awt.Shape outline = gv.getOutline(x, y + baselineOffset);

        // Convert Shape to JavaFX Path
        Path fxPath = awtShapeToFXPath(outline);

        // Copy fill and stroke if desired, or leave bare
        fxPath.setManaged(false);
        return fxPath;
    }

    private static Path awtShapeToFXPath(java.awt.Shape shape) {
        Path fxPath = new Path();
        PathIterator pi = shape.getPathIterator(new AffineTransform());
        double[] coords = new double[6];

        while (!pi.isDone()) {
            int segType = pi.currentSegment(coords);
            switch (segType) {
                case PathIterator.SEG_MOVETO:
                    fxPath.getElements().add(new MoveTo(coords[0], coords[1]));
                    break;
                case PathIterator.SEG_LINETO:
                    fxPath.getElements().add(new LineTo(coords[0], coords[1]));
                    break;
                case PathIterator.SEG_QUADTO:
                    fxPath.getElements().add(
                            new QuadCurveTo(coords[0], coords[1], coords[2], coords[3]));
                    break;
                case PathIterator.SEG_CUBICTO:
                    fxPath.getElements().add(
                            new CubicCurveTo(coords[0], coords[1], coords[2], coords[3], coords[4], coords[5]));
                    break;
                case PathIterator.SEG_CLOSE:
                    fxPath.getElements().add(new ClosePath());
                    break;
            }
            pi.next();
        }
        return fxPath;
    }
}
