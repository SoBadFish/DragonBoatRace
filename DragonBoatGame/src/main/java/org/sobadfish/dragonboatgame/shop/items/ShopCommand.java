package org.sobadfish.dragonboatgame.shop.items;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.ConsoleCommandSender;
import org.sobadfish.dragonboatgame.room.GameRoom;
import org.sobadfish.dragonboatgame.shop.BaseShopButton;
import org.sobadfish.dragonboatgame.shop.ButtonInfo;
import org.sobadfish.dragonboatgame.shop.ShopButtonType;

import java.util.Map;

/**
 * @author Sobadfish
 * 12:46
 */
public class ShopCommand extends BaseShopButton {
    public String command;

    public ShopCommand(ButtonInfo info) {
        super(ShopButtonType.COMMAND, info);
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getCommand() {
        return command;
    }

    @Override
    public boolean onClick(GameRoom room, Player player) {
        if(reduce(room, player)) {
            String cmd = getCommand();
            Server.getInstance().getCommandMap().dispatch(new ConsoleCommandSender(),cmd.replace("@p","'"+player.getName()+"'"));
            return true;
        }
        return false;
    }

    public static ShopCommand build(Map<?,?> config){
        String cmd = "me 管理员未配置 command";
        if(config.containsKey("command")){
            cmd = config.get("command").toString();
        }

        ShopCommand shopButton = new ShopCommand(ButtonInfo.asInfo(config));
        shopButton.setCommand(cmd);
        return shopButton;

    }

}


