package com.bitifyware.control.skin;

import com.bitifyware.control.CodeArea;
import com.bitifyware.control.behavior.CodeAreaBehavior;
import com.sun.javafx.scene.control.skin.Utils;
import com.sun.javafx.scene.text.FontHelper;
import com.sun.javafx.scene.text.TextLayout;
import com.sun.javafx.tk.Toolkit;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.binding.IntegerBinding;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableIntegerValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.*;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import javafx.util.Duration;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import static com.sun.javafx.PlatformUtil.isMac;
import static com.sun.javafx.PlatformUtil.isWindows;

/**
 * @author antipro
 */
@SuppressWarnings("FieldCanBeLocal")
public class CodeAreaSkin extends CodeInputControlSkin<CodeArea> {

    /* ************************************************************************
     *
     * Static fields
     *
     **************************************************************************/

    /** A shared helper object, used only by downLines(). */
    private static final Path tmpCaretPath = new Path();
    private static TextLayout layout;



    /* ************************************************************************
     *
     * Private fields
     *
     **************************************************************************/

    final private CodeArea codeArea;

    // *** NOTE: Multiple node mode is not yet fully implemented *** //
    private static final boolean USE_MULTIPLE_NODES = true;

    private final CodeAreaBehavior behavior;

    private double computedMinWidth = Double.NEGATIVE_INFINITY;
    private double computedMinHeight = Double.NEGATIVE_INFINITY;
    private double computedPrefWidth = Double.NEGATIVE_INFINITY;
    private double computedPrefHeight = Double.NEGATIVE_INFINITY;
    private double widthForComputedPrefHeight = Double.NEGATIVE_INFINITY;
    private double characterWidth;
    private double lineHeight;

    private ContentView contentView = new ContentView();
    private final VBox lineNoBar = new VBox();
    private Group paragraphNodes = new Group();

    private Text promptNode;
    private ObservableBooleanValue usePromptText;

    private ObservableIntegerValue caretPosition;
    private Group selectionHighlightGroup = new Group();

    private ScrollPane scrollPane;
    private Bounds oldViewportBounds;

    private VerticalDirection scrollDirection = null;

    private Path characterBoundingPath = new Path();

    private Timeline scrollSelectionTimeline = new Timeline();
    private EventHandler<ActionEvent> scrollSelectionHandler = event -> {
        switch (scrollDirection) {
            case UP: {
                // TODO Get previous offset
                break;
            }

            case DOWN: {
                // TODO Get next offset
                break;
            }
        }
    };

    private double pressX, pressY; // For dragging handles on embedded
    private boolean handlePressed;

    private EventHandler<ScrollEvent> scrollEventFilter;

    /**
     * Remembers horizontal position when traversing up / down.
     */
    double targetCaretX = -1;



    /* ************************************************************************
     *
     * Constructors
     *
     **************************************************************************/

    /**
     * Creates a new CodeAreaSkin instance, installing the necessary child
     * nodes into the Control {@link Control#getChildren() children} list, as
     * well as the necessary input mappings for handling key, mouse, etc events.
     *
     * @param control The control that this skin should be installed onto.
     */
    public CodeAreaSkin(final CodeArea control) {
        super(control);

        // install default input map for the text area control
        this.behavior = new CodeAreaBehavior(control);
        this.behavior.setCodeAreaSkin(this);
//        control.setInputMap(behavior.getInputMap());
        this.paragraphNodes.getStyleClass().add("paragraph-nodes");
        this.codeArea = control;

        caretPosition = new IntegerBinding() {
            { bind(control.caretPositionProperty()); }
            @Override protected int computeValue() {
                return control.getCaretPosition();
            }
        };
        caretPosition.addListener((observable, oldValue, newValue) -> {
            targetCaretX = -1;
            if (control.getWidth() > 0) {
                setForwardBias(true);
            }
        });

        forwardBiasProperty().addListener(observable -> {
            if (control.getWidth() > 0) {
                Text textNode = (Text)((TextFlow) paragraphNodes.getChildren().getFirst()).getChildren().getFirst();
                updateTextNodeCaretPos(control.getCaretPosition(), textNode);
            }
        });

//        setManaged(false);

        // Initialize content
        scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(control.isWrapText());
        HBox hBox = new HBox();
        lineNoBar.setPadding(new Insets(6, 0, 10, 0));
        lineNoBar.setAlignment(Pos.TOP_RIGHT);
        ResourceBundle bundle = ResourceBundle.getBundle("lang", Locale.getDefault());

        MenuItem miToggleWrap = new MenuItem(
                codeArea.isWrapText() ? bundle.getString("CodeArea.Unwrap") : bundle.getString("CodeArea.Wrap")
        );
        miToggleWrap.setOnAction(event -> {
            codeArea.setWrapText(!codeArea.isWrapText());
        });
        ContextMenu contextMenu = new ContextMenu(miToggleWrap);
        lineNoBar.setOnContextMenuRequested(event -> {
            miToggleWrap.setText(
                    codeArea.isWrapText() ? bundle.getString("CodeArea.Unwrap") : bundle.getString("CodeArea.Wrap")
            );
            contextMenu.show(lineNoBar, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        hBox.getChildren().addAll(lineNoBar, contentView);
        HBox.setHgrow(contentView, Priority.ALWAYS);
        scrollPane.setContent(hBox);
        getChildren().add(scrollPane);

        scrollEventFilter = event -> {
            if (event.isDirect() && handlePressed) {
                event.consume();
            }
        };
        getSkinnable().addEventFilter(ScrollEvent.ANY, scrollEventFilter);

        // Add selection
        selectionHighlightGroup.setManaged(false);
        selectionHighlightGroup.setVisible(false);
        contentView.getChildren().add(selectionHighlightGroup);

        // Add content view
        paragraphNodes.setManaged(false);
        contentView.getChildren().add(paragraphNodes);

        // Add caret
        caretPath.setManaged(false);
        caretPath.setStrokeWidth(1);
        caretPath.fillProperty().bind(textFillProperty());
        caretPath.strokeProperty().bind(textFillProperty());
        // modifying visibility of the caret forces a layout-pass (RT-32373), so
        // instead we modify the opacity.
        caretPath.opacityProperty().bind(new DoubleBinding() {
            { bind(caretVisibleProperty()); }
            @Override protected double computeValue() {
                return caretVisibleProperty().get() ? 1.0 : 0.0;
            }
        });
        contentView.getChildren().add(caretPath);

        if (SHOW_HANDLES) {
            contentView.getChildren().addAll(caretHandle, selectionHandle1, selectionHandle2);
        }

        scrollPane.hvalueProperty().addListener((observable, oldValue, newValue) -> {
            getSkinnable().setScrollLeft(newValue.doubleValue() * getScrollLeftMax());
        });

        scrollPane.vvalueProperty().addListener((observable, oldValue, newValue) -> {
            getSkinnable().setScrollTop(newValue.doubleValue() * getScrollTopMax());
        });

        // Initialize the scroll selection timeline
        scrollSelectionTimeline.setCycleCount(Timeline.INDEFINITE);
        List<KeyFrame> scrollSelectionFrames = scrollSelectionTimeline.getKeyFrames();
        scrollSelectionFrames.clear();
        scrollSelectionFrames.add(new KeyFrame(Duration.millis(350), scrollSelectionHandler));

        // Add initial text content
        for (int i = 0, n = USE_MULTIPLE_NODES ? control.getParagraphs().size() : 1; i < n; i++) {
            CharSequence paragraph = (n == 1) ? control.textProperty().getValueSafe() : control.getParagraphs().get(i);
            addParagraphNode(i, paragraph.toString());
        }

        registerChangeListener(control.selectionProperty(), e -> {
            // TODO Why do we need two calls here?
            control.requestLayout();
            contentView.requestLayout();
        });

        registerChangeListener(control.syntaxHighlighterProperty(), e -> {
            contentView.requestLayout();
        });

        registerChangeListener(control.wrapTextProperty(), e -> {
            invalidateMetrics();
            scrollPane.setFitToWidth(control.isWrapText());
        });

        registerChangeListener(control.prefColumnCountProperty(), e -> {
            invalidateMetrics();
            updatePrefViewportWidth();
        });

        registerChangeListener(control.prefRowCountProperty(), e -> {
            invalidateMetrics();
            updatePrefViewportHeight();
        });

        updateFontMetrics();
        fontMetrics.addListener(valueModel -> {
            updateFontMetrics();
        });

        contentView.paddingProperty().addListener(valueModel -> {
            updatePrefViewportWidth();
            updatePrefViewportHeight();
        });

        scrollPane.viewportBoundsProperty().addListener(valueModel -> {
            if (scrollPane.getViewportBounds() != null) {
                // ScrollPane creates a new Bounds instance for each
                // layout pass, so we need to check if the width/height
                // have really changed to avoid infinite layout requests.
                Bounds newViewportBounds = scrollPane.getViewportBounds();
                if (oldViewportBounds == null ||
                        oldViewportBounds.getWidth() != newViewportBounds.getWidth() ||
                        oldViewportBounds.getHeight() != newViewportBounds.getHeight()) {

                    invalidateMetrics();
                    oldViewportBounds = newViewportBounds;
                    contentView.requestLayout();
                }
            }
        });

        registerChangeListener(control.scrollTopProperty(), e -> {
            double newValue = control.getScrollTop();
            double vValue = (newValue < getScrollTopMax())
                    ? (newValue / getScrollTopMax()) : 1.0;
            scrollPane.setVvalue(vValue);
        });

        registerChangeListener(control.scrollLeftProperty(), e -> {
            double newValue = control.getScrollLeft();
            double hValue = (newValue < getScrollLeftMax())
                    ? (newValue / getScrollLeftMax()) : 1.0;
            scrollPane.setHvalue(hValue);
        });

        control.getErrorPosList().addListener((ListChangeListener<Integer>) change -> {
            contentView.requestLayout();
        });

        if (USE_MULTIPLE_NODES) {
            registerListChangeListener(control.getParagraphs(), change -> {
                while (change.next()) {
                    int from = change.getFrom();
                    int to = change.getTo();
                    List<? extends CharSequence> removed = (List<? extends CharSequence>) change.getRemoved();
                    if (from < to) {

                        if (removed.isEmpty()) {
                            // This is an add
                            for (int i = from, n = to; i < n; i++) {
                                addParagraphNode(i, change.getList().get(i).toString());
                            }
                        } else {
//                            if (from < paragraphNodes.getChildren().size() - 1
//                                    || to >= paragraphNodes.getChildren().size()) {
//                                return;
//                            }
//                            List<TextFlow> removedNodes = new ArrayList<>();
                            // This is an update
                            for (int i = from, n = to; i < n; i++) {
                                TextFlow paragraphNode = (TextFlow) paragraphNodes.getChildren().get(i);
                                paragraphNode.getChildren().clear();
                                String string = change.getList().get(i).toString();
                                List<Text> texts = codeArea.getSyntaxHighlighter().decompose(
                                        string,
                                        codeArea.tabSizeProperty(),
                                        (observable, oldValue, newValue) -> {
                                            invalidateMetrics();
                                            updateFontMetrics();
                                        },
                                        codeArea.fontProperty(),
                                        highlightTextFillProperty()
                                );
//                                if (texts.isEmpty()) {
//                                    removedNodes.add(paragraphNode);
//                                    continue;
//                                }
                                paragraphNode.getChildren().addAll(texts);
//                                Text paragraphNode = (Text) textFlow.getChildren().get(0);
//                                paragraphNode.setText(change.getList().get(i).toString());
//                                Node node = paragraphNodes.getChildren().get(i);
//                                Text paragraphNode = (Text) node;
//                                paragraphNode.setText(change.getList().get(i).toString());
                            }
//                            paragraphNodes.getChildren().removeAll(removedNodes);
                        }
                    } else {
                        // This is a remove
                        paragraphNodes.getChildren().subList(from, from + removed.size()).clear();
                    }
                }
            });
        } else {
            registerInvalidationListener(control.textProperty(), e -> {
                invalidateMetrics();
                ((Text)paragraphNodes.getChildren().getFirst()).setText(control.textProperty().getValueSafe());
                contentView.requestLayout();
            });
        }

        usePromptText = new BooleanBinding() {
            { bind(control.textProperty(), control.promptTextProperty()); }
            @Override protected boolean computeValue() {
                String txt = control.getText();
                String promptTxt = control.getPromptText();
                return ((txt == null || txt.isEmpty()) &&
                        promptTxt != null && !promptTxt.isEmpty());
            }
        };

        if (usePromptText.get()) {
            createPromptNode();
        }

        registerInvalidationListener(usePromptText, e -> {
            createPromptNode();
            control.requestLayout();
        });

        updateHighlightFill();
        updatePrefViewportWidth();
        updatePrefViewportHeight();
        if (control.isFocused()) setCaretAnimating(true);

        if (SHOW_HANDLES) {
            selectionHandle1.setRotate(180);

            EventHandler<MouseEvent> handlePressHandler = e -> {
                pressX = e.getX();
                pressY = e.getY();
                handlePressed = true;
                e.consume();
            };

            EventHandler<MouseEvent> handleReleaseHandler = event -> {
                handlePressed = false;
            };

            caretHandle.setOnMousePressed(handlePressHandler);
            selectionHandle1.setOnMousePressed(handlePressHandler);
            selectionHandle2.setOnMousePressed(handlePressHandler);

            caretHandle.setOnMouseReleased(handleReleaseHandler);
            selectionHandle1.setOnMouseReleased(handleReleaseHandler);
            selectionHandle2.setOnMouseReleased(handleReleaseHandler);

            caretHandle.setOnMouseDragged(e -> {
                Text textNode = getTextNode(e.getX(), e.getY());
                Point2D tp = textNode.localToScene(0, 0);
                Point2D p = new Point2D(e.getSceneX() - tp.getX() - pressX + caretHandle.getWidth() / 2,
                        e.getSceneY() - tp.getY() - pressY - 6);
                HitInfo hit = textNode.hitTest(translateCaretPosition(p));
                GlobalHitInfo myHit = new GlobalHitInfo(hit.getCharIndex(), hit.getInsertionIndex(), hit.isLeading(), textNode);
                positionCaret(myHit, false);
                e.consume();
            });

            selectionHandle1.setOnMouseDragged(e -> {
                CodeArea control1 = getSkinnable();
                Text textNode = getTextNode(e.getX(), e.getY());
                Point2D tp = textNode.localToScene(0, 0);
                Point2D p = new Point2D(e.getSceneX() - tp.getX() - pressX + selectionHandle1.getWidth() / 2,
                        e.getSceneY() - tp.getY() - pressY + selectionHandle1.getHeight() + 5);
                HitInfo hit = textNode.hitTest(translateCaretPosition(p));
                if (control1.getAnchor() < control1.getCaretPosition()) {
                    // Swap caret and anchor
                    control1.selectRange(control1.getCaretPosition(), control1.getAnchor());
                }
                int pos = hit.getCharIndex();
                if (pos > 0) {
                    if (pos >= control1.getAnchor()) {
                        pos = control1.getAnchor();
                    }
                }
                GlobalHitInfo myHit = new GlobalHitInfo(hit.getCharIndex(), hit.getInsertionIndex(), hit.isLeading(), textNode);
                positionCaret(myHit, true);
                e.consume();
            });

            selectionHandle2.setOnMouseDragged(e -> {
                CodeArea control1 = getSkinnable();
                Text textNode = getTextNode(e.getX(), e.getY());
                Point2D tp = textNode.localToScene(0, 0);
                Point2D p = new Point2D(e.getSceneX() - tp.getX() - pressX + selectionHandle2.getWidth() / 2,
                        e.getSceneY() - tp.getY() - pressY - 6);
                HitInfo hit = textNode.hitTest(translateCaretPosition(p));
                if (control1.getAnchor() > control1.getCaretPosition()) {
                    // Swap caret and anchor
                    control1.selectRange(control1.getCaretPosition(), control1.getAnchor());
                }
                int pos = hit.getCharIndex();
                if (pos > 0) {
                    if (pos <= control1.getAnchor() + 1) {
                        pos = Math.min(control1.getAnchor() + 2, control1.getLength());
                    }
                    GlobalHitInfo myHit = new GlobalHitInfo(hit.getCharIndex(), hit.getInsertionIndex(), hit.isLeading(), textNode);
                    positionCaret(myHit, true);
                }
                e.consume();
            });
        }
    }



    /* *************************************************************************
     *                                                                         *
     * Public API                                                              *
     *                                                                         *
     **************************************************************************/

    /** {@inheritDoc} */
    @Override protected void invalidateMetrics() {
        computedMinWidth = Double.NEGATIVE_INFINITY;
        computedMinHeight = Double.NEGATIVE_INFINITY;
        computedPrefWidth = Double.NEGATIVE_INFINITY;
        computedPrefHeight = Double.NEGATIVE_INFINITY;
    }

    /** {@inheritDoc} */
    @Override protected void layoutChildren(double contentX, double contentY, double contentWidth, double contentHeight) {
        scrollPane.resizeRelocate(contentX, contentY, contentWidth, contentHeight);
    }

    /** {@inheritDoc} */
    @Override protected void updateHighlightFill() {
        for (Node node : selectionHighlightGroup.getChildren()) {
            Path selectionHighlightPath = (Path)node;
            selectionHighlightPath.setFill(highlightFillProperty().get());
            selectionHighlightPath.setFillRule(FillRule.NON_ZERO);
            selectionHighlightPath.setStroke(highlightFillProperty().get());
        }
    }

    // Public for behavior
    /**
     * Performs a hit test, mapping point to index in the content.
     *
     * @param x the x coordinate of the point.
     * @param y the y coordinate of the point.
     * @return a {@code HitInfo} object describing the index and forward bias.
     */
    public GlobalHitInfo getIndex(double x, double y) {
        // adjust the event to be in the same coordinate space as the
        // text content of the textInputControl
//        Text textNode = getTextNode(x, y);
//        TextFlow textFlow = (TextFlow) textNode.getParent();
//        Point2D p = new Point2D(x - (textFlow.getLayoutX() + textNode.getLayoutX()), y - getTextTranslateY());
//        HitInfo hit = textNode.hitTest(translateCaretPosition(p));
//        return hit;
        int offset = 0;
        ObservableList<Node> paragraphNodesChildren = paragraphNodes.getChildren();
        for (int i = 0; i < paragraphNodesChildren.size(); i++) {
            TextFlow textFlow = (TextFlow) paragraphNodesChildren.get(i);
            ObservableList<Node> children = textFlow.getChildren();
            for (int j = 0; j < children.size(); j++) {
                Text text = (Text) children.get(j);
                Bounds textBounds = text.getBoundsInLocal();
                // Left Top
                if (i == 0 && j == 0 &&
                        x < textFlow.getLayoutX() &&
                        y < textFlow.getLayoutY()
                ) {
                    Point2D p = new Point2D(x - textFlow.getLayoutX(),
                            y - textFlow.getLayoutY());
                    HitInfo hit = text.hitTest(translateCaretPosition(p));

                    return new GlobalHitInfo(
                            hit.getCharIndex() + offset,
                            hit.getInsertionIndex() + offset,
                            hit.isLeading(), text);
                }
                // Middle Top
                if (i == 0 &&
                        x >= textFlow.getLayoutX() + text.getLayoutX() &&
                        x < textFlow.getLayoutX() + text.getLayoutX() + textBounds.getWidth() &&
                        y < textFlow.getLayoutY()
                ) {
                    Point2D p = new Point2D(x - textFlow.getLayoutX() - text.getLayoutX(),
                            y - textFlow.getLayoutY());
                    HitInfo hit = text.hitTest(translateCaretPosition(p));

                    return new GlobalHitInfo(
                            hit.getCharIndex() + offset,
                            hit.getInsertionIndex() + offset,
                            hit.isLeading(), text);
                }
                // Right Above
                if (i == 0 && j == children.size() - 1 &&
                        x >= textFlow.getLayoutX() + text.getLayoutX() + textBounds.getWidth() &&
                        y < textFlow.getLayoutY()
                ) {
                    Point2D p = new Point2D(x - textFlow.getLayoutX() - text.getLayoutX() - textBounds.getWidth(),
                            y - textFlow.getLayoutY());
                    HitInfo hit = text.hitTest(translateCaretPosition(p));

                    return new GlobalHitInfo(
                            hit.getCharIndex() + offset,
                            hit.getInsertionIndex() + offset,
                            hit.isLeading(), text);
                }
                // Center Left
                if (j == 0 &&
                        x < textFlow.getLayoutX() &&
                        y >= textFlow.getLayoutY() &&
                        y < textFlow.getLayoutY() + text.getLayoutY() + textBounds.getHeight()
                ) {
                    Point2D p = new Point2D(x - textFlow.getLayoutX(),
                            y - textFlow.getLayoutY());
                    HitInfo hit = text.hitTest(translateCaretPosition(p));

                    return new GlobalHitInfo(
                            hit.getCharIndex() + offset,
                            hit.getInsertionIndex() + offset,
                            hit.isLeading(), text);
                }
                // Center
                if (
                        x >= textFlow.getLayoutX() + text.getLayoutX() &&
                                x < textFlow.getLayoutX() + text.getLayoutX() + textBounds.getWidth() &&
                                y >= textFlow.getLayoutY() + text.getLayoutY() &&
                                y < textFlow.getLayoutY() + text.getLayoutY() + textBounds.getHeight()
                ) {
                    Point2D p = new Point2D(x - textFlow.getLayoutX() - text.getLayoutX(),
                            y - textFlow.getLayoutY() - text.getLayoutY());
                    HitInfo hit = text.hitTest(translateCaretPosition(p));

                    return new GlobalHitInfo(
                            hit.getCharIndex() + offset,
                            hit.getInsertionIndex() + offset,
                            hit.isLeading(), text);
                }
                // Center Right
                if (j == children.size() - 1 &&
                        x >= textFlow.getLayoutX() + text.getLayoutX() &&
                        y >= textFlow.getLayoutY() &&
                        y < textFlow.getLayoutY() + text.getLayoutY() + textBounds.getHeight()
                ) {
                    Point2D p = new Point2D(x - textFlow.getLayoutX() - text.getLayoutX(),
                            y - textFlow.getLayoutY());
                    HitInfo hit = text.hitTest(translateCaretPosition(p));

                    return new GlobalHitInfo(
                            hit.getCharIndex() + offset,
                            hit.getInsertionIndex() + offset,
                            hit.isLeading(), text);
                }
                // Bottom Left
                if (i == paragraphNodesChildren.size() - 1 && j == 0 &&
                        x < textFlow.getLayoutX() &&
                        y >= textFlow.getLayoutY() + text.getLayoutY() + textBounds.getHeight()
                ) {
                    Point2D p = new Point2D(x - textFlow.getLayoutX(),
                            y - textFlow.getLayoutY() - text.getLayoutY() - textBounds.getHeight());
                    HitInfo hit = text.hitTest(translateCaretPosition(p));

                    return new GlobalHitInfo(
                            hit.getCharIndex() + offset,
                            hit.getInsertionIndex() + offset,
                            hit.isLeading(), text);
                }
                // Bottom Center
                if (i == paragraphNodesChildren.size() - 1 &&
                        text.getLayoutY() + textBounds.getHeight() == textFlow.getLayoutY() + textFlow.getHeight() &&
                        x >= textFlow.getLayoutX() + text.getLayoutX() &&
                        x < textFlow.getLayoutX() + text.getLayoutX() + textBounds.getWidth() &&
                        y >= textFlow.getLayoutY() + text.getLayoutY() + textBounds.getHeight()
                ) {
                    Point2D p = new Point2D(x - textFlow.getLayoutX() - text.getLayoutX(),
                            y - textFlow.getLayoutY() - text.getLayoutY() - textBounds.getHeight());
                    HitInfo hit = text.hitTest(translateCaretPosition(p));

                    return new GlobalHitInfo(
                            hit.getCharIndex() + offset,
                            hit.getInsertionIndex() + offset,
                            hit.isLeading(), text);
                }
                // Bottom Right
                if (i == paragraphNodesChildren.size() - 1 && j == children.size() - 1 &&
                        x >= textFlow.getLayoutX() + text.getLayoutX() &&
                        y >= textFlow.getLayoutY() + text.getLayoutY() + textBounds.getHeight()
                ) {
                    Point2D p = new Point2D(x - textFlow.getLayoutX() - text.getLayoutX(),
                            y - textFlow.getLayoutY() - text.getLayoutY() - textBounds.getHeight());
                    HitInfo hit = text.hitTest(translateCaretPosition(p));

                    return new GlobalHitInfo(
                            hit.getCharIndex() + offset,
                            hit.getInsertionIndex() + offset,
                            hit.isLeading(), text);
                }


                offset += text.getText().length();
            }

            // There is a \n character
            offset += 1;


        }
        return null;
    }

    /** {@inheritDoc} */
    @Override public void moveCaret(TextUnit unit, Direction dir, boolean select) {
        switch (unit) {
            case CHARACTER:
                switch (dir) {
                    case LEFT:
                    case RIGHT:
                        nextCharacterVisually(dir == Direction.RIGHT);
                        break;
                    default:
                        throw new IllegalArgumentException(""+dir);
                }
                break;

            case LINE:
                switch (dir) {
                    case UP:
                        previousLine(select);
                        break;
                    case DOWN:
                        nextLine(select);
                        break;
                    case BEGINNING:
                        lineStart(select, select && isMac());
                        break;
                    case END:
                        lineEnd(select, select && isMac());
                        break;
                    default:
                        throw new IllegalArgumentException(""+dir);
                }
                break;

            case PAGE:
                switch (dir) {
                    case UP:
                        previousPage(select);
                        break;
                    case DOWN:
                        nextPage(select);
                        break;
                    default:
                        throw new IllegalArgumentException(""+dir);
                }
                break;

            case PARAGRAPH:
                switch (dir) {
                    case UP:
                        paragraphStart(true, select);
                        break;
                    case DOWN:
                        paragraphEnd(true, select);
                        break;
                    case BEGINNING:
                        paragraphStart(false, select);
                        break;
                    case END:
                        paragraphEnd(false, select);
                        break;
                    default:
                        throw new IllegalArgumentException(""+dir);
                }
                break;

            default:
                throw new IllegalArgumentException(""+unit);
        }
    }

    private void nextCharacterVisually(boolean moveRight) {
        if (isRTL()) {
            // Text node is mirrored.
            moveRight = !moveRight;
        }

        Text textNode = getTextNode(0, 0);
        Bounds caretBounds = caretPath.getLayoutBounds();
        if (caretPath.getElements().size() == 4) {
            // The caret is split
            // TODO: Find a better way to get the primary caret position
            // instead of depending on the internal implementation.
            // See RT-25465.
            caretBounds = new Path(caretPath.getElements().get(0), caretPath.getElements().get(1)).getLayoutBounds();
        }
        double hitX = moveRight ? caretBounds.getMaxX() : caretBounds.getMinX();
        double hitY = (caretBounds.getMinY() + caretBounds.getMaxY()) / 2;
        HitInfo hit = textNode.hitTest(new Point2D(hitX, hitY));
        boolean leading = hit.isLeading();
        Path charShape = new Path(textNode.rangeShape(hit.getCharIndex(), hit.getCharIndex() + 1));
        if ((moveRight && charShape.getLayoutBounds().getMaxX() > caretBounds.getMaxX()) ||
                (!moveRight && charShape.getLayoutBounds().getMinX() < caretBounds.getMinX())) {
            leading = !leading;
            positionCaret(hit.getInsertionIndex(), leading, false, false);
        } else {
            // We're at beginning or end of line. Try moving up / down.
            int dot = codeArea.getCaretPosition();
            targetCaretX = moveRight ? 0 : Double.MAX_VALUE;
            // TODO: Use Bidi sniffing instead of assuming right means forward here?
            downLines(moveRight ? 1 : -1, false, false);
            targetCaretX = -1;
            if (dot == codeArea.getCaretPosition()) {
                if (moveRight) {
                    codeArea.forward();
                } else {
                    codeArea.backward();
                }
            }
        }
    }

    private void downLines(int nLines, boolean select, boolean extendSelection) {
//        Text textNode = getTextNode(0, 0);
        // According to Caret Position get the Text Node
        Text textNode = getTextNode(caretPath.getLayoutX(), caretPath.getLayoutY());
        Bounds caretBounds = caretPath.getLayoutBounds();

        // The middle y coordinate of the the line we want to go to.
        double targetLineMidY = (caretBounds.getMinY() + caretBounds.getMaxY()) / 2 + nLines * lineHeight;
//        if (targetLineMidY < 0) {
//            targetLineMidY = 0;
//        }

        // The target x for the caret. This may have been set during a
        // previous call.
        double x = (targetCaretX >= 0) ? targetCaretX : (caretBounds.getMaxX());

        // Find a text position for the target x,y.
//        Text targetTextNode = getTextNode(caretPath.getLayoutX() + x, caretPath.getLayoutY() + targetLineMidY);
        GlobalHitInfo hit = getIndex(caretPath.getLayoutX() + x, caretPath.getLayoutY() + targetLineMidY);
        if (hit == null) {
            return;
        }
//        int pos = hit.getCharIndex();
        Text targetTextNode = hit.getTextNode();
        // Save the old pos temporarily while testing the new one.
//        int oldPos = textNode.getCaretPosition();
//        boolean oldBias = textNode.isCaretBias();
//        targetTextNode.setCaretBias(hit.isLeading());
//        targetTextNode.setCaretPosition(pos);
        tmpCaretPath.getElements().clear();
        tmpCaretPath.getElements().addAll(targetTextNode.getCaretShape());
        tmpCaretPath.setLayoutX(targetTextNode.getLayoutX());
        tmpCaretPath.setLayoutY(targetTextNode.getLayoutY());
//        Bounds tmpCaretBounds = tmpCaretPath.getLayoutBounds();
        // The y for the middle of the row we found.
//        double foundLineMidY = (tmpCaretBounds.getMinY() + tmpCaretBounds.getMaxY()) / 2;
//        targetTextNode.setCaretBias(oldBias);
//        targetTextNode.setCaretPosition(oldPos);

        // Test if the found line is in the correct direction and move
        // the caret.
//        if (nLines == 0 ||
//                (nLines > 0 && foundLineMidY > caretBounds.getMaxY()) ||
//                (nLines < 0 && foundLineMidY < caretBounds.getMinY())) {

            positionCaret(hit.getInsertionIndex(), hit.isLeading(), select, extendSelection);
            targetCaretX = x;
//        }
    }

    private void previousLine(boolean select) {
        downLines(-1, select, false);
    }

    private void nextLine(boolean select) {
        downLines(1, select, false);
    }

    private void previousPage(boolean select) {
        downLines(-(int)(scrollPane.getViewportBounds().getHeight() / lineHeight),
                select, false);
    }

    private void nextPage(boolean select) {
        downLines((int)(scrollPane.getViewportBounds().getHeight() / lineHeight),
                select, false);
    }

    private void lineStart(boolean select, boolean extendSelection) {
        targetCaretX = 0;
        downLines(0, select, extendSelection);
        targetCaretX = -1;
    }

    private void lineEnd(boolean select, boolean extendSelection) {
        targetCaretX = Double.MAX_VALUE;
        downLines(0, select, extendSelection);
        targetCaretX = -1;
    }


    private void paragraphStart(boolean previousIfAtStart, boolean select) {
        CodeArea codeArea = getSkinnable();
        String text = codeArea.textProperty().getValueSafe();
        int pos = codeArea.getCaretPosition();

        if (pos > 0) {
            if (previousIfAtStart && text.codePointAt(pos-1) == 0x0a) {
                // We are at the beginning of a paragraph.
                // Back up to the previous paragraph.
                pos--;
            }
            // Back up to the beginning of this paragraph
            while (pos > 0 && text.codePointAt(pos-1) != 0x0a) {
                pos--;
            }
            if (select) {
                codeArea.selectPositionCaret(pos);
            } else {
                codeArea.positionCaret(pos);
                setForwardBias(true);
            }
        }
    }

    private void paragraphEnd(boolean goPastInitialNewline, boolean select) {
        CodeArea codeArea = getSkinnable();
        String text = codeArea.textProperty().getValueSafe();
        int pos = codeArea.getCaretPosition();
        int len = text.length();
        boolean wentPastInitialNewline = false;
        boolean goPastTrailingNewline = isWindows();

        if (pos < len) {
            if (goPastInitialNewline && text.codePointAt(pos) == 0x0a) {
                // We are at the end of a paragraph, start by moving to the
                // next paragraph.
                pos++;
                wentPastInitialNewline = true;
            }
            if (!(goPastTrailingNewline && wentPastInitialNewline)) {
                // Go to the end of this paragraph
                while (pos < len && text.codePointAt(pos) != 0x0a) {
                    pos++;
                }
                if (goPastTrailingNewline && pos < len) {
                    // We are at the end of a paragraph, finish by moving to
                    // the beginning of the next paragraph (Windows behavior).
                    pos++;
                }
            }
            if (select) {
                codeArea.selectPositionCaret(pos);
            } else {
                codeArea.positionCaret(pos);
            }
        }
    }

    /** {@inheritDoc} */
    @Override protected PathElement[] getUnderlineShape(int start, int end) {
        int pStart = 0;
        for (Node node : paragraphNodes.getChildren()) {
            // Need to Locate Text
            TextFlow textFlow = (TextFlow)node;
            for (Node child : textFlow.getChildren()) {
                Text text = (Text) child;
                int pEnd = pStart + text.textProperty().getValueSafe().length();
                if (pEnd >= start) {
                    return text.underlineShape(start - pStart, end - pStart);
                }
                pStart = pEnd;
            }
            pStart += 1;
        }
        return null;
//        int pStart = 0;
//        for (Node node : paragraphNodes.getChildren()) {
//            Text p = (Text)node;
//            int pEnd = pStart + p.textProperty().getValueSafe().length();
//            if (pEnd >= start) {
//                return p.underlineShape(start - pStart, end - pStart);
//            }
//            pStart = pEnd + 1;
//        }
//        return null;
    }

    /** {@inheritDoc} */
    @Override protected PathElement[] getRangeShape(int start, int end) {
        int pStart = 0;
        for (Node node : paragraphNodes.getChildren()) {
            Text p = (Text)node;
            int pEnd = pStart + p.textProperty().getValueSafe().length();
            if (pEnd >= start) {
                return p.rangeShape(start - pStart, end - pStart);
            }
            pStart = pEnd + 1;
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override protected void addHighlight(List<? extends Node> nodes, int start) {
        int pStart = 0;
        Text textNode = null;
//        for (Node node : paragraphNodes.getChildren()) {
//            Text p = (Text)node;
//            int pEnd = pStart + p.textProperty().getValueSafe().length();
//            if (pEnd >= start) {
//                textNode = p;
//                break;
//            }
//            pStart = pEnd + 1;
//        }
        TextFlow textFlow = null;
        boolean found = false;

        /* ************************************************************************
         * Although the XY position is calculated after layoutChildren.
         * But here I found them are all 0. So I have to calculate them again.
         * Strange. the XY position of TextFlow is still exist.
         **************************************************************************/
        double subX = 0;
        double subY = 0;
        double oneLineHeight = 0;
        final double leftPadding = snappedLeftInset();
        double width = contentView.getWidth();
        double wrappingWidth = Math.max(width - (leftPadding + snappedRightInset()), 0);

        for (Node node : paragraphNodes.getChildren()) {
            textFlow = (TextFlow)node;
            subX = 0;
            subY = 0;
            ObservableList<Node> children = textFlow.getChildren();
            for (int i = 0; i < children.size(); i++) {
                textNode = (Text) children.get(i);

                if (oneLineHeight == 0) {
                    oneLineHeight = Utils.computeTextHeight(textNode.getFont(), "A", 0, textNode.getBoundsType());
                }
                double unwrapWidth = Utils.computeTextWidth(textNode.getFont(), textNode.getText(), 0);
                if (i > 0 && subX + unwrapWidth > wrappingWidth) {
                    // Not first of line and exceed the border. Move to new line
                    subY += oneLineHeight;
                    subX = 0;
                }
                textNode.setLayoutX(subX);
                textNode.setLayoutY(subY);
                if (subX + unwrapWidth > wrappingWidth) {
                    // Single Text Node exceeds wrapping width
                    textNode.setWrappingWidth(wrappingWidth);
                    subX = 0;
                    subY += textNode.getBoundsInParent().getHeight();
                } else {
                    textNode.setWrappingWidth(0);
                    subX += unwrapWidth;
                }

                int pEnd = pStart + textNode.textProperty().getValueSafe().length();
                if (pEnd >= start) {
                    found = true;
                    break;
                }
                pStart = pEnd;
            }
            if (found) {
                break;
            }
            pStart += 1;
        }
        if (textFlow != null && textNode != null) {
//            System.out.println("Selected: " + textNode.getText());
//            System.out.println("textFlow Position: " + textFlow.getLayoutX() + ", " + textFlow.getLayoutY());
//            System.out.println("textNode Position: " + subX + ", " + subY);
//            System.out.println();
            for (Node node : nodes) {
                node.setLayoutX(textFlow.getLayoutX() + subX);
                node.setLayoutY(textFlow.getLayoutY() + subY);
            }
        }
        contentView.getChildren().addAll(nodes);
    }

    /** {@inheritDoc} */
    @Override protected void removeHighlight(List<? extends Node> nodes) {
        contentView.getChildren().removeAll(nodes);
    }

    /** {@inheritDoc} */
    @Override public Point2D getMenuPosition() {
        contentView.layoutChildren();
        Point2D p = super.getMenuPosition();
        if (p != null) {
            p = new Point2D(Math.max(0, p.getX() - contentView.snappedLeftInset() - getSkinnable().getScrollLeft()),
                    Math.max(0, p.getY() - contentView.snappedTopInset() - getSkinnable().getScrollTop()));
        }
        return p;
    }

    // Public for FXVKSkin
    /**
     * Gets the {@code Bounds} of the caret of the skinned {@code CodeArea}.
     * @return the {@code Bounds} of the caret shape, relative to the {@code CodeArea}.
     */
    public Bounds getCaretBounds() {
        return getSkinnable().sceneToLocal(caretPath.localToScene(caretPath.getBoundsInLocal()));
    }

    /** {@inheritDoc} */
    @Override protected Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        switch (attribute) {
            case LINE_FOR_OFFSET:
            case LINE_START:
            case LINE_END:
            case BOUNDS_FOR_RANGE:
            case OFFSET_AT_POINT:
                Text text = getTextNode(0, 0);
                return text.queryAccessibleAttribute(attribute, parameters);
            default: return super.queryAccessibleAttribute(attribute, parameters);
        }
    }

    /** {@inheritDoc} */
    @Override public void dispose() {
        if (getSkinnable() == null) return;
        getSkinnable().removeEventFilter(ScrollEvent.ANY, scrollEventFilter);
        getChildren().remove(scrollPane);
        super.dispose();

        if (behavior != null) {
            behavior.dispose();
        }
    }

    /** {@inheritDoc} */
    @Override public double computeBaselineOffset(double topInset, double rightInset, double bottomInset, double leftInset) {
        TextFlow firstFlow = (TextFlow) paragraphNodes.getChildren().getFirst();
        Text firstParagraph = (Text) firstFlow.getChildren().getFirst();
        return Utils.getAscent(getSkinnable().getFont(), firstParagraph.getBoundsType())
                + contentView.snappedTopInset() + codeArea.snappedTopInset();
    }

    private char getCharacter(int index) {
        int n = paragraphNodes.getChildren().size();

        int paragraphIndex = 0;
        int offset = index;

        String paragraph = null;
        while (paragraphIndex < n) {
            TextFlow textFlow = (TextFlow)paragraphNodes.getChildren().get(paragraphIndex);
            for (Node child : textFlow.getChildren()) {
                Text text = (Text) child;
                paragraph = text.getText();
                int count = paragraph.length() + 1;

                if (offset < count) {
                    break;
                }

                offset -= count;
            }
//            Text paragraphNode = (Text)paragraphNodes.getChildren().get(paragraphIndex);
//            paragraph = paragraphNode.getText();
//            int count = paragraph.length() + 1;
//
//            if (offset < count) {
//                break;
//            }
//
//            offset -= count;
            paragraphIndex++;
        }

        return offset == paragraph.length() ? '\n' : paragraph.charAt(offset);
    }

    /** {@inheritDoc} */
    @Override protected int getInsertionPoint(double x, double y) {
        CodeArea codeArea = getSkinnable();

        int n = paragraphNodes.getChildren().size();
        int index = -1;

        if (n > 0) {
            if (y < contentView.snappedTopInset()) {
                // Select the character at x in the first row
//                Text paragraphNode = (Text)paragraphNodes.getChildren().getFirst();
                Text paragraphNode = getTextNode(x, y);
                index = getNextInsertionPoint(paragraphNode, x, -1, VerticalDirection.DOWN);
            } else if (y > contentView.snappedTopInset() + contentView.getHeight()) {
                // Select the character at x in the last row
                int lastParagraphIndex = n - 1;
//                Text lastParagraphView = (Text)paragraphNodes.getChildren().get(lastParagraphIndex);
                Text lastParagraphView = getTextNode(x, y);
                index = getNextInsertionPoint(lastParagraphView, x, -1, VerticalDirection.UP)
                        + (codeArea.getLength() - lastParagraphView.getText().length());
            } else {
                // Select the character at x in the row at y
                int paragraphOffset = 0;
                for (int i = 0; i < n; i++) {
//                    Text paragraphNode = (Text)paragraphNodes.getChildren().get(i);
                    Text paragraphNode = getTextNode(x, y);
                    Bounds bounds = paragraphNode.getBoundsInLocal();
                    double paragraphViewY = paragraphNode.getLayoutY() + bounds.getMinY();
                    if (y >= paragraphViewY
                            && y < paragraphViewY + paragraphNode.getBoundsInLocal().getHeight()) {
                        index = getInsertionPoint(paragraphNode,
                                x - paragraphNode.getLayoutX(),
                                y - paragraphNode.getLayoutY()) + paragraphOffset;
                        break;
                    }

                    paragraphOffset += paragraphNode.getText().length() + 1;
                }
            }
        }

        return index;
    }

    // Public for behavior
    /**
     * Moves the caret to the specified position.
     *
     * @param hit the new position and forward bias of the caret.
     * @param select whether to extend selection to the new position.
     */
    public void positionCaret(GlobalHitInfo hit, boolean select) {
        if (hit == null) {
            return;
        }
        positionCaret(hit.getInsertionIndex(), hit.isLeading(), select, false);
    }

    private void positionCaret(int pos, boolean leading, boolean select, boolean extendSelection) {
        boolean isNewLine =
                (pos > 0 &&
                        pos <= getSkinnable().getLength() &&
                        getSkinnable().getText().codePointAt(pos-1) == 0x0a);

        // special handling for a new line
        if (!leading && isNewLine) {
            leading = true;
            pos -= 1;
        }

        if (select) {
            if (extendSelection) {
                getSkinnable().extendSelection(pos);
            } else {
                getSkinnable().selectPositionCaret(pos);
            }
        } else {
            getSkinnable().positionCaret(pos);
        }

        setForwardBias(leading);
    }

    /** {@inheritDoc} */
    @Override public Rectangle2D getCharacterBounds(int index) {
        CodeArea codeArea = getSkinnable();

        int paragraphIndex = paragraphNodes.getChildren().size();
        int paragraphOffset = codeArea.getLength() + 1;

        Text paragraphNode = null;
        TextFlow textFlow;
        do {
            textFlow = (TextFlow) paragraphNodes.getChildren().get(--paragraphIndex);
            ObservableList<Node> children = textFlow.getChildren();
            for (int i = children.size() - 1; i >= 0; i--) {
                paragraphNode = (Text) children.get(i);
                paragraphOffset -= paragraphNode.getText().length();
                if (index >= paragraphOffset) {
                    break;
                }
            }
            paragraphOffset--;
//            paragraphNode = (Text)paragraphNodes.getChildren().get(--paragraphIndex);
//            paragraphOffset -= paragraphNode.getText().length() + 1;
        } while (index < paragraphOffset);

        int characterIndex = index - paragraphOffset;
        boolean terminator = false;

        if (characterIndex == paragraphNode.getText().length()) {
            characterIndex--;
            terminator = true;
        }

        characterBoundingPath.getElements().clear();
        characterBoundingPath.getElements().addAll(paragraphNode.rangeShape(characterIndex, characterIndex + 1));
        characterBoundingPath.setLayoutX(textFlow.getLayoutX() + paragraphNode.getLayoutX());
        characterBoundingPath.setLayoutY(textFlow.getLayoutY() + paragraphNode.getLayoutY());

        Bounds bounds = characterBoundingPath.getBoundsInParent();

        double x = bounds.getMinX() - codeArea.getScrollLeft();
        double y = bounds.getMinY() - codeArea.getScrollTop();

        // Sometimes the bounds is empty, in which case we must ignore the width/height
        double width = bounds.isEmpty() ? 0 : bounds.getWidth();
        double height = bounds.isEmpty() ? 0 : bounds.getHeight();

        if (terminator) {
            x += width;
            width = 0;
        }

        return new Rectangle2D(x, y, width, height);
    }

    /** {@inheritDoc} */
    @Override protected void scrollCharacterToVisible(final int index) {
        // TODO We queue a callback because when characters are added or
        // removed the bounds are not immediately updated; is this really
        // necessary?

        Platform.runLater(() -> {
            if (getSkinnable().getLength() == 0) {
                return;
            }
            Rectangle2D characterBounds = getCharacterBounds(index);
            scrollBoundsToVisible(characterBounds);
        });
    }



    /* ************************************************************************
     *
     * Private implementation
     *
     **************************************************************************/

    @Override
    CodeAreaBehavior getBehavior() {
        return behavior;
    }

    private void createPromptNode() {
        if (promptNode == null && usePromptText.get()) {
            promptNode = new Text();
            contentView.getChildren().addFirst(promptNode);
            promptNode.setManaged(false);
            promptNode.getStyleClass().add("text");
            promptNode.visibleProperty().bind(usePromptText);
            promptNode.fontProperty().bind(getSkinnable().fontProperty());
            promptNode.textProperty().bind(getSkinnable().promptTextProperty());
            promptNode.fillProperty().bind(promptTextFillProperty());
        }
    }

    private void addParagraphNode(int i, String string) {
        final CodeArea codeArea = getSkinnable();
//        Text paragraphNode = new Text(string);
//        paragraphNode.setTextOrigin(VPos.TOP);
//        paragraphNode.setManaged(false);
//        paragraphNode.getStyleClass().add("text");
//        paragraphNode.boundsTypeProperty().addListener((observable, oldValue, newValue) -> {
//            invalidateMetrics();
//            updateFontMetrics();
//        });
//        paragraphNodes.getChildren().add(i, paragraphNode);
//
//        paragraphNode.fontProperty().bind(codeArea.fontProperty());
//        paragraphNode.fillProperty().bind(textFillProperty());
//        paragraphNode.selectionFillProperty().bind(highlightTextFillProperty());
        TextFlow paragraphNode = new TextFlow();

        List<Text> texts = codeArea.getSyntaxHighlighter().decompose(
                string,
                codeArea.tabSizeProperty(),
                (observable, oldValue, newValue) -> {
                    invalidateMetrics();
                    updateFontMetrics();
                },
                codeArea.fontProperty(),
                highlightTextFillProperty()
        );
        paragraphNode.getChildren().addAll(texts);
        if (paragraphNode.getChildren().isEmpty()) {
            return;
        }
        paragraphNodes.getChildren().add(i, paragraphNode);
    }

    private double getScrollTopMax() {
        return Math.max(0, contentView.getHeight() - scrollPane.getViewportBounds().getHeight());
    }

    private double getScrollLeftMax() {
        return Math.max(0, contentView.getWidth() - scrollPane.getViewportBounds().getWidth());
    }

    private int getInsertionPoint(Text paragraphNode, double x, double y) {
        HitInfo hitInfo = paragraphNode.hitTest(new Point2D(x, y));
        return hitInfo.getInsertionIndex();
    }

    private int getNextInsertionPoint(Text paragraphNode, double x, int from,
                                      VerticalDirection scrollDirection) {
        // TODO
        return 0;
    }

    private void scrollCaretToVisible() {
        CodeArea codeArea = getSkinnable();
//        Bounds bounds = caretPath.getLayoutBounds();
        Bounds bounds = caretPath.getBoundsInParent();
        double x = bounds.getMinX() - codeArea.getScrollLeft();
        double y = bounds.getMinY() - codeArea.getScrollTop();
        double w = bounds.getWidth();
        double h = bounds.getHeight();

        if (SHOW_HANDLES) {
            if (caretHandle.isVisible()) {
                h += caretHandle.getHeight();
            } else if (selectionHandle1.isVisible() && selectionHandle2.isVisible()) {
                x -= selectionHandle1.getWidth() / 2;
                y -= selectionHandle1.getHeight();
                w += selectionHandle1.getWidth() / 2 + selectionHandle2.getWidth() / 2;
                h += selectionHandle1.getHeight() + selectionHandle2.getHeight();
            }
        }

        if (w > 0 && h > 0) {
            scrollBoundsToVisible(new Rectangle2D(x, y, w, h));
        }
    }

    private void scrollBoundsToVisible(Rectangle2D bounds) {
        CodeArea codeArea = getSkinnable();
        Bounds viewportBounds = scrollPane.getViewportBounds();

        double viewportWidth = viewportBounds.getWidth();
        double viewportHeight = viewportBounds.getHeight();
        double scrollTop = codeArea.getScrollTop();
        double scrollLeft = codeArea.getScrollLeft();
        double slop = 6.0;

        if (bounds.getMinY() < 0) {
            double y = scrollTop + bounds.getMinY();
            if (y <= contentView.snappedTopInset()) {
                y = 0;
            }
            codeArea.setScrollTop(y);
        } else if (contentView.snappedTopInset() + bounds.getMaxY() > viewportHeight) {
            double y = scrollTop + contentView.snappedTopInset() + bounds.getMaxY() - viewportHeight;
            if (y >= getScrollTopMax() - contentView.snappedBottomInset()) {
                y = getScrollTopMax();
            }
            codeArea.setScrollTop(y);
        }


        if (bounds.getMinX() < 0) {
            double x = scrollLeft + bounds.getMinX() - slop;
            if (x <= contentView.snappedLeftInset() + slop) {
                x = 0;
            }
            codeArea.setScrollLeft(x);
        } else if (contentView.snappedLeftInset() + bounds.getMaxX() > viewportWidth) {
            double x = scrollLeft + contentView.snappedLeftInset() + bounds.getMaxX() - viewportWidth + slop;
            if (x >= getScrollLeftMax() - contentView.snappedRightInset() - slop) {
                x = getScrollLeftMax();
            }
            codeArea.setScrollLeft(x);
        }
    }

    private void updatePrefViewportWidth() {
        int columnCount = getSkinnable().getPrefColumnCount();
        scrollPane.setPrefViewportWidth(columnCount * characterWidth + contentView.snappedLeftInset() + contentView.snappedRightInset());
        scrollPane.setMinViewportWidth(characterWidth + contentView.snappedLeftInset() + contentView.snappedRightInset());
    }

    private void updatePrefViewportHeight() {
        int rowCount = getSkinnable().getPrefRowCount();
        scrollPane.setPrefViewportHeight(rowCount * lineHeight + contentView.snappedTopInset() + contentView.snappedBottomInset());
        scrollPane.setMinViewportHeight(lineHeight + contentView.snappedTopInset() + contentView.snappedBottomInset());
    }

    private void updateFontMetrics() {
        TextFlow textFlow = (TextFlow) paragraphNodes.getChildren().getFirst();
        Text firstParagraph = (Text)textFlow.getChildren().getFirst();
        lineHeight = Utils.getLineHeight(getSkinnable().getFont(), firstParagraph.getBoundsType());
        characterWidth = fontMetrics.get().getCharWidth('W');
    }

    private double getTextTranslateX() {
        return contentView.snappedLeftInset();
    }

    private double getTextTranslateY() {
        return contentView.snappedTopInset();
    }

    private double getTextLeft() {
        return 0;
    }

    private Point2D translateCaretPosition(Point2D p) {
        return p;
    }

    // package for testing only!
    Text getTextNode(double x, double y) {
//        if (USE_MULTIPLE_NODES) {
////            throw new IllegalArgumentException("Multiple node traversal is not yet implemented.");
//        }
//        return (Text)paragraphNodes.getChildren().get(0);
        for (Node child : paragraphNodes.getChildren()) {
            TextFlow textFlow = (TextFlow)child;
            for (Node textFlowChild : textFlow.getChildren()) {
                Text text = (Text)textFlowChild;
                Bounds bounds = text.getBoundsInLocal();
                if (x >= textFlow.getLayoutX() + text.getLayoutX() &&
                        x < textFlow.getLayoutX() + text.getLayoutX() + bounds.getWidth() &&
                        y >= textFlow.getLayoutY() + text.getLayoutY() &&
                        y < textFlow.getLayoutY() + text.getLayoutY() + bounds.getHeight()) {
                    return text;
                }
            }
        }
        TextFlow textFlow = (TextFlow) paragraphNodes.getChildren().getFirst();
        return (Text)textFlow.getChildren().getFirst();
    }

    private void updateTextNodeCaretPos(int pos, Text textNode) {
//        int offset = 0;
//        for (Node child : paragraphNodes.getChildren()) {
//            TextFlow textFlow = (TextFlow)child;
//            for (Node textFlowChild : textFlow.getChildren()) {
//                Text text = (Text)textFlowChild;
//                int length = text.getText().length();
//                if (pos < offset + length) {
//                    if (isForwardBias()) {
//                        text.setCaretPosition(pos - offset);
//                    } else {
//                        text.setCaretPosition(pos - offset - 1);
//                    }
//                    text.caretBiasProperty().set(isForwardBias());
//                    for (PathElement pathElement : text.getCaretShape()) {
//                        System.out.println("path element: " + pathElement);
//                    }
//                    return;
//                }
//                offset += length;
//            }
//        }
//        Text textNode = getTextNode(0, 0);
        if (isForwardBias()) {
            textNode.setCaretPosition(pos);
        } else {
            textNode.setCaretPosition(pos - 1);
        }
        textNode.caretBiasProperty().set(isForwardBias());
    }

    // for testing
    void setHandlePressed(boolean pressed) {
        handlePressed = pressed;
    }

    // for testing
    ScrollPane getScrollPane() {
        return scrollPane;
    }

    // for testing
    Text getPromptNode() {
        return promptNode;
    }

    void addLineNumber(int no, double prefHeight) {
        Label label;
        if (no < lineNoBar.getChildren().size()) {
            label = (Label) lineNoBar.getChildren().get(no);
            label.setPrefHeight(prefHeight);
        } else {
            label = new Label(String.valueOf(no + 1));
            label.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
                event.consume();
                lineNoBar.fireEvent(event.copyFor(lineNoBar, lineNoBar));
            });
            label.setPadding(new Insets(0, 0, 0, 0));
            label.setAlignment(Pos.TOP_CENTER);
            label.setPrefHeight(prefHeight);
            label.setMinWidth(Region.USE_PREF_SIZE);
            label.setOnContextMenuRequested(Event::consume);
            label.fontProperty().bind(codeArea.fontProperty());
            label.textFillProperty().bind(textFillProperty());
            lineNoBar.getChildren().add(label);
        }
    }

    /* ************************************************************************
     *
     * Support classes
     *
     **************************************************************************/

    private class ContentView extends Region {
        {
            getStyleClass().add("content");

            addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
                behavior.mousePressed(event);
                event.consume();
            });

            addEventHandler(MouseEvent.MOUSE_RELEASED, event -> {
                behavior.mouseReleased(event);
                event.consume();
            });

            addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
                behavior.mouseDragged(event);
                event.consume();
            });
        }

        @Override protected ObservableList<Node> getChildren() {
            return super.getChildren();
        }

        @Override public Orientation getContentBias() {
            return Orientation.HORIZONTAL;
        }

        /**
         * Compute the preferred width of the ContentView
         */
        @Override protected double computePrefWidth(double height) {
            if (computedPrefWidth < 0) {
                double prefWidth = 0;

                for (Node node : paragraphNodes.getChildren()) {
                    TextFlow textFlow = (TextFlow)node;
                    Text paragraphNode = (Text)textFlow.getChildren().getFirst();
                    String text = textFlow.getChildren().stream()
                            .map(n -> ((Text)n).getText())
                            .collect(Collectors.joining());
                    prefWidth = Math.max(prefWidth,
                            Utils.computeTextWidth(paragraphNode.getFont(),
                                    text, 0));
                    textFlow.setPrefWidth(prefWidth);
                }

                prefWidth += snappedLeftInset() + snappedRightInset();

                Bounds viewPortBounds = scrollPane.getViewportBounds();
                computedPrefWidth = Math.max(prefWidth, (viewPortBounds != null) ? viewPortBounds.getWidth() : 0);
            }
            return computedPrefWidth;
        }

        /**
         * Compute the preferred height of the ContentView
         */
        @Override protected double computePrefHeight(double width) {
            if (width != widthForComputedPrefHeight) {
                invalidateMetrics();
                widthForComputedPrefHeight = width;
            }

            if (computedPrefHeight < 0) {
                double wrappingWidth;
                if (width == -1) {
                    wrappingWidth = 0;
                } else {
                    wrappingWidth = Math.max(width - (snappedLeftInset() + snappedRightInset()), 0);
                }

                double prefHeight = 0;

                for (Node node : paragraphNodes.getChildren()) {
//                    Text paragraphNode = (Text)node;
//                    prefHeight += Utils.computeTextHeight(
//                            paragraphNode.getFont(),
//                            paragraphNode.getText(),
//                            wrappingWidth,
//                            paragraphNode.getBoundsType());
                    TextFlow textFlow = (TextFlow)node;
                    Text paragraphNode = (Text)textFlow.getChildren().getFirst();
                    String text = textFlow.getChildren().stream()
                            .map(n -> ((Text)n).getText())
                            .collect(Collectors.joining());
                    double lineHeight = Utils.computeTextHeight(
                            paragraphNode.getFont(),
                            text,
                            wrappingWidth,
                            paragraphNode.getBoundsType());
                    textFlow.setPrefHeight(lineHeight);
                    prefHeight += lineHeight;
                }

                prefHeight += snappedTopInset() + snappedBottomInset();

                Bounds viewPortBounds = scrollPane.getViewportBounds();
                computedPrefHeight = Math.max(prefHeight, (viewPortBounds != null) ? viewPortBounds.getHeight() : 0);
            }
            return computedPrefHeight;
        }

        @Override protected double computeMinWidth(double height) {
            if (computedMinWidth < 0) {
                double hInsets = snappedLeftInset() + snappedRightInset();
                computedMinWidth = Math.min(characterWidth + hInsets, computePrefWidth(height));
            }
            return computedMinWidth;
        }

        @Override protected double computeMinHeight(double width) {
            if (computedMinHeight < 0) {
                double vInsets = snappedTopInset() + snappedBottomInset();
                computedMinHeight = Math.min(lineHeight + vInsets, computePrefHeight(width));
            }
            return computedMinHeight;
        }

        @Override public void layoutChildren() {
            List<Node> errorLines = contentView.getChildren()
                    .stream()
                    .filter(node -> node.getStyleClass().contains("error-line"))
                    .toList();
            contentView.getChildren().removeAll(errorLines);
            CodeArea codeArea = getSkinnable();
            double width = getWidth();

            // Lay out paragraphs
            final double topPadding = snappedTopInset();
            final double leftPadding = snappedLeftInset();

            double wrappingWidth = Math.max(width - (leftPadding + snappedRightInset()), 0);

            double y = topPadding;

            final List<Node> paragraphNodesChildren = paragraphNodes.getChildren();

            int no = 0;
            double oneLineHeight = 0;
            for (Node paragraphNodesChild : paragraphNodesChildren) {

//                Node node = paragraphNodesChildren.get(i);
//                Text paragraphNode = (Text)node;
//                paragraphNode.setWrappingWidth(wrappingWidth);
//
//                Bounds bounds = paragraphNode.getBoundsInLocal();
//                paragraphNode.setLayoutX(leftPadding);
//                paragraphNode.setLayoutY(y);
//
//                y += bounds.getHeight();
                TextFlow textFlow = (TextFlow) paragraphNodesChild;
                textFlow.setPrefWidth(wrappingWidth);
                textFlow.setLayoutX(leftPadding);
                textFlow.setLayoutY(y);

                double subX = 0;
                double subY = 0;
                ObservableList<Node> children = textFlow.getChildren();
                for (int i = 0; i < children.size(); i++) {
                    Text textNode = (Text) children.get(i);
                    codeArea.getSyntaxHighlighter().highlight(textNode);
                    if (oneLineHeight == 0) {
                        oneLineHeight = Utils.computeTextHeight(textNode.getFont(), "1", 0, TextBoundsType.LOGICAL_VERTICAL_CENTER);
                    }
                    double unwrapWidth = computeTextWidth(textNode.getText(), textNode.getFont(), 0, getSkinnable().tabSizeProperty().get());
                    if (i > 0 && subX + unwrapWidth > wrappingWidth) {
                        // Not first of line and exceed the border. Move to new line
                        subY += oneLineHeight;
                        subX = 0;
                    }
                    textNode.setLayoutX(subX);
                    textNode.setLayoutY(subY);
                    if (subX + unwrapWidth > wrappingWidth) {
                        // Single Text Node exceeds wrapping width
                        textNode.setWrappingWidth(wrappingWidth);
                        subX = 0;
                        double wrapHeight = textNode.getBoundsInParent().getHeight();
                        if (wrapHeight % oneLineHeight != 0) {
                            wrapHeight = oneLineHeight * Math.ceil(wrapHeight / oneLineHeight);
                        }
                        subY += wrapHeight;
                    } else {
                        textNode.setWrappingWidth(0);
                        if (textNode.getBoundsInParent().getHeight() < oneLineHeight) {
                            textNode.setLayoutY(subY + oneLineHeight - textNode.getBoundsInParent().getHeight());
                        }
                        subX += unwrapWidth;
                    }
                }
                if (subX == 0) {
                    textFlow.setPrefHeight(Math.max(subY, oneLineHeight));
                } else {
                    textFlow.setPrefHeight(subY + oneLineHeight);
                }
                y += textFlow.getPrefHeight();
                addLineNumber(no++, textFlow.getPrefHeight());
            }
            int diff = no - lineNoBar.getChildren().size();
            if (diff < 0) {
                // Clear the extra line numbers
                lineNoBar.getChildren().remove(no, lineNoBar.getChildren().size());
            }

            if (promptNode != null) {
                promptNode.setLayoutX(0);
                promptNode.setLayoutY(topPadding + promptNode.getBaselineOffset());
                promptNode.setWrappingWidth(wrappingWidth);
            }

            // Update the selection
            IndexRange selection = codeArea.getSelection();
            Bounds oldCaretBounds = caretPath.getBoundsInParent();

            selectionHighlightGroup.getChildren().clear();

            int caretPos = codeArea.getCaretPosition();
            int anchorPos = codeArea.getAnchor();

            if (SHOW_HANDLES) {
                // Install and resize the handles for caret and anchor.
                if (selection.getLength() > 0) {
                    selectionHandle1.resize(selectionHandle1.prefWidth(-1),
                            selectionHandle1.prefHeight(-1));
                    selectionHandle2.resize(selectionHandle2.prefWidth(-1),
                            selectionHandle2.prefHeight(-1));
                } else {
                    caretHandle.resize(caretHandle.prefWidth(-1),
                            caretHandle.prefHeight(-1));
                }

                // Position the handle for the anchor. This could be handle1 or handle2.
                // Do this before positioning the actual caret.
                if (selection.getLength() > 0) {
                    int paragraphIndex = paragraphNodesChildren.size();
                    int paragraphOffset = codeArea.getLength() + 1;
                    Text paragraphNode = null;
                    TextFlow textFlow;
                    do {
//                        paragraphNode = (Text)paragraphNodesChildren.get(--paragraphIndex);
//                        paragraphOffset -= paragraphNode.getText().length() + 1;
                        textFlow = (TextFlow)paragraphNodesChildren.get(--paragraphIndex);
                        ObservableList<Node> children = textFlow.getChildren();
                        for (int i = children.size() - 1; i >= 0; i--) {
                            Node child = children.get(i);
                            paragraphNode = (Text) child;
                            paragraphOffset -= paragraphNode.getText().length();
                            if (anchorPos >= paragraphOffset) {
                                break;
                            }
                        }
                        paragraphOffset--;
                    } while (anchorPos < paragraphOffset);

                    updateTextNodeCaretPos(anchorPos - paragraphOffset, paragraphNode);
                    caretPath.getElements().clear();
                    caretPath.getElements().addAll(paragraphNode.getCaretShape());
                    caretPath.setLayoutX(paragraphNode.getLayoutX());
                    caretPath.setLayoutY(paragraphNode.getLayoutY());

                    Bounds b = caretPath.getBoundsInParent();
                    if (caretPos < anchorPos) {
                        selectionHandle2.setLayoutX(b.getMinX() - selectionHandle2.getWidth() / 2);
                        selectionHandle2.setLayoutY(b.getMaxY() - 1);
                    } else {
                        selectionHandle1.setLayoutX(b.getMinX() - selectionHandle1.getWidth() / 2);
                        selectionHandle1.setLayoutY(b.getMinY() - selectionHandle1.getHeight() + 1);
                    }
                }
            }

            {
                // Position caret
                int paragraphIndex = paragraphNodesChildren.size();

                TextFlow caretTextFlow = (TextFlow) paragraphNodesChildren.getFirst();
                Text caretTextNode = (Text) caretTextFlow
                        .getChildren()
                        .getFirst();
                int caretOffset = codeArea.getLength() + 1;
                boolean foundCaretNode = false;

                int errorPosIndex = codeArea.getErrorPosList().size();
                int textOffset = caretOffset;
                while (paragraphIndex > 0) {
                    TextFlow textFlow = (TextFlow) paragraphNodesChildren.get(--paragraphIndex);
                    ObservableList<Node> children = textFlow.getChildren();
                    for (int i = children.size() - 1; i >= 0; i--) {
                        Text textNode = (Text) children.get(i);
                        textOffset -= textNode.getText().length();
                        if (!foundCaretNode && caretPos >= textOffset) {
                            foundCaretNode = true;
                            caretTextFlow = textFlow;
                            caretTextNode = textNode;
                            caretOffset = textOffset - 1;
                        }
                        if (!codeArea.getErrorPosList().isEmpty()
                                && errorPosIndex > 0
                                && codeArea.getErrorPosList().get(errorPosIndex - 1) >= textOffset - 1) {
                            Integer errorPos = codeArea.getErrorPosList().get(--errorPosIndex);
                            updateErrorLine(textNode, errorPos, textOffset, textFlow);
                        }
                    }
                    textOffset--;
                    if (!foundCaretNode && caretPos >= textOffset) {
                        foundCaretNode = true;
                        caretTextFlow = textFlow;
                        caretTextNode = (Text) textFlow.getChildren().getFirst();
                        caretOffset = textOffset;
                    }
                    if (!codeArea.getErrorPosList().isEmpty()
                            && errorPosIndex > 0
                            && codeArea.getErrorPosList().get(errorPosIndex - 1) >= textOffset) {
                        Text textNode = (Text) textFlow.getChildren().getFirst();
                        Integer errorPos = codeArea.getErrorPosList().get(--errorPosIndex);
                        updateErrorLine(textNode, errorPos, textOffset, textFlow);
                    }
                }

                updateTextNodeCaretPos(caretPos - caretOffset, caretTextNode);

                caretPath.getElements().clear();
                caretPath.getElements().addAll(caretTextNode.getCaretShape());
                caretPath.setLayoutX(caretTextFlow.getLayoutX() + caretTextNode.getLayoutX());

                caretPath.setLayoutY(caretTextFlow.getLayoutY() + caretTextNode.getLayoutY());
                Point2D caretPoint = new Point2D(caretPath.getLayoutX(), caretPath.getLayoutY());
                getSkinnable().caretPointProperty().set(caretPoint);

                // Position caret
//                int paragraphIndex = paragraphNodesChildren.size();
//                int paragraphOffset = codeArea.getLength() + 1;
//
//                Text paragraphNode = null;
//                TextFlow textFlow;
//                do {
////                    paragraphNode = (Text)paragraphNodesChildren.get(--paragraphIndex);
////                    paragraphOffset -= paragraphNode.getText().length() + 1;
//                    textFlow = (TextFlow)paragraphNodesChildren.get(--paragraphIndex);
//                    ObservableList<Node> children = textFlow.getChildren();
//                    for (int i = children.size() - 1; i >= 0; i--) {
//                        paragraphNode = (Text) children.get(i);
//                        paragraphOffset -= paragraphNode.getText().length();
//                        if (caretPos >= paragraphOffset) {
//                            break;
//                        }
//                    }
//                    paragraphOffset--;
//                } while (caretPos < paragraphOffset);
//
//                updateTextNodeCaretPos(caretPos - paragraphOffset, paragraphNode);
//
//                caretPath.getElements().clear();
//                caretPath.getElements().addAll(paragraphNode.getCaretShape());
//                caretPath.setLayoutX(textFlow.getLayoutX() + paragraphNode.getLayoutX());
//
//                caretPath.setLayoutY(paragraphNode.getParent().getLayoutY() + paragraphNode.getLayoutY());


                if (oldCaretBounds == null || !oldCaretBounds.equals(caretPath.getBoundsInParent())) {
                    scrollCaretToVisible();
                }
            }

            // Update selection fg and bg
            int start = selection.getStart();
            int end = selection.getEnd();
            for (int i = 0, max = paragraphNodesChildren.size(); i < max; i++) {
                TextFlow textFlow = (TextFlow)paragraphNodesChildren.get(i);
                for (int j = 0; j < textFlow.getChildren().size(); j++) {
                    Text textNode = (Text) textFlow.getChildren().get(j);
                    int paragraphLength = textNode.getText().length();
                    if (end > start && start < paragraphLength) {
                        textNode.setSelectionStart(start);
                        textNode.setSelectionEnd(Math.min(end, paragraphLength));

                        Path selectionHighlightPath = new Path();
                        selectionHighlightPath.setLayoutX(textNode.getLayoutX());
                        selectionHighlightPath.setLayoutY(textFlow.getLayoutY() + textNode.getLayoutY());
                        selectionHighlightPath.setManaged(false);
                        PathElement[] selectionShape = textNode.getSelectionShape();
                        if (selectionShape != null) {
                            selectionHighlightPath.getElements().addAll(selectionShape);
                        }
                        selectionHighlightGroup.getChildren().add(selectionHighlightPath);

//                        selectionHighlightGroup.setVisible(true);
                        updateHighlightFill();
                    } else {
                        textNode.setSelectionStart(-1);
                        textNode.setSelectionEnd(-1);
//                        selectionHighlightGroup.setVisible(false);
                    }
                    start = Math.max(0, start - paragraphLength);
                    end   = Math.max(0, end   - paragraphLength);
                }
                start = Math.max(0, start - 1);
                end   = Math.max(0, end   - 1);
            }
            if (!selectionHighlightGroup.getChildren().isEmpty()) {
                selectionHighlightGroup.setLayoutX(paragraphNodes.getBoundsInLocal().getMinX());
//                selectionHighlightGroup.setLayoutY(paragraphNodes.getBoundsInLocal().getMinY());
                selectionHighlightGroup.setVisible(true);
            }

            if (SHOW_HANDLES) {
                // Position handle for the caret. This could be handle1 or handle2 when
                // a selection is active.
                Bounds b = caretPath.getBoundsInParent();
                if (selection.getLength() > 0) {
                    if (caretPos < anchorPos) {
                        selectionHandle1.setLayoutX(b.getMinX() - selectionHandle1.getWidth() / 2);
                        selectionHandle1.setLayoutY(b.getMinY() - selectionHandle1.getHeight() + 1);
                    } else {
                        selectionHandle2.setLayoutX(b.getMinX() - selectionHandle2.getWidth() / 2);
                        selectionHandle2.setLayoutY(b.getMaxY() - 1);
                    }
                } else {
                    caretHandle.setLayoutX(b.getMinX() - caretHandle.getWidth() / 2 + 1);
                    caretHandle.setLayoutY(b.getMaxY());
                }
            }

            if (scrollPane.getPrefViewportWidth() == 0
                    || scrollPane.getPrefViewportHeight() == 0) {
                updatePrefViewportWidth();
                updatePrefViewportHeight();
                if (getParent() != null && scrollPane.getPrefViewportWidth() > 0
                        || scrollPane.getPrefViewportHeight() > 0) {
                    // Force layout of viewRect in ScrollPaneSkin
                    getParent().requestLayout();
                }
            }

            // RT-36454: Fit to width/height only if smaller than viewport.
            // That is, grow to fit but don't shrink to fit.
            Bounds viewportBounds = scrollPane.getViewportBounds();
            boolean wasFitToWidth = scrollPane.isFitToWidth();
            boolean wasFitToHeight = scrollPane.isFitToHeight();
            boolean setFitToWidth = codeArea.isWrapText() || computePrefWidth(-1) <= viewportBounds.getWidth();
            boolean setFitToHeight = computePrefHeight(width) <= viewportBounds.getHeight();
            if (wasFitToWidth != setFitToWidth || wasFitToHeight != setFitToHeight) {
                Platform.runLater(() -> {
                    scrollPane.setFitToWidth(setFitToWidth);
                    scrollPane.setFitToHeight(setFitToHeight);
                });
                getParent().requestLayout();
            }
        }

        /**
         * Draw a line under the text node at the error position
         * @param textNode The text node to update the error line for
         * @param errorPos The position of the error
         * @param textOffset The offset of the text node
         * @param textFlow The text flow containing the text node
         */
        private void updateErrorLine(Text textNode, Integer errorPos, int textOffset, TextFlow textFlow) {
            Line errorLine = new Line();
            errorLine.getStyleClass().add("error-line");
            errorLine.setManaged(false);
            errorLine.setStroke(Color.RED);
            errorLine.setStrokeWidth(2);
            PathElement[] pathElements = textNode.underlineShape(errorPos - textOffset + 1, textNode.getText().length());
            if (pathElements.length == 5) {
                errorLine.setStartX(((LineTo)pathElements[3]).getX());
                errorLine.setStartY(((LineTo)pathElements[3]).getY());
                errorLine.setEndX(((LineTo)pathElements[2]).getX());
                errorLine.setEndY(((LineTo)pathElements[2]).getY());
                errorLine.setLayoutX(textFlow.getLayoutX() + textNode.getLayoutX());
                errorLine.setLayoutY(textFlow.getLayoutY() + textNode.getLayoutY());
                contentView.getChildren().add(errorLine);
            }
        }
    }

    public static double computeTextWidth(String text, Font font, double wrappingWidth, int tabSize) {
        if (layout == null) {
            layout = Toolkit.getToolkit().getTextLayoutFactory().createLayout();
        }
        layout.setTabSize(tabSize);
        layout.setContent(text != null ? text : "", FontHelper.getNativeFont(font));
        layout.setWrapWidth((float)wrappingWidth);
        return layout.getBounds().getWidth();
    }
}