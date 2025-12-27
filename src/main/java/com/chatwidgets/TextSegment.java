package com.chatwidgets;

import java.awt.Color;

public class TextSegment {
    public static final int LINE_BREAK = -2;

    public final String text;
    public final int iconId;
    public final int width;
    public final Color color;

    public TextSegment(String text, int iconId, int width, Color color) {
        this.text = text;
        this.iconId = iconId;
        this.width = width;
        this.color = color;
    }
}
