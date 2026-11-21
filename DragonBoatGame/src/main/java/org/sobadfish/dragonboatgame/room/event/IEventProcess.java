package org.sobadfish.dragonboatgame.room.event;


import org.sobadfish.dragonboatgame.room.GameRoom;

/**
 * 通过接口解�?
 * @author Sobadfish
 *  2022/10/15
 */
public interface IEventProcess {

    /**
     * 启动事件,当事件启动时会调用这个方�?
     * 当事件成功启动后才会被执�?
     * @return 是否启动成功
     * */
    boolean onEnable();
    /**
     * 判断这个事件是否被成功启�?
     * @return 是否启动
     * */
    boolean isEnable();
    /**
     * 下个执行的任�?
     * 可以返回当前事件形成链表
     * @return 返回流程的事�?
     * */
    IGameRoomEvent nextEvent();

    /**
     * 流程事件被触�?
     * @param room 触发的游戏房�?
     * */
    void doNextEvent(GameRoom room);
}


