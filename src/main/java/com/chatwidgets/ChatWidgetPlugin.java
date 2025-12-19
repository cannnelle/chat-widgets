package com.chatwidgets;

import com.google.inject.Provides;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ResizeableChanged;
import net.runelite.api.events.VarClientIntChanged;
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
import net.runelite.client.ui.overlay.OverlayPosition;

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

    private final CopyOnWriteArrayList<WidgetMessage> gameMessages = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<WidgetMessage> privateMessages = new CopyOnWriteArrayList<>();

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

        if ("swapStackingOrder".equals(event.getKey())) {
            overlayManager.remove(gameOverlay);
            overlayManager.remove(privateOverlay);
            overlayManager.add(gameOverlay);
            overlayManager.add(privateOverlay);
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
    public void onVarClientIntChanged(VarClientIntChanged event) {
        if (event.getIndex() == VarClientID.CHAT_VIEW) {
            updateSmartPosition(gameOverlay);
            updateSmartPosition(privateOverlay);
        }
    }

    @Subscribe
    public void onResizeableChanged(ResizeableChanged event) {
        updateDefaultPosition(gameOverlay);
        updateDefaultPosition(privateOverlay);
        updateSmartPosition(gameOverlay);
        updateSmartPosition(privateOverlay);
    }

    private void updateDefaultPosition(net.runelite.client.ui.overlay.Overlay overlay) {
        OverlayPosition defaultPos = client.isResized()
                ? OverlayPosition.ABOVE_CHATBOX_RIGHT
                : OverlayPosition.BOTTOM_LEFT;
        overlay.setPosition(defaultPos);
    }

    private void updateSmartPosition(net.runelite.client.ui.overlay.Overlay overlay) {
        if (!config.smartPositioning()) {
            return;
        }

        OverlayPosition currentPos = overlay.getPreferredPosition() != null
                ? overlay.getPreferredPosition()
                : overlay.getPosition();

        if (isTopPosition(currentPos)) {
            return;
        }

        OverlayPosition targetPos;

        if (!client.isResized()) {
            targetPos = OverlayPosition.BOTTOM_LEFT;
        } else if (isChatboxHidden()) {
            targetPos = OverlayPosition.ABOVE_CHATBOX_RIGHT;
        } else {
            return;
        }

        if (currentPos != targetPos) {
            overlay.setPreferredPosition(targetPos);
        }
    }

    private boolean isTopPosition(OverlayPosition position) {
        return position == OverlayPosition.TOP_CENTER
                || position == OverlayPosition.TOP_RIGHT
                || position == OverlayPosition.TOP_LEFT
                || position == OverlayPosition.CANVAS_TOP_RIGHT;
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        String option = event.getMenuOption();
        String target = event.getMenuTarget();
        if (option == null) {
            return;
        }

        if (option.contains("Game:") && option.contains("Clear")) {
            clearGameMessages();
            return;
        }
        if (option.contains("Private:") && option.contains("Clear")) {
            clearPrivateMessages();
            return;
        }

        if (option.equals("Clear") && target != null) {
            if (target.contains("Merged")) {
                clearGameMessages();
                clearPrivateMessages();
            } else if (target.contains("Game")) {
                clearGameMessages();
            } else if (target.contains("Private")) {
                clearPrivateMessages();
            }
        }

        if (option.contains("Game:") && option.contains("Clear")) {
            clearGameMessages();
        }
        if (option.contains("Private:") && option.contains("Clear")) {
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
        String currentStripped = stripTags(cleanMessage);
        int existingCount = 0;

        if (config.collapseGameChat()) {
            for (int i = gameMessages.size() - 1; i >= 0; i--) {
                WidgetMessage existing = gameMessages.get(i);
                if (stripTags(existing.getMessage()).equals(currentStripped)) {
                    existingCount = existing.getCount();
                    gameMessages.remove(i);
                    break;
                }
            }
        }

        WidgetMessage newMsg = WidgetMessage.gameMessage(
                cleanMessage, System.currentTimeMillis(), event.getType(), isBossKc);
        for (int i = 0; i < existingCount; i++) {
            newMsg.incrementCount();
        }
        gameMessages.add(newMsg);

        while (gameMessages.size() > config.gameMaxMessages() * 2) {
            gameMessages.remove(0);
        }
    }

    private String stripTags(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("<[^>]*>", "");
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

        privateMessages.add(WidgetMessage.privateMessage(
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

    public boolean isChatboxHidden() {
        return client.getVarcIntValue(VarClientID.CHAT_VIEW) == 1337;
    }

    public boolean isWidgetsMerged() {
        return client.isResized()
                && isChatboxHidden()
                && config.mergeWithGameWidget()
                && config.enableGameMessages()
                && config.enablePrivateMessages()
                && config.gamePosition() == WidgetPosition.DEFAULT;
    }

    public boolean isGameFilterEnabled() {
        return client.getVarbitValue(VarbitID.GAME_FILTER) == 1;
    }

    public boolean isBossKcFilterEnabled() {
        return client.getVarbitValue(VarbitID.BOSS_KILLCOUNT_FILTERED) == 1;
    }

    public List<WidgetMessage> getGameMessages() {
        int size = gameMessages.size();
        if (size == 0) {
            return new ArrayList<>(0);
        }

        long currentTime = System.currentTimeMillis();
        int fadeOutDuration = config.gameFadeOutDuration();
        long fadeOutThreshold = fadeOutDuration > 0 ? (fadeOutDuration * 2000L) + 2000 : 0;
        boolean gameFilterEnabled = isGameFilterEnabled();
        boolean bossKcFilterEnabled = isBossKcFilterEnabled();

        List<WidgetMessage> filtered = new ArrayList<>(Math.min(size, config.gameMaxMessages()));
        for (int i = 0; i < size; i++) {
            WidgetMessage msg = gameMessages.get(i);

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

    public List<WidgetMessage> getPrivateMessages() {
        int size = privateMessages.size();
        if (size == 0) {
            return new ArrayList<>(0);
        }

        long currentTime = System.currentTimeMillis();
        int fadeOutDuration = config.privateFadeOutDuration();
        long fadeOutThreshold = fadeOutDuration > 0 ? (fadeOutDuration * 2000L) + 2000 : 0;

        List<WidgetMessage> filtered = new ArrayList<>(Math.min(size, config.privateMaxMessages()));
        for (int i = 0; i < size; i++) {
            WidgetMessage msg = privateMessages.get(i);

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
