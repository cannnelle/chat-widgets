package com.chatwidgets;

import com.google.inject.Provides;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

@PluginDescriptor(name = "Chat Widgets", description = "Displays game and private chat messages in customizable overlay widgets.", tags = {
        "game", "private", "chat", "pm", "message", "widget", "overlay", "split", "move", "custom", "customize",
        "resizable", "transparent" })
public class ChatWidgetPlugin extends Plugin {

    private static final Pattern BOSS_KC_PATTERN = Pattern.compile("Your .+ count is:");

    @Inject
    private Client client;

    @Inject
    private ChatWidgetConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private GameChatOverlay gameOverlay;

    @Inject
    private PrivateChatOverlay privateOverlay;

    private final CopyOnWriteArrayList<com.chatwidgets.ChatMessage> gameMessages = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<com.chatwidgets.ChatMessage> privateMessages = new CopyOnWriteArrayList<>();

    @Override
    protected void startUp() {
        overlayManager.add(gameOverlay);
        overlayManager.add(privateOverlay);

        if (config.enablePrivateMessages()) {
            hidePmWidgets();
        }
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(gameOverlay);
        overlayManager.remove(privateOverlay);
        clearGameMessages();
        clearPrivateMessages();
        showPmWidgets();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!"chatwidgets".equals(event.getGroup())) {
            return;
        }

        if ("enablePrivateMessages".equals(event.getKey())) {
            if (config.enablePrivateMessages()) {
                hidePmWidgets();
            } else {
                showPmWidgets();
            }
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN) {
            clearGameMessages();
        }
        if (event.getGameState() == GameState.LOGGED_IN && config.enablePrivateMessages()) {
            hidePmWidgets();
        }
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event) {
        if (event.getGroupId() == InterfaceID.PM_CHAT && config.enablePrivateMessages()) {
            hidePmWidgets();
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        String option = event.getMenuOption();
        if (option == null) {
            return;
        }

        if (option.contains("Game:") && option.contains("Clear history")) {
            clearGameMessages();
        }
        if (option.contains("Private:") && option.contains("Clear history")) {
            clearPrivateMessages();
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        ChatMessageType type = event.getType();

        if (type == ChatMessageType.GAMEMESSAGE
                || type == ChatMessageType.SPAM
                || type == ChatMessageType.CONSOLE
                || type == ChatMessageType.WELCOME) {
            handleGameMessage(event);
        } else if (type == ChatMessageType.PRIVATECHAT || type == ChatMessageType.PRIVATECHATOUT) {
            handlePrivateMessage(event);
        }
    }

    private void handleGameMessage(ChatMessage event) {
        String message = event.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        String cleanMessage = message.trim();
        boolean isBossKc = BOSS_KC_PATTERN.matcher(cleanMessage).find();

        gameMessages.add(com.chatwidgets.ChatMessage.gameMessage(
                cleanMessage, System.currentTimeMillis(), event.getType(), isBossKc));

        while (gameMessages.size() > config.gameMaxMessages() * 2) {
            gameMessages.remove(0);
        }
    }

    private void handlePrivateMessage(ChatMessage event) {
        boolean isOutgoing = event.getType() == ChatMessageType.PRIVATECHATOUT;

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

        privateMessages.add(com.chatwidgets.ChatMessage.privateMessage(
                sender, message.trim(), System.currentTimeMillis(), isOutgoing));

        while (privateMessages.size() > config.privateMaxMessages() * 2) {
            privateMessages.remove(0);
        }
    }

    public boolean shouldShowGameOverlay() {
        if (!config.enableGameMessages()) {
            return false;
        }
        if (client.getGameState() != GameState.LOGGED_IN) {
            return false;
        }
        if (!client.isResized()) {
            return false;
        }
        return isChatboxHidden();
    }

    public boolean shouldShowPrivateOverlay() {
        return config.enablePrivateMessages();
    }

    private boolean isChatboxHidden() {
        return client.getVarcIntValue(VarClientID.CHAT_VIEW) == 1337;
    }

    public boolean isGameFilterEnabled() {
        return client.getVarbitValue(VarbitID.GAME_FILTER) == 1;
    }

    public boolean isBossKcFilterEnabled() {
        return client.getVarbitValue(VarbitID.BOSS_KILLCOUNT_FILTERED) == 1;
    }

    public List<com.chatwidgets.ChatMessage> getGameMessages() {
        int size = gameMessages.size();
        if (size == 0) {
            return new ArrayList<>(0);
        }

        long currentTime = System.currentTimeMillis();
        int fadeOutDuration = config.gameFadeOutDuration();
        long fadeOutThreshold = fadeOutDuration > 0 ? (fadeOutDuration * 2000L) + 2000 : 0;
        boolean gameFilterEnabled = isGameFilterEnabled();
        boolean bossKcFilterEnabled = isBossKcFilterEnabled();

        List<com.chatwidgets.ChatMessage> filtered = new ArrayList<>(Math.min(size, config.gameMaxMessages()));
        for (int i = 0; i < size; i++) {
            com.chatwidgets.ChatMessage msg = gameMessages.get(i);

            if (fadeOutThreshold > 0 && currentTime - msg.getTimestamp() >= fadeOutThreshold) {
                continue;
            }

            if (gameFilterEnabled && msg.getType() == ChatMessageType.SPAM) {
                continue;
            }

            if (bossKcFilterEnabled && msg.isBossKc()) {
                continue;
            }

            filtered.add(msg);
        }

        return filtered;
    }

    public List<com.chatwidgets.ChatMessage> getPrivateMessages() {
        int size = privateMessages.size();
        if (size == 0) {
            return new ArrayList<>(0);
        }

        long currentTime = System.currentTimeMillis();
        int fadeOutDuration = config.privateFadeOutDuration();
        long fadeOutThreshold = fadeOutDuration > 0 ? (fadeOutDuration * 2000L) + 2000 : 0;

        List<com.chatwidgets.ChatMessage> filtered = new ArrayList<>(Math.min(size, config.privateMaxMessages()));
        for (int i = 0; i < size; i++) {
            com.chatwidgets.ChatMessage msg = privateMessages.get(i);

            if (fadeOutThreshold > 0 && currentTime - msg.getTimestamp() >= fadeOutThreshold) {
                continue;
            }

            filtered.add(msg);
        }

        return filtered;
    }

    public void clearGameMessages() {
        gameMessages.clear();
    }

    public void clearPrivateMessages() {
        privateMessages.clear();
    }

    private void hidePmWidgets() {
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

    @Provides
    ChatWidgetConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ChatWidgetConfig.class);
    }
}
