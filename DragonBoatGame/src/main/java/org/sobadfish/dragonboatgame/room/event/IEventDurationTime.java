package org.sobadfish.dragonboatgame.room.event;

/**
 * 定时事件
 * 每秒执行一次可终止
 *
 * @author Sobadfish
 * */
public interface IEventDurationTime {


    /**
     * 获取当前事件的持续事件是否结�?
     * 当返回值为false时事�?结束
     * @return 是否超时
     * */
    boolean isOutTime();

    /**
     * 每秒更新一�?
     * 当未结束时，每秒执行一次update方法
     * */
    void update();

}


