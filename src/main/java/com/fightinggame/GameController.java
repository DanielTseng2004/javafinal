package com.fightinggame;

import javafx.animation.AnimationTimer;
import javafx.scene.Scene;

public class GameController {
    private Player player1;
    private Player player2;
    private Scene scene;
    private static final double MOVE_SPEED = 5.0;
    private static final double JUMP_FORCE = -15.0;
    private static final double GRAVITY = 0.8;

    public GameController(Scene scene, Player player1, Player player2) {
        this.scene = scene;
        this.player1 = player1;
        this.player2 = player2;
        setupControls();
        startGameLoop();
    }

    private void setupControls() {
        scene.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                // 玩家1控制
                case LEFT:
                    player1.move(-MOVE_SPEED);
                    break;
                case RIGHT:
                    player1.move(MOVE_SPEED);
                    break;
                case UP:
                    player1.jump(JUMP_FORCE);
                    break;
                case SPACE:
                    player1.attack();
                    break;

                // 玩家2控制
                case A:
                    player2.move(-MOVE_SPEED);
                    break;
                case D:
                    player2.move(MOVE_SPEED);
                    break;
                case W:
                    player2.jump(JUMP_FORCE);
                    break;
                case F:
                    player2.attack();
                    break;
            }
        });
    }

    private void startGameLoop() {
        AnimationTimer gameLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                // 應用重力
                player1.applyGravity(GRAVITY);
                player2.applyGravity(GRAVITY);

                // 檢查地面碰撞
                checkGroundCollision(player1);
                checkGroundCollision(player2);

                // 檢查玩家之間的碰撞
                checkPlayerCollision();
            }
        };
        gameLoop.start();
    }

    private void checkGroundCollision(Player player) {
        // 假設地面在y=500的位置
        if (player.getY() >= 500) {
            player.setY(500);
            player.setOnGround(true);
        }
    }

    private void checkPlayerCollision() {
        // 檢查玩家之間的碰撞
        if (player1.getBounds().intersects(player2.getBounds())) {
            // 簡單的碰撞處理：將玩家推開
            double overlap = player1.getBounds().getWidth() / 2 + player2.getBounds().getWidth() / 2 
                           - Math.abs(player1.getX() - player2.getX());
            if (overlap > 0) {
                if (player1.getX() < player2.getX()) {
                    player1.move(-overlap / 2);
                    player2.move(overlap / 2);
                } else {
                    player1.move(overlap / 2);
                    player2.move(-overlap / 2);
                }
            }
        }
    }
} 