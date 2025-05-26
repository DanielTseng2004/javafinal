// GameLauncher.java
package com.fightinggame;

import java.net.InetAddress;

import com.fightinggame.network.GameServer;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class GameLauncher extends Application {

    private static final int DEFAULT_PORT = 5000;

    @Override
    public void start(Stage primaryStage) {
        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(30));
        root.getStyleClass().add("root");

        Button hostButton = new Button("創建遊戲");
        Button joinButton = new Button("加入遊戲");

        // 服務器設置
        HBox serverSettings = new HBox(10);
        serverSettings.setAlignment(Pos.CENTER);
        TextField ipField = new TextField();
        ipField.setPromptText("伺服器IP地址");
        ipField.setPrefWidth(150);

        TextField portField = new TextField(String.valueOf(DEFAULT_PORT));
        portField.setPromptText("端口");
        portField.setPrefWidth(70);

        serverSettings.getChildren().addAll(ipField, portField);

        hostButton.setOnAction(e -> {
            startServer();
            primaryStage.close();
        });

        joinButton.setOnAction(e -> {
            String ip = ipField.getText().trim();
            if (ip.isEmpty()) {
                showError("請輸入伺服器IP地址");
                return;
            }

            int port = DEFAULT_PORT;
            try {
                String portText = portField.getText().trim();
                if (!portText.isEmpty()) {
                    port = Integer.parseInt(portText);
                }
            } catch (NumberFormatException ex) {
                showError("端口號格式不正確");
                return;
            }

            startGame(false, ip, port);
            primaryStage.close();
        });

        root.getChildren().addAll(
                new Label("格鬥遊戲連接設置"),
                hostButton,
                new Label("- 或 -"),
                serverSettings,
                joinButton
        );

        Scene scene = new Scene(root, 400, 300);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        primaryStage.setTitle("格鬥遊戲 - 啟動器");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("錯誤");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void startServer() {
        try {
            GameServer server = new GameServer(DEFAULT_PORT);
            new Thread(() -> {
                try {
                    server.start();
                } catch (Exception e) {
                    System.out.println("Server error: " + e.getMessage());
                }
            }).start();

            // Wait for server to start
            Thread.sleep(1000);

            // 顯示服務器信息的對話框
            Platform.runLater(() -> {
                Stage infoStage = new Stage();
                VBox infoBox = new VBox(10);
                infoBox.setAlignment(Pos.CENTER);
                infoBox.setPadding(new Insets(20));

                // 獲取本機IP地址
                String localIP = "未知";
                try {
                    localIP = InetAddress.getLocalHost().getHostAddress();
                } catch (Exception e) {
                    localIP = "請查看網絡設置";
                }

                Label ipLabel = new Label("伺服器IP地址: " + localIP);
                Label portLabel = new Label("伺服器端口: " + server.getPort());
                Label infoLabel = new Label("請告訴其他玩家以上信息，以便連接到您的遊戲");

                Button okButton = new Button("確定");
                okButton.setOnAction(e -> infoStage.close());

                infoBox.getChildren().addAll(ipLabel, portLabel, infoLabel, okButton);

                Scene infoScene = new Scene(infoBox, 400, 200);
                infoStage.setScene(infoScene);
                infoStage.setTitle("伺服器信息");
                infoStage.show();
            });

            // Create game instance
            Game game = new Game(true, "localhost", server.getPort());
            startGame(game);
        } catch (Exception e) {
            System.out.println("Error starting server: " + e.getMessage());
        }
    }

    private void startGame(boolean isHost, String serverAddress, int serverPort) {
        try {
            // If client, wait to ensure server is started
            if (!isHost) {
                Thread.sleep(1000);
            }

            Game game = new Game(isHost, serverAddress, serverPort);
            Stage gameStage = new Stage();
            gameStage.setTitle("格鬥遊戲 - " + (isHost ? "主機" : "客戶端"));

            Scene gameScene = new Scene(game.getRoot(), 800, 600);
            gameScene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

            // Set up input handling
            game.setupInputHandling(gameScene);

            gameStage.setScene(gameScene);
            gameStage.show();

            // Start game
            game.start();

            // Stop game when window is closed
            gameStage.setOnCloseRequest(event -> {
                game.stop();
            });
        } catch (Exception e) {
            System.out.println("Error starting game: " + e.getMessage());
        }
    }

    private void startGame(Game game) {
        Stage gameStage = new Stage();
        Scene gameScene = new Scene(game.getRoot(), 800, 600);
        gameStage.setScene(gameScene);
        gameStage.setTitle("格鬥遊戲 - 主機");
        gameStage.show();

        // Set up input handling
        game.setupInputHandling(gameScene);

        // Start game
        game.start();

        // Stop game when window is closed
        gameStage.setOnCloseRequest(event -> {
            game.stop();
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
