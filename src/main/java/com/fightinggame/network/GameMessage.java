// GameMessage.java
package com.fightinggame.network;

import java.io.Serializable;

public class GameMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public enum MessageType {
        PLAYER_POSITION,    // 玩家位置更新
        PLAYER_ATTACK,      // 玩家攻擊
        PLAYER_DAMAGE,      // 玩家受傷
        GAME_STATE,         // 遊戲狀態（分數等）
        PLAYER_ANIMATION    // 玩家動畫狀態
    }

    private MessageType type;
    private Object data;
    private int playerId;
    private long timestamp;  // 添加時間戳

    public GameMessage(MessageType type, Object data, int playerId) {
        this.type = type;
        this.data = data;
        this.playerId = playerId;
        this.timestamp = System.currentTimeMillis();
    }

    public MessageType getType() {
        return type;
    }

    public Object getData() {
        return data;
    }

    public int getPlayerId() {
        return playerId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("GameMessage{type=%s, playerId=%d, timestamp=%d, data=%s}",
            type, playerId, timestamp, data);
    }
}