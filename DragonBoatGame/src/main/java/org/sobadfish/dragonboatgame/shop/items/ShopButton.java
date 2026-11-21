package org.sobadfish.dragonboatgame.shop.items;

import cn.nukkit.Player;
import org.sobadfish.dragonboatgame.manager.ShopManager;
import org.sobadfish.dragonboatgame.panel.DisPlayWindowsFrom;
import org.sobadfish.dragonboatgame.panel.from.GameFrom;
import org.sobadfish.dragonboatgame.room.GameRoom;
import org.sobadfish.dragonboatgame.shop.BaseShopButton;
import org.sobadfish.dragonboatgame.shop.ButtonInfo;
import org.sobadfish.dragonboatgame.shop.IShopItem;
import org.sobadfish.dragonboatgame.shop.ShopButtonType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Sobadfish
 *  2023/2/6
 */
public class ShopButton extends BaseShopButton {

    public List<? extends IShopItem> menu = new ArrayList<>();

    public ShopButton(ButtonInfo buttonInfo) {
        super(ShopButtonType.BUTTON,buttonInfo);
    }

    public void setMenu(List<? extends IShopItem> menu) {
        this.menu = menu;
    }

    public List<? extends IShopItem> getMenu() {
        return menu;
    }

    @Override
    public boolean onClick(GameRoom room, Player player){
        if(reduce(room, player)) {
            GameFrom gameFrom = new GameFrom(getInfo().displayName, "");
            ShopManager.addGameFromButton(room, gameFrom, menu);
            DisPlayWindowsFrom.disPlayFrom(player, gameFrom);
            return true;
        }
        return false;


    }


    public static ShopButton build(Map<?,?> config){
        List<BaseShopButton> shopButtons = new ArrayList<>();
        if(config.containsKey("menu")){
            List<?> menu = (List<?>) config.get("menu");
            for(Object o: menu){
                if(o instanceof Map){
                    BaseShopButton shopButton = BaseShopButton.build((Map<?, ?>) o);
                    if(shopButton != null){
                        shopButtons.add(shopButton);
                    }
                }
            }
        }


        ShopButton shopButton = new ShopButton(ButtonInfo.asInfo(config));
        shopButton.setMenu(shopButtons);
        return shopButton;

    }
}


