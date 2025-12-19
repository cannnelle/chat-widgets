package com.chatwidgets;

import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
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

public class PrivateChatOverlay extends Overlay {

    private static final Pattern IMG_TAG_PATTERN = Pattern.compile("<img=(\\d+)>");
    private static final int MAX_MESSAGE_LENGTH = 500;

    private final ChatWidgetPlugin plugin;
    private final ChatWidgetConfig config;
    private final Client client;

    private final Map<Integer, BufferedImage> spriteCache = new HashMap<>();
    private SimpleDateFormat cachedDateFormat;
    private String cachedFormatPattern;

    @Inject
    public PrivateChatOverlay(ChatWidgetPlugin plugin, ChatWidgetConfig config, Client client) {
        this.plugin = plugin;
        this.config = config;
        this.client = client;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(0);
        setResizable(true);
        setMovable(true);
        setMinimumSize(150);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!plugin.shouldShowPrivateOverlay()) {
            return null;
        }

        if (config.mergeWithGameWidget() && config.gamePosition() == WidgetPosition.DEFAULT) {
            return null;
        }

        List<ChatMessage> messages = plugin.getPrivateMessages();
        if (messages.isEmpty()) {
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
                : config.privateWidgetWidth();
        int lineHeight = metrics.getHeight() - (fontSize == FontSize.SMALL ? 2 : 3) + 1;
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
        List<ChatMessage> visibleMessages = new ArrayList<>(maxMessages);

        for (int i = startIndex; i < messageCount; i++) {
            ChatMessage msg = messages.get(i);
            if (fadeOutDuration == 0 || (currentTime - msg.getTimestamp()) < fadeOutThreshold) {
                visibleMessages.add(msg);
            }
        }

        if (visibleMessages.isEmpty()) {
            return null;
        }

        List<GameChatOverlay.RenderLine> renderableLines = new ArrayList<>(visibleMessages.size() * 2);
        for (ChatMessage msg : visibleMessages) {
            List<GameChatOverlay.RenderLine> msgLines = buildRenderLines(msg, metrics, widgetWidth, currentTime,
                    fadeOutMs,
                    wrapText, textColor);

            boolean hasVisibleLine = false;
            for (GameChatOverlay.RenderLine msgLine : msgLines) {
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
        int padding = bgColor.getAlpha() > 0 ? 3 : 0;

        if (padding > 0) {
            widgetHeight += padding * 2;
        }

        if (bgColor.getAlpha() > 0) {
            graphics.setColor(bgColor);
            graphics.fillRect(0, 0, widgetWidth, widgetHeight);
        }

        Shape originalClip = graphics.getClip();
        graphics.setClip(0, 0, widgetWidth, widgetHeight + 4);

        int y = widgetHeight - padding - metrics.getDescent();
        IndexedSprite[] modIcons = client.getModIcons();

        for (int i = renderableLines.size() - 1; i >= 0; i--) {
            GameChatOverlay.RenderLine line = renderableLines.get(i);
            if (line.alpha <= 0) {
                continue;
            }

            int x = padding;

            for (GameChatOverlay.TextSegment segment : line.segments) {
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
                    if (drawShadow) {
                        graphics.setColor(withAlpha(Color.BLACK, line.alpha));
                        graphics.drawString(segment.text, x + 2, y + 1);
                    }
                    graphics.setColor(withAlpha(textColor, line.alpha));
                    graphics.drawString(segment.text, x + 1, y);
                    x += metrics.stringWidth(segment.text);
                }
            }
            y -= lineHeight;
        }

        graphics.setClip(originalClip);
        return new Dimension(widgetWidth, widgetHeight);
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

    private List<GameChatOverlay.RenderLine> buildRenderLines(ChatMessage msg, FontMetrics metrics, int widgetWidth,
            long currentTime, long fadeOutMs, boolean wrapText, Color textColor) {
        List<GameChatOverlay.RenderLine> lines = new ArrayList<>();
        int alpha = calculateAlpha(msg, currentTime, fadeOutMs);

        List<GameChatOverlay.TextSegment> headerSegments = new ArrayList<>();
        int headerWidth = 0;

        if (config.showTimestamp()) {
            String format = config.timestampFormat();
            if (format != null && !format.isEmpty()) {
                String ts = formatTimestamp(msg.getTimestamp(), format + " ");
                if (ts != null) {
                    headerSegments.add(new GameChatOverlay.TextSegment(ts, -1, metrics.stringWidth(ts), textColor));
                    headerWidth += metrics.stringWidth(ts);
                }
            }
        }

        String prefix = msg.isOutgoing() ? "To " : "From ";
        headerSegments.add(new GameChatOverlay.TextSegment(prefix, -1, metrics.stringWidth(prefix), textColor));
        headerWidth += metrics.stringWidth(prefix);

        List<GameChatOverlay.TextSegment> senderSegments = parseTextWithIcons(msg.getSender(), metrics,
                client.getModIcons(), textColor);
        for (GameChatOverlay.TextSegment seg : senderSegments) {
            headerSegments.add(seg);
            headerWidth += seg.width;
        }

        headerSegments.add(new GameChatOverlay.TextSegment(": ", -1, metrics.stringWidth(": "), textColor));
        headerWidth += metrics.stringWidth(": ");

        String messageText = msg.getMessage();
        if (messageText != null && messageText.length() > MAX_MESSAGE_LENGTH) {
            messageText = messageText.substring(0, MAX_MESSAGE_LENGTH) + "...";
        }

        List<GameChatOverlay.TextSegment> messageSegments = parseTextWithIcons(messageText, metrics,
                client.getModIcons(), textColor);

        if (!wrapText) {
            List<GameChatOverlay.TextSegment> singleLine = new ArrayList<>(headerSegments);
            singleLine.addAll(messageSegments);
            lines.add(new GameChatOverlay.RenderLine(singleLine, alpha));
        } else {
            int firstLineRemaining = widgetWidth - headerWidth;
            List<List<GameChatOverlay.TextSegment>> wrappedLines = wrapSegments(messageSegments, metrics,
                    firstLineRemaining,
                    widgetWidth, textColor);

            if (wrappedLines.isEmpty()) {
                lines.add(new GameChatOverlay.RenderLine(headerSegments, alpha));
            } else {
                List<GameChatOverlay.TextSegment> firstLine = new ArrayList<>(headerSegments);
                firstLine.addAll(wrappedLines.get(0));
                lines.add(new GameChatOverlay.RenderLine(firstLine, alpha));

                for (int i = 1; i < wrappedLines.size(); i++) {
                    lines.add(new GameChatOverlay.RenderLine(wrappedLines.get(i), alpha));
                }
            }
        }

        return lines;
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

    private List<GameChatOverlay.TextSegment> parseTextWithIcons(String text, FontMetrics metrics,
            IndexedSprite[] modIcons, Color textColor) {
        List<GameChatOverlay.TextSegment> segments = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return segments;
        }

        Matcher matcher = IMG_TAG_PATTERN.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String before = text.substring(lastEnd, matcher.start());
                segments.add(new GameChatOverlay.TextSegment(before, -1, metrics.stringWidth(before), textColor));
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
                segments.add(new GameChatOverlay.TextSegment("", iconId, iconWidth, textColor));
            } catch (NumberFormatException e) {
                String raw = matcher.group(0);
                segments.add(new GameChatOverlay.TextSegment(raw, -1, metrics.stringWidth(raw), textColor));
            }
            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            String after = text.substring(lastEnd);
            segments.add(new GameChatOverlay.TextSegment(after, -1, metrics.stringWidth(after), textColor));
        }

        return segments;
    }

    private List<List<GameChatOverlay.TextSegment>> wrapSegments(List<GameChatOverlay.TextSegment> segments,
            FontMetrics metrics,
            int firstLineWidth, int subsequentLineWidth, Color textColor) {
        List<List<GameChatOverlay.TextSegment>> lines = new ArrayList<>();
        List<GameChatOverlay.TextSegment> currentLine = new ArrayList<>();
        int currentWidth = firstLineWidth;

        for (GameChatOverlay.TextSegment segment : segments) {
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
                            currentLine.add(new GameChatOverlay.TextSegment(" ", -1, spaceWidth, segment.color));
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
                            currentLine.add(new GameChatOverlay.TextSegment(" ", -1, spaceWidth, segment.color));
                            currentWidth -= spaceWidth;
                        }
                        currentLine.add(new GameChatOverlay.TextSegment(word, -1, wordWidth, segment.color));
                        currentWidth -= wordWidth;
                    } else {
                        if (!currentLine.isEmpty()) {
                            lines.add(currentLine);
                            currentLine = new ArrayList<>();
                            currentWidth = subsequentLineWidth;
                        }
                        currentLine.add(new GameChatOverlay.TextSegment(word, -1, wordWidth, segment.color));
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

    private int calculateAlpha(ChatMessage msg, long currentTime, long fadeOutMs) {
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
}
