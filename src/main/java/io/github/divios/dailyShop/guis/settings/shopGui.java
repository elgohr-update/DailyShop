package io.github.divios.dailyShop.guis.settings;

import com.cryptomorin.xseries.XMaterial;
import io.github.divios.core_lib.inventory.InventoryGUI;
import io.github.divios.core_lib.inventory.ItemButton;
import io.github.divios.core_lib.inventory.inventoryUtils;
import io.github.divios.core_lib.itemutils.ItemBuilder;
import io.github.divios.core_lib.misc.Task;
import io.github.divios.dailyShop.DRShop;
import io.github.divios.dailyShop.conf_msg;
import io.github.divios.dailyShop.guis.confirmIH;
import io.github.divios.dailyShop.guis.customizerguis.customizerMainGuiIH;
import io.github.divios.dailyShop.lorestategy.loreStrategy;
import io.github.divios.dailyShop.lorestategy.shopItemsManagerLore;
import io.github.divios.lib.dLib.dItem;
import io.github.divios.lib.dLib.dShop;
import io.github.divios.lib.managers.shopsManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class shopGui {

    private static final DRShop plugin = DRShop.getInstance();
    private static final shopsManager sManager = shopsManager.getInstance();

    private final List<InventoryGUI> invs = new ArrayList<>();
    private final Player p;
    private final dShop shop;
    private final List<ItemStack> items;

    private shopGui(Player p, dShop shop) {
        loreStrategy strategy = new shopItemsManagerLore(shop.getType());

        this.p = p;
        this.shop = shop;
        this.items = shop.getItems().stream()
                .map(dItem -> dItem.getItem().clone())
                .collect(Collectors.toList());

        items.parallelStream().forEach(strategy::setLore);

        createGuis();
        invs.get(0).open(p);
    }

    public static void open(Player p, String shop) {
        sManager.getShop(shop).ifPresent(shop1 -> open(p, shop1));
    }

    public static void open(Player p, dShop shop) {
        new shopGui(p, shop);
    }

    private void createGuis() {
        IntStream.range(0, items.isEmpty() ? 1 : (int) Math.ceil(items.size() / 32D))
                .forEach(value -> invs.add(new InventoryGUI(plugin, 54, "&f&l" + shop.getName())));

        if (items.size() > 32 * 2)
            invs.parallelStream().forEach(inventoryGUI ->
                    createGui(invs.indexOf(inventoryGUI), inventoryGUI));

        else
            invs.forEach(inventoryGUI ->
                    createGui(invs.indexOf(inventoryGUI), inventoryGUI));
    }

    private void createGui(int index, InventoryGUI inventoryGUI) {

        inventoryGUI.setDestroyOnClose(false);

        IntStream.of(0, 1, 9, 7, 8, 17, 45, 46, 36, 52, 53, 44).forEach(value ->
                inventoryGUI.addButton(new ItemButton(
                        new ItemBuilder(XMaterial.GREEN_STAINED_GLASS_PANE)
                                .setName("&c"), e -> {
                }), value));

        IntStream.of(2, 6, 47, 51).forEach(value ->
                inventoryGUI.addButton(new ItemButton(
                        new ItemBuilder(XMaterial.LIME_STAINED_GLASS_PANE)
                                .setName("&c"), e -> {
                }), value));

        IntStream.of(3, 4, 5, 48, 49, 50).forEach(value ->
                inventoryGUI.addButton(new ItemButton(
                        new ItemBuilder(XMaterial.WHITE_STAINED_GLASS_PANE)
                                .setName("&c"), e -> {
                }), value));

        if (index != invs.size() - 1) {      // next buttom
            inventoryGUI.addButton(new ItemButton(
                    new ItemBuilder(XMaterial.PLAYER_HEAD)
                            .setName("&1Next").applyTexture("19bf3292e126a105b54eba713aa1b152d541a1d8938829c56364d178ed22bf"),
                    e -> invs.get(index + 1).open(p)), 51);
        }

        if (index != 0) {                   // previous buttom
            inventoryGUI.addButton(new ItemButton(
                    new ItemBuilder(XMaterial.PLAYER_HEAD)
                            .setName("&1Previous").applyTexture("bd69e06e5dadfd84e5f3d1c21063f2553b2fa945ee1d4d7152fdc5425bc12a9"),
                    e -> invs.get(index - 1).open(p)), 47);
        }

        inventoryGUI.addButton(new ItemButton(new ItemBuilder(XMaterial.PLAYER_HEAD)
                .setName("&cReturn").setLore("&7Click to return")
                .applyTexture("19bf3292e126a105b54eba713aa1b152d541a1d8938829c56364d178ed22bf")
                , e -> {
            Task.syncDelayed(plugin, this::destroyAll, 3L);
            shopsManagerGui.open(p);
        }), 8);

        inventoryGUI.addButton(new ItemButton(new ItemBuilder(XMaterial.PLAYER_HEAD)
                .setName(conf_msg.DAILY_ITEMS_MENU_ADD).addLore(conf_msg.DAILY_ITEMS_MENU_ADD_LORE)
                .applyTexture("9b425aa3d94618a87dac9c94f377af6ca4984c07579674fad917f602b7bf235"),
                e -> addDailyGuiIH.open(p, shop, itemStack -> {
                    shop.addItem(new dItem(itemStack));
                    refresh();
                }, this::refresh)), 53);

        for (int i = index * 32; i < (index + 1) * 32; i++) {

            int slot = inventoryUtils.getFirstEmpty(inventoryGUI.getInventory());

            if (slot == -1 || i >= items.size()) break;

            inventoryGUI.addButton(new ItemButton(new ItemBuilder(items.get(i)),
                    e -> {

                        Player p = (Player) e.getWhoClicked();
                        UUID uid = dItem.getUid(e.getCurrentItem());

                        if (e.isLeftClick()) {
                            Task.syncDelayed(plugin, this::destroyAll, 3L);
                            customizerMainGuiIH.open((Player) e.getWhoClicked(),
                                    shop.getItem(uid).get(), shop);
                        } else if (e.isRightClick())
                            new confirmIH(p, (player, aBoolean) -> {
                                if (aBoolean)
                                    shop.removeItem(uid);
                                Task.syncDelayed(plugin, this::destroyAll, 3L);
                                open(p, shop);
                            }, e.getCurrentItem(),
                                    conf_msg.CONFIRM_GUI_ACTION_NAME, conf_msg.CONFIRM_MENU_YES, conf_msg.CONFIRM_MENU_NO);
                    }), slot);
        }


    }

    private void refresh() {
        Task.syncDelayed(plugin, this::destroyAll, 3L);
        open(p, shop);
    }

    private void destroyAll() {
        invs.forEach(InventoryGUI::destroy);
    }

}
