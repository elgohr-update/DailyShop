package io.github.divios.lib.dLib;

import com.google.gson.JsonElement;
import io.github.divios.core_lib.events.Events;
import io.github.divios.core_lib.events.Subscription;
import io.github.divios.core_lib.misc.timeStampUtils;
import io.github.divios.core_lib.scheduler.Schedulers;
import io.github.divios.core_lib.scheduler.Task;
import io.github.divios.dailyShop.DailyShop;
import io.github.divios.dailyShop.events.reStockShopEvent;
import io.github.divios.dailyShop.events.updateItemEvent;
import io.github.divios.dailyShop.files.Settings;
import io.github.divios.dailyShop.guis.settings.shopGui;
import io.github.divios.dailyShop.utils.DebugLog;
import io.github.divios.lib.dLib.dTransaction.Bill;
import io.github.divios.lib.dLib.log.dLog;
import io.github.divios.lib.dLib.log.options.dLogEntry;
import io.github.divios.lib.dLib.synchronizedGui.singleGui.dInventory;
import io.github.divios.lib.dLib.synchronizedGui.syncHashMenu;
import io.github.divios.lib.dLib.synchronizedGui.syncMenu;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class dShop {

    protected static final DailyShop plugin = DailyShop.get();

    protected String name;
    protected Map<UUID, newDItem> items = Collections.synchronizedMap(new LinkedHashMap<>());
    protected syncHashMenu guis;
    protected Timestamp timestamp;
    protected int timer;
    protected boolean announce_restock = true;
    protected boolean isDefault = false;

    protected Set<Task> tasks = new HashSet<>();
    protected Set<Subscription> listeners = new HashSet<>();

    public dShop(String name) {
        this(name, Settings.DEFAULT_TIMER.getValue().getAsInt());
    }

    public dShop(String name, int timer) {
        this(name, timer, new Timestamp(System.currentTimeMillis()), Collections.EMPTY_LIST);
    }

    public dShop(String name, int timer, Timestamp timestamp) {
        this(name, timer, timestamp, Collections.EMPTY_LIST);
    }

    public dShop(String name, int timer, Timestamp timestamp, Collection<newDItem> items) {
        this.name = name.toLowerCase();
        this.timer = timer;
        this.timestamp = timestamp;
        this.guis = syncHashMenu.create(this);
        items.forEach(dItem -> this.items.put(dItem.getUUID(), dItem));

        startTimerTask();
        startListeners();
    }

    public dShop(String name, JsonElement gui, Timestamp timestamp, int timer) {
        this(name, gui, timestamp, timer, new HashSet<>());
    }

    public dShop(String name, JsonElement gui, Timestamp timestamp, int timer, Set<newDItem> items) {
        this.name = name.toLowerCase();
        this.timestamp = timestamp;
        this.timer = timer;
        items.forEach(dItem -> this.items.put(dItem.getUUID(), dItem));

        guis = syncHashMenu.fromJson(gui, this);
        startTimerTask();
        startListeners();
    }

    protected void startTimerTask() {
        tasks.add(
                Schedulers.async().runRepeating(() -> {

                    if (timer == -1) return;
                    if (timeStampUtils.diff(timestamp, new Timestamp(System.currentTimeMillis())) >= timer)
                        Schedulers.sync().run(this::reStock);

                }, 1, TimeUnit.SECONDS, 1, TimeUnit.SECONDS)
        );
    }

    protected void startListeners() {
        listeners.add(
                Events.subscribe(updateItemEvent.class)
                        .filter(o -> o.getShop().equals(this))
                        .handler(guis::updateItem)
        );
    }

    /**
     * Opens the actual shop for the player
     */
    public void openShop(Player p) {
        guis.generate(p);
    }

    /**
     * Opens the gui to manage the items of this shop
     */
    public void manageItems(Player p) {
        shopGui.open(p, this);
    }

    /**
     * Opens the gui to customize the display of this shop
     */
    public void openCustomizeGui(Player p) {
        guis.customizeGui(p);
    }

    /**
     * Gets the name of the shop
     */
    public String getName() {
        return name.toLowerCase();
    }

    /**
     * Sets the name of the shop
     */
    public void rename(String name) {
        this.name = name;
    }

    /**
     * Returns the amount of items in this shop
     */

    public int size() {
        return items.size();
    }

    /**
     * Gets a copy the items in the shop
     *
     * @return returns a List of dItems. Note that this list is a copy of the original,
     * any change made to it won't affect the original one
     */
    public @NotNull
    Set<newDItem> getItems() {
        return items.values().stream()
                .filter(Objects::nonNull)
                .map(newDItem::clone)
                .collect(Collectors.toSet());
    }

    /**
     * Gets the item by ID
     *
     * @param ID the ID to search
     * @return null if it does not exist
     */
    public @Nullable
    newDItem getItem(@NotNull String ID) {
        return getItem(UUID.nameUUIDFromBytes(ID.getBytes()));
    }

    /**
     * Gets the item by uuid
     *
     * @param uid the UUID to search
     * @return null if it does not exist
     */
    public @Nullable
    newDItem getItem(@NotNull UUID uid) {
        newDItem item;
        return (item = items.get(uid)) == null ? null : item.clone();
    }

    public boolean hasItem(@NotNull String id) {
        return hasItem(UUID.nameUUIDFromBytes(id.getBytes()));
    }

    /**
     * Checks if the shop has a particular item
     *
     * @param uid the UUID to check
     * @return true if exits, false if not
     */
    public boolean hasItem(UUID uid) {
        return items.containsKey(uid);
    }

    /**
     * Restocks the items of this shop.
     */
    public void reStock() {
        timestamp = new Timestamp(System.currentTimeMillis());
        Events.callEvent(new reStockShopEvent(this));
        guis.reStock(!announce_restock);
        DebugLog.info("Time elapsed to restock shop " + name + ": " + (new Timestamp(System.currentTimeMillis()).getTime() - timestamp.getTime()));
    }

    /**
     * Updates the item of the shop
     */
    public void updateItem(@NotNull newDItem newItem) {
        UUID uid = newItem.getUUID();

        if (!items.containsKey(uid)) {
            addItem(newItem);
            return;
        }

        items.put(uid, newItem);
        guis.updateItem(new updateItemEvent(uid, updateItemEvent.type.UPDATE_ITEM, this));    // Event to update item
    }


    /**
     * Sets the items of this shop
     */
    public void setItems(@NotNull Collection<newDItem> items) {
        DebugLog.info("Setting items");
        Map<UUID, newDItem> newItems = new HashMap<>();
        items.forEach(dItem -> newItems.put(dItem.getUUID(), dItem));            // Cache values for a O(1) search

        for (Map.Entry<UUID, newDItem> entry : new HashMap<>(this.items).entrySet()) {          // Remove or update
            if (newItems.containsKey(entry.getKey())) {     // Update items if changed
                newDItem toUpdateItem = newItems.remove(entry.getKey());

                if (toUpdateItem != null && !toUpdateItem.isSimilar(entry.getValue())) {
                    DebugLog.info("Updating item with ID: " + toUpdateItem.getID() + " from dShop");
                    updateItem(toUpdateItem);
                }
            } else {
                DebugLog.info("Removing item with ID: " + entry.getValue().getID() + " from dShop");
                removeItem(entry.getKey());
            }
        }

        newItems.values().forEach(newDItem -> {
            this.addItem(newDItem);             // Add newItems
            DebugLog.info("Added new item with ID: " + newDItem.getID() + " from dShop");
        });
    }

    /**
     * Adds an item to this shop
     *
     * @param item item to be added
     */
    public void addItem(@NotNull newDItem item) {
        items.put(item.getUUID(), item);
    }

    /**
     * Removes an item from the shop
     *
     * @param uid UUID of the item to be removed
     * @return true if the item was removed. False if not
     */
    public boolean removeItem(UUID uid) {
        newDItem removed = items.remove(uid);
        if (removed == null) return false;
        guis.updateItem(new updateItemEvent(uid, updateItemEvent.type.DELETE_ITEM, this));
        return true;
    }

    public void updateShopGui(dInventory inv) {
        guis.updateBase(inv);
    }

    public void updateShopGui(dInventory inv, boolean isSilent) {
        guis.updateBase(inv, isSilent);
    }

    public void computeBill(Bill bill) {
        DebugLog.info("Received bill on shop " + name);
        bill.getBillTable().forEach((s, entry) -> {

            newDItem shopItem = getItem(s);
            if (shopItem == null) return;

            if (shopItem.getDStock() != null)
                guis.updateItem(new updateItemEvent(bill.getPlayer(),
                                shopItem.getUUID(),
                                entry.getValue(),
                                updateItemEvent.type.NEXT_AMOUNT,
                                this
                        )
                );

            dLog.log(
                    dLogEntry.builder()
                            .withPlayer(bill.getPlayer())
                            .withShopID(name)
                            .withItemID(s)
                            .withRawItem(shopItem.getItem())
                            .withQuantity(entry.getValue())
                            .withType(dLogEntry.Type.valueOf(bill.getType().name()))
                            .withPrice(entry.getKey())
                            .build()
            );
        });
    }

    /**
     * Return the dGui of this shop
     */
    public syncMenu getGuis() {
        return guis;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    public Timestamp getTimestamp() {
        return this.timestamp;
    }

    public int getTimer() {
        return timer;
    }

    public boolean get_announce() {
        return announce_restock;
    }

    public void set_announce(boolean announce_restock) {
        this.announce_restock = announce_restock;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setTimer(int timer) {
        this.timer = timer;
    }

    public void destroy() {
        guis.destroy();
        tasks.forEach(Task::stop);
        tasks.clear();
        listeners.forEach(Subscription::unregister);
        listeners.clear();
    }

    @Override
    public String toString() {
        return "dShop{" +
                "name='" + name + '\'' +
                ", items=" + items +
                ", guis=" + guis +
                ", timestamp=" + timestamp +
                ", timer=" + timer +
                ", tasks=" + tasks +
                ", listeners=" + listeners +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof dShop && this.getName().equalsIgnoreCase(((dShop) o).getName());
    }

    @Override
    public int hashCode() {
        return this.getName().hashCode();
    }

}
