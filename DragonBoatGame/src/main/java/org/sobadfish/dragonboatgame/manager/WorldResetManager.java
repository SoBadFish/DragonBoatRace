package org.sobadfish.dragonboatgame.manager;


import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * 重置地图队列
 * */
public class WorldResetManager {

    public static HashMap<String,String> RESET_QUEUE = new LinkedHashMap<>();
    public static volatile String CURRENT_RESET_ROOM = null;

}


