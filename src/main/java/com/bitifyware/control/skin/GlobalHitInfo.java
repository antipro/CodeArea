package com.bitifyware.control.skin;

import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/**
 * @author antipro
 */
public class GlobalHitInfo {
    private final int charIndex;
    private final boolean leading;
    private final int insertionIndex;
    private final Text textNode;
    private final TextFlow textFlow;

    /**
     * Create a HitInfo object representing a text index and forward bias.
     *
     * @param charIndex the character index.
     * @param leading   whether the hit is on the leading edge of the character. If it is false, it represents the trailing edge.
     * @param textNode  the text node.
     * @param textFlow the text flow.
     */
    public GlobalHitInfo(int charIndex, int insertionIndex, boolean leading, Text textNode, TextFlow textFlow) {
        this.charIndex = charIndex;
        this.leading = leading;
        this.insertionIndex = insertionIndex;
        this.textNode = textNode;
        this.textFlow = textFlow;
    }

    /**
     * The index of the character which this hit information refers to.
     * @return the index of the character which this hit information refers to
     */
    public int getCharIndex() {
        return charIndex;
    }

    /**
     * Indicates whether the hit is on the leading edge of the character.
     * If it is false, it represents the trailing edge.
     * @return if true the hit is on the leading edge of the character, otherwise
     * the trailing edge
     */
    public boolean isLeading() {
        return leading;
    }

    /**
     * Returns the index of the insertion position.
     * @return the index of the insertion position
     */
    public int getInsertionIndex() {
        return insertionIndex;
    }

    @Override
    public String toString() {
        return "charIndex: " + charIndex + ", isLeading: " + leading + ", insertionIndex: " + insertionIndex;
    }

    public Text getTextNode() {
        return textNode;
    }

    public TextFlow getTextFlow() {
        return textFlow;
    }

}