package org.sobadfish.dragonboat.customitem;

import cn.nukkit.item.Item;
import cn.nukkit.item.customitem.CustomItemDefinition;
import cn.nukkit.item.customitem.ItemCustom;
import cn.nukkit.network.protocol.types.inventory.creative.CreativeItemCategory;
import org.jetbrains.annotations.NotNull;

public abstract class BaseDragonBoatItem extends ItemCustom {

    public BaseDragonBoatItem(@NotNull String id, @NotNull String name, @NotNull String texture) {
        super("minecraft:" + id, name, texture);
    }

    @Override
    public CustomItemDefinition getDefinition() {
        return CustomItemDefinition
                .simpleBuilder(this, CreativeItemCategory.EQUIPMENT)
                .allowOffHand(true)
                .handEquipped(true)
                .build();
    }

    @Override
    public int getMaxStackSize() {
        return 16;
    }

    @Override
    public Item setCustomName(String name) {
        return super.setCustomName("§r" + name);
    }
}
