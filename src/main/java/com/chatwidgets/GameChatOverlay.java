package com.chatwidgets;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import net.runelite.api.MenuAction;
import net.runelite.api.Point;
import net.runelite.api.Player;
import net.runelite.client.config.ChatColorConfig;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GameChatOverlay extends Overlay {

    private static final Pattern IMG_TAG_PATTERN = Pattern.compile("<img=(\\d+)>");
    private static final Pattern COL_TAG_PATTERN = Pattern.compile("<col=([0-9a-fA-F]{6})>");
    private static final Pattern COL_NAMED_PATTERN = Pattern.compile("<col(NORMAL|HIGHLIGHT)>");
    private static final Pattern COL_UNKNOWN_PATTERN = Pattern.compile("<col[^>]*>");
    private static final Pattern COL_END_PATTERN = Pattern.compile("</col>");
    private static final Pattern BR_TAG_PATTERN = Pattern.compile("<br>");
    private static final int MAX_MESSAGE_LENGTH = 500;

    private static final int MIN_ZOOM = -22;
    private static final int MAX_ZOOM = 1400;
    private static final int BELOW_OFFSET_MIN_ZOOM = 40;
    private static final int BELOW_OFFSET_MAX_ZOOM = 150;
    private static final int ABOVE_OFFSET_MIN_ZOOM = -40;
    private static final int ABOVE_OFFSET_MAX_ZOOM = -120;

    private final ChatWidgetPlugin plugin;
    private final ChatWidgetConfig config;
    private final Client client;
    private final ChatColorConfig chatColorConfig;

    private final Map<Integer, BufferedImage> spriteCache = new HashMap<>();
    private SimpleDateFormat cachedDateFormat;
    private String cachedFormatPattern;

    @Inject
    public GameChatOverlay(ChatWidgetPlugin plugin, ChatWidgetConfig config, Client client,
            ChatColorConfig chatColorConfig) {
        this.plugin = plugin;
        this.config = config;
        this.client = client;
        this.chatColorConfig = chatColorConfig;
        setPosition(client.isResized() ? OverlayPosition.ABOVE_CHATBOX_RIGHT : OverlayPosition.BOTTOM_LEFT);
        setLayer(OverlayLayer.UNDER_WIDGETS);
        setPriority(config.swapStackingOrder() ? 9f : 10f);
        setResizable(true);
        setMovable(true);
        setMinimumSize(150);
        getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, "Clear", "Game chat history"));
    }

    private boolean lastMergedState = false;

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!plugin.shouldShowGameOverlay()) {
            return null;
        }

        if (config.swapStackingOrder()) {
            setPriority(9f);
        } else {
            setPriority(10f);
        }

        boolean isMerged = plugin.isWidgetsMerged();
        if (isMerged != lastMergedState) {
            lastMergedState = isMerged;
            getMenuEntries().clear();
            if (isMerged) {
                getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, "Clear", "Merged chat history"));
            } else {
                getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, "Clear", "Game chat history"));
            }
        }

        List<WidgetMessage> gameMessages = plugin.getGameMessages();
        WidgetPosition positionMode = config.gamePosition();
        boolean followPlayer = positionMode != WidgetPosition.DEFAULT;
        List<WidgetMessage> privateMessages = isMerged ? plugin.getPrivateMessages() : new ArrayList<>();

        if (gameMessages.isEmpty() && privateMessages.isEmpty()) {
            return null;
        }

        FontSize fontSize = config.fontSize();
        FontMetrics metrics = ChatRenderUtils.setupGraphics(graphics, fontSize);

        Dimension preferredSize = getPreferredSize();
        int widgetWidth = (preferredSize != null && preferredSize.width > 0)
                ? preferredSize.width
                : config.gameWidgetWidth();
        int lineHeight = metrics.getHeight() - (fontSize == FontSize.SMALL ? 2 : 3) + 1;
        long currentTime = System.currentTimeMillis();
        boolean retainContextualColours = config.retainContextualColours();
        Color gameTextColor = config.gameTextColor();
        Color privateTextColor = config.privateTextColor();
        boolean drawShadow = config.textShadow();
        boolean wrapText = config.wrapText();
        boolean useDynamicHeight = followPlayer || config.gameDynamicHeight();

        List<RenderLine> renderableLines = new ArrayList<>();
        boolean swapOrder = config.swapStackingOrder();

        if (swapOrder) {
            addGameMessages(renderableLines, gameMessages, metrics, widgetWidth, currentTime, wrapText,
                    retainContextualColours, gameTextColor);
            if (isMerged) {
                addPrivateMessages(renderableLines, privateMessages, metrics, widgetWidth, currentTime, wrapText,
                        privateTextColor);
            }
        } else {
            if (isMerged) {
                addPrivateMessages(renderableLines, privateMessages, metrics, widgetWidth, currentTime, wrapText,
                        privateTextColor);
            }
            addGameMessages(renderableLines, gameMessages, metrics, widgetWidth, currentTime, wrapText,
                    retainContextualColours, gameTextColor);
        }

        if (renderableLines.isEmpty()) {
            return null;
        }

        int totalMaxMessages = config.gameMaxMessages() + (isMerged ? config.privateMaxMessages() : 0);
        int widgetHeight;

        if (useDynamicHeight) {
            widgetHeight = renderableLines.size() * lineHeight;
        } else {
            widgetHeight = totalMaxMessages * lineHeight;
        }

        boolean isPositionDefault = positionMode == WidgetPosition.DEFAULT;
        Color bgColor = config.gameBackgroundColor();
        int bgPadding = (bgColor.getAlpha() > 0 && isPositionDefault) ? 3 : 0;
        int marginTop = config.gameMarginTop();
        int marginBottom = config.gameMarginBottom();

        int contentHeight = widgetHeight + bgPadding * 2;
        widgetHeight = contentHeight + marginTop + marginBottom;

        if (followPlayer) {
            Player localPlayer = client.getLocalPlayer();
            if (localPlayer != null) {
                Point playerPoint;
                if (positionMode == WidgetPosition.BELOW_PLAYER) {
                    playerPoint = localPlayer.getCanvasTextLocation(graphics, "", 0);
                } else {
                    playerPoint = localPlayer.getCanvasTextLocation(graphics, "", localPlayer.getLogicalHeight());
                }
                if (playerPoint != null) {
                    int zoomOffset = calculateZoomOffset(positionMode);
                    int playerOffset = getClampedPlayerOffset(positionMode);
                    int x = playerPoint.getX() - widgetWidth / 2;
                    int y = playerPoint.getY() + zoomOffset + playerOffset - widgetHeight / 2;
                    graphics.translate(x - getBounds().x, y - getBounds().y);
                }
            }
        }

        if (bgColor.getAlpha() > 0 && isPositionDefault) {
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

            int lineWidth = calculateLineWidth(line.segments, metrics);
            int x = followPlayer ? bgPadding + (widgetWidth - bgPadding * 2 - lineWidth) / 2 : bgPadding;

            for (TextSegment segment : line.segments) {
                if (segment.iconId >= 0 && modIcons != null && segment.iconId < modIcons.length) {
                    BufferedImage img = ChatRenderUtils.getCachedSprite(modIcons, segment.iconId, spriteCache);
                    x += ChatRenderUtils.drawIcon(graphics, img, fontSize, metrics, x, y);
                } else {
                    Color segmentColor = segment.color != null ? segment.color : gameTextColor;
                    x += ChatRenderUtils.drawText(graphics, segment.text, segmentColor, line.alpha, x, y,
                            drawShadow, metrics);
                }
            }
            y -= lineHeight;
        }

        graphics.setClip(originalClip);

        if (followPlayer) {
            return null;
        }
        return new Dimension(widgetWidth, widgetHeight);
    }

    private int getClampedPlayerOffset(WidgetPosition positionMode) {
        int offset = config.gamePlayerOffset();

        if (positionMode == WidgetPosition.BELOW_PLAYER) {
            if (offset == 0) {
                return -25;
            }
            return Math.max(-50, Math.min(0, offset));
        } else {
            if (offset == 0) {
                return 25;
            }
            return Math.max(0, Math.min(50, offset));
        }
    }

    private int calculateZoomOffset(WidgetPosition positionMode) {
        int zoom = client.get3dZoom();
        double normalizedZoom = (double) (zoom - MIN_ZOOM) / (MAX_ZOOM - MIN_ZOOM);
        normalizedZoom = Math.max(0, Math.min(1, normalizedZoom));

        int minOffset, maxOffset;
        if (positionMode == WidgetPosition.BELOW_PLAYER) {
            minOffset = BELOW_OFFSET_MIN_ZOOM;
            maxOffset = BELOW_OFFSET_MAX_ZOOM;
        } else {
            minOffset = ABOVE_OFFSET_MIN_ZOOM;
            maxOffset = ABOVE_OFFSET_MAX_ZOOM;
        }

        return (int) (minOffset + normalizedZoom * (maxOffset - minOffset));
    }

    private int calculateLineWidth(List<TextSegment> segments, FontMetrics metrics) {
        int width = 0;
        for (TextSegment segment : segments) {
            if (segment.iconId >= 0) {
                width += segment.width;
            } else {
                width += metrics.stringWidth(segment.text);
            }
        }
        return width;
    }

    private Color getMessageTypeColor(ChatMessageType type, Color defaultColor) {
        switch (type) {
            case DIDYOUKNOW:
                return new Color(125, 255, 100);
            case BROADCAST:
                return new Color(255, 255, 0);
            case TRADEREQ:
                return new Color(126, 0, 128);
            default:
                return defaultColor;
        }
    }

    private int buildTimestampHeader(List<TextSegment> headerSegments, WidgetMessage msg,
            FontMetrics metrics, Color textColor) {
        if (!config.showTimestamp()) {
            return 0;
        }
        String format = config.timestampFormat();
        if (format == null || format.isEmpty()) {
            return 0;
        }
        String ts = ChatRenderUtils.formatTimestamp(msg.getTimestamp(), format + " ");
        if (ts == null) {
            return 0;
        }
        int width = metrics.stringWidth(ts);
        headerSegments.add(new TextSegment(ts, -1, width, textColor));
        return width;
    }

    private List<RenderLine> buildGameRenderLines(WidgetMessage msg, FontMetrics metrics, int widgetWidth,
            long currentTime, long fadeOutMs, boolean wrapText, boolean retainContextualColours, Color textColor) {
        List<RenderLine> lines = new ArrayList<>();
        int alpha = ChatRenderUtils.calculateAlpha(msg, currentTime, fadeOutMs);

        Color effectiveTextColor = retainContextualColours
                ? getMessageTypeColor(msg.getType(), textColor)
                : textColor;

        List<TextSegment> headerSegments = new ArrayList<>();
        int headerWidth = buildTimestampHeader(headerSegments, msg, metrics, textColor);

        String messageText = msg.getMessage();
        if (messageText != null && messageText.length() > MAX_MESSAGE_LENGTH) {
            messageText = messageText.substring(0, MAX_MESSAGE_LENGTH) + "...";
        }

        if (msg.getCount() > 1 && !config.hideDuplicateCount()) {
            messageText = messageText + " (" + msg.getCount() + ")";
        }

        List<TextSegment> messageSegments = parseTextWithColoursAndIcons(messageText, metrics,
                client.getModIcons(), retainContextualColours, effectiveTextColor);

        if (!wrapText) {
            List<TextSegment> singleLine = new ArrayList<>(headerSegments);
            singleLine.addAll(messageSegments);
            lines.add(new RenderLine(singleLine, alpha));
        } else {
            int firstLineRemaining = widgetWidth - headerWidth;
            List<List<TextSegment>> wrappedLines = ChatRenderUtils.wrapSegments(messageSegments, metrics,
                    firstLineRemaining,
                    widgetWidth, textColor);

            ChatRenderUtils.addWrappedLines(lines, alpha, headerSegments, wrappedLines);
        }

        return lines;
    }

    private void addGameMessages(List<RenderLine> renderableLines, List<WidgetMessage> gameMessages,
            FontMetrics metrics, int widgetWidth, long currentTime, boolean wrapText,
            boolean retainContextualColours, Color gameTextColor) {
        if (gameMessages.isEmpty()) {
            return;
        }

        int gameFadeOutDuration = config.gameFadeOutDuration();
        long gameFadeOutMs = gameFadeOutDuration * 1000L;
        long gameFadeOutThreshold = gameFadeOutMs + 5000;
        int gameMaxMessages = config.gameMaxMessages();

        int gameMessageCount = gameMessages.size();
        int gameStartIndex = Math.max(0, gameMessageCount - gameMaxMessages);

        for (int i = gameStartIndex; i < gameMessageCount; i++) {
            WidgetMessage msg = gameMessages.get(i);
            if (gameFadeOutDuration == 0 || (currentTime - msg.getTimestamp()) < gameFadeOutThreshold) {
                List<RenderLine> msgLines = buildGameRenderLines(msg, metrics, widgetWidth, currentTime,
                        gameFadeOutMs, wrapText, retainContextualColours, gameTextColor);

                for (RenderLine msgLine : msgLines) {
                    if (msgLine.alpha > 0) {
                        renderableLines.add(msgLine);
                    }
                }
            }
        }
    }

    private void addPrivateMessages(List<RenderLine> renderableLines, List<WidgetMessage> privateMessages,
            FontMetrics metrics, int widgetWidth, long currentTime, boolean wrapText, Color privateTextColor) {
        if (privateMessages.isEmpty()) {
            return;
        }

        int privateFadeOutDuration = config.privateFadeOutDuration();
        long privateFadeOutMs = privateFadeOutDuration * 1000L;
        long privateFadeOutThreshold = privateFadeOutMs + 5000;
        int privateMaxMessages = config.privateMaxMessages();

        int privateMessageCount = privateMessages.size();
        int privateStartIndex = Math.max(0, privateMessageCount - privateMaxMessages);

        for (int i = privateStartIndex; i < privateMessageCount; i++) {
            WidgetMessage msg = privateMessages.get(i);
            if (privateFadeOutDuration == 0 || (currentTime - msg.getTimestamp()) < privateFadeOutThreshold) {
                List<RenderLine> msgLines = ChatRenderUtils.buildPrivateMessageLines(msg, metrics, widgetWidth,
                        currentTime, privateFadeOutMs,
                        wrapText, privateTextColor, config.fontSize(), client.getModIcons(), config.showTimestamp(),
                        config.timestampFormat(), MAX_MESSAGE_LENGTH);

                for (RenderLine msgLine : msgLines) {
                    if (msgLine.alpha > 0) {
                        renderableLines.add(msgLine);
                    }
                }
            }
        }
    }

    private List<TextSegment> parseTextWithColoursAndIcons(String text, FontMetrics metrics,
            IndexedSprite[] modIcons, boolean retainContextualColours, Color textColor) {
        List<TextSegment> segments = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return segments;
        }

        Color currentColor = textColor;
        StringBuilder currentText = new StringBuilder();
        int i = 0;

        while (i < text.length()) {
            Matcher imgMatcher = IMG_TAG_PATTERN.matcher(text.substring(i));
            Matcher colMatcher = COL_TAG_PATTERN.matcher(text.substring(i));
            Matcher colNamedMatcher = COL_NAMED_PATTERN.matcher(text.substring(i));
            Matcher colEndMatcher = COL_END_PATTERN.matcher(text.substring(i));
            Matcher brMatcher = BR_TAG_PATTERN.matcher(text.substring(i));

            if (brMatcher.lookingAt()) {
                if (currentText.length() > 0) {
                    String str = currentText.toString();
                    segments.add(new TextSegment(str, -1, metrics.stringWidth(str), currentColor));
                    currentText = new StringBuilder();
                }
                segments.add(new TextSegment("", TextSegment.LINE_BREAK, 0, currentColor));
                i += brMatcher.end();
            } else if (imgMatcher.lookingAt()) {
                if (currentText.length() > 0) {
                    String str = currentText.toString();
                    segments.add(new TextSegment(str, -1, metrics.stringWidth(str), currentColor));
                    currentText = new StringBuilder();
                }
                try {
                    int iconId = Integer.parseInt(imgMatcher.group(1));
                    int iconWidth = ChatRenderUtils.calculateIconWidth(modIcons, iconId, config.fontSize());
                    segments.add(new TextSegment("", iconId, iconWidth, currentColor));
                } catch (NumberFormatException e) {
                    currentText.append(imgMatcher.group(0));
                }
                i += imgMatcher.end();
            } else if (colNamedMatcher.lookingAt()) {
                if (currentText.length() > 0) {
                    String str = currentText.toString();
                    segments.add(new TextSegment(str, -1, metrics.stringWidth(str), currentColor));
                    currentText = new StringBuilder();
                }
                String colorName = colNamedMatcher.group(1);
                if ("NORMAL".equals(colorName)) {
                    currentColor = textColor;
                } else if ("HIGHLIGHT".equals(colorName)) {
                    Color highlight = chatColorConfig.transparentExamineHighlight();
                    currentColor = highlight != null ? highlight : textColor;
                }
                i += colNamedMatcher.end();
            } else if (colMatcher.lookingAt() && retainContextualColours) {
                if (currentText.length() > 0) {
                    String str = currentText.toString();
                    segments.add(new TextSegment(str, -1, metrics.stringWidth(str), currentColor));
                    currentText = new StringBuilder();
                }
                try {
                    currentColor = Color.decode("#" + colMatcher.group(1));
                } catch (NumberFormatException e) {
                    currentColor = textColor;
                }
                i += colMatcher.end();
            } else if (colEndMatcher.lookingAt() && retainContextualColours) {
                if (currentText.length() > 0) {
                    String str = currentText.toString();
                    segments.add(new TextSegment(str, -1, metrics.stringWidth(str), currentColor));
                    currentText = new StringBuilder();
                }
                currentColor = textColor;
                i += colEndMatcher.end();
            } else if (colMatcher.lookingAt() && !retainContextualColours) {
                i += colMatcher.end();
            } else if (colEndMatcher.lookingAt() && !retainContextualColours) {
                i += colEndMatcher.end();
            } else {
                Matcher colUnknownMatcher = COL_UNKNOWN_PATTERN.matcher(text.substring(i));
                if (colUnknownMatcher.lookingAt()) {
                    i += colUnknownMatcher.end();
                } else {
                    currentText.append(text.charAt(i));
                    i++;
                }
            }
        }

        if (currentText.length() > 0) {
            String str = currentText.toString();
            segments.add(new TextSegment(str, -1, metrics.stringWidth(str), currentColor));
        }

        return segments;
    }
}
