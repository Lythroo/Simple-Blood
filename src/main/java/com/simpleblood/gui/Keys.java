package com.simpleblood.gui;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Key and mouse button codes the way the running game numbers them. Up to 26.2 these are
 * GLFW codes (Escape 256, left button 0); 26.3 moved to SDL scancodes (Escape 41, left
 * button 1), so nothing here is a literal.
 */
final class Keys {

    private Keys() {}

    static final int ESCAPE = InputConstants.KEY_ESCAPE;
    static final int ENTER = InputConstants.KEY_RETURN;
    static final int NUMPAD_ENTER = InputConstants.KEY_NUMPADENTER;
    static final int BACKSPACE = InputConstants.KEY_BACKSPACE;
    static final int DELETE = InputConstants.KEY_DELETE;
    static final int LEFT = InputConstants.KEY_LEFT;
    static final int RIGHT = InputConstants.KEY_RIGHT;
    static final int HOME = InputConstants.KEY_HOME;
    static final int END = InputConstants.KEY_END;

    static final int MOUSE_LEFT = InputConstants.MOUSE_BUTTON_LEFT;
    static final int MOUSE_RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;

    /** Our own modifier bits, worked out from the event by the screen so widgets never see raw platform flags. */
    static final int CTRL = 1, SHIFT = 2, PASTE = 4, SELECT_ALL = 8, COPY = 16, CUT = 32;
}
