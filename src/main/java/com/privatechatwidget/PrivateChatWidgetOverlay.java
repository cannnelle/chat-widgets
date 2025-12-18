package com.privatechatwidget;

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
import java.util.stream.Collectors;

public class PrivateChatWidgetOverlay extends Overlay {

    private static final Pattern IMG_TAG_PATTERN = Pattern.compile("<img=(\\d+)>");
    private static final int MAX_MESSAGE_LENGTH = 500;

    private final PrivateChatWidgetPlugin plugin;
    private final PrivateChatWidgetConfig config;
    private final Client client;

    private final Map<Integer, BufferedImage> spriteCache = new HashMap<>();
    private SimpleDateFormat cachedDateFormat;
    private String cachedFormatPattern;

    @Inject
    public PrivateChatWidgetOverlay(PrivateChatWidgetPlugin plugin, PrivateChatWidgetConfig config, Client client) {
        this.plugin = plugin;
        this.config = config;
        this.client = client;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setResizable(true);
        setMovable(true);
        setMinimumSize(150);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        List<PrivateChatMessage> messages = plugin.getMessages();
        if (messages.isEmpty()) {
            return null;
        }

        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        Font baseFont = config.fontSize() == FontSize.SMALL
                ? FontManager.getRunescapeSmallFont()
                : FontManager.getRunescapeFont();
        graphics.setFont(baseFont);
        FontMetrics metrics = graphics.getFontMetrics();

        int widgetWidth = getPreferredSize() != null && getPreferredSize().width > 0
                ? getPreferredSize().width
                : config.widgetWidth();
        int lineHeight = metrics.getHeight() - (config.fontSize() == FontSize.SMALL ? 2 : 3);
        long currentTime = System.currentTimeMillis();
        long fadeOutMs = config.fadeOutDuration() * 1000L;
        Color textColor = config.textColor();
        boolean drawShadow = config.textShadow();
        int maxMessages = config.maxMessages();

        List<PrivateChatMessage> visibleMessages = messages.stream()
                .filter(msg -> fadeOutMs == 0 || (currentTime - msg.getTimestamp()) < fadeOutMs + 2000)
                .collect(Collectors.toList());

        if (visibleMessages.size() > maxMessages) {
            visibleMessages = new ArrayList<>(
                    visibleMessages.subList(visibleMessages.size() - maxMessages, visibleMessages.size()));
        }

        if (visibleMessages.isEmpty()) {
            return null;
        }

        List<RenderLine> renderLines = new ArrayList<>();
        for (PrivateChatMessage msg : visibleMessages) {
            renderLines.addAll(buildRenderLines(msg, metrics, widgetWidth, currentTime, fadeOutMs, config.wrapText()));
        }

        int actualContentHeight = renderLines.size() * lineHeight;
        int widgetHeight;
        int yOffset;

        if (config.dynamicHeight()) {
            widgetHeight = actualContentHeight;
            yOffset = 0;
        } else {
            widgetHeight = maxMessages * lineHeight;
            yOffset = widgetHeight - actualContentHeight;
        }

        Color bgColor = config.backgroundColor();
        if (bgColor.getAlpha() > 0) {
            graphics.setColor(bgColor);
            graphics.fillRect(0, 0, widgetWidth, widgetHeight);
        }

        Shape originalClip = graphics.getClip();
        graphics.setClip(0, 0, widgetWidth, widgetHeight + 4);

        int y = yOffset + metrics.getAscent();
        IndexedSprite[] modIcons = client.getModIcons();

        for (RenderLine line : renderLines) {
            if (line.alpha <= 0)
                continue;

            int x = 0;
            for (TextSegment segment : line.segments) {
                if (segment.iconId >= 0 && modIcons != null && segment.iconId < modIcons.length) {
                    BufferedImage img = getCachedSprite(modIcons, segment.iconId);
                    if (img != null) {
                        boolean isSmallFont = config.fontSize() == FontSize.SMALL;
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
            y += lineHeight;
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
        if (sprite == null)
            return null;

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
                if (idx >= pixels.length)
                    continue;
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

    private List<RenderLine> buildRenderLines(PrivateChatMessage msg, FontMetrics metrics, int widgetWidth,
            long currentTime, long fadeOutMs, boolean wrapText) {
        List<RenderLine> lines = new ArrayList<>();
        int alpha = calculateAlpha(msg, currentTime, fadeOutMs);

        List<TextSegment> headerSegments = new ArrayList<>();
        int headerWidth = 0;

        if (config.showTimestamp()) {
            String format = config.timestampFormat();
            if (format != null && !format.isEmpty()) {
                String ts = formatTimestamp(msg.getTimestamp(), format + " ");
                if (ts != null) {
                    headerSegments.add(new TextSegment(ts, -1, metrics.stringWidth(ts)));
                    headerWidth += metrics.stringWidth(ts);
                }
            }
        }

        String prefix = msg.isOutgoing() ? "To " : "From ";
        headerSegments.add(new TextSegment(prefix, -1, metrics.stringWidth(prefix)));
        headerWidth += metrics.stringWidth(prefix);

        List<TextSegment> senderSegments = parseTextWithIcons(msg.getSender(), metrics, client.getModIcons());
        for (TextSegment seg : senderSegments) {
            headerSegments.add(seg);
            headerWidth += seg.width;
        }

        headerSegments.add(new TextSegment(": ", -1, metrics.stringWidth(": ")));
        headerWidth += metrics.stringWidth(": ");

        String messageText = msg.getMessage();
        if (messageText != null && messageText.length() > MAX_MESSAGE_LENGTH) {
            messageText = messageText.substring(0, MAX_MESSAGE_LENGTH) + "...";
        }

        List<TextSegment> messageSegments = parseTextWithIcons(messageText, metrics, client.getModIcons());

        if (!wrapText) {
            List<TextSegment> singleLine = new ArrayList<>(headerSegments);
            singleLine.addAll(messageSegments);
            lines.add(new RenderLine(singleLine, alpha));
        } else {
            int firstLineRemaining = widgetWidth - headerWidth;
            List<List<TextSegment>> wrappedLines = wrapSegments(messageSegments, metrics, firstLineRemaining,
                    widgetWidth);

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

    private List<TextSegment> parseTextWithIcons(String text, FontMetrics metrics, IndexedSprite[] modIcons) {
        List<TextSegment> segments = new ArrayList<>();
        if (text == null || text.isEmpty())
            return segments;

        Matcher matcher = IMG_TAG_PATTERN.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String before = text.substring(lastEnd, matcher.start());
                segments.add(new TextSegment(before, -1, metrics.stringWidth(before)));
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
                segments.add(new TextSegment("", iconId, iconWidth));
            } catch (NumberFormatException e) {
                String raw = matcher.group(0);
                segments.add(new TextSegment(raw, -1, metrics.stringWidth(raw)));
            }
            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            String after = text.substring(lastEnd);
            segments.add(new TextSegment(after, -1, metrics.stringWidth(after)));
        }

        return segments;
    }

    private List<List<TextSegment>> wrapSegments(List<TextSegment> segments, FontMetrics metrics,
            int firstLineWidth, int subsequentLineWidth) {
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
                for (int i = 0; i < words.length; i++) {
                    String word = words[i];
                    if (word.isEmpty() && i < words.length - 1) {
                        int spaceWidth = metrics.stringWidth(" ");
                        if (spaceWidth <= currentWidth) {
                            currentLine.add(new TextSegment(" ", -1, spaceWidth));
                            currentWidth -= spaceWidth;
                        }
                        continue;
                    }

                    int wordWidth = metrics.stringWidth(word);
                    int spaceWidth = metrics.stringWidth(" ");
                    boolean needsSpace = !currentLine.isEmpty() && i > 0;
                    int neededWidth = wordWidth + (needsSpace ? spaceWidth : 0);

                    if (neededWidth <= currentWidth) {
                        if (needsSpace) {
                            currentLine.add(new TextSegment(" ", -1, spaceWidth));
                            currentWidth -= spaceWidth;
                        }
                        currentLine.add(new TextSegment(word, -1, wordWidth));
                        currentWidth -= wordWidth;
                    } else {
                        if (!currentLine.isEmpty()) {
                            lines.add(currentLine);
                            currentLine = new ArrayList<>();
                            currentWidth = subsequentLineWidth;
                        }
                        currentLine.add(new TextSegment(word, -1, wordWidth));
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

    private int calculateAlpha(PrivateChatMessage msg, long currentTime, long fadeOutMs) {
        if (fadeOutMs <= 0)
            return 255;
        long age = currentTime - msg.getTimestamp();
        if (age <= fadeOutMs)
            return 255;
        double fadeProgress = Math.min(1.0, (age - fadeOutMs) / 1200.0);
        return (int) (255 * (1.0 - fadeProgress));
    }

    private Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    private static class RenderLine {
        final List<TextSegment> segments;
        final int alpha;

        RenderLine(List<TextSegment> segments, int alpha) {
            this.segments = segments;
            this.alpha = alpha;
        }
    }

    private static class TextSegment {
        final String text;
        final int iconId;
        final int width;

        TextSegment(String text, int iconId, int width) {
            this.text = text;
            this.iconId = iconId;
            this.width = width;
        }
    }
}
