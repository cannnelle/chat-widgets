package com.chatwidgets;

import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

import java.awt.Color;

@ConfigGroup("chatwidgets")
public interface ChatWidgetConfig extends Config {

    @ConfigSection(name = "Game Messages", description = "Game message settings", position = 0, closedByDefault = false)
    String gameSection = "game";

    @ConfigSection(name = "Private Messages", description = "Private message settings", position = 1, closedByDefault = false)
    String privateSection = "private";

    @ConfigSection(name = "Appearance", description = "Shared appearance settings", position = 2, closedByDefault = false)
    String appearanceSection = "appearance";

    // Game Messages Section
    @ConfigItem(keyName = "enableGameMessages", name = "Enable", description = "Enables the game messages widget. Only renders when the chatbox is minimized.", section = gameSection, position = 0)
    default boolean enableGameMessages() {
        return true;
    }

    @ConfigItem(keyName = "gamePosition", name = "Position", description = "Widget position. Player-relative options work best with fade and low max messages.", section = gameSection, position = 1)
    default WidgetPosition gamePosition() {
        return WidgetPosition.DEFAULT;
    }

    @ConfigItem(keyName = "gamePlayerOffset", name = "Player Offset", description = "Vertical offset when positioned relative to player (-50 to 50)", section = gameSection, position = 2)
    @Range(min = -50, max = 50)
    default int gamePlayerOffset() {
        return 0;
    }

    @ConfigItem(keyName = "gameFadeOutDuration", name = "Fade Out Duration", description = "Seconds before messages start fading (0 = never fade)", section = gameSection, position = 3)
    @Range(min = 0, max = 300)
    default int gameFadeOutDuration() {
        return 0;
    }

    @ConfigItem(keyName = "gameMaxMessages", name = "Max Messages", description = "Maximum number of messages to display", section = gameSection, position = 4)
    @Range(min = 1, max = 20)
    default int gameMaxMessages() {
        return 5;
    }

    @ConfigItem(keyName = "gameWidgetWidth", name = "Widget Width", description = "Width of the widget in pixels", section = gameSection, position = 5)
    @Range(min = 150, max = 1024)
    default int gameWidgetWidth() {
        return 512;
    }

    @ConfigItem(keyName = "gameDynamicHeight", name = "Dynamic Height", description = "Widget height adjusts to content", section = gameSection, position = 6)
    default boolean gameDynamicHeight() {
        return true;
    }

    @ConfigItem(keyName = "retainContextualColours", name = "Contextual Colours", description = "Retain colour tags in game messages", section = gameSection, position = 7)
    default boolean retainContextualColours() {
        return true;
    }

    @Alpha
    @ConfigItem(keyName = "gameTextColor", name = "Text Colour", description = "Base colour for message text", section = gameSection, position = 8)
    default Color gameTextColor() {
        return Color.WHITE;
    }

    @Alpha
    @ConfigItem(keyName = "gameBackgroundColor", name = "Background", description = "Background colour of the widget", section = gameSection, position = 9)
    default Color gameBackgroundColor() {
        return new Color(0, 0, 0, 0);
    }

    // Private Messages Section
    @ConfigItem(keyName = "enablePrivateMessages", name = "Enable", description = "Enables the private messages widget and hides the client's split private chat widget", section = privateSection, position = 0)
    default boolean enablePrivateMessages() {
        return true;
    }

    @ConfigItem(keyName = "mergeWithGameWidget", name = "Merge Widgets", description = "Render private messages above game messages in a single widget", section = privateSection, position = 1)
    default boolean mergeWithGameWidget() {
        return true;
    }

    @ConfigItem(keyName = "privateFadeOutDuration", name = "Fade Out Duration", description = "Seconds before messages start fading (0 = never fade)", section = privateSection, position = 2)
    @Range(min = 0, max = 300)
    default int privateFadeOutDuration() {
        return 0;
    }

    @ConfigItem(keyName = "privateMaxMessages", name = "Max Messages", description = "Maximum number of messages to display", section = privateSection, position = 3)
    @Range(min = 1, max = 20)
    default int privateMaxMessages() {
        return 5;
    }

    @ConfigItem(keyName = "privateWidgetWidth", name = "Widget Width", description = "Width of the widget (only when not merged)", section = privateSection, position = 4)
    @Range(min = 150, max = 1024)
    default int privateWidgetWidth() {
        return 512;
    }

    @ConfigItem(keyName = "privateDynamicHeight", name = "Dynamic Height", description = "Widget height adjusts to content (only when not merged)", section = privateSection, position = 5)
    default boolean privateDynamicHeight() {
        return true;
    }

    @Alpha
    @ConfigItem(keyName = "privateTextColor", name = "Text Colour", description = "Colour for message text", section = privateSection, position = 6)
    default Color privateTextColor() {
        return new Color(0, 255, 255);
    }

    @Alpha
    @ConfigItem(keyName = "privateBackgroundColor", name = "Background", description = "Background colour of the widget (only when not merged)", section = privateSection, position = 7)
    default Color privateBackgroundColor() {
        return new Color(0, 0, 0, 0);
    }

    // Appearance Section (Shared)
    @ConfigItem(keyName = "fontSize", name = "Font Size", description = "Font size for all messages", section = appearanceSection, position = 0)
    default FontSize fontSize() {
        return FontSize.REGULAR;
    }

    @ConfigItem(keyName = "wrapText", name = "Wrap Text", description = "Wrap long messages to multiple lines", section = appearanceSection, position = 1)
    default boolean wrapText() {
        return true;
    }

    @ConfigItem(keyName = "textShadow", name = "Text Shadow", description = "Draw shadow behind text", section = appearanceSection, position = 2)
    default boolean textShadow() {
        return true;
    }

    @ConfigItem(keyName = "showTimestamp", name = "Show Timestamp", description = "Prefix messages with timestamp", section = appearanceSection, position = 3)
    default boolean showTimestamp() {
        return false;
    }

    @ConfigItem(keyName = "timestampFormat", name = "Timestamp Format", description = "Format for timestamps (e.g. [HH:mm:ss], [HH:mm])", section = appearanceSection, position = 4)
    default String timestampFormat() {
        return "[HH:mm]";
    }
}
