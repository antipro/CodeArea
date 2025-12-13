# Selection/Highlight Rendering Fix Documentation

## Problem Description

Users reported that selection highlight rectangles were misaligned vertically with text glyphs despite Text nodes being configured with `TextBoundsType.LOGICAL` and `VPos.TOP`. The selection rectangles did not correctly cover the glyphs, leading to visual gaps and misalignment.

## Root Causes Identified

### 1. Missing TextBoundsType.LOGICAL Configuration
**Issue:** Text nodes were not explicitly setting `TextBoundsType.LOGICAL`, causing JavaFX to use default bounds which include visual overhangs and vary based on font rendering hints.

**Impact:** Selection rectangles calculated from `Text.getSelectionShape()` did not match the actual visual bounds of the glyphs.

### 2. Fractional Pixel Coordinates
**Issue:** Text layout and selection path coordinates were not rounded to integer pixels, causing sub-pixel positioning that leads to anti-aliasing artifacts and blurred selection edges.

**Impact:** On Windows with ClearType and other platforms with sub-pixel rendering, the selection rectangles appeared offset by fractional pixels.

### 3. Inconsistent Line Height Calculation
**Issue:** Line height was calculated using `TextBoundsType.LOGICAL_VERTICAL_CENTER` while text nodes should use `TextBoundsType.LOGICAL` for consistency.

**Impact:** Vertical alignment calculations didn't match the actual text layout bounds.

### 4. Anti-aliasing on Selection Paths
**Issue:** Selection paths had smooth rendering enabled, causing blurred edges that didn't align crisply with text glyphs.

**Impact:** Visual blur around selection edges made misalignment more noticeable.

## Solutions Implemented

### 1. Configure Text Nodes with TextBoundsType.LOGICAL
```java
Text textNode = new Text(text);
textNode.setBoundsType(TextBoundsType.LOGICAL);
textNode.setTextOrigin(VPos.TOP);
```

**Files Modified:**
- `src/main/java/com/bitifyware/control/syntax/DemoSyntax.java`
- `src/main/java/com/bitifyware/control/CodeArea.java` (default syntax highlighter)

**Rationale:** `TextBoundsType.LOGICAL` provides consistent bounds that exclude visual overhangs, ensuring selection shapes align with the logical character positions.

### 2. Round All Layout Coordinates
```java
textNode.setLayoutX(Math.round(subX));
textNode.setLayoutY(Math.round(subY));

linePath.setLayoutX(Math.round(textNode.getLayoutX()));
linePath.setLayoutY(Math.round(textFlow.getLayoutY() + textNode.getLayoutY()));
```

**Files Modified:**
- `src/main/java/com/bitifyware/control/skin/CodeAreaSkin.java`

**Rationale:** Rounding ensures all coordinates snap to integer pixels, preventing sub-pixel rendering artifacts and ensuring consistent alignment across different platforms and DPI settings.

### 3. Disable Smoothing on Selection Paths
```java
linePath.setSmooth(false);
blankLinePath.setSmooth(false);
```

**Files Modified:**
- `src/main/java/com/bitifyware/control/skin/CodeAreaSkin.java`

**Rationale:** Disabling smoothing prevents anti-aliasing blur on selection rectangles, ensuring crisp edges that align precisely with text glyphs.

### 4. Use Consistent Bounds Type for Line Height
```java
oneLineHeight = Utils.computeTextHeight(textNode.getFont(), "1", 0, TextBoundsType.LOGICAL);
```

**Files Modified:**
- `src/main/java/com/bitifyware/control/skin/CodeAreaSkin.java`

**Rationale:** Using the same `TextBoundsType.LOGICAL` for line height calculations ensures vertical spacing matches the text node bounds.

### 5. Set Mouse Transparent on Selection Group
```java
selectionHighlightGroup.setMouseTransparent(true);
```

**Files Modified:**
- `src/main/java/com/bitifyware/control/skin/CodeAreaSkin.java`

**Rationale:** Prevents selection rectangles from interfering with mouse events on the underlying text.

## Testing

### Manual Testing
A test application was created at:
- `src/test/java/com/bitifyware/example/SelectionRenderingTest.java`

**To run:**
```bash
mvn compile exec:java -Dexec.mainClass="com.bitifyware.example.SelectionRenderingTest"
```

**Test Steps:**
1. Launch the test application
2. Click and drag to select text
3. Verify that selection highlight aligns precisely with text glyphs
4. Test on multiple lines with different selection lengths
5. Verify no vertical gaps or misalignment

### Expected Results
- Selection rectangles should precisely cover the glyphs
- No vertical gaps between selection and text
- Consistent alignment across all lines
- Crisp selection edges without blur
- Stable rendering across window resizing and scrolling

## Platform-Specific Considerations

### Windows
- **ClearType:** The coordinate rounding is especially important on Windows where ClearType sub-pixel rendering is used
- **DPI Scaling:** Rounding ensures selection alignment remains correct with different DPI scaling factors

### macOS
- **Retina Displays:** Coordinate rounding works correctly with Retina scaling
- **Font Rendering:** LOGICAL bounds ensure consistent behavior with macOS font rendering

### Linux
- **Font Hinting:** LOGICAL bounds work consistently across different font hinting settings
- **X11/Wayland:** Coordinate rounding prevents fractional pixel issues in both display servers

## Technical Background

### TextBoundsType Options in JavaFX
- **LOGICAL:** Bounds based on font metrics (ascent, descent, line height) - excludes visual overhangs
- **VISUAL:** Bounds based on actual rendered pixels - includes anti-aliasing overhangs
- **LOGICAL_VERTICAL_CENTER:** LOGICAL bounds with vertical centering

### Why LOGICAL is Correct for Code Editors
1. **Predictable Layout:** Character positions are based on logical metrics, not visual rendering
2. **Monospace Alignment:** Essential for code editors where character grid alignment matters
3. **Cross-Platform Consistency:** Logical bounds are consistent across different font rendering engines

### Coordinate Rounding Best Practices
- **Layout Positions:** Always round node position coordinates
- **Bounds Calculations:** Use logical bounds first, then round final positions
- **Selection Shapes:** Round the layout position of the containing path, not individual path elements
- **Snap to Pixel:** Can optionally enable on container nodes for additional stability

## Future Improvements

### Potential Enhancements
1. **Font Bundling:** Bundle a monospace font with consistent metrics to eliminate font substitution issues
2. **DPI-Aware Scaling:** Add explicit DPI awareness for high-DPI displays
3. **Platform-Specific Adjustments:** Add platform-specific tweaks if needed for edge cases
4. **Performance Optimization:** Cache computed text metrics to reduce recalculation

### Monitoring
- Watch for user reports on specific platforms or font configurations
- Test with various font sizes and DPI settings
- Verify behavior with different syntax highlighters

## References

### JavaFX Documentation
- [Text Bounds Types](https://openjfx.io/javadoc/17/javafx.graphics/javafx/scene/text/TextBoundsType.html)
- [Text Node API](https://openjfx.io/javadoc/17/javafx/scene/text/Text.html)
- [VPos (Vertical Positioning)](https://openjfx.io/javadoc/17/javafx.geometry/javafx/geometry/VPos.html)

### Related Issues
- Sub-pixel rendering and text alignment in JavaFX
- Font metrics and bounds calculation
- Selection rendering in text editors

## Commit History
- Initial build compatibility fixes (Java 17, JavaFX 17)
- Text bounds and selection rendering fixes implementation
- Test application and documentation

## Authors
- Fix implemented by: GitHub Copilot
- Original repository: antipro/CodeArea
