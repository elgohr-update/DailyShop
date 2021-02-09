package io.github.divios.dailyrandomshop.builders.itemBuildersHooks;

import org.bukkit.inventory.ItemStack;

public interface itemsBuilder {

    boolean isItem(ItemStack item);
    ItemStack getItem(ItemStack item);
    String getUuid(ItemStack item);
    boolean updateItem(ItemStack toUpdate);

}
