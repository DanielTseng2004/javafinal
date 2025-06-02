// Game.java
package com.fightinggame;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.fightinggame.network.GameClient;
import com.fightinggame.network.GameMessage;

import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.util.Duration;

public class Game {

    private static final int WINDOW_WIDTH = 800;
    private static final int WINDOW_HEIGHT = 600;
    private static final Color BACKGROUND_COLOR = Color.WHITE;
    private static final double GRAVITY = 0.5;
    private static final double JUMP_FORCE = -15;
    private static final double MOVE_SPEED = 5;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final int RECONNECT_DELAY_MS = 2000;
    private static final int ATTACK_SCORE_COOLDOWN = 1000; // 攻擊得分冷卻時間（毫秒）

    private Pane root;
    private Player player1;
    private Player player2;
    private Set<KeyCode> pressedKeys;
    private AnimationTimer gameLoop;
    private GameClient gameClient;
    private boolean isHost;
    private Text scoreText;
    private Text gameOverText;
    private Text connectionStatusText;
    private Button restartButton;
    private ScheduledExecutorService reconnectExecutor;
    private int reconnectAttempts = 0;
    private String serverAddress;
    private int serverPort;
    private int player1Hits = 0;
    private int player2Hits = 0;
    private long lastPlayer1ScoreTime = 0;
    private long lastPlayer2ScoreTime = 0;

    public Game(boolean isHost, String serverAddress, int serverPort) {
        this.isHost = isHost;
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        initializeGame();
    }

    private void initializeGame() {
        root = new Pane();
        root.setStyle("-fx-background-color: white;");

        // 創建遊戲區域容器
        Pane gameArea = new Pane();
        gameArea.setStyle("-fx-background-color: rgba(255, 255, 255, 0.8);");
        gameArea.setPrefSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        root.getChildren().add(gameArea);

        // 初始化玩家
        player1 = new Player(WINDOW_WIDTH * 0.25, WINDOW_HEIGHT - 200, "Player 1");
        player2 = new Player(WINDOW_WIDTH * 0.75, WINDOW_HEIGHT - 200, "Player 2");
        gameArea.getChildren().addAll(player1.getSprite(), player2.getSprite());

        // 初始化UI
        setupUI();

        // 初始化輸入處理
        pressedKeys = new HashSet<>();

        // 初始化遊戲循環
        setupGameLoop();

        // 連接到服務器
        connectToServer(serverAddress);
    }

    public void start() {
        gameLoop.start();
    }

    public Pane getRoot() {
        return root;
    }

    private void setupUI() {
        // 創建頂部信息面板
        Pane topPanel = new Pane();
        topPanel.setStyle(
                "-fx-background-color: rgba(255, 255, 255, 0.7);"
                + "-fx-background-radius: 10;"
                + "-fx-padding: 10;"
        );
        topPanel.setPrefSize(WINDOW_WIDTH, 50);
        topPanel.setLayoutY(0);
        root.getChildren().add(topPanel);

        // 分數顯示
        scoreText = new Text();
        scoreText.setFill(Color.BLACK);
        scoreText.setFont(Font.font("Arial", FontWeight.BOLD, 32));
        scoreText.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 2);");
        scoreText.setText("Player 1: 0 - Player 2: 0"); // 設置初始文字
        scoreText.setX((WINDOW_WIDTH - scoreText.getLayoutBounds().getWidth()) / 2);
        scoreText.setY(40);
        topPanel.getChildren().add(scoreText);

        // 遊戲結束文字
        gameOverText = new Text();
        gameOverText.setFill(Color.BLACK);
        gameOverText.setFont(Font.font("Arial", FontWeight.BOLD, 48));
        gameOverText.setStyle(
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 10, 0, 0, 2);"
                + "-fx-fill: linear-gradient(to bottom, #000000, #333333);"
        );
        gameOverText.setVisible(false);
        root.getChildren().add(gameOverText);

        // 重新開始按鈕
        restartButton = new Button("重新開始");
        restartButton.setStyle(
                "-fx-background-color: #4CAF50;"
                + "-fx-text-fill: white;"
                + "-fx-font-size: 20px;"
                + "-fx-padding: 10 20;"
                + "-fx-background-radius: 5;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 5, 0, 0, 1);"
        );
        restartButton.setVisible(false);
        restartButton.setOnAction(e -> restartGame());
        root.getChildren().add(restartButton);

        // 連接狀態
        connectionStatusText = new Text();
        connectionStatusText.setFill(Color.BLACK);
        connectionStatusText.setFont(Font.font("Arial", 14));
        connectionStatusText.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 5, 0, 0, 1);");
        connectionStatusText.setX(10);
        connectionStatusText.setY(WINDOW_HEIGHT - 20);
        root.getChildren().add(connectionStatusText);

        // 添加控制說明
        Text controlsText = new Text(
                "Controls:\n"
                + "Player 1 (Blue): WASD to move, SPACE to attack\n"
                + "Player 2 (Red): Arrow keys to move, ENTER to attack\n"
                + "F12: Toggle attack range visibility"
        );
        controlsText.setFill(Color.BLACK);
        controlsText.setFont(Font.font("Arial", 12));
        controlsText.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 5, 0, 0, 1);");
        controlsText.setX(WINDOW_WIDTH - 300);
        controlsText.setY(WINDOW_HEIGHT - 80);
        root.getChildren().add(controlsText);
    }

    public void setupInputHandling(Scene scene) {
        scene.setOnKeyPressed(event -> {
            pressedKeys.add(event.getCode());
            if (event.getCode() == KeyCode.F12) {
                boolean showBounds = !player1.getAttackBox().isVisible();
                player1.showAttackBounds(showBounds);
                player2.showAttackBounds(showBounds);
                System.out.println("攻擊範圍可視化：" + (showBounds ? "開啟" : "關閉"));
            }

            if (gameClient != null && gameClient.isConnected()) {
                handleInput(event.getCode());
            }
        });
        scene.setOnKeyReleased(event -> {
            pressedKeys.remove(event.getCode());
            if (gameClient != null && gameClient.isConnected()) {
                handleInput(event.getCode());
            }
        });
        root.setFocusTraversable(true);
    }

    public Set<KeyCode> getPressedKeys() {
        return pressedKeys;
    }

    public void handleInput(KeyCode code) {
        if (gameClient == null || !gameClient.isConnected()) {
            System.out.println("Not connected to server");
            return;
        }

        if (isHost) {
            handleHostInput(code);
        } else {
            handleClientInput(code);
        }
    }

    private void handleHostInput(KeyCode code) {
        switch (code) {
            case A:
                if (pressedKeys.contains(KeyCode.A)) {
                    player1.move(-MOVE_SPEED);
                }
                break;
            case D:
                if (pressedKeys.contains(KeyCode.D)) {
                    player1.move(MOVE_SPEED);
                }
                break;
            case W:
                if (pressedKeys.contains(KeyCode.W) && player1.isOnGround()) {
                    player1.jump(JUMP_FORCE);
                }
                break;
            case SPACE:
                if (pressedKeys.contains(KeyCode.SPACE)) {
                    player1.attack();
                    sendAttackUpdate(player1);
                }
                break;
        }
    }

    private void handleClientInput(KeyCode code) {
        switch (code) {
            case LEFT:
                if (pressedKeys.contains(KeyCode.LEFT)) {
                    player2.move(-MOVE_SPEED);
                }
                break;
            case RIGHT:
                if (pressedKeys.contains(KeyCode.RIGHT)) {
                    player2.move(MOVE_SPEED);
                }
                break;
            case UP:
                if (pressedKeys.contains(KeyCode.UP) && player2.isOnGround()) {
                    player2.jump(JUMP_FORCE);
                }
                break;
            case ENTER:
                if (pressedKeys.contains(KeyCode.ENTER)) {
                    player2.attack();
                    sendAttackUpdate(player2);
                }
                break;
        }
    }

    private void setupGameLoop() {
        gameLoop = new AnimationTimer() {
            private long lastUpdate = 0;
            private static final long UPDATE_INTERVAL = 16_666_667; // ~60 FPS

            @Override
            public void handle(long now) {
                if (now - lastUpdate >= UPDATE_INTERVAL) {
                    update();
                    processNetworkMessages();
                    lastUpdate = now;
                }
            }
        };
    }

    private void connectToServer(String serverAddress) {
        try {
            gameClient = new GameClient(serverAddress, serverPort);
            if (gameClient.isConnected()) {
                connectionStatusText.setText("Connected to server");
                reconnectAttempts = 0;
                if (gameLoop != null) {
                    gameLoop.start();
                }
            } else {
                handleConnectionFailure();
            }
        } catch (Exception e) {
            System.out.println("Connection error: " + e.getMessage());
            handleConnectionFailure();
        }
    }

    private void handleConnectionFailure() {
        connectionStatusText.setText("Connection failed - Retrying...");
        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++;
            if (reconnectExecutor == null) {
                reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
            }
            reconnectExecutor.schedule(() -> {
                Platform.runLater(() -> connectToServer(serverAddress));
            }, RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS);
        } else {
            connectionStatusText.setText("Connection failed - Max retries reached");
        }
    }

    private void checkAttackCollision(Player attacker, Player defender) {
        if (attacker.isAttacking()) {
            Bounds attackBounds = attacker.getAttackBounds();
            Bounds defenderBounds = defender.getBounds();

            System.out.println("=== 攻擊碰撞檢測 ===");
            System.out.println("攻擊者：" + attacker.getName() + " 正在攻擊");
            System.out.println("攻擊框範圍："
                    + String.format("X: %.1f-%.1f, Y: %.1f-%.1f",
                            attackBounds.getMinX(), attackBounds.getMaxX(),
                            attackBounds.getMinY(), attackBounds.getMaxY()));
            System.out.println("防禦者：" + defender.getName());
            System.out.println("防禦者範圍："
                    + String.format("X: %.1f-%.1f, Y: %.1f-%.1f",
                            defenderBounds.getMinX(), defenderBounds.getMaxX(),
                            defenderBounds.getMinY(), defenderBounds.getMaxY()));

            // 檢查攻擊框和防禦者是否相交
            boolean intersects = attackBounds.intersects(defenderBounds);
            System.out.println("是否相交：" + intersects);

            if (intersects) {
                System.out.println("*** 攻擊命中！***");

                // 檢查是否為本地控制的玩家
                boolean isLocalPlayer = (isHost && attacker == player1) || (!isHost && attacker == player2);
                System.out.println("是否為本地玩家攻擊：" + isLocalPlayer);

                if (isLocalPlayer) {
                    long currentTime = System.currentTimeMillis();
                    boolean canScore = false;

                    // 檢查攻擊者是否可以得分
                    if (attacker == player1) {
                        if (currentTime - lastPlayer1ScoreTime >= ATTACK_SCORE_COOLDOWN) {
                            canScore = true;
                            lastPlayer1ScoreTime = currentTime;
                        }
                    } else {
                        if (currentTime - lastPlayer2ScoreTime >= ATTACK_SCORE_COOLDOWN) {
                            canScore = true;
                            lastPlayer2ScoreTime = currentTime;
                        }
                    }

                    if (canScore) {
                        // 更新命中次數，每次只加一分
                        if (attacker == player1) {
                            player1Hits++;
                            System.out.println("Player 1 得分！當前分數：" + player1Hits);
                        } else {
                            player2Hits++;
                            System.out.println("Player 2 得分！當前分數：" + player2Hits);
                        }

                        // 立即更新UI
                        Platform.runLater(() -> {
                            updateScore();
                            checkGameOver();
                        });

                        // 發送網路同步消息
                        if (gameClient != null && gameClient.isConnected()) {
                            GameMessage scoreMessage = new GameMessage(
                                    GameMessage.MessageType.GAME_STATE,
                                    new int[]{player1Hits, player2Hits},
                                    isHost ? 1 : 2
                            );
                            gameClient.sendMessage(scoreMessage);
                        }
                    } else {
                        System.out.println("攻擊冷卻中，無法得分");
                    }
                }
            }
            System.out.println("===================");
        }
    }

    private void update() {
        if (gameClient != null && gameClient.isConnected()) {
            // 處理輸入
            for (KeyCode key : pressedKeys) {
                handleInput(key);
            }

            // 同步位置和狀態
            if (isHost) {
                sendPositionUpdate(player1);
            } else {
                sendPositionUpdate(player2);
            }
        }

        // 更新玩家狀態
        player1.update();
        player2.update();

        // 應用物理
        player1.applyGravity(GRAVITY);
        player2.applyGravity(GRAVITY);

        checkGroundCollision(player1);
        checkGroundCollision(player2);

        // 檢查攻擊碰撞
        if (player1.isAttacking()) {
            checkAttackCollision(player1, player2);
        }
        if (player2.isAttacking()) {
            checkAttackCollision(player2, player1);
        }

        processNetworkMessages();
        updateScore();
        checkGameOver();
        Platform.runLater(() -> {
            updateScore();
            checkGameOver();
        });
    }

    private void checkGroundCollision(Player player) {
        double groundY = WINDOW_HEIGHT - 100;
        if (player.getY() >= groundY) {
            player.setY(groundY);
            player.setOnGround(true);
        }
    }

    private void processNetworkMessages() {
        if (gameClient == null || !gameClient.isConnected()) {
            return;
        }

        GameMessage message;
        while ((message = gameClient.getNextMessage()) != null) {
            try {
                switch (message.getType()) {
                    case PLAYER_POSITION:
                        handlePositionUpdate(message);
                        break;
                    case PLAYER_ATTACK:
                        handleAttackUpdate(message);
                        break;
                    case PLAYER_DAMAGE:
                        handleDamageUpdate(message);
                        break;
                    case GAME_STATE:
                        handleGameStateUpdate(message);
                        break;
                }
            } catch (Exception e) {
                System.out.println("Error processing message: " + e.getMessage());
            }
        }
    }

    private void sendPositionUpdate(Player player) {
        if (gameClient == null || !gameClient.isConnected()) {
            return;
        }

        // 發送位置和狀態信息
        double[] position = {
            player.getX(),
            player.getY(),
            player.isAttacking() ? 1 : 0 // 添加攻擊狀態
        };
        GameMessage message = new GameMessage(
                GameMessage.MessageType.PLAYER_POSITION,
                position,
                player == player1 ? 1 : 2
        );
        gameClient.sendMessage(message);
    }

    private void sendAttackUpdate(Player player) {
        if (gameClient == null || !gameClient.isConnected()) {
            return;
        }

        // 發送攻擊消息，包含攻擊者的位置信息
        double[] position = {player.getX(), player.getY()};
        GameMessage message = new GameMessage(
                GameMessage.MessageType.PLAYER_ATTACK,
                position, // 發送位置信息
                player == player1 ? 1 : 2
        );
        gameClient.sendMessage(message);
    }

    private void handlePositionUpdate(GameMessage message) {
        double[] position = (double[]) message.getData();
        Player targetPlayer = message.getPlayerId() == 1 ? player1 : player2;

        if ((isHost && message.getPlayerId() == 2) || (!isHost && message.getPlayerId() == 1)) {
            Platform.runLater(() -> {
                targetPlayer.setX(position[0]);
                targetPlayer.setY(position[1]);

                // 如果有攻擊狀態信息，更新攻擊狀態
                if (position.length > 2 && position[2] == 1) {
                    targetPlayer.attack();
                }
            });
        }
    }

    private void handleAttackUpdate(GameMessage message) {
        Player attacker = message.getPlayerId() == 1 ? player1 : player2;
        Player target = message.getPlayerId() == 1 ? player2 : player1;

        Platform.runLater(() -> {
            // 強制更新位置，確保攻擊判定使用最新位置
            attacker.attack();

            // 獲取正確的攻擊範圍和目標範圍
            Bounds attackBounds = attacker.getAttackBounds();
            Bounds targetBounds = target.getBounds();

            System.out.println("處理攻擊更新 - 攻擊者：" + attacker.getName()
                    + " 攻擊框：" + attackBounds
                    + " 目標：" + target.getName()
                    + " 位置：" + targetBounds);

            // 檢查碰撞
            if (attackBounds.intersects(targetBounds)) {
                System.out.println("攻擊命中！");

                long currentTime = System.currentTimeMillis();
                boolean canScore = false;

                // 檢查攻擊者是否可以得分
                if (message.getPlayerId() == 1) {
                    if (currentTime - lastPlayer1ScoreTime >= ATTACK_SCORE_COOLDOWN) {
                        canScore = true;
                        lastPlayer1ScoreTime = currentTime;
                    }
                } else {
                    if (currentTime - lastPlayer2ScoreTime >= ATTACK_SCORE_COOLDOWN) {
                        canScore = true;
                        lastPlayer2ScoreTime = currentTime;
                    }
                }

                if (canScore) {
                    // 更新命中次數，每次只加一分
                    if (message.getPlayerId() == 1) {
                        player1Hits++;
                        System.out.println("Player 1 得分！當前分數：" + player1Hits);
                    } else {
                        player2Hits++;
                        System.out.println("Player 2 得分！當前分數：" + player2Hits);
                    }

                    // 立即更新UI和同步到後端
                    Platform.runLater(() -> {
                        updateScore();
                        checkGameOver();

                        // 發送分數更新到後端
                        GameMessage scoreMessage = new GameMessage(
                                GameMessage.MessageType.GAME_STATE,
                                new int[]{player1Hits, player2Hits},
                                isHost ? 1 : 2
                        );
                        gameClient.sendMessage(scoreMessage);
                    });

                    // 發送傷害同步消息
                    GameMessage damageMessage = new GameMessage(
                            GameMessage.MessageType.PLAYER_DAMAGE,
                            1, // 傷害值固定為1
                            target == player1 ? 1 : 2 // 標記受傷的玩家ID
                    );
                    gameClient.sendMessage(damageMessage);
                } else {
                    System.out.println("攻擊冷卻中，無法得分");
                }
            }
        });
    }

    private void handleDamageUpdate(GameMessage message) {
        int damage = (int) message.getData();
        int targetPlayerId = message.getPlayerId();

        System.out.println("收到傷害更新：玩家 " + targetPlayerId + " 受到 " + damage + " 點傷害");

        // 根據消息中的玩家ID來確定誰受傷
        Player targetPlayer = targetPlayerId == 1 ? player1 : player2;

        // 應用傷害
        targetPlayer.takeDamage(damage);

        // 立即更新UI
        Platform.runLater(() -> {
            updateScore();
            checkGameOver();
        });
    }

    private void handleGameStateUpdate(GameMessage message) {
        int[] scores = (int[]) message.getData();
        Platform.runLater(() -> {
            // 只在收到對方玩家的分數更新時更新
            if ((isHost && message.getPlayerId() == 2) || (!isHost && message.getPlayerId() == 1)) {
                player1Hits = scores[0];
                player2Hits = scores[1];
                updateScore();
                checkGameOver();
                System.out.println("收到分數更新 - Player 1: " + player1Hits + ", Player 2: " + player2Hits);
            }
        });
    }

    private void updateScore() {
        // 直接在UI線程中更新，不需要再套Platform.runLater
        String scoreString = String.format("%s: %d - %s: %d",
                player1.getName(), player1Hits,
                player2.getName(), player2Hits);

        scoreText.setText(scoreString);

        // 使用Timeline來確保文字尺寸計算完成後再調整位置
        Timeline positionUpdate = new Timeline(new KeyFrame(Duration.millis(50), e -> {
            double textWidth = scoreText.getBoundsInLocal().getWidth();
            scoreText.setX((WINDOW_WIDTH - textWidth) / 2);
        }));
        positionUpdate.play();

        System.out.println("更新分數 - " + player1.getName() + ": " + player1Hits
                + " - " + player2.getName() + ": " + player2Hits);
    }

    private void restartGame() {
        // 重置分數
        player1Hits = 0;
        player2Hits = 0;
        lastPlayer1ScoreTime = 0;
        lastPlayer2ScoreTime = 0;

        // 重置玩家位置
        player1.setX(WINDOW_WIDTH * 0.25);
        player1.setY(WINDOW_HEIGHT - 200);
        player2.setX(WINDOW_WIDTH * 0.75);
        player2.setY(WINDOW_HEIGHT - 200);

        // 重置UI
        gameOverText.setVisible(false);
        restartButton.setVisible(false);
        updateScore();

        // 重新開始遊戲循環
        if (gameLoop != null) {
            gameLoop.start();
        }

        // 發送重置消息給對方玩家
        if (gameClient != null && gameClient.isConnected()) {
            GameMessage resetMessage = new GameMessage(
                    GameMessage.MessageType.GAME_STATE,
                    new int[]{0, 0},
                    isHost ? 1 : 2
            );
            gameClient.sendMessage(resetMessage);
        }
    }

    private void checkGameOver() {
        if (player1Hits >= 10 || player2Hits >= 10) {
            String winner = player1Hits >= 10 ? player1.getName() : player2.getName();
            gameOverText.setText(winner + " wins!");
            gameOverText.setX((WINDOW_WIDTH - gameOverText.getLayoutBounds().getWidth()) / 2);
            gameOverText.setY(WINDOW_HEIGHT / 2);
            gameOverText.setVisible(true);

            // 顯示重新開始按鈕
            restartButton.setLayoutX((WINDOW_WIDTH - restartButton.getWidth()) / 2);
            restartButton.setLayoutY(WINDOW_HEIGHT / 2 + 60);
            restartButton.setVisible(true);

            // 添加獲勝特效
            gameOverText.setStyle(
                    "-fx-fill: linear-gradient(to bottom, #000000, #333333);"
                    + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 20, 0, 0, 2);"
                    + "-fx-font-size: 48px;"
                    + "-fx-font-weight: bold;"
            );

            // 發送遊戲結束消息
            GameMessage gameOverMessage = new GameMessage(
                    GameMessage.MessageType.GAME_STATE,
                    new int[]{player1Hits, player2Hits},
                    isHost ? 1 : 2
            );
            gameClient.sendMessage(gameOverMessage);

            gameLoop.stop();
            System.out.println("遊戲結束！獲勝者：" + winner);
        }
    }

    private void cleanup() {
        if (gameLoop != null) {
            gameLoop.stop();
        }
        if (gameClient != null) {
            gameClient.disconnect();
        }
        if (reconnectExecutor != null) {
            reconnectExecutor.shutdown();
        }
    }

    public void stop() {
        cleanup();
    }
}
