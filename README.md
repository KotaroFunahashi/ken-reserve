# 👨‍🏫 けんぼう予約システム
> **フリーランス家庭教師のための、スマートな授業予約・進捗管理プラットフォーム**

個人で活動する家庭教師が、生徒ごとの授業予約、指導報告、および月謝管理を一元化するためのシステムです。

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.3-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
![Gradle](https://img.shields.io/badge/Gradle-9.3.1-02303A?style=for-the-badge&logo=gradle&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=for-the-badge&logo=mysql&logoColor=white)

---

## ✨ Features (主な機能)

- 🗓️ **Flexible Scheduling**: カレンダー形式での授業予約・キャンセル管理。
- 📝 **Study Reports**: 授業ごとの指導内容・宿題の記録と、保護者への共有支援。
- 💰 **Billing Management**: 指導時間に基づいた月謝計算と支払いステータス管理。
- 🔒 **Role-Based Access**: 講師（管理者）と生徒/保護者それぞれの専用マイページ（Spring Security）。

---

## 🛠️ Technology Stack (技術スタック)

- **Language**: Java 21 (LTS)
- **Framework**: Spring Boot 4.0.3
- **View Engine**: Thymeleaf + Tailwind CSS
- **Database**: MySQL 8.0 (Development & Production)
- **Build Tool**: Gradle (Kotlin DSL)
- **ORM**: Spring Data JPA (Hibernate)

---

## 📂 Project Structure (フォルダ構成)

```text
src/main/java/com/coha9nus/kenreserve/
├── config/             # Security設定, WebMvc設定, MySQL接続設定
├── controller/         # 各画面のルーティング (GET/POST)
├── service/            # 予約重複バリデーション, 月謝計算ロジック
├── repository/         # Spring Data JPA (Students, Lessons, Payments)
├── entity/             # MySQLのテーブル定義に対応するEntity
└── dto/                # 画面からの入力値を保持するデータクラス
src/main/resources/
├── templates/          # Thymeleaf テンプレートファイル
└── static/             # CSS / JS / Assets
```

---

## 🚀 Getting Started (環境構築・起動)

### Prerequisites

| ツール | バージョン | 備考 |
|--------|----------|------|
| JDK | 21 | |
| Docker / Docker Compose | 任意 | DBをDockerで起動する場合 |
| MySQL | 8.0 | Dockerを使わない場合 |

### 1. リポジトリのクローン

```bash
git clone <repository-url>
cd ken-reserve
```

### 2. IDE の設定

**VS Code の場合**

`ken-reserve.code-workspace.sample` をコピーして `ken-reserve.code-workspace` を作成し、JDK 21 のパスを設定してください。

**Eclipse の場合**

`File > Import > Gradle > Existing Gradle Project` でインポートし、JDK 21 を使用するよう設定してください。

### 3. データベースの準備

**Docker を使う場合**

```bash
docker-compose up -d
```

MySQL 8.0 が `localhost:3306`、DB名 `ken_reserve`、rootパスワード `password` で起動します。

**Docker を使わない場合**

MySQL 8.0 を別途用意し、DBを作成してください。

```sql
CREATE DATABASE ken_reserve CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 4. アプリケーション設定

`src/main/resources/application.properties` の DB 接続設定（url / username / password）が環境に合っていることを確認してください。

### 5. アプリケーションの起動

```bash
./gradlew bootRun
```

起動後、`http://localhost:8080` にアクセスしてください。
