package com.chatwidgets;

import net.runelite.api.ChatMessageType;

public class ChatMessage {
    private final String message;
    private final long timestamp;
    private final ChatMessageType type;
    private final boolean bossKc;
    private final String sender;
    private final boolean outgoing;
    private final boolean isPrivate;

    public static ChatMessage gameMessage(String message, long timestamp, ChatMessageType type, boolean bossKc) {
        return new ChatMessage(message, timestamp, type, bossKc, null, false, false);
    }

    public static ChatMessage privateMessage(String sender, String message, long timestamp, boolean outgoing) {
        return new ChatMessage(message, timestamp,
                outgoing ? ChatMessageType.PRIVATECHATOUT : ChatMessageType.PRIVATECHAT,
                false, sender, outgoing, true);
    }

    private ChatMessage(String message, long timestamp, ChatMessageType type, boolean bossKc,
            String sender, boolean outgoing, boolean isPrivate) {
        this.message = message;
        this.timestamp = timestamp;
        this.type = type;
        this.bossKc = bossKc;
        this.sender = sender;
        this.outgoing = outgoing;
        this.isPrivate = isPrivate;
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
}
