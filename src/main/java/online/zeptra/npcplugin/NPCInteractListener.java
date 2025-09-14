package online.zeptra.npcplugin;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class NPCInteractListener implements Listener {
    private final SellGUI sellGUI;
    private final NPCManager npcManager;

    public NPCInteractListener(SellGUI sellGUI, NPCManager npcManager) {
        this.sellGUI = sellGUI;
        this.npcManager = npcManager;
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();

        if (!(event.getRightClicked() instanceof LivingEntity)) {
            return;
        }

        LivingEntity entity = (LivingEntity) event.getRightClicked();

        TraderNPC npc = npcManager.getNPCByEntity(entity);
        if (npc == null) {
            return;
        }

        event.setCancelled(true);

        if (!npc.isEnabled()) {
            player.sendMessage(npc.getGreeting());
            return;
        }

        if (!player.hasPermission("npcplugin.sell")) {
            player.sendMessage("§cYou don't have permission to trade with NPCs!");
            return;
        }

        sellGUI.openSellGUI(player, npc);
    }
}