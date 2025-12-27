package com.chatwidgets;

public class MessageMergeRule {
    private final String previousPrefix;
    private final String nextPattern;
    private final boolean exactMatch;

    public MessageMergeRule(String previousPrefix, String nextPattern, boolean exactMatch) {
        this.previousPrefix = previousPrefix;
        this.nextPattern = nextPattern;
        this.exactMatch = exactMatch;
    }

    public boolean matches(String previousMessage, String newMessage) {
        if (!previousMessage.startsWith(previousPrefix)) {
            return false;
        }
        return exactMatch ? newMessage.equals(nextPattern) : newMessage.startsWith(nextPattern);
    }

    public String merge(String previousMessage, String newMessage) {
        return previousMessage + "<br>" + newMessage;
    }
}
