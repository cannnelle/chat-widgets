package com.chatwidgets;

import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PrivateChatOverlay extends Overlay {

    private static final int MAX_MESSAGE_LENGTH = 500;

    private final ChatWidgetPlugin plugin;
    private final ChatWidgetConfig config;
    private final Client client;

    private final Map<Integer, BufferedImage> spriteCache = new HashMap<>();

    @Inject
    public PrivateChatOverlay(ChatWidgetPlugin plugin, ChatWidgetConfig config, Client client) {
        this.plugin = plugin;
        this.config = config;
        this.client = client;
        setPosition(client.isResized() ? OverlayPosition.ABOVE_CHATBOX_RIGHT : OverlayPosition.BOTTOM_LEFT);
        setLayer(OverlayLayer.UNDER_WIDGETS);
        setPriority(config.swapStackingOrder() ? 10f : 9f);
        setResizable(true);
        setMovable(true);
        setMinimumSize(150);
        getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, "Clear", "Private chat history"));
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!plugin.shouldShowPrivateOverlay()) {
            return null;
        }

        if (config.swapStackingOrder()) {
            setPriority(10f);
        } else {
            setPriority(9f);
        }

        if (plugin.isWidgetsMerged()) {
            return null;
        }

        List<WidgetMessage> messages = plugin.getPrivateMessages();
        if (messages.isEmpty()) {
            return null;
        }

        FontSize fontSize = config.fontSize();
        FontMetrics metrics = ChatRenderUtils.setupGraphics(graphics, fontSize);

        Dimension preferredSize = getPreferredSize();
        int widgetWidth = (preferredSize != null && preferredSize.width > 0)
                ? preferredSize.width
                : config.privateWidgetWidth();
        int lineHeight = metrics.getHeight() - 2;
        long currentTime = System.currentTimeMillis();
        int fadeOutDuration = config.privateFadeOutDuration();
        long fadeOutMs = fadeOutDuration * 1000L;
        long fadeOutThreshold = fadeOutMs + 5000;
        Color textColor = config.privateTextColor();
        boolean drawShadow = config.textShadow();
        int maxMessages = config.privateMaxMessages();
        boolean wrapText = config.wrapText();
        boolean useDynamicHeight = config.privateDynamicHeight();

        int messageCount = messages.size();
        int startIndex = Math.max(0, messageCount - maxMessages);
        List<WidgetMessage> visibleMessages = new ArrayList<>(maxMessages);

        for (int i = startIndex; i < messageCount; i++) {
            WidgetMessage msg = messages.get(i);
            int msgMaxFade = msg.getMaxFadeSeconds();
            long msgFadeThreshold;
            if (msgMaxFade > 0) {
                msgFadeThreshold = msgMaxFade * 1000L + 5000;
            } else {
                msgFadeThreshold = fadeOutThreshold;
            }
            if ((fadeOutDuration == 0 && msgMaxFade == 0) || (currentTime - msg.getTimestamp()) < msgFadeThreshold) {
                visibleMessages.add(msg);
            }
        }

        if (visibleMessages.isEmpty()) {
            return null;
        }

        List<RenderLine> renderableLines = new ArrayList<>(visibleMessages.size() * 2);
        for (WidgetMessage msg : visibleMessages) {
            List<RenderLine> msgLines = ChatRenderUtils.buildPrivateMessageLines(msg, metrics, widgetWidth, currentTime, fadeOutMs,
                    wrapText, textColor, config.fontSize(), client.getModIcons(), config.showTimestamp(),
                    config.timestampFormat(), MAX_MESSAGE_LENGTH);

            boolean hasVisibleLine = false;
            for (RenderLine msgLine : msgLines) {
                if (msgLine.alpha > 0) {
                    hasVisibleLine = true;
                    break;
                }
            }
            if (hasVisibleLine) {
                renderableLines.addAll(msgLines);
            }
        }

        if (renderableLines.isEmpty()) {
            return null;
        }

        int widgetHeight;

        if (useDynamicHeight) {
            widgetHeight = renderableLines.size() * lineHeight;
        } else {
            widgetHeight = maxMessages * lineHeight;
        }

        Color bgColor = config.privateBackgroundColor();
        int bgPadding = bgColor.getAlpha() > 0 ? 3 : 0;
        int marginTop = config.privateMarginTop();
        int marginBottom = config.privateMarginBottom();

        int contentHeight = widgetHeight + bgPadding * 2;
        widgetHeight = contentHeight + marginTop + marginBottom;

        if (bgColor.getAlpha() > 0) {
            graphics.setColor(bgColor);
            graphics.fillRect(0, marginTop, widgetWidth, contentHeight);
        }

        Shape originalClip = graphics.getClip();
        graphics.setClip(0, 0, widgetWidth, widgetHeight + 4);

        int y = widgetHeight - bgPadding - marginBottom - metrics.getDescent();
        IndexedSprite[] modIcons = client.getModIcons();

        for (int i = renderableLines.size() - 1; i >= 0; i--) {
            RenderLine line = renderableLines.get(i);
            if (line.alpha <= 0) {
                continue;
            }

            int x = bgPadding;

            for (TextSegment segment : line.segments) {
                if (segment.iconId >= 0 && modIcons != null && segment.iconId < modIcons.length) {
                    BufferedImage img = ChatRenderUtils.getCachedSprite(modIcons, segment.iconId, spriteCache);
                    x += ChatRenderUtils.drawIcon(graphics, img, fontSize, metrics, x, y);
                } else {
                    x += ChatRenderUtils.drawText(graphics, segment.text, textColor, line.alpha, x, y,
                            drawShadow, metrics);
                }
            }
            y -= lineHeight;
        }

        graphics.setClip(originalClip);
        return new Dimension(widgetWidth, widgetHeight);
    }
}
