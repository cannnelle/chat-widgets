package com.chatwidgets;

import net.runelite.api.ChatMessageType;

public class WidgetMessage {
    private final String message;
    private final long timestamp;
    private final ChatMessageType type;
    private final boolean bossKc;
    private final String sender;
    private final boolean outgoing;
    private final boolean isPrivate;
    private final int maxFadeSeconds;
    private int count = 1;

    public static WidgetMessage gameMessage(String message, long timestamp, ChatMessageType type, boolean bossKc) {
        return new WidgetMessage(message, timestamp, type, bossKc, null, false, false, 0);
    }

    public static WidgetMessage privateMessage(String sender, String message, long timestamp, boolean outgoing) {
        return new WidgetMessage(message, timestamp,
                outgoing ? ChatMessageType.PRIVATECHATOUT : ChatMessageType.PRIVATECHAT,
                false, sender, outgoing, true, 0);
    }

    public static WidgetMessage loginNotification(String sender, String message, long timestamp, int maxFadeSeconds) {
        return new WidgetMessage(message, timestamp, ChatMessageType.LOGINLOGOUTNOTIFICATION,
                false, sender, false, true, maxFadeSeconds);
    }

    private WidgetMessage(String message, long timestamp, ChatMessageType type, boolean bossKc,
            String sender, boolean outgoing, boolean isPrivate, int maxFadeSeconds) {
        this.message = message;
        this.timestamp = timestamp;
        this.type = type;
        this.bossKc = bossKc;
        this.sender = sender;
        this.outgoing = outgoing;
        this.isPrivate = isPrivate;
        this.maxFadeSeconds = maxFadeSeconds;
    }

    public String getMessage() {
        return message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public ChatMessageType getType() {
        return type;
    }

    public boolean isBossKc() {
        return bossKc;
    }

    public String getSender() {
        return sender;
    }

    public boolean isOutgoing() {
        return outgoing;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public int getCount() {
        return count;
    }

    public void incrementCount() {
        count++;
    }

    public int getMaxFadeSeconds() {
        return maxFadeSeconds;
    }
}
