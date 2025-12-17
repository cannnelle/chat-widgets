package rs.proven;

import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

import java.awt.Color;

@ConfigGroup("privatechatwidget")
public interface PrivateChatWidgetConfig extends Config {

    @ConfigItem(keyName = "maxMessages", name = "Max Messages", description = "Maximum number of messages to display", position = 0)
    @Range(min = 1, max = 20)
    default int maxMessages() {
        return 5;
    }

    @ConfigItem(keyName = "widgetWidth", name = "Widget Width", description = "Width of the widget in pixels", position = 1)
    @Range(min = 150, max = 1024)
    default int widgetWidth() {
        return 512;
    }

    @ConfigItem(keyName = "showTimestamp", name = "Show Timestamp", description = "Prefix messages with timestamp", position = 2)
    default boolean showTimestamp() {
        return false;
    }

    @ConfigItem(keyName = "timestampFormat", name = "Timestamp Format", description = "Format for timestamps (e.g. [HH:mm:ss], [HH:mm])", position = 3)
    default String timestampFormat() {
        return "[HH:mm]";
    }

    @ConfigItem(keyName = "wrapText", name = "Wrap Text", description = "Wrap long messages to multiple lines", position = 4)
    default boolean wrapText() {
        return true;
    }

    @ConfigItem(keyName = "fontSize", name = "Font Size", description = "Font size for messages", position = 5)
    default FontSize fontSize() {
        return FontSize.REGULAR;
    }

    @Alpha
    @ConfigItem(keyName = "textColor", name = "Text Color", description = "Color for all text in the widget", position = 5)
    default Color textColor() {
        return new Color(0, 255, 255);
    }

    @ConfigItem(keyName = "textShadow", name = "Text Shadow", description = "Draw shadow behind text", position = 6)
    default boolean textShadow() {
        return true;
    }

    @Alpha
    @ConfigItem(keyName = "backgroundColor", name = "Background Color", description = "Background color of the widget", position = 7)
    default Color backgroundColor() {
        return new Color(0, 0, 0, 0);
    }

    @ConfigItem(keyName = "fadeOutDuration", name = "Fade Out Duration", description = "Seconds before messages start fading (0 = never fade)", position = 8)
    @Range(min = 0, max = 300)
    default int fadeOutDuration() {
        return 0;
    }
}
