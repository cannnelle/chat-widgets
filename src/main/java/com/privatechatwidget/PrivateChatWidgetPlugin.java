package com.privatechatwidget;

import com.google.inject.Provides;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@PluginDescriptor(name = "Private Chat Widget", description = "Displays private chat messages in a customizable overlay widget.", tags = {
        "private", "chat", "pm", "message", "widget", "overlay", "pms", "split", "move", "custom", "customize" })
public class PrivateChatWidgetPlugin extends Plugin {

    @Inject
    private Client client;

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
        hidePmWidgets();
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(overlay);
        showPmWidgets();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGGED_IN) {
            hidePmWidgets();
        }
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event) {
        if (event.getGroupId() == InterfaceID.PM_CHAT) {
            hidePmWidgets();
        }
    }

    private void hidePmWidgets() {
        //probably a better way to do this lol
        hideGameframePmContainer(InterfaceID.TOPLEVEL, 36);
        hideGameframePmContainer(InterfaceID.TOPLEVEL_OSRS_STRETCH, 93);
        hideGameframePmContainer(InterfaceID.TOPLEVEL_PRE_EOC, 90);

        Widget pmContainer = client.getWidget(InterfaceID.PM_CHAT, 0);
        if (pmContainer != null) {
            pmContainer.setHidden(true);
            Widget[] dynamicChildren = pmContainer.getDynamicChildren();
            if (dynamicChildren != null) {
                for (Widget child : dynamicChildren) {
                    if (child != null) {
                        child.setHidden(true);
                    }
                }
            }
        }
    }

    private void showPmWidgets() {
        showGameframePmContainer(InterfaceID.TOPLEVEL, 36);
        showGameframePmContainer(InterfaceID.TOPLEVEL_OSRS_STRETCH, 93);
        showGameframePmContainer(InterfaceID.TOPLEVEL_PRE_EOC, 90);

        Widget pmContainer = client.getWidget(InterfaceID.PM_CHAT, 0);
        if (pmContainer != null) {
            pmContainer.setHidden(false);
            Widget[] dynamicChildren = pmContainer.getDynamicChildren();
            if (dynamicChildren != null) {
                for (Widget child : dynamicChildren) {
                    if (child != null) {
                        child.setHidden(false);
                    }
                }
            }
        }
    }

    private void hideGameframePmContainer(int group, int child) {
        Widget container = client.getWidget(group, child);
        if (container != null) {
            container.setHidden(true);
        }
    }

    private void showGameframePmContainer(int group, int child) {
        Widget container = client.getWidget(group, child);
        if (container != null) {
            container.setHidden(false);
        }
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

    public void clearMessages() {
        messages.clear();
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (event.getMenuOption().contains("Private:") && event.getMenuOption().contains("Clear history")) {
            clearMessages();
        }
    }

    @Provides
    PrivateChatWidgetConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(PrivateChatWidgetConfig.class);
    }
}
