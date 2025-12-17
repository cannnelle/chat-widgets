package com.privatechatwidget;

import com.google.inject.Provides;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@PluginDescriptor(name = "Private Chat Widget", description = "Displays private chat messages in a customizable overlay widget. Disabling the 'Split friends private chat' setting is recommended.", tags = {
        "private", "chat", "pm", "message", "widget", "overlay", "pms", "split" })
public class PrivateChatWidgetPlugin extends Plugin {

    @Inject
    private PrivateChatWidgetConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private PrivateChatWidgetOverlay overlay;

    private final CopyOnWriteArrayList<PrivateChatMessage> messages = new CopyOnWriteArrayList<>();

    @Override
    protected void startUp() {
        overlayManager.add(overlay);
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(overlay);
        messages.clear();
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        ChatMessageType type = event.getType();
        boolean isIncoming = type == ChatMessageType.PRIVATECHAT;
        boolean isOutgoing = type == ChatMessageType.PRIVATECHATOUT;

        if (!isIncoming && !isOutgoing) {
            return;
        }

        String sender = event.getName();
        if (sender != null) {
            sender = sender.replace('\u00A0', ' ').trim();
        } else {
            sender = "Unknown";
        }

        String message = event.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        messages.add(new PrivateChatMessage(sender, message.trim(), System.currentTimeMillis(), isOutgoing));

        while (messages.size() > config.maxMessages() * 2) {
            messages.remove(0);
        }
    }

    public List<PrivateChatMessage> getMessages() {
        long currentTime = System.currentTimeMillis();
        long fadeOutMs = config.fadeOutDuration() * 1000L;

        if (fadeOutMs > 0) {
            List<PrivateChatMessage> filtered = new ArrayList<>();
            for (PrivateChatMessage msg : messages) {
                if (currentTime - msg.getTimestamp() < fadeOutMs + 2000) {
                    filtered.add(msg);
                }
            }
            return filtered;
        }
        return new ArrayList<>(messages);
    }

    @Provides
    PrivateChatWidgetConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(PrivateChatWidgetConfig.class);
    }
}
