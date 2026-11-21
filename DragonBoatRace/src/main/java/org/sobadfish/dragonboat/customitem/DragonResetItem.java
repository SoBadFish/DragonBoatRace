package org.sobadfish.dragonboat.customitem;

public class DragonResetItem extends BaseDragonBoatItem {

    public DragonResetItem() {
        super("dragon_reset", "重置位置", "dragon_reset");
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }
}
