package rs.proven;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PrivateChatWidgetPluginTest {
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(PrivateChatWidgetPlugin.class);
        RuneLite.main(args);
    }
}
