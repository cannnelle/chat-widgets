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

    private static final MessageMergeRule[] MESSAGE_MERGE_RULES = {
            new MessageMergeRule("You eat", "It heals some health.", true),
            new MessageMergeRule("You drink", "You have", false)
    };

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
        //keep history for now i think
        //clearGameMessages();
        //clearPrivateMessages();
        showPmWidgets();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals("chatwidgets")) {
            return;
        }

        if (event.getKey().equals("enablePrivateMessages")) {
            if (config.enablePrivateMessages()) {
                hidePmWidgets();
            } else {
                showPmWidgets();
            }
        }

        if (event.getKey().equals("swapStackingOrder")) {
            overlayManager.remove(gameOverlay);
            overlayManager.remove(privateOverlay);
            overlayManager.add(gameOverlay);
            overlayManager.add(privateOverlay);
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN) {
            //keep history for now i think
            // clearGameMessages();
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
            switch (target) {
                case "Merged chat history":
                    clearGameMessages();
                    clearPrivateMessages();
                    break;
                case "Game chat history":
                    clearGameMessages();
                    break;
                case "Private chat history":
                    clearPrivateMessages();
                    break;
            }
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        ChatMessageType type = event.getType();

        switch (type) {
            case GAMEMESSAGE:
            case SPAM:
            case CONSOLE:
            case WELCOME:
            case BROADCAST:
            case DIDYOUKNOW:
            case ENGINE:
            case FRIENDNOTIFICATION:
            case FRIENDSCHATNOTIFICATION:
            case IGNORENOTIFICATION:
            case ITEM_EXAMINE:
            case NPC_EXAMINE:
            case OBJECT_EXAMINE:
            case PLAYERRELATED:
            case SNAPSHOTFEEDBACK:
            case TRADE:
            case TRADE_SENT:
            case TRADEREQ:
            case UNKNOWN: //combat achievements-related?
                handleGameMessage(event);
                break;

            case PRIVATECHAT:
            case PRIVATECHATOUT:
            case MODPRIVATECHAT:
                handlePrivateMessage(event);
                break;

            case LOGINLOGOUTNOTIFICATION:
                handleLoginLogoutNotification(event);
                break;

            default:
                break;
        }
    }

    private void handleGameMessage(ChatMessage event) {
        String message = event.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        String cleanMessage = message.trim();
        for (MessageMergeRule rule : MESSAGE_MERGE_RULES) {
            if (rule.matchesPreviousPrefix(cleanMessage)) {
                cleanMessage = cleanMessage.replace("<br>", " ");
                break;
            }
        }
        boolean isBossKc = BOSS_KC_PATTERN.matcher(cleanMessage).find();

        if (!gameMessages.isEmpty()) {
            WidgetMessage lastMsg = gameMessages.get(gameMessages.size() - 1);
            String merged = tryMergeMessages(lastMsg.getMessage(), cleanMessage);
            if (merged != null) {
                String mergedStripped = stripTags(merged);
                int existingCount = 0;

                if (config.collapseGameChat()) {
                    for (int i = gameMessages.size() - 2; i >= 0; i--) {
                        WidgetMessage existing = gameMessages.get(i);
                        if (stripTags(existing.getMessage()).equals(mergedStripped)) {
                            existingCount = existing.getCount();
                            gameMessages.remove(i);
                            break;
                        }
                    }
                }

                WidgetMessage mergedMsg = WidgetMessage.gameMessage(
                        merged, System.currentTimeMillis(), lastMsg.getType(), lastMsg.isBossKc());
                for (int i = 0; i < existingCount; i++) {
                    mergedMsg.incrementCount();
                }
                gameMessages.set(gameMessages.size() - 1, mergedMsg);
                return;
            }
        }

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

        while (gameMessages.size() > 50) {
            gameMessages.remove(0);
        }
    }

    private String tryMergeMessages(String previousMessage, String newMessage) {
        for (MessageMergeRule rule : MESSAGE_MERGE_RULES) {
            if (rule.matches(previousMessage, newMessage)) {
                return rule.merge(previousMessage, newMessage);
            }
        }
        return null;
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

        while (privateMessages.size() > 50) {
            privateMessages.remove(0);
        }
    }

    private void handleLoginLogoutNotification(ChatMessage event) {
        String sender = event.getName();
        if (sender != null) {
            sender = sender.replace('\u00A0', ' ').trim();
        } else {
            sender = "System";
        }

        String message = event.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        int maxFade = Math.min(5, config.privateFadeOutDuration());
        if (maxFade <= 0) {
            maxFade = 5;
        }

        privateMessages.add(WidgetMessage.loginNotification(
                sender, message.trim(), System.currentTimeMillis(), maxFade));

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

        int maxMessages = config.gameMaxMessages();
        List<WidgetMessage> filtered = new ArrayList<>(maxMessages);
        for (int i = size - 1; i >= 0 && filtered.size() < maxMessages; i--) {
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

            filtered.add(0, msg);
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
        long defaultFadeOutThreshold = fadeOutDuration > 0 ? (fadeOutDuration * 2000L) + 2000 : 0;

        int maxMessages = config.privateMaxMessages();
        List<WidgetMessage> filtered = new ArrayList<>(maxMessages);
        int pmCount = 0;
        for (int i = size - 1; i >= 0; i--) {
            WidgetMessage msg = privateMessages.get(i);

            int msgMaxFade = msg.getMaxFadeSeconds();
            long fadeOutThreshold;
            if (msgMaxFade > 0) {
                fadeOutThreshold = (msgMaxFade * 1000L) + 2000;
            } else {
                fadeOutThreshold = defaultFadeOutThreshold;
            }

            if (fadeOutThreshold > 0 && currentTime - msg.getTimestamp() >= fadeOutThreshold) {
                continue;
            }

            boolean isLoginNotification = msg.getType() == ChatMessageType.LOGINLOGOUTNOTIFICATION;
            if (!isLoginNotification && pmCount >= maxMessages) {
                continue;
            }

            filtered.add(0, msg);
            if (!isLoginNotification) {
                pmCount++;
            }
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
        //hide all regardless idk if this will hide something unintentionally?
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
