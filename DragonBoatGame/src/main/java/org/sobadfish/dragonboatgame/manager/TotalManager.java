package org.sobadfish.dragonboatgame.manager;

import cn.nukkit.Player;
import cn.nukkit.entity.EntityHuman;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.TextFormat;
import org.sobadfish.dragonboatgame.manager.data.PlayerDataManager;
import org.sobadfish.dragonboatgame.manager.data.PlayerInventoryBackupManager;
import org.sobadfish.dragonboatgame.manager.data.PlayerTopManager;
import org.sobadfish.dragonboatgame.manager.data.TagItemDataManager;
import org.sobadfish.dragonboatgame.panel.DisPlayWindowsFrom;
import org.sobadfish.dragonboatgame.panel.lib.AbstractFakeInventory;
import org.sobadfish.dragonboatgame.room.event.defaults.ChestResetEvent;
import org.sobadfish.dragonboatgame.room.event.defaults.CommandEvent;
import org.sobadfish.dragonboatgame.room.event.defaults.CustomEvent;
import org.sobadfish.dragonboatgame.room.event.defaults.EffectEvent;
import org.sobadfish.dragonboatgame.variable.GameNpcVariable;
import org.sobadfish.dragonboatgame.variable.GameTipVariable;
import tip.messages.BaseMessage;
import tip.messages.defaults.ScoreBoardMessage;
import tip.utils.Api;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * 插件启动控制�?
 * @author Sobadfish
 * 2022/9/12
 */
public class TotalManager {


    /**
     * 游戏指令
     * */
    public static String COMMAND_NAME = "dbg";

    /**
     * 游戏管理员指�?
     * */
    public static String COMMAND_ADMIN_NAME = "dbga";

    /**
     * 游戏内喊话指�?
     * */
    public static String COMMAND_MESSAGE_NAME = "dbgs";

    /**
     * 小游戏名�?
     * */
    public static final String GAME_NAME = "DragonBoatGame";
    

    public static PluginBase plugin = null;


    private static PlayerDataManager dataManager;

    private static PlayerTopManager topManager;

    private static TagItemDataManager tagItemDataManager;

    public static LanguageManager language;

    private static final Map<String, BaseMessage> TIPS_SCOREBOARD_OVERRIDE = new HashMap<>();


    public static int upExp;

    public static void init(PluginBase pluginBase){
        TotalManager.plugin = pluginBase;
        checkServer();
        loadConfig();
        loadVariable();
        COMMAND_NAME = plugin.getConfig().getString("command-player","dbg");
        COMMAND_ADMIN_NAME = plugin.getConfig().getString("command-admin","dbga");
        COMMAND_MESSAGE_NAME = plugin.getConfig().getString("command-msg","dbgs");
        RoomEventManager.register("custom", CustomEvent.class);
        RoomEventManager.register("effect", EffectEvent.class);
        RoomEventManager.register("command", CommandEvent.class);
        RoomEventManager.register("chest_reset", ChestResetEvent.class);
        ThreadManager.init();
    }

    private static void loadVariable() {
        try{
            Class.forName("com.smallaswater.npc.variable.BaseVariableV2");
            GameNpcVariable.init();
        }catch (Exception ignore){}
        try{
            Class.forName("tip.utils.variables.BaseVariable");
            GameTipVariable.init();
        }catch (Exception ignore){}
    }

    public static void initLanguage(PluginBase plugin) {
        language = new LanguageManager(plugin);
    }


    private static RoomManager roomManager;

    private static MenuRoomManager menuRoomManager;

    public static Boolean isDisabled(){
        if(plugin != null){
            return plugin.isDisabled();
        }
        return true;
    }

    public static PluginBase getPlugin() {
        return plugin;
    }

    public static Config getConfig(){
        if(plugin == null){
            return new Config();
        }
        return plugin.getConfig();
    }

    public static void sendMessageToConsole(String msg){
        sendMessageToObject(msg,null);
    }

    public static String getTitle(){
        return TextFormat.colorize('&', plugin.getConfig().getString("title"));
    }

    public static String getScoreBoardTitle(){
        return TextFormat.colorize('&', plugin.getConfig().getString("scoreboard-title","&f[&b龙舟竞赛&f]"));
    }

    public static LanguageManager getLanguage() {
        return language;
    }

    public static void sendTipMessageToObject(String msg, Object o){
        String message = TextFormat.colorize('&',msg);
        if(o != null){
            if(o instanceof Player){
                if(((Player) o).isOnline()) {
                    ((Player) o).sendMessage(message);
                    return;
                }
            }
            if(o instanceof EntityHuman){
                message = ((EntityHuman) o).getName()+"->"+message;
            }
        }
        plugin.getLogger().info(message);

    }



    /**
     * 加载配置文件
     */
    public static void loadConfig(){
        if(plugin == null){
            return;
        }
        //TODO 得释放一些缓存
        DisPlayWindowsFrom.FROM.clear();
        RoomManager.LOCK_GAME.clear();
        upExp = getConfig().getInt("up-exp",500);
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        tagItemDataManager = TagItemDataManager.asFile(new File(plugin.getDataFolder()+File.separator+"tag.json"));
        File mainFileDir = new File(plugin.getDataFolder()+File.separator+"rooms");
        if(!mainFileDir.exists()){
            if(!mainFileDir.mkdirs()){
                sendMessageToConsole(language.getLanguage("plugin-room-folder-create-error","&c创建文件夹 rooms失败"));
            }
        }

        roomManager = RoomManager.initGameRoomConfig(mainFileDir);
        sendMessageToConsole(language.getLanguage("plugin-room-load-success","&a房间数据全部加载完成"));
        plugin.getServer().getPluginManager().registerEvents(roomManager,plugin);
        if(plugin.getConfig().getAll().size() == 0) {
            plugin.saveResource("config.yml", true);
            plugin.reloadConfig();
        }
        menuRoomManager = new MenuRoomManager(plugin.getConfig());
        dataManager = PlayerDataManager.asFile(new File(plugin.getDataFolder()+File.separator+"player.json"));
        //初始化玩家背包存档（服务器重启后还原用）
        PlayerInventoryBackupManager.init(plugin.getDataFolder());
        //初始化排行榜
        topManager = PlayerTopManager.asFile(new File(plugin.getDataFolder()+File.separator+"top.json"));
        if(topManager != null){
            topManager.init();
        }
        //TODO 初始化按键物品
        ButtonItemManager.init();
        //TODO 初始化皮肤
        SkinManager.init();
        //TODO 初始化文件
        plugin.saveResource("变量表_v1.0.txt", true);

    }
    public static PlayerDataManager getDataManager() {
        return dataManager;
    }

    public static PlayerTopManager getTopManager() {
        return topManager;
    }

    public static RoomManager getRoomManager() {
        return roomManager;
    }

    public static MenuRoomManager getMenuRoomManager() {
        return menuRoomManager;
    }

    public static TagItemDataManager getTagItemDataManager() {
        return tagItemDataManager;
    }

    public static void sendMessageToObject(String msg, Object o){

        String message = TextFormat.colorize('&',getTitle()+" &b>>&r "+msg);
        sendTipMessageToObject(message,o);
    }

    public static int getUpExp() {
        return upExp;
    }

    public static void sendSubTitle(String msg, Player o){
        String message = TextFormat.colorize('&',msg);
        if(o != null){
            if(o.isOnline()) {
                o.setSubtitle(message);
            }
        }else{
            plugin.getLogger().info(message);
        }
    }

    public static void sendTitle(String msg,int time,Player o){
        String message = TextFormat.colorize('&',msg);
        if(o != null){
            if(o.isOnline()) {
                o.sendTitle(message,null,0,time,0);
            }
        }else{
            plugin.getLogger().info(message);
        }
    }

    public static void sendTip(String msg,Player o){
        String message = TextFormat.colorize('&',msg);
        if(o != null){
            if(o.isOnline()) {
                o.sendTip(message);
            }
        }else{
            plugin.getLogger().info(message);
        }
    }

    public static void disableTipsScoreboard(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        try {
            restoreTipsScoreboard(player);
            BaseMessage disableMessage = new ScoreBoardMessage("", false,
                    player.getLevel().getFolderName(), new LinkedList<String>());
            Api.setPlayerShowMessage(player.getName(), disableMessage);
            TIPS_SCOREBOARD_OVERRIDE.put(player.getName(), disableMessage);
        } catch (Throwable ignore) {
        }
    }

    public static void restoreTipsScoreboard(Player player) {
        if (player == null) {
            return;
        }
        try {
            BaseMessage baseMessage = TIPS_SCOREBOARD_OVERRIDE.remove(player.getName());
            if (baseMessage != null) {
                Api.removePlayerShowMessage(player.getName(), baseMessage);
            }
        } catch (Throwable ignore) {
        }
    }

    public static void saveResource(String s, String s1, boolean b) {
        if(!isDisabled()){
            plugin.saveResource(s, s1, b);
        }
    }

    public static void saveResource(String s,  boolean b) {
        if(!isDisabled()){
            plugin.saveResource(s, b);
        }
    }

    public static File getDataFolder() {
        return plugin.getDataFolder();
    }

    public static void onDisable() {
        if(topManager != null){
            topManager.save();
        }
        if(dataManager != null){
            dataManager.save();
        }

    }


    private static void checkServer(){
        boolean ver = false;
        //双核心兼容
        try {
            Class<?> c = Class.forName("cn.nukkit.Nukkit");
            c.getField("NUKKIT_PM1E");
            ver = true;

        } catch (ClassNotFoundException | NoSuchFieldException ignore) { }
        try {
            Class<?> c = Class.forName("cn.nukkit.Nukkit");
            c.getField("NUKKIT").get(c).toString().equalsIgnoreCase("Nukkit PetteriM1 Edition");
            ver = true;
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException ignore) {
        }

        AbstractFakeInventory.IS_PM1E = ver;
        if(ver){
            sendMessageToConsole(language.getLanguage("plugin-load-server-pm1e","&e当前核心为 Nukkit PM1E"));
        }else{
            sendMessageToConsole(language.getLanguage("plugin-load-server-nukkit","&e当前核心为 Nukkit"));
        }
    }


}


