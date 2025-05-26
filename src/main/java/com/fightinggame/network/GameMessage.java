// GameMessage.java
package com.fightinggame.network;

import java.io.Serializable;

public class GameMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public enum MessageType {
        PLAYER_POSITION,
        PLAYER_ATTACK,
        PLAYER_DAMAGE,
        GAME_STATE
    }

    private MessageType type;
    private Object data;
    private int playerId;

    public GameMessage(MessageType type, Object data, int playerId) {
        this.type = type;
        this.data = data;
        this.playerId = playerId;
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
}