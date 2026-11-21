package org.sobadfish.dragonboatgame.dlc;

import org.sobadfish.dragonboatgame.room.GameRoom;

/**
 * 重写房间结束判断条件
 * @author Sobadfish
 * 2023/2/22
 */
public interface IGameEndJudge {


    /**
     * 代替默认的结束判断条�?
     * @param room 游戏房间
     * @return 是否结束游戏
     * */
    boolean judgeGameEnd(GameRoom room);
}


