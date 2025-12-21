package com.chatwidgets;

import net.runelite.api.ChatMessageType;
import net.runelite.api.IndexedSprite;
import net.runelite.client.ui.FontManager;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatRenderUtils {

    private ChatRenderUtils() { }

    public static FontMetrics setupGraphics(Graphics2D graphics, FontSize fontSize) {
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        Font baseFont = fontSize == FontSize.SMALL
                ? FontManager.getRunescapeSmallFont()
                : FontManager.getRunescapeFont();
        graphics.setFont(baseFont);
        return graphics.getFontMetrics();
    }

    public static int drawIcon(Graphics2D graphics, BufferedImage img, FontSize fontSize,
            FontMetrics metrics, int x, int y) {
        if (img == null) {
            return 0;
        }
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
        return iconWidth + 2;
    }

    public static int drawText(Graphics2D graphics, String text, Color color, int alpha, int x, int y,
            boolean drawShadow, FontMetrics metrics) {
        if (drawShadow) {
            graphics.setColor(withAlpha(Color.BLACK, alpha));
            graphics.drawString(text, x + 2, y + 1);
        }
        graphics.setColor(withAlpha(color, alpha));
        graphics.drawString(text, x + 1, y);
        return metrics.stringWidth(text);
    }

    public static int calculateAlpha(WidgetMessage msg, long currentTime, long fadeOutMs) {
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

    public static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    public static String formatTimestamp(long timestamp, String format) {
        try {
            return new SimpleDateFormat(format).format(new Date(timestamp));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static List<RenderLine> buildPrivateMessageLines(WidgetMessage msg, FontMetrics metrics,
            int widgetWidth, long currentTime, long fadeOutMs, boolean wrapText, Color textColor,
            FontSize fontSize, IndexedSprite[] modIcons, boolean showTimestamp, String timestampFormat,
            int maxMessageLength) {

        List<RenderLine> lines = new ArrayList<>();
        int alpha = calculateAlpha(msg, currentTime, fadeOutMs);

        List<TextSegment> headerSegments = new ArrayList<>();
        int headerWidth = 0;

        if (showTimestamp && timestampFormat != null && !timestampFormat.isEmpty()) {
            String ts = formatTimestamp(msg.getTimestamp(), timestampFormat + " ");
            if (ts != null) {
                int width = metrics.stringWidth(ts);
                headerSegments.add(new TextSegment(ts, -1, width, textColor));
                headerWidth += width;
            }
        }

        boolean isLoginNotification = msg.getType() == ChatMessageType.LOGINLOGOUTNOTIFICATION;

        if (!isLoginNotification) {
            String prefix = msg.isOutgoing() ? "To " : "From ";
            headerSegments.add(new TextSegment(prefix, -1, metrics.stringWidth(prefix), textColor));
            headerWidth += metrics.stringWidth(prefix);

            List<TextSegment> senderSegments = parseTextWithIcons(msg.getSender(), metrics, modIcons,
                    textColor, fontSize);
            for (TextSegment seg : senderSegments) {
                headerSegments.add(seg);
                headerWidth += seg.width;
            }

            headerSegments.add(new TextSegment(": ", -1, metrics.stringWidth(": "), textColor));
            headerWidth += metrics.stringWidth(": ");
        }

        String messageText = msg.getMessage();
        if (messageText != null && messageText.length() > maxMessageLength) {
            messageText = messageText.substring(0, maxMessageLength) + "...";
        }

        List<TextSegment> messageSegments = parseTextWithIcons(messageText, metrics, modIcons, textColor, fontSize);

        if (!wrapText) {
            List<TextSegment> singleLine = new ArrayList<>(headerSegments);
            singleLine.addAll(messageSegments);
            lines.add(new RenderLine(singleLine, alpha));
        } else {
            int firstLineRemaining = widgetWidth - headerWidth;
            List<List<TextSegment>> wrappedLines = wrapSegments(messageSegments, metrics, firstLineRemaining,
                    widgetWidth, textColor);

            addWrappedLines(lines, alpha, headerSegments, wrappedLines);
        }

        return lines;
    }

    public static void addWrappedLines(List<RenderLine> lines, int alpha, List<TextSegment> headerSegments, List<List<TextSegment>> wrappedLines) {
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

    public static BufferedImage getCachedSprite(IndexedSprite[] modIcons, int iconId,
            Map<Integer, BufferedImage> spriteCache) {
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

    public static BufferedImage spriteToBufferedImage(IndexedSprite sprite) {
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

    public static int calculateIconWidth(IndexedSprite[] modIcons, int iconId, FontSize fontSize) {
        int iconWidth = 13;
        if (modIcons != null && iconId >= 0 && iconId < modIcons.length && modIcons[iconId] != null) {
            iconWidth = modIcons[iconId].getWidth() + 1;
            if (fontSize == FontSize.SMALL) {
                iconWidth = (int) (iconWidth * 0.75);
            }
        }
        return iconWidth;
    }

    public static List<TextSegment> parseTextWithIcons(String text, FontMetrics metrics,
            IndexedSprite[] modIcons, Color textColor, FontSize fontSize) {
        List<TextSegment> segments = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return segments;
        }

        Pattern imgTagPattern = Pattern.compile("<img=(\\d+)>");
        Matcher matcher = imgTagPattern.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String before = text.substring(lastEnd, matcher.start());
                segments.add(new TextSegment(before, -1, metrics.stringWidth(before), textColor));
            }

            try {
                int iconId = Integer.parseInt(matcher.group(1));
                int iconWidth = calculateIconWidth(modIcons, iconId, fontSize);
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

    public static List<List<TextSegment>> wrapSegments(List<TextSegment> segments,
            FontMetrics metrics, int firstLineWidth, int subsequentLineWidth, Color textColor) {
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
}
