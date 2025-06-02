# Fighting Game

這是一個使用 Java 和 JavaFX 開發的格鬥遊戲專案。

## 技術棧

- Java 17
- JavaFX 17.0.2
- Maven

## 系統需求

- JDK 17 或更高版本
- Maven 3.6.0 或更高版本

## 如何運行

1. 確保已安裝 JDK 17 和 Maven
2. 克隆專案到本地
3. 在專案根目錄執行以下命令：
   ```bash
   mvn clean package
   ```
4. 運行生成的 JAR 文件：
   ```bash
   java -jar target/fighting-game-1.0-SNAPSHOT.jar
   ```

## 專案結構

```
src/
├── main/
│   ├── java/        # Java 源代碼
│   └── resources/   # 資源文件（圖片、音效等）
└── test/            # 測試代碼
```

## 主要功能

- 格鬥遊戲核心機制
- 使用 JavaFX 實現的圖形界面
- 角色動畫和特效
- 音效和背景音樂
