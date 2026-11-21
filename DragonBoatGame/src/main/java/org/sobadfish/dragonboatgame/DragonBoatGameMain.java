package org.sobadfish.dragonboatgame;

import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.TextFormat;
import org.sobadfish.dragonboatgame.command.GameAdminCommand;
import org.sobadfish.dragonboatgame.command.GameCommand;
import org.sobadfish.dragonboatgame.command.GameSpeakCommand;
import org.sobadfish.dragonboatgame.manager.TotalManager;
import org.sobadfish.dragonboatgame.player.data.IDataValue;
import org.sobadfish.dragonboatgame.race.DragonBoatRoomCreateListener;


/**

 * @author Sobadfish
 * 13:07
 */
public class DragonBoatGameMain extends PluginBase {



    @Override
    public void onEnable() {


        TotalManager.initLanguage(this);
        //字符生成地址 http://www.network-science.de/ascii/
        //Font: small
        this.getLogger().info(TextFormat.colorize('&',"&e ___                         _               _   "));
        this.getLogger().info(TextFormat.colorize('&',"&e|   \\ _ _ __ _ __ _ ___ _ _ | |__  ___  __ _| |_ "));
        this.getLogger().info(TextFormat.colorize('&',"&e| |) | '_/ _` / _` / _ \\ ' \\| '_ \\/ _ \\/ _` |  _|"));
        this.getLogger().info(TextFormat.colorize('&',"&e|___/|_| \\__,_\\__, \\___/_||_|_.__/\\___/\\__,_|\\__|"));
        this.getLogger().info(TextFormat.colorize('&',"&e              |___/                              "));
        this.getLogger().info(TextFormat.colorize('&',TotalManager.getLanguage().getLanguage("version","&e正在加载[1] 插件 本版本为&av[2]"
                ,TotalManager.GAME_NAME,this.getDescription().getVersion())));
        //TODO 注册
        IDataValue.init();
        this.getServer().getPluginManager().registerEvents(new DragonBoatRoomCreateListener(), this);
        TotalManager.init(this);
        this.getServer().getCommandMap().register(TotalManager.GAME_NAME,new GameAdminCommand(TotalManager.COMMAND_ADMIN_NAME));
        this.getServer().getCommandMap().register(TotalManager.GAME_NAME,new GameCommand(TotalManager.COMMAND_NAME));
        this.getServer().getCommandMap().register(TotalManager.GAME_NAME,new GameSpeakCommand(TotalManager.COMMAND_MESSAGE_NAME));

        this.getLogger().info(TextFormat.colorize('&',TotalManager.getLanguage().getLanguage("success","&a插件加载完成，祝您使用愉快")));

//        //需要测试一�?
//        PlayerData data = TotalManager.getDataManager().getData("test");
//        data.addData("test",new IntegerDataValue(25));
//        data.addData("test2",new StringDataValue("时间"));
//        data.save();

    }

    @Override
    public void onDisable() {
       TotalManager.onDisable();
    }

}


