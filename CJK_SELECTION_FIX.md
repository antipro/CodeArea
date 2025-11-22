# CJK Selection Line Height Fix Documentation

## Problem Statement

Selection rectangles were misaligning vertically when CJK (Chinese, Japanese, Korean) characters were present in the text, even though Text nodes were configured with `TextBoundsType.LOGICAL` and `VPos.TOP`. The misalignment would appear when CJK characters were added and would disappear when they were removed, indicating that per-Text bounds were varying with different glyphs and font fallback.

### Symptoms
- Selection rectangles did not align vertically across lines
- Adding CJK characters caused selection to shift vertically
- Deleting CJK characters made selection recover
- Issue was particularly noticeable on Windows with ClearType rendering
- Problem persisted even with proper TextBoundsType and VPos configuration

### Root Cause

The selection rendering code was using per-Text node bounds to compute line height and selection geometry. The critical issues were:

1. **Line Height Measurement**: Line height was computed using only the "1" character (`Utils.computeTextHeight(textNode.getFont(), "1", ...)`) which doesn't account for CJK glyph heights
2. **Per-Text Bounds Variation**: Text nodes containing CJK characters have different bounds due to:
   - Larger glyph heights for CJK characters
   - Font fallback to CJK-capable fonts
   - Platform-specific font rasterization
3. **Selection Adjustment Logic**: Code at `CodeAreaSkin.java:2101-2106` adjusted selection Y coordinates based on `textNode.getBoundsInLocal().getMaxY()` which varies per-Text

## Solution

### 1. Stable Line Height Computation

Added a new method `computeStableLineHeight()` that uses a representative string "Hg中" to measure line height:

```java
private double computeStableLineHeight() {
    if (stableLineHeight > 0) {
        return stableLineHeight;
    }
    
    Font font = getSkinnable().getFont();
    if (font == null) {
        font = Font.getDefault();
    }
    
    // Create or reuse measuring Text node with representative characters
    // "Hg" covers Latin ascenders/descenders, "中" ensures CJK height is included
    if (measuringText == null) {
        measuringText = new Text("Hg中");
        measuringText.setBoundsType(TextBoundsType.LOGICAL);
        measuringText.setTextOrigin(VPos.TOP);
    }
    measuringText.setFont(font);
    
    // Apply CSS to get accurate bounds
    measuringText.applyCss();
    
    // Use layout bounds height as the stable measurement
    double height = measuringText.getLayoutBounds().getHeight();
    
    // Cache the stable height, rounded to integer pixels
    stableLineHeight = Math.ceil(height);
    
    return stableLineHeight;
}
```

**Why "Hg中"?**
- **"H"**: Covers uppercase Latin height with ascender
- **"g"**: Covers lowercase Latin height with descender
- **"中"**: Ensures CJK character height is included in measurement
- This combination gives the maximum height needed for any line of text

### 2. Updated Layout Code

Changed line 1853 in `CodeAreaSkin.java` from:
```java
if (oneLineHeight == 0) {
    oneLineHeight = Utils.computeTextHeight(textNode.getFont(), "1", 0, TextBoundsType.LOGICAL_VERTICAL_CENTER);
}
```

To:
```java
// Use stable line height that accounts for CJK characters and font fallback
double oneLineHeight = computeStableLineHeight();
```

This ensures consistent line height for layout regardless of actual text content.

### 3. Fixed Selection Rendering

Changed line 2154 in `CodeAreaSkin.java` from:
```java
double offset = oneLineHeight - textNode.getBoundsInLocal().getMaxY();
```

To:
```java
// FIX: Use stable line height for consistent selection alignment
double stableHeight = computeStableLineHeight();
double offset = stableHeight - textNode.getBoundsInLocal().getMaxY();
```

This prevents selection misalignment by using a consistent height measurement.

### 4. Font Change Handling

Added invalidation of cached stable line height in `updateFontMetrics()`:
```java
private void updateFontMetrics() {
    TextFlow textFlow = (TextFlow) paragraphNodes.getChildren().get(0);
    Text firstParagraph = (Text)textFlow.getChildren().get(0);
    lineHeight = Utils.getLineHeight(getSkinnable().getFont(), firstParagraph.getBoundsType());
    characterWidth = fontMetrics.get().getCharWidth('W');
    // Invalidate stable line height when font metrics change
    invalidateStableLineHeight();
}
```

This ensures the stable height is recalculated when the font changes.

### 5. Consistent Text Node Configuration

Updated both `DemoSyntax.java` and the default syntax highlighter in `CodeArea.java` to set `TextBoundsType.LOGICAL`:

```java
Text textNode = new Text(text);
textNode.setBoundsType(TextBoundsType.LOGICAL);
textNode.setTextOrigin(VPos.TOP);
```

This ensures all Text nodes have consistent bounds configuration.

## Files Modified

1. **CodeAreaSkin.java**
   - Added `measuringText` and `stableLineHeight` fields
   - Added `computeStableLineHeight()` method
   - Added `invalidateStableLineHeight()` method
   - Updated layout code to use stable height (line ~1853)
   - Updated selection rendering to use stable height (line ~2154)
   - Updated `updateFontMetrics()` to invalidate cache

2. **DemoSyntax.java**
   - Added `textNode.setBoundsType(TextBoundsType.LOGICAL)` to decompose method

3. **CodeArea.java**
   - Added `singleText.setBoundsType(TextBoundsType.LOGICAL)` to default syntax highlighter

4. **TestSelectionLineHeightFix.java** (new)
   - Test application demonstrating the fix with CJK characters

## Testing

### Manual Testing

Run the test application:
```bash
mvn compile exec:java -Dexec.mainClass="com.bitifyware.example.TestSelectionLineHeightFix"
```

**Test Steps:**
1. Select text across lines 3-5 (containing CJK characters)
2. Verify selection highlight aligns correctly on all lines
3. Click the "Toggle CJK Characters" button
4. Verify selection remains aligned when CJK characters are added/removed
5. Compare selection on CJK lines vs. Latin-only lines - should be identical

### Expected Results

**Before Fix:**
- Selection rectangles misalign vertically on lines with CJK characters
- Adding CJK characters causes selection to shift
- Visible gaps between selection and text on CJK lines

**After Fix:**
- Selection rectangles align consistently on all lines
- Adding/removing CJK characters doesn't affect selection alignment
- No gaps or vertical misalignment
- Selection height is consistent across Latin and CJK lines

## Technical Details

### Why This Approach Works

1. **Stable Measurement**: Using "Hg中" ensures the line height accounts for the tallest possible combination of Latin and CJK glyphs
2. **Font Fallback Handling**: The measurement happens after font fallback, so it captures the actual rendered height
3. **Caching**: Computing the stable height once per font change is efficient
4. **Platform Agnostic**: Works across Windows, macOS, and Linux without platform-specific hacks
5. **Layout Consistent**: Uses the same TextBoundsType.LOGICAL as Text nodes for consistency

### Performance Considerations

- Stable height is computed once and cached
- Invalidated only on font changes (rare)
- No per-frame or per-line overhead
- Minimal memory footprint (one Text node for measurement)

### Coordinate System Notes

All coordinates are:
- Rounded to integer pixels using `Math.ceil()` for the stable height
- Consistent with existing rounding in layout code (`Math.round()`)
- Prevents fractional-pixel drift across coordinate space conversions

## Future Enhancements

### Potential Improvements

1. **Font Bundling**: Bundle a CJK-capable monospace font to eliminate font fallback variations
2. **Per-Line Height**: Could optionally compute height per visual line if TextFlow provides it
3. **DPI Awareness**: Add explicit DPI-aware scaling for high-DPI displays
4. **IME Support**: Add listeners for InputMethodEvents to update selection during composition

### Not Implemented (Out of Scope)

- Per-glyph vertical centering (would break monospace alignment)
- Platform-specific font rendering adjustments
- Dynamic line height per line content (would break consistent editor UX)

## Platform Compatibility

### Windows
- Works correctly with ClearType sub-pixel rendering
- Handles font fallback to Microsoft YaHei, SimSun, etc.
- Tested with various DPI scaling factors

### macOS
- Compatible with Retina displays
- Works with Apple's font rendering
- Handles font fallback to Hiragino, PingFang, etc.

### Linux
- Works with various font hinting settings
- Compatible with X11 and Wayland
- Handles font fallback to Noto CJK, WenQuanYi, etc.

## References

- **JavaFX Text Bounds Types**: https://openjfx.io/javadoc/17/javafx.graphics/javafx/scene/text/TextBoundsType.html
- **Issue**: Selection rectangles misalign with CJK characters
- **Repository**: antipro/CodeArea
- **Related**: Previous selection fix addressed TextBoundsType and coordinate rounding

## Summary

This fix ensures that selection rendering in CodeArea uses a stable, layout-consistent line height that accounts for CJK characters and font fallback. The key insight is that per-Text bounds vary with content, so we must compute a conservative stable height using a representative measurement. This approach is platform-agnostic, efficient, and maintains the consistent monospace editor UX.
