package com.bergerkiller.bukkit.tc.controller.type;

import com.bergerkiller.bukkit.common.entity.type.CommonMinecartChest;
import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import com.bergerkiller.bukkit.common.inventory.ItemParser;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.controller.persistence.MinecartInventoryPersistentCartAttribute;
import com.bergerkiller.bukkit.tc.exception.GroupUnloadedException;
import com.bergerkiller.bukkit.tc.exception.MemberMissingException;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class MinecartMemberChest extends MinecartMember<CommonMinecartChest> {

    public MinecartMemberChest(TrainCarts plugin) {
        super(plugin);
        this.addPersistentCartAttribute(new MinecartInventoryPersistentCartAttribute());
    }

    @Override
    public void onAttached() {
        super.onAttached();
    }

    public boolean hasItem(ItemParser item) {
        if (item == null)
            return false;
        if (item.hasData()) {
            return this.hasItem(item.getType(), item.getData());
        } else {
            return this.hasItem(item.getType());
        }
    }

    public boolean hasItem(Material type, int data) {
        for (ItemStack stack : this.entity.getInventory()) {
            if (!LogicUtil.nullOrEmpty(stack)) {
                if (stack.getType() == type && stack.getDurability() == data) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasItem(Material type) {
        for (ItemStack stack : this.entity.getInventory()) {
            if (!LogicUtil.nullOrEmpty(stack)) {
                if (stack.getType() == type) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasItems() {
        for (ItemStack stack : this.entity.getInventory()) {
            if (stack != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onPhysicsPostMove() throws MemberMissingException, GroupUnloadedException {
        super.onPhysicsPostMove();
        if (this.getProperties().canPickup()) {
            Inventory inv = entity.getInventory();
            double distance;
            for (Entity e : entity.getNearbyEntities(TCConfig.itemPickupRadius)) {
                if (!(e instanceof Item) || EntityUtil.isIgnored(e)) {
                    continue;
                }
                CommonItemStack stack = CommonItemStack.of(((Item) e).getItemStack());
                distance = entity.loc.distanceSquared(e);
                if (stack.testTransferTo(inv) == stack.getAmount()) {
                    if (distance < 0.7) {
                        stack.transferAllTo(inv);
                        // This.world.playNote
                        entity.getWorld().playEffect(entity.getLocation(), Effect.CLICK1, 0);
                        if (stack.getAmount() == 0) {
                            e.remove();
                        }
                    } else {
                        final double factor;
                        if (distance > 1) {
                            factor = 0.8;
                        } else if (distance > 0.75) {
                            factor = 0.5;
                        } else {
                            factor = 0.1;
                        }
                        this.push(e, -factor / distance);
                    }
                }
            }
        }
    }

    @Override
    public void onItemSet(int index, ItemStack item) {
        super.onItemSet(index, item);
        // Mark the Entity as changed
        onPropertiesChanged();
    }
}
