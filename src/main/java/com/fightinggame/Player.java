package com.fightinggame;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.RotateTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
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
    private static final Color PLAYER_COLOR = Color.WHITE;
    
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

    public Player(double x, double y, String name) {
        this.name = name;
        this.health = MAX_HEALTH;
        this.velocityY = 0;
        this.onGround = false;
        
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
        this.attackBox.setStroke(Color.RED);
        this.attackBox.setStrokeWidth(1.5);
        this.attackBox.setOpacity(0.7);
        this.attackBox.setVisible(false);
        
        // Position attack box relative to sprite
        this.attackBox.setX(30); // To the right of sprite
        this.attackBox.setY(0);  // Aligned with sprite top
        
        // Add all components to visual group
        visualGroup.getChildren().addAll(sprite, attackBox);
        visualGroup.setLayoutX(x);
        visualGroup.setLayoutY(y);
    }

    private void createStickFigure() {
        // Head - make it a circle instead of a line for better visibility
        Rectangle head = new Rectangle(-10, -20, 20, 20);
        head.setFill(PLAYER_COLOR);
        head.setArcWidth(20);
        head.setArcHeight(20);

        // Body
        Line body = new Line(0, 0, 0, 20);
        body.setStroke(PLAYER_COLOR);
        body.setStrokeWidth(2);

        // Left arm
        leftArm = new Line(0, 0, -15, 10);
        leftArm.setStroke(PLAYER_COLOR);
        leftArm.setStrokeWidth(2);

        // Right arm
        rightArm = new Line(0, 0, 15, 10);
        rightArm.setStroke(PLAYER_COLOR);
        rightArm.setStrokeWidth(2);

        // Left leg
        leftLeg = new Line(0, 20, -10, 40);
        leftLeg.setStroke(PLAYER_COLOR);
        leftLeg.setStrokeWidth(2);

        // Right leg
        rightLeg = new Line(0, 20, 10, 40);
        rightLeg.setStroke(PLAYER_COLOR);
        rightLeg.setStrokeWidth(2);

        sprite.getChildren().addAll(head, body, leftArm, rightArm, leftLeg, rightLeg);
    }

    private void setupAnimations() {
        // Walking animation - improved with smoother transitions
        walkingAnimation = new Timeline(
            new KeyFrame(Duration.millis(100), e -> {
                if (isMoving) {
                    leftLeg.setRotate(20);
                    rightLeg.setRotate(-20);
                    leftArm.setRotate(-15);
                    rightArm.setRotate(15);
                }
            }),
            new KeyFrame(Duration.millis(200), e -> {
                if (isMoving) {
                    leftLeg.setRotate(-20);
                    rightLeg.setRotate(20);
                    leftArm.setRotate(15);
                    rightArm.setRotate(-15);
                }
            })
        );
        walkingAnimation.setCycleCount(Animation.INDEFINITE);

        // Attack animation - synchronized with attack box
        attackAnimation = new RotateTransition(Duration.millis(200), rightArm);
        attackAnimation.setFromAngle(0);
        attackAnimation.setToAngle(90);
        attackAnimation.setAutoReverse(true);
        attackAnimation.setCycleCount(2);
        attackAnimation.setOnFinished(e -> {
            isAttacking = false;
            rightArm.setRotate(0);
            attackBox.setVisible(false);
        });

        // Damage animation - improved with color flash and movement
        damageAnimation = new Timeline(
            new KeyFrame(Duration.millis(0), e -> {
                sprite.setOpacity(0.5);
                sprite.setTranslateX(-5);
            }),
            new KeyFrame(Duration.millis(50), e -> {
                sprite.setOpacity(1.0);
                sprite.setTranslateX(5);
            }),
            new KeyFrame(Duration.millis(100), e -> {
                sprite.setOpacity(0.5);
                sprite.setTranslateX(-3);
            }),
            new KeyFrame(Duration.millis(150), e -> {
                sprite.setOpacity(1.0);
                sprite.setTranslateX(0);
            })
        );
        damageAnimation.setCycleCount(1);

        // Jump animation - improved with arm movements
        jumpAnimation = new Timeline(
            new KeyFrame(Duration.millis(0), e -> {
                if (!onGround) {
                    leftLeg.setRotate(-30);
                    rightLeg.setRotate(-30);
                    leftArm.setRotate(-15);
                    rightArm.setRotate(15);
                }
            }),
            new KeyFrame(Duration.millis(300), e -> {
                if (!onGround) {
                    leftArm.setRotate(-30);
                    rightArm.setRotate(30);
                }
            })
        );
        jumpAnimation.setCycleCount(1);
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

    public void attack() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAttackTime >= ATTACK_COOLDOWN && !isAttacking) {
            isAttacking = true;
            lastAttackTime = currentTime;
            
            // 根據角色朝向調整攻擊框位置
            if (sprite.getScaleX() > 0) {
                attackBox.setX(30); // 向右攻擊
            } else {
                attackBox.setX(-70); // 向左攻擊
            }
            
            // 顯示攻擊判定框
            attackBox.setVisible(true);
            
            // 播放攻擊動畫
            attackAnimation.play();
            
            // 創建一個新的線程來處理攻擊判定的時間
            new Thread(() -> {
                try {
                    // 攻擊持續時間
                    Thread.sleep(200);
                    
                    // 使用 Platform.runLater 確保在 JavaFX 線程中更新 UI
                    Platform.runLater(() -> {
                        isAttacking = false;
                        attackBox.setVisible(false);
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }
    

    public void takeDamage(int damage) {
        int oldHealth = health;
        health = Math.max(0, health - damage);
        
        // 如果生命值確實發生了變化，才播放動畫
        if (oldHealth != health) {
            System.out.println(name + " 受到 " + damage + " 點傷害！剩餘生命值：" + health);
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
        return sprite.getBoundsInParent();
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
        // 獲取攻擊框在父容器坐標系中的邊界，考慮角色的朝向
        Bounds localBounds = attackBox.getBoundsInLocal();
        if (sprite.getScaleX() < 0) {
            // 如果角色朝左，調整攻擊框的位置
            localBounds = attackBox.localToParent(localBounds);
        }
        return localBounds;
    }
    public void showAttackBounds(boolean show) {
        attackBox.setVisible(show);
    }
}