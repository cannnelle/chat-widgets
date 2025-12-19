package com.chatwidgets;

import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import net.runelite.api.MenuAction;
import net.runelite.api.Point;
import net.runelite.api.Player;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
    private static final Pattern COL_END_PATTERN = Pattern.compile("</col>");
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

    private final Map<Integer, BufferedImage> spriteCache = new HashMap<>();
    private SimpleDateFormat cachedDateFormat;
    private String cachedFormatPattern;

    @Inject
    public GameChatOverlay(ChatWidgetPlugin plugin, ChatWidgetConfig config, Client client) {
        this.plugin = plugin;
        this.config = config;
        this.client = client;
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
        boolean mergePrivate = !followPlayer && plugin.isChatboxHidden() && config.mergeWithGameWidget()
                && config.enableGameMessages() && config.enablePrivateMessages();
        List<WidgetMessage> privateMessages = mergePrivate ? plugin.getPrivateMessages() : new ArrayList<>();

        if (gameMessages.isEmpty() && privateMessages.isEmpty()) {
            return null;
        }

        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        FontSize fontSize = config.fontSize();
        Font baseFont = fontSize == FontSize.SMALL
                ? FontManager.getRunescapeSmallFont()
                : FontManager.getRunescapeFont();
        graphics.setFont(baseFont);
        FontMetrics metrics = graphics.getFontMetrics();

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
            if (mergePrivate) {
                addPrivateMessages(renderableLines, privateMessages, metrics, widgetWidth, currentTime, wrapText,
                        privateTextColor);
            }
        } else {
            if (mergePrivate) {
                addPrivateMessages(renderableLines, privateMessages, metrics, widgetWidth, currentTime, wrapText,
                        privateTextColor);
            }
            addGameMessages(renderableLines, gameMessages, metrics, widgetWidth, currentTime, wrapText,
                    retainContextualColours, gameTextColor);
        }

        if (renderableLines.isEmpty()) {
            return null;
        }

        int totalMaxMessages = config.gameMaxMessages() + (mergePrivate ? config.privateMaxMessages() : 0);
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
                    BufferedImage img = getCachedSprite(modIcons, segment.iconId);
                    if (img != null) {
                        boolean isSmallFont = fontSize == FontSize.SMALL;
                        int iconWidth = img.getWidth();
                        int iconHeight = img.getHeight();

                        if (isSmallFont) {
                            iconWidth = (int) (iconWidth * 0.75);
                            iconHeight = (int) (iconHeight * 0.75);
                        }

                        int iconY = y - iconHeight + metrics.getDescent() - 4;
                        if (isSmallFont) {
                            iconY += 2;
                        }

                        graphics.drawImage(img, x + 1, Math.max(iconY, 0), iconWidth, iconHeight, null);
                        x += iconWidth + 2;
                    }
                } else {
                    Color segmentColor = segment.color != null ? segment.color : gameTextColor;
                    if (drawShadow) {
                        graphics.setColor(withAlpha(Color.BLACK, line.alpha));
                        graphics.drawString(segment.text, x + 2, y + 1);
                    }
                    graphics.setColor(withAlpha(segmentColor, line.alpha));
                    graphics.drawString(segment.text, x + 1, y);
                    x += metrics.stringWidth(segment.text);
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

    private BufferedImage getCachedSprite(IndexedSprite[] modIcons, int iconId) {
        if (modIcons == null || iconId < 0 || iconId >= modIcons.length) {
            return null;
        }

        IndexedSprite sprite = modIcons[iconId];
        if (sprite == null) {
            return null;
        }

        BufferedImage cached = spriteCache.get(iconId);
        if (cached != null && cached.getWidth() == sprite.getWidth() && cached.getHeight() == sprite.getHeight()) {
            return cached;
        }

        BufferedImage img = spriteToBufferedImage(sprite);
        if (img != null) {
            spriteCache.put(iconId, img);
        }
        return img;
    }

    private BufferedImage spriteToBufferedImage(IndexedSprite sprite) {
        if (sprite == null) {
            return null;
        }

        int width = sprite.getWidth();
        int height = sprite.getHeight();
        byte[] pixels = sprite.getPixels();
        int[] palette = sprite.getPalette();

        if (width <= 0 || height <= 0 || pixels == null || palette == null) {
            return null;
        }

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                int idx = py * width + px;
                if (idx >= pixels.length) {
                    continue;
                }
                int paletteIdx = pixels[idx] & 0xFF;
                if (paletteIdx == 0) {
                    img.setRGB(px, py, 0x00000000);
                } else if (paletteIdx < palette.length) {
                    img.setRGB(px, py, 0xFF000000 | palette[paletteIdx]);
                }
            }
        }
        return img;
    }

    private List<RenderLine> buildGameRenderLines(WidgetMessage msg, FontMetrics metrics, int widgetWidth,
            long currentTime, long fadeOutMs, boolean wrapText, boolean retainContextualColours, Color textColor) {
        List<RenderLine> lines = new ArrayList<>();
        int alpha = calculateAlpha(msg, currentTime, fadeOutMs);

        List<TextSegment> headerSegments = new ArrayList<>();
        int headerWidth = 0;

        if (config.showTimestamp()) {
            String format = config.timestampFormat();
            if (format != null && !format.isEmpty()) {
                String ts = formatTimestamp(msg.getTimestamp(), format + " ");
                if (ts != null) {
                    headerSegments.add(new TextSegment(ts, -1, metrics.stringWidth(ts), textColor));
                    headerWidth += metrics.stringWidth(ts);
                }
            }
        }

        String messageText = msg.getMessage();
        if (messageText != null && messageText.length() > MAX_MESSAGE_LENGTH) {
            messageText = messageText.substring(0, MAX_MESSAGE_LENGTH) + "...";
        }

        if (msg.getCount() > 1 && !config.hideDuplicateCount()) {
            messageText = messageText + " (" + msg.getCount() + ")";
        }

        List<TextSegment> messageSegments = parseTextWithColoursAndIcons(messageText, metrics,
                client.getModIcons(), retainContextualColours, textColor);

        if (!wrapText) {
            List<TextSegment> singleLine = new ArrayList<>(headerSegments);
            singleLine.addAll(messageSegments);
            lines.add(new RenderLine(singleLine, alpha));
        } else {
            int firstLineRemaining = widgetWidth - headerWidth;
            List<List<TextSegment>> wrappedLines = wrapSegments(messageSegments, metrics, firstLineRemaining,
                    widgetWidth, textColor);

            if (wrappedLines.isEmpty()) {
                lines.add(new RenderLine(headerSegments, alpha));
            } else {
                List<TextSegment> firstLine = new ArrayList<>(headerSegments);
                firstLine.addAll(wrappedLines.get(0));
                lines.add(new RenderLine(firstLine, alpha));

                for (int i = 1; i < wrappedLines.size(); i++) {
                    lines.add(new RenderLine(wrappedLines.get(i), alpha));
                }
            }
        }

        return lines;
    }

    private List<RenderLine> buildPrivateRenderLines(WidgetMessage msg, FontMetrics metrics, int widgetWidth,
            long currentTime, long fadeOutMs, boolean wrapText, Color textColor) {
        List<RenderLine> lines = new ArrayList<>();
        int alpha = calculateAlpha(msg, currentTime, fadeOutMs);

        List<TextSegment> headerSegments = new ArrayList<>();
        int headerWidth = 0;

        if (config.showTimestamp()) {
            String format = config.timestampFormat();
            if (format != null && !format.isEmpty()) {
                String ts = formatTimestamp(msg.getTimestamp(), format + " ");
                if (ts != null) {
                    headerSegments.add(new TextSegment(ts, -1, metrics.stringWidth(ts), textColor));
                    headerWidth += metrics.stringWidth(ts);
                }
            }
        }

        String prefix = msg.isOutgoing() ? "To " : "From ";
        headerSegments.add(new TextSegment(prefix, -1, metrics.stringWidth(prefix), textColor));
        headerWidth += metrics.stringWidth(prefix);

        List<TextSegment> senderSegments = parseTextWithIcons(msg.getSender(), metrics, client.getModIcons(),
                textColor);
        for (TextSegment seg : senderSegments) {
            headerSegments.add(seg);
            headerWidth += seg.width;
        }

        headerSegments.add(new TextSegment(": ", -1, metrics.stringWidth(": "), textColor));
        headerWidth += metrics.stringWidth(": ");

        String messageText = msg.getMessage();
        if (messageText != null && messageText.length() > MAX_MESSAGE_LENGTH) {
            messageText = messageText.substring(0, MAX_MESSAGE_LENGTH) + "...";
        }

        List<TextSegment> messageSegments = parseTextWithIcons(messageText, metrics, client.getModIcons(), textColor);

        if (!wrapText) {
            List<TextSegment> singleLine = new ArrayList<>(headerSegments);
            singleLine.addAll(messageSegments);
            lines.add(new RenderLine(singleLine, alpha));
        } else {
            int firstLineRemaining = widgetWidth - headerWidth;
            List<List<TextSegment>> wrappedLines = wrapSegments(messageSegments, metrics, firstLineRemaining,
                    widgetWidth, textColor);

            if (wrappedLines.isEmpty()) {
                lines.add(new RenderLine(headerSegments, alpha));
            } else {
                List<TextSegment> firstLine = new ArrayList<>(headerSegments);
                firstLine.addAll(wrappedLines.get(0));
                lines.add(new RenderLine(firstLine, alpha));

                for (int i = 1; i < wrappedLines.size(); i++) {
                    lines.add(new RenderLine(wrappedLines.get(i), alpha));
                }
            }
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
                List<RenderLine> msgLines = buildPrivateRenderLines(msg, metrics, widgetWidth, currentTime,
                        privateFadeOutMs, wrapText, privateTextColor);

                for (RenderLine msgLine : msgLines) {
                    if (msgLine.alpha > 0) {
                        renderableLines.add(msgLine);
                    }
                }
            }
        }
    }

    private List<TextSegment> parseTextWithIcons(String text, FontMetrics metrics, IndexedSprite[] modIcons,
            Color textColor) {
        List<TextSegment> segments = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return segments;
        }

        Matcher matcher = IMG_TAG_PATTERN.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String before = text.substring(lastEnd, matcher.start());
                segments.add(new TextSegment(before, -1, metrics.stringWidth(before), textColor));
            }

            try {
                int iconId = Integer.parseInt(matcher.group(1));
                int iconWidth = 13;
                if (modIcons != null && iconId >= 0 && iconId < modIcons.length && modIcons[iconId] != null) {
                    iconWidth = modIcons[iconId].getWidth() + 1;
                    if (config.fontSize() == FontSize.SMALL) {
                        iconWidth = (int) (iconWidth * 0.75);
                    }
                }
                segments.add(new TextSegment("", iconId, iconWidth, textColor));
            } catch (NumberFormatException e) {
                String raw = matcher.group(0);
                segments.add(new TextSegment(raw, -1, metrics.stringWidth(raw), textColor));
            }
            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            String after = text.substring(lastEnd);
            segments.add(new TextSegment(after, -1, metrics.stringWidth(after), textColor));
        }

        return segments;
    }

    private String formatTimestamp(long timestamp, String format) {
        try {
            if (cachedDateFormat == null || !format.equals(cachedFormatPattern)) {
                cachedDateFormat = new SimpleDateFormat(format);
                cachedFormatPattern = format;
            }
            return cachedDateFormat.format(new Date(timestamp));
        } catch (IllegalArgumentException e) {
            return null;
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
            Matcher colEndMatcher = COL_END_PATTERN.matcher(text.substring(i));

            if (imgMatcher.lookingAt()) {
                if (currentText.length() > 0) {
                    String str = currentText.toString();
                    segments.add(new TextSegment(str, -1, metrics.stringWidth(str), currentColor));
                    currentText = new StringBuilder();
                }
                try {
                    int iconId = Integer.parseInt(imgMatcher.group(1));
                    int iconWidth = 13;
                    if (modIcons != null && iconId >= 0 && iconId < modIcons.length && modIcons[iconId] != null) {
                        iconWidth = modIcons[iconId].getWidth() + 1;
                        if (config.fontSize() == FontSize.SMALL) {
                            iconWidth = (int) (iconWidth * 0.75);
                        }
                    }
                    segments.add(new TextSegment("", iconId, iconWidth, currentColor));
                } catch (NumberFormatException e) {
                    currentText.append(imgMatcher.group(0));
                }
                i += imgMatcher.end();
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
                currentText.append(text.charAt(i));
                i++;
            }
        }

        if (currentText.length() > 0) {
            String str = currentText.toString();
            segments.add(new TextSegment(str, -1, metrics.stringWidth(str), currentColor));
        }

        return segments;
    }

    private List<List<TextSegment>> wrapSegments(List<TextSegment> segments, FontMetrics metrics,
            int firstLineWidth, int subsequentLineWidth, Color textColor) {
        List<List<TextSegment>> lines = new ArrayList<>();
        List<TextSegment> currentLine = new ArrayList<>();
        int currentWidth = firstLineWidth;

        for (TextSegment segment : segments) {
            if (segment.iconId >= 0) {
                if (segment.width <= currentWidth) {
                    currentLine.add(segment);
                    currentWidth -= segment.width;
                } else {
                    if (!currentLine.isEmpty()) {
                        lines.add(currentLine);
                        currentLine = new ArrayList<>();
                        currentWidth = subsequentLineWidth;
                    }
                    currentLine.add(segment);
                    currentWidth -= segment.width;
                }
            } else {
                String[] words = segment.text.split(" ", -1);
                for (int wi = 0; wi < words.length; wi++) {
                    String word = words[wi];
                    if (word.isEmpty() && wi < words.length - 1) {
                        int spaceWidth = metrics.stringWidth(" ");
                        if (spaceWidth <= currentWidth) {
                            currentLine.add(new TextSegment(" ", -1, spaceWidth, segment.color));
                            currentWidth -= spaceWidth;
                        }
                        continue;
                    }

                    int wordWidth = metrics.stringWidth(word);
                    int spaceWidth = metrics.stringWidth(" ");
                    boolean needsSpace = !currentLine.isEmpty() && wi > 0;
                    int neededWidth = wordWidth + (needsSpace ? spaceWidth : 0);

                    if (neededWidth <= currentWidth) {
                        if (needsSpace) {
                            currentLine.add(new TextSegment(" ", -1, spaceWidth, segment.color));
                            currentWidth -= spaceWidth;
                        }
                        currentLine.add(new TextSegment(word, -1, wordWidth, segment.color));
                        currentWidth -= wordWidth;
                    } else {
                        if (!currentLine.isEmpty()) {
                            lines.add(currentLine);
                            currentLine = new ArrayList<>();
                            currentWidth = subsequentLineWidth;
                        }
                        currentLine.add(new TextSegment(word, -1, wordWidth, segment.color));
                        currentWidth -= wordWidth;
                    }
                }
            }
        }

        if (!currentLine.isEmpty()) {
            lines.add(currentLine);
        }
        return lines;
    }

    private int calculateAlpha(WidgetMessage msg, long currentTime, long fadeOutMs) {
        if (fadeOutMs <= 0) {
            return 255;
        }
        long age = currentTime - msg.getTimestamp();
        if (age <= fadeOutMs) {
            return 255;
        }
        double fadeProgress = Math.min(1.0, (age - fadeOutMs) / 1200.0);
        return (int) (255 * (1.0 - fadeProgress));
    }

    private Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    static class RenderLine {
        final List<TextSegment> segments;
        final int alpha;

        RenderLine(List<TextSegment> segments, int alpha) {
            this.segments = segments;
            this.alpha = alpha;
        }
    }

    static class TextSegment {
        final String text;
        final int iconId;
        final int width;
        final Color color;

        TextSegment(String text, int iconId, int width, Color color) {
            this.text = text;
            this.iconId = iconId;
            this.width = width;
            this.color = color;
        }
    }
}
