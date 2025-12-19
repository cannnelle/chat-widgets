package com.chatwidgets;

public enum FontSize {
    REGULAR("Regular"),
    SMALL("Small");

    private final String name;

    FontSize(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
