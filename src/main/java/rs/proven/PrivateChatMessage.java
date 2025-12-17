package rs.proven;

public class PrivateChatMessage {
    private final String sender;
    private final String message;
    private final long timestamp;
    private final boolean outgoing;

    public PrivateChatMessage(String sender, String message, long timestamp, boolean outgoing) {
        this.sender = sender;
        this.message = message;
        this.timestamp = timestamp;
        this.outgoing = outgoing;
    }

    public String getSender() {
        return sender;
    }

    public String getMessage() {
        return message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isOutgoing() {
        return outgoing;
    }
}
