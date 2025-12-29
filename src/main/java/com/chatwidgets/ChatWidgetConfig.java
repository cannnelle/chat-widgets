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

    @ConfigSection(name = "Appearance (Shared)", description = "Shared appearance settings", position = 2, closedByDefault = false)
    String appearanceSection = "appearance";

    @ConfigSection(name = "Game Messages (Adv.)", description = "Advanced game message settings", position = 3, closedByDefault = true)
    String gameAdvancedSection = "gameAdvanced";

    @ConfigSection(name = "Private Messages (Adv.)", description = "Advanced private message settings", position = 4, closedByDefault = true)
    String privateAdvancedSection = "privateAdvanced";

    // Game Messages Section
    @ConfigItem(keyName = "enableGameMessages", name = "Enable", description = "Enables the game messages widget. Only renders when the chatbox is minimized.", section = gameSection, position = 0)
    default boolean enableGameMessages() {
        return true;
    }

    @ConfigItem(keyName = "gamePosition", name = "Position", description = "Widget position. Player-relative options work best with fade and low max messages.", section = gameSection, position = 1)
    default WidgetPosition gamePosition() {
        return WidgetPosition.DEFAULT;
    }

    @ConfigItem(keyName = "gameMaxMessages", name = "Max Messages", description = "Maximum number of messages to display", section = gameSection, position = 2)
    @Range(min = 1, max = 20)
    default int gameMaxMessages() {
        return 5;
    }

    @Alpha
    @ConfigItem(keyName = "gameTextColor", name = "Text Colour", description = "Base colour for message text", section = gameSection, position = 3)
    default Color gameTextColor() {
        return Color.WHITE;
    }

    @Alpha
    @ConfigItem(keyName = "gameBackgroundColor", name = "Background", description = "Background colour of the widget", section = gameSection, position = 4)
    default Color gameBackgroundColor() {
        return new Color(0, 0, 0, 0);
    }

    // Private Messages Section
    @ConfigItem(keyName = "enablePrivateMessages", name = "Enable", description = "Enables the private messages widget and hides the client's split private chat widget", section = privateSection, position = 0)
    default boolean enablePrivateMessages() {
        return true;
    }

    @ConfigItem(keyName = "privateMaxMessages", name = "Max Messages", description = "Maximum number of messages to display", section = privateSection, position = 1)
    @Range(min = 1, max = 20)
    default int privateMaxMessages() {
        return 5;
    }

    @Alpha
    @ConfigItem(keyName = "privateTextColor", name = "Text Colour", description = "Colour for message text", section = privateSection, position = 2)
    default Color privateTextColor() {
        return new Color(0, 255, 255);
    }

    @Alpha
    @ConfigItem(keyName = "privateBackgroundColor", name = "Background", description = "Background colour of the widget (only when not merged)", section = privateSection, position = 3)
    default Color privateBackgroundColor() {
        return new Color(0, 0, 0, 0);
    }

    // Appearance Section (Shared)
    @ConfigItem(keyName = "fontSize", name = "Font Size", description = "Font size for all messages", section = appearanceSection, position = 0)
    default FontSize fontSize() {
        return FontSize.REGULAR;
    }

    @ConfigItem(keyName = "mergeWithGameWidget", name = "Merge Chat Widgets", description = "When enabled renders all chat widgets as a single widget. Disable if using fixed mode. This setting is ignored if game messages are disabled or are positioned relatively to the player.", section = appearanceSection, position = 1)
    default boolean mergeWithGameWidget() {
        return true;
    }

    @ConfigItem(keyName = "swapStackingOrder", name = "Swap Stacking Order", description = "Swap the stacking order of the chat widgets", section = appearanceSection, position = 2)
    default boolean swapStackingOrder() {
        return false;
    }

    @ConfigItem(keyName = "smartPositioning", name = "Smart Positioning", description = "Automatically reposition widgets based on client mode and chatbox state", section = appearanceSection, position = 3)
    default boolean smartPositioning() {
        return true;
    }

    @ConfigItem(keyName = "wrapText", name = "Wrap Text", description = "Wrap long messages to multiple lines", section = appearanceSection, position = 4)
    default boolean wrapText() {
        return true;
    }

    @ConfigItem(keyName = "textShadow", name = "Text Shadow", description = "Draw shadow behind text", section = appearanceSection, position = 5)
    default boolean textShadow() {
        return true;
    }

    @ConfigItem(keyName = "showTimestamp", name = "Show Timestamps", description = "Prefix messages with a timestamp", section = appearanceSection, position = 6)
    default boolean showTimestamp() {
        return false;
    }

    @ConfigItem(keyName = "timestampFormat", name = "Timestamp Format", description = "Format for timestamps (e.g. [HH:mm:ss], [HH:mm])", section = appearanceSection, position = 7)
    default String timestampFormat() {
        return "[HH:mm]";
    }

    // Game Messages Advanced Section
    @ConfigItem(keyName = "gameDynamicHeight", name = "Dynamic Height", description = "Widget height adjusts to message count", section = gameAdvancedSection, position = 0)
    default boolean gameDynamicHeight() {
        return false;
    }

    @ConfigItem(keyName = "retainContextualColours", name = "Contextual Colours", description = "Retain colour tags in game messages", section = gameAdvancedSection, position = 1)
    default boolean retainContextualColours() {
        return true;
    }

    @ConfigItem(keyName = "collapseGameChat", name = "Collapse Duplicates", description = "Collapse identical consecutive messages with a count", section = gameAdvancedSection, position = 2)
    default boolean collapseGameChat() {
        return false;
    }

    @ConfigItem(keyName = "hideDuplicateCount", name = "Hide Duplicate Count", description = "Hide the count badge when collapsing duplicates", section = gameAdvancedSection, position = 3)
    default boolean hideDuplicateCount() {
        return false;
    }

    @ConfigItem(keyName = "gameFadeOutDuration", name = "Fade Out Duration", description = "Seconds before messages start fading (0 = never fade)", section = gameAdvancedSection, position = 5)
    @Range(min = 0, max = 300)
    default int gameFadeOutDuration() {
        return 0;
    }

    @ConfigItem(keyName = "gameWidgetWidth", name = "Widget Width", description = "Width of the widget in pixels. Overridden if the widget is manually resized.", section = gameAdvancedSection, position = 6)
    @Range(min = 150, max = 1024)
    default int gameWidgetWidth() {
        return 512;
    }

    @ConfigItem(keyName = "gameMarginTop", name = "Margin Top", description = "Extra space above the widget", section = gameAdvancedSection, position = 7)
    @Range(min = 0, max = 200)
    default int gameMarginTop() {
        return 0;
    }

    @ConfigItem(keyName = "gameMarginBottom", name = "Margin Bottom", description = "Extra space below the widget", section = gameAdvancedSection, position = 8)
    @Range(min = 0, max = 200)
    default int gameMarginBottom() {
        return 0;
    }

    // Private Messages Advanced Section
    @ConfigItem(keyName = "privateDynamicHeight", name = "Dynamic Height", description = "Widget height adjusts to message count (only when not merged)", section = privateAdvancedSection, position = 0)
    default boolean privateDynamicHeight() {
        return false;
    }

    @ConfigItem(keyName = "privateFadeOutDuration", name = "Fade Out Duration", description = "Seconds before messages start fading (0 = never fade)", section = privateAdvancedSection, position = 1)
    @Range(min = 0, max = 300)
    default int privateFadeOutDuration() {
        return 0;
    }

    @ConfigItem(keyName = "privateWidgetWidth", name = "Widget Width", description = "Width of the widget (only when not merged). Overridden if the widget is manually resized.", section = privateAdvancedSection, position = 2)
    @Range(min = 150, max = 1024)
    default int privateWidgetWidth() {
        return 512;
    }

    @ConfigItem(keyName = "privateMarginTop", name = "Margin Top", description = "Extra space above the widget (only when not merged)", section = privateAdvancedSection, position = 3)
    @Range(min = 0, max = 200)
    default int privateMarginTop() {
        return 0;
    }

    @ConfigItem(keyName = "privateMarginBottom", name = "Margin Bottom", description = "Extra space below the widget (only when not merged)", section = privateAdvancedSection, position = 4)
    @Range(min = 0, max = 200)
    default int privateMarginBottom() {
        return 0;
    }
}
