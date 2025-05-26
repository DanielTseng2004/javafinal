package com.fightinggame;

import javafx.animation.Animation;
import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.RotateTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public class Player {

    private Group sprite;
    private double velocityY;
    private boolean onGround;
    private int health;
    private String name;
    private static final int PLAYER_WIDTH = 30;
    private static final int PLAYER_HEIGHT = 60;
    private static final int MAX_HEALTH = 100;
    private static final Color PLAYER1_COLOR = Color.BLUE;
    private static final Color PLAYER2_COLOR = Color.RED;
    private Color playerColor;
    
    // Animation components
    private Line leftArm;
    private Line rightArm;
    private Line leftLeg;
    private Line rightLeg;
    private boolean isMoving = false;
    private boolean isAttacking = false;
    private Timeline walkingAnimation;
    private RotateTransition attackAnimation;
    private Timeline damageAnimation;
    private Timeline jumpAnimation;

    private static final int ATTACK_COOLDOWN = 500; // 攻擊冷卻時間（毫秒）
    private long lastAttackTime = 0;
    private Rectangle attackBox;
    private Group visualGroup; // Contains sprite and attack box
    private AnimationTimer attackAnimationTimer;
    private int attackCooldown = 0;

    public Player(double x, double y, String name) {
        this.name = name;
        this.health = MAX_HEALTH;
        this.velocityY = 0;
        this.onGround = false;
        this.isAttacking = false;
        this.attackCooldown = 0;

        // 設置玩家顏色
        this.playerColor = name.equals("Player 1") ? PLAYER1_COLOR : PLAYER2_COLOR;

        // Initialize parent group for all visual elements
        visualGroup = new Group();

        sprite = new Group();
        createStickFigure();
        sprite.setLayoutX(0);
        sprite.setLayoutY(0);
        setupAnimations();

        // 初始化攻擊判定框
        this.attackBox = new Rectangle(40, 30);
        this.attackBox.setFill(Color.TRANSPARENT);
        this.attackBox.setStroke(name.equals("Player 1") ? Color.LIGHTBLUE : Color.PINK);
        this.attackBox.setStrokeWidth(2);
        this.attackBox.setStyle(
                "-fx-stroke: linear-gradient(to bottom, "
                + (name.equals("Player 1") ? "#00ffff, #0088ff" : "#ff88ff, #ff0088") + ");"
                + "-fx-stroke-width: 2;"
                + "-fx-effect: dropshadow(gaussian, "
                + (name.equals("Player 1") ? "rgba(0,255,255,0.5)" : "rgba(255,0,255,0.5)")
                + ", 5, 0, 0, 1);"
        );
        this.attackBox.setVisible(false);

        // Position attack box relative to sprite
        this.attackBox.setX(30); // To the right of sprite
        this.attackBox.setY(0);  // Aligned with sprite top

        // Add all components to visual group
        visualGroup.getChildren().addAll(sprite, attackBox);
        visualGroup.setLayoutX(x);
        visualGroup.setLayoutY(y);

        // 設置攻擊動畫 - 只旋轉右手臂
        attackAnimation = new RotateTransition(Duration.millis(200), rightArm);
        attackAnimation.setFromAngle(0);
        attackAnimation.setToAngle(90);
        attackAnimation.setAutoReverse(true);
        attackAnimation.setCycleCount(1);
        attackAnimation.setOnFinished(event -> {
            isAttacking = false;
            attackBox.setVisible(false);
            rightArm.setRotate(0); // 重置手臂角度
        });
    }

    private void createStickFigure() {
        // Head - make it a circle instead of a line for better visibility
        Rectangle head = new Rectangle(-10, -20, 20, 20);
        head.setFill(playerColor);
        head.setArcWidth(20);
        head.setArcHeight(20);

        // Body
        Line body = new Line(0, 0, 0, 20);
        body.setStroke(playerColor);
        body.setStrokeWidth(2);

        // Left arm
        leftArm = new Line(0, 0, -15, 10);
        leftArm.setStroke(playerColor);
        leftArm.setStrokeWidth(2);

        // Right arm
        rightArm = new Line(0, 0, 15, 10);
        rightArm.setStroke(playerColor);
        rightArm.setStrokeWidth(2);

        // Left leg
        leftLeg = new Line(0, 20, -10, 40);
        leftLeg.setStroke(playerColor);
        leftLeg.setStrokeWidth(2);

        // Right leg
        rightLeg = new Line(0, 20, 10, 40);
        rightLeg.setStroke(playerColor);
        rightLeg.setStrokeWidth(2);

        sprite.getChildren().addAll(head, body, leftArm, rightArm, leftLeg, rightLeg);
    }

    private void setupAnimations() {
        // 行走動畫 - 更自然的擺動效果
        walkingAnimation = new Timeline(
            new KeyFrame(Duration.millis(50), e -> {
                if (isMoving) {
                    leftLeg.setRotate(15);
                    rightLeg.setRotate(-15);
                    leftArm.setRotate(-10);
                    rightArm.setRotate(10);
                }
            }),
            new KeyFrame(Duration.millis(100), e -> {
                if (isMoving) {
                    leftLeg.setRotate(-15);
                    rightLeg.setRotate(15);
                    leftArm.setRotate(10);
                    rightArm.setRotate(-10);
                }
            }),
            new KeyFrame(Duration.millis(150), e -> {
                if (isMoving) {
                    leftLeg.setRotate(15);
                    rightLeg.setRotate(-15);
                    leftArm.setRotate(-10);
                    rightArm.setRotate(10);
                }
            }),
            new KeyFrame(Duration.millis(200), e -> {
                if (isMoving) {
                    leftLeg.setRotate(-15);
                    rightLeg.setRotate(15);
                    leftArm.setRotate(10);
                    rightArm.setRotate(-10);
                }
            })
        );
        walkingAnimation.setCycleCount(Animation.INDEFINITE);

        // 受傷動畫 - 更明顯的震動效果
        damageAnimation = new Timeline(
            new KeyFrame(Duration.millis(0), e -> {
                sprite.setOpacity(0.7);
                sprite.setTranslateX(-8);
                sprite.setTranslateY(-2);
            }),
            new KeyFrame(Duration.millis(50), e -> {
                sprite.setOpacity(0.9);
                sprite.setTranslateX(8);
                sprite.setTranslateY(2);
            }),
            new KeyFrame(Duration.millis(100), e -> {
                sprite.setOpacity(0.7);
                sprite.setTranslateX(-4);
                sprite.setTranslateY(-1);
            }),
            new KeyFrame(Duration.millis(150), e -> {
                sprite.setOpacity(1.0);
                sprite.setTranslateX(0);
                sprite.setTranslateY(0);
            })
        );
        damageAnimation.setCycleCount(1);

        // 跳躍動畫 - 更自然的動作
        jumpAnimation = new Timeline(
            new KeyFrame(Duration.millis(0), e -> {
                if (!onGround) {
                    leftLeg.setRotate(-20);
                    rightLeg.setRotate(-20);
                    leftArm.setRotate(-15);
                    rightArm.setRotate(15);
                }
            }),
            new KeyFrame(Duration.millis(150), e -> {
                if (!onGround) {
                    leftLeg.setRotate(-30);
                    rightLeg.setRotate(-30);
                    leftArm.setRotate(-25);
                    rightArm.setRotate(25);
                }
            }),
            new KeyFrame(Duration.millis(300), e -> {
                if (!onGround) {
                    leftLeg.setRotate(-20);
                    rightLeg.setRotate(-20);
                    leftArm.setRotate(-15);
                    rightArm.setRotate(15);
                }
            })
        );
        jumpAnimation.setCycleCount(1);

        // 攻擊動畫 - 更流暢的動作
        attackAnimation = new RotateTransition(Duration.millis(150), rightArm);
        attackAnimation.setFromAngle(0);
        attackAnimation.setToAngle(90);
        attackAnimation.setAutoReverse(true);
        attackAnimation.setCycleCount(1);
        attackAnimation.setOnFinished(event -> {
            isAttacking = false;
            attackBox.setVisible(false);
            rightArm.setRotate(0);
        });
    }

    public void move(double dx) {
        visualGroup.setLayoutX(visualGroup.getLayoutX() + dx);

        // Start or stop walking animation
        if (dx != 0 && !isMoving) {
            isMoving = true;
            walkingAnimation.play();
        } else if (dx == 0 && isMoving) {
            isMoving = false;
            walkingAnimation.stop();
            resetLimbs();
        }

        // Flip character based on movement direction
        if (dx > 0) {
            sprite.setScaleX(1);
            attackBox.setX(30); // Attack box to the right
        } else if (dx < 0) {
            sprite.setScaleX(-1);
            attackBox.setX(-70); // Attack box to the left (accounting for width)
        }
    }

    private void resetLimbs() {
        leftLeg.setRotate(0);
        rightLeg.setRotate(0);
        leftArm.setRotate(0);
        rightArm.setRotate(0);
    }

    public void setX(double x) {
        visualGroup.setLayoutX(x);
    }

    public void setY(double y) {
        visualGroup.setLayoutY(y);
    }

    public void jump(double force) {
        if (onGround) {
            velocityY = force;
            onGround = false;
            jumpAnimation.play();
        }
    }

    public void applyGravity(double gravity) {
        velocityY += gravity;
        visualGroup.setLayoutY(visualGroup.getLayoutY() + velocityY);
    }

    public void update() {
        // 更新攻擊冷卻
        if (attackCooldown > 0) {
            attackCooldown -= 16; // 假設60FPS，每幀約16ms
        }

        // 更新動畫狀態
        if (isAttacking) {
            // 確保攻擊框位置正確
            if (sprite.getScaleX() < 0) { // 面向左
                attackBox.setX(-70);
            } else { // 面向右
                attackBox.setX(30);
            }
            attackBox.setY(0);
        }
        
    }

    public void attack() {
        if (attackCooldown <= 0) {
            isAttacking = true;
            attackBox.setVisible(true);
            attackCooldown = ATTACK_COOLDOWN;
            
            // 根據角色朝向調整攻擊框位置
            if (sprite.getScaleX() < 0) {
                attackBox.setX(-70);
            } else {
                attackBox.setX(30);
            }
            
            attackBox.setY(0);
            
            // 播放攻擊動畫
            Platform.runLater(() -> {
                attackAnimation.stop();
                attackAnimation.play();
                
                // 添加攻擊特效
                Timeline attackEffect = new Timeline(
                    new KeyFrame(Duration.millis(0), e -> {
                        attackBox.setOpacity(1.0);
                        attackBox.setScaleX(1.0);
                        attackBox.setScaleY(1.0);
                    }),
                    new KeyFrame(Duration.millis(75), e -> {
                        attackBox.setOpacity(0.8);
                        attackBox.setScaleX(1.2);
                        attackBox.setScaleY(1.2);
                    }),
                    new KeyFrame(Duration.millis(150), e -> {
                        attackBox.setOpacity(0.0);
                        attackBox.setScaleX(1.0);
                        attackBox.setScaleY(1.0);
                    })
                );
                attackEffect.play();
                
                // 設置一個計時器來重置攻擊狀態
                new Thread(() -> {
                    try {
                        Thread.sleep(150);
                        Platform.runLater(() -> {
                            isAttacking = false;
                            attackBox.setVisible(false);
                        });
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }).start();
            });
        }
    }

    public void takeDamage(int damage) {
        int oldHealth = health;
        health = Math.max(0, health - damage);

        // 如果生命值確實發生了變化，才播放動畫
        if (oldHealth != health) {
            System.out.println(name + " 受到 " + damage + " 點傷害！剩餘生命值：" + health);

            // 在 JavaFX 線程中執行動畫
            Platform.runLater(() -> {
                damageAnimation.playFromStart();

                // 根據血量不同顯示不同視覺效果
                if (health <= 30) {
                    sprite.setOpacity(0.7);
                }
                if (health <= 0) {
                    sprite.setOpacity(0.5);
                    // 添加死亡特效
                    leftLeg.setRotate(45);
                    rightLeg.setRotate(-45);
                    leftArm.setRotate(30);
                    rightArm.setRotate(-30);
                }
            });
        }
    }

    public double getX() {
        return visualGroup.getLayoutX();
    }

    public double getY() {
        return visualGroup.getLayoutY();
    }

    public boolean isOnGround() {
        return onGround;
    }

    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
        if (onGround) {
            velocityY = 0;
            resetLimbs();
        }
    }

    public Group getSprite() {
        return visualGroup;  // Return the complete visual group
    }

    public Bounds getBounds() {
        // 獲取整體視覺組件在場景中的邊界
        Bounds bounds = visualGroup.getBoundsInParent();
        System.out.println(name + " 角色邊界："
                + String.format("X: %.1f-%.1f, Y: %.1f-%.1f",
                        bounds.getMinX(), bounds.getMaxX(),
                        bounds.getMinY(), bounds.getMaxY()));
        return bounds;
    }

    public int getHealth() {
        return health;
    }

    public String getName() {
        return name;
    }

    public boolean isAttacking() {
        return isAttacking;
    }

    public Rectangle getAttackBox() {
        return attackBox;
    }

    public Bounds getAttackBounds() {
        // 確保攻擊框位置是最新的
        if (sprite.getScaleX() < 0) { // 面向左
            attackBox.setX(-70);
        } else { // 面向右
            attackBox.setX(30);
        }
        attackBox.setY(0);
        
        // 獲取攻擊框在父節點中的實際邊界
        Bounds bounds = attackBox.getBoundsInParent();
        
        // 需要加上玩家的位置偏移
        double playerX = visualGroup.getLayoutX();
        double playerY = visualGroup.getLayoutY();
        
        // 創建實際的攻擊範圍
        Bounds actualBounds = new BoundingBox(
            playerX + bounds.getMinX(),
            playerY + bounds.getMinY(),
            bounds.getWidth(),
            bounds.getHeight()
        );
        
        System.out.println(name + " 攻擊框實際位置：" + 
            String.format("X: %.1f-%.1f, Y: %.1f-%.1f", 
                actualBounds.getMinX(), actualBounds.getMaxX(),
                actualBounds.getMinY(), actualBounds.getMaxY()));
        
        return actualBounds;
    }

    public void showAttackBounds(boolean show) {
        attackBox.setVisible(show);
        if (show) {
            // 當顯示攻擊範圍時，添加閃爍效果
            Timeline flashTimeline = new Timeline(
                    new KeyFrame(Duration.millis(0), e -> attackBox.setOpacity(1.0)),
                    new KeyFrame(Duration.millis(500), e -> attackBox.setOpacity(0.5)),
                    new KeyFrame(Duration.millis(1000), e -> attackBox.setOpacity(1.0))
            );
            flashTimeline.setCycleCount(Timeline.INDEFINITE);
            flashTimeline.play();
        }
    }
}
