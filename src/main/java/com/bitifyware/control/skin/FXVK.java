package com.bitifyware.control.skin;

import com.bitifyware.control.CodeInputControl;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.EventHandler;
import javafx.geometry.NodeOrientation;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.input.KeyEvent;

/**
 * @author antipro
 */
public class FXVK extends Control {

    public enum Type {
        TEXT,
        NUMERIC,
        EMAIL,
    }


    private final ObjectProperty<EventHandler<KeyEvent>> onAction =
            new SimpleObjectProperty<>(this, "onAction");
    public final void setOnAction(EventHandler<KeyEvent> value) { onAction.set(value); }
    public final EventHandler<KeyEvent> getOnAction() { return onAction.get(); }
    public final ObjectProperty<EventHandler<KeyEvent>> onActionProperty() { return onAction; }


    final static String[] VK_TYPE_NAMES = new String[] { "text", "numeric", "url", "email" };
    public final static String VK_TYPE_PROP_KEY = "vkType";

    String[] chars;

    public FXVK() {
        setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    final ObjectProperty<Node> attachedNodeProperty() {
        if (attachedNode == null) {
            attachedNode = new ObjectPropertyBase<>() {
                @Override public Object getBean() {
                    return FXVK.this;
                }

                @Override public String getName() {
                    return "attachedNode";
                }
            };
        }
        return attachedNode;
    }

    private ObjectProperty<Node> attachedNode;
    final void setAttachedNode(Node value) { attachedNodeProperty().setValue(value); }
    final Node getAttachedNode() { return attachedNode == null ? null : attachedNode.getValue(); }
    static FXVK vk;

    public static void init(Node textInput) {
        if (vk != null) return;
        vk = new FXVK();
        FXVKSkin vkskin = new FXVKSkin(vk);
        vk.setSkin(vkskin);
        vkskin.prerender(textInput);
    }

    public static void attach(final Node textInput) {

        if (vk == null) {
            vk = new FXVK();
            vk.setSkin(new FXVKSkin(vk));
        }
        vk.setAttachedNode(textInput);
    }

    public static void detach() {
        if (vk != null) {
            vk.setAttachedNode(null);
        }
    }

    private final static boolean IS_FXVK_SUPPORTED = Platform.isSupported(ConditionalFeature.VIRTUAL_KEYBOARD);
    private static boolean USE_FXVK = IS_FXVK_SUPPORTED;

    public static boolean useFXVK() {
        return USE_FXVK;
    }

    public static void toggleUseVK(CodeInputControl textInput) {
        Integer vkType = (Integer)textInput.getProperties().get(VK_TYPE_PROP_KEY);
        if (vkType == null) {
            vkType = -1;
        }
        vkType++;
        if (vkType < 4) {
            USE_FXVK = true;
            textInput.getProperties().put(VK_TYPE_PROP_KEY, vkType);
            attach(textInput);
        } else {
            detach();
            textInput.getProperties().put(VK_TYPE_PROP_KEY, null);
            USE_FXVK = false;
        }
    }

    /***************************************************************************
     *                                                                         *
     * Methods                                                                 *
     *                                                                         *
     **************************************************************************/

    /** {@inheritDoc} */
    @Override protected Skin<?> createDefaultSkin() {
        return new FXVKSkin(this);
    }

    /***************************************************************************
     *                                                                         *
     * Stylesheet Handling                                                     *
     *                                                                         *
     **************************************************************************/

    private static final String DEFAULT_STYLE_CLASS = "fxvk";
}