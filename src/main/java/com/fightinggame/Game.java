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
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

public class Game {

    private static final int WINDOW_WIDTH = 800;
    private static final int WINDOW_HEIGHT = 600;
    private static final Color BACKGROUND_COLOR = Color.BLACK;
    private static final double GRAVITY = 0.5;
    private static final double JUMP_FORCE = -15;
    private static final double MOVE_SPEED = 5;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final int RECONNECT_DELAY_MS = 2000;

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
    private ProgressBar player1HealthBar;
    private ProgressBar player2HealthBar;
    private ScheduledExecutorService reconnectExecutor;
    private int reconnectAttempts = 0;
    private String serverAddress;
    private int serverPort;

    public Game(boolean isHost, String serverAddress, int serverPort) {
        this.isHost = isHost;
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        initializeGame();
    }

    private void initializeGame() {
        root = new Pane();
        root.setStyle("-fx-background-color: black;");

        // Initialize players with proper spacing
        player1 = new Player(WINDOW_WIDTH * 0.25, WINDOW_HEIGHT - 200, "Player 1");
        player2 = new Player(WINDOW_WIDTH * 0.75, WINDOW_HEIGHT - 200, "Player 2");
        root.getChildren().addAll(player1.getSprite(), player2.getSprite());

        // Initialize UI
        setupUI();

        // Initialize input handling
        pressedKeys = new HashSet<>();

        // Initialize game loop
        setupGameLoop();

        // Connect to server
        connectToServer(serverAddress);
    }

    public void start() {
        gameLoop.start();
    }

    public Pane getRoot() {
        return root;
    }

    private void setupUI() {
        // Score text
        scoreText = new Text();
        scoreText.setFill(Color.WHITE);
        scoreText.setFont(Font.font(20));
        scoreText.setX(10);
        scoreText.setY(30);
        root.getChildren().add(scoreText);

        // Game over text
        gameOverText = new Text();
        gameOverText.setFill(Color.RED);
        gameOverText.setFont(Font.font(40));
        gameOverText.setVisible(false);
        root.getChildren().add(gameOverText);

        // Connection status
        connectionStatusText = new Text();
        connectionStatusText.setFill(Color.WHITE);
        connectionStatusText.setX(10);
        connectionStatusText.setY(WINDOW_HEIGHT - 20);
        root.getChildren().add(connectionStatusText);

        // Health bars
        player1HealthBar = new ProgressBar(1.0);
        player1HealthBar.setLayoutX(10);
        player1HealthBar.setLayoutY(50);
        player1HealthBar.setPrefWidth(200);
        player1HealthBar.setStyle("-fx-accent: red;");

        player2HealthBar = new ProgressBar(1.0);
        player2HealthBar.setLayoutX(WINDOW_WIDTH - 210);
        player2HealthBar.setLayoutY(50);
        player2HealthBar.setPrefWidth(200);
        player2HealthBar.setStyle("-fx-accent: blue;");

        root.getChildren().addAll(player1HealthBar, player2HealthBar);
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
        if (attacker.isAttacking() && attacker.getAttackBounds().intersects(defender.getBounds())) {
            // 本地攻擊判定，僅限控制的玩家
            if ((isHost && attacker == player1) || (!isHost && attacker == player2)) {
                int damage = 10;
                GameMessage attackMsg = new GameMessage(
                        GameMessage.MessageType.PLAYER_ATTACK,
                        damage, // 這裡直接傳遞傷害值
                        attacker == player1 ? 1 : 2
                );
                gameClient.sendMessage(attackMsg);

                // 僅顯示攻擊效果，實際傷害通過網路同步
                System.out.println("本地攻擊判定：命中！");
            }
        }
    }

    private void update() {
        if (gameClient != null && gameClient.isConnected()) {
            // 處理輸入
            for (KeyCode key : pressedKeys) {
                handleInput(key);
            }

            // 同步位置
            if (isHost) {
                sendPositionUpdate(player1);
            } else {
                sendPositionUpdate(player2);
            }
        }

        // 應用物理
        player1.applyGravity(GRAVITY);
        player2.applyGravity(GRAVITY);

        checkGroundCollision(player1);
        checkGroundCollision(player2);

        // 增加本地攻擊檢測 - 當玩家攻擊時就檢查
        if (player1.isAttacking()) {
            checkAttackCollision(player1, player2);
        }
        if (player2.isAttacking()) {
            checkAttackCollision(player2, player1);
        }

        checkPlayerCollision();
        processNetworkMessages();
        updateScore();
        updateHealthBars();
        checkGameOver();
    }

    private void checkGroundCollision(Player player) {
        double groundY = WINDOW_HEIGHT - 100;
        if (player.getY() >= groundY) {
            player.setY(groundY);
            player.setOnGround(true);
        }
    }

    private void checkPlayerCollision() {
        if (player1.getBounds().intersects(player2.getBounds())) {
            double overlap = calculateOverlap(player1, player2);
            resolveCollision(player1, player2, overlap);
        }
    }

    private double calculateOverlap(Player p1, Player p2) {
        double p1Right = p1.getX() + 30; // Player width
        double p2Left = p2.getX();
        return p1Right - p2Left;
    }

    private void resolveCollision(Player p1, Player p2, double overlap) {
        if (overlap > 0) {
            p1.setX(p1.getX() - overlap / 2);
            p2.setX(p2.getX() + overlap / 2);
            sendPositionUpdate(p1);
            sendPositionUpdate(p2);
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

        double[] position = {player.getX(), player.getY()};
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

        GameMessage message = new GameMessage(
                GameMessage.MessageType.PLAYER_ATTACK,
                null,
                player == player1 ? 1 : 2
        );
        gameClient.sendMessage(message);
    }

    private void handlePositionUpdate(GameMessage message) {
        double[] position = (double[]) message.getData();
        Player targetPlayer = message.getPlayerId() == 1 ? player1 : player2;

        if ((isHost && message.getPlayerId() == 2) || (!isHost && message.getPlayerId() == 1)) {
            targetPlayer.setX(position[0]);
            targetPlayer.setY(position[1]);
        }
    }

    private void handleAttackUpdate(GameMessage message) {
        Player attacker = message.getPlayerId() == 1 ? player1 : player2;
        Player target = message.getPlayerId() == 1 ? player2 : player1;

        // 強制更新位置，確保攻擊判定使用最新位置
        attacker.attack();

        // 獲取正確的攻擊範圍和目標範圍
        Bounds attackBounds = attacker.getAttackBounds();
        Bounds targetBounds = target.getBounds();

        // 檢查碰撞
        if (attackBounds.intersects(targetBounds)) {
            System.out.println("攻擊命中！造成傷害！");

            // 計算傷害
            int damage = 10;
            
            // 發送傷害同步消息
            GameMessage damageMessage = new GameMessage(
                    GameMessage.MessageType.PLAYER_DAMAGE,
                    damage,
                    target == player1 ? 1 : 2 // 標記受傷的玩家ID
            );
            gameClient.sendMessage(damageMessage);
            
            // 本地立即應用傷害，確保視覺效果同步
            target.takeDamage(damage);
        }
    }

    private void handleDamageUpdate(GameMessage message) {
        int damage = (int) message.getData();
        int targetPlayerId = message.getPlayerId();

        System.out.println("收到傷害更新：玩家 " + targetPlayerId + " 受到 " + damage + " 點傷害");

        // 根據消息中的玩家ID來確定誰受傷
        Player targetPlayer = targetPlayerId == 1 ? player1 : player2;
        
        // 確保血量不會低於0
        int currentHealth = targetPlayer.getHealth();
        int newHealth = Math.max(0, currentHealth - damage);
        
        // 應用傷害
        targetPlayer.takeDamage(damage);

        // 立即更新血量條
        Platform.runLater(() -> {
            updateHealthBars();
            checkGameOver();
        });
    }

    private void updateScore() {
        scoreText.setText(String.format("Player 1: %d - Player 2: %d",
                player1.getHealth(), player2.getHealth()));
    }

    private void updateHealthBars() {
        Platform.runLater(() -> {
            double p1Health = Math.max(0, player1.getHealth()) / 100.0;
            double p2Health = Math.max(0, player2.getHealth()) / 100.0;

            player1HealthBar.setProgress(p1Health);
            player2HealthBar.setProgress(p2Health);

            // 根據血量改變顏色
            if (p1Health < 0.3) {
                player1HealthBar.setStyle("-fx-accent: darkred;");
            }
            if (p2Health < 0.3) {
                player2HealthBar.setStyle("-fx-accent: darkred;");
            }

            // 更新分數文本
            scoreText.setText(String.format("%s: %d - %s: %d",
                    player1.getName(), player1.getHealth(),
                    player2.getName(), player2.getHealth()));
        });
    }

    private void checkGameOver() {
        if (player1.getHealth() <= 0 || player2.getHealth() <= 0) {
            String winner = player1.getHealth() <= 0 ? player2.getName() : player1.getName();
            gameOverText.setText(winner + " wins!");
            gameOverText.setX((WINDOW_WIDTH - gameOverText.getLayoutBounds().getWidth()) / 2);
            gameOverText.setY(WINDOW_HEIGHT / 2);
            gameOverText.setVisible(true);
            gameLoop.stop();
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
