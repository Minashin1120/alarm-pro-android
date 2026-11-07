# MathWake（算数覚醒アラーム）

数学の計算問題を解いて指定回数連続で正解するまでアラームを停止できない、二度寝・寝過ごしを徹底防止するAndroidアラームアプリ。

---

## User Review & Critical Decisions

> [!IMPORTANT]
> ユーザー様からのフィードバック（音量のアプリ内個別設定および端末音量遵守オプションの追加）を反映しました。

- **アラーム解除条件**: 連続指定問数（1問 / 3問 / 5問からアラームごとに設定可能、デフォルト3問）正解するまでアラームが鳴り続ける。
- **音量設定＆脱出防止仕様**:
  - **音量モード選択**:
    - **独自音量モード（推奨）**: アプリ内のスライダーで音量（0〜100%）を設定。アラーム鳴動時はその音量に強制設定され、鳴動中に音量キーで下げられても設定音量へ自動復元して固定。
    - **端末音量遵守モード**: 端末の現在のシステムアラーム音量をそのまま維持して鳴動。
  - **全画面ロック**: ロック画面上へのフルスクリーン起動（`setShowWhenLocked` / `setTurnScreenOn`）、戻るボタンの無効化、全画面維持。※Android OSの制約上、物理電源ボタンのハードウェア長押しによる強制再起動のみOSセキュリティ仕様により介入不可ですが、それ以外の抜け道を最大限塞ぎます。
- **誤答時のペナルティ**: 特別なペナルティ（カウント全リセット等）は設けず、不正解時は即座に次の問題へ切り替わり、目標の連続正解数に達するまで解答を継続する仕様。

---

## 1. Overview & Core Concept

- **目的**: 朝起きられない人やアラームを無意識に止めて二度寝してしまう人のために、脳を覚醒させる計算問題を解かないと停止できない環境を提供する。
- **主な機能**:
  1. **アラーム設定・管理**:
     - 時刻設定、曜日リピート、ラベル。
     - 計算問題の難易度（簡単・普通・難しい）。
     - 連続必要正解数（1 / 3 / 5問）。
     - **音量設定**: アプリ指定音量（0〜100%スライダー） または 「端末の音量設定を遵守」トグル。
     - バイブレーション ON/OFF。
  2. **アラーム鳴動＆計算画面**:
     - ロック画面上でのフルスクリーン表示（画面点灯・スリープ解除）。
     - 音量固定制御（独自音量モード時は設定値に常時復元、端末遵守モード時はシステム音量を維持）。
     - 4択選択肢形式の算数問題（直感的にタップして解答可能）。
     - 連続正解ゲージ（「あと○問で解除！」を視覚化）。
  3. **即時テスト鳴動機能**: 設定したアラームや計算問題をいつでも試せる「テスト起動」機能。
  4. **計算トレーニングモード**: アラーム時間外でも各難易度の計算パズルを練習できるモード。

---

## 2. User Experience & Visual Design

### Key User Flows
1. **アラーム一覧＆新規登録**:
   - トップ画面で登録済みアラームの一覧とON/OFF切り替え。
   - アラームカードで設定時間・難易度・音量設定（例：「音量: 80% (固定)」または「音量: 端末設定準拠」）を確認可能。
   - フローティングアクションボタン（FAB）または追加ボタンで作成ダイアログ/シートを開く。
   - 時刻選択、難易度、連続正解数、音量設定スライダー（端末準拠スイッチ付き）を選択して保存。
2. **アラーム鳴動時**:
   - 端末がロックされていても画面が点灯し、フルスクリーンのアラーム画面が強制起動。
   - 指定された音量（または端末音量）＋振動が作動。
   - 画面中央に大きな算数問題と4つの選択肢カードが表示。
   - 正解するとプログレスバーが進み、指定問数をクリアすると「起床成功！」アニメーションとともにアラーム停止。
3. **テスト起動**:
   - アラームカードの「テスト鳴動」ボタンから、実際の解除フローや音量挙動を安全に体験可能。

### Visual Identity & Theme
- **テーマ**: "Neon Awakening / Midnight Energy"
- **カラーパレット**:
  - Background: 深いミッドナイトダーク (`#0F172A`)
  - Surface: 奥行きのあるスレートカード (`#1E293B`, `#334155`)
  - Primary Accent: 鮮烈なエナジーオレンジ / アンバー (`#F59E0B`, `#F97316`)
  - Success/Correct: エメラルドグリーン (`#10B981`)
  - Alarm Warning: パルスレッド / コーラル (`#EF4444`)
- **タイポグラフィ**:
  - 時刻・計算式: 太字で視認性の高い等幅・数字フォントデザイン。寝ぼけた目でも即座に読める大きめフォント。

---

## 3. Key Product Decisions & Trade-Offs

- **計算問題の出題アルゴリズム**:
  - *簡単*: 1桁〜2桁の足し算・引き算（例: $8 + 7$, $23 - 9$）
  - *普通*: 2桁の足し算・引き算、九九〜2桁×1桁（例: $34 + 48$, $7 \times 8$, $54 - 28$）
  - *難しい*: 2桁×2桁、四則混合計算（例: $13 \times 14$, $(17 + 8) \times 3$, $96 \div 4 + 15$）
  - 選択肢生成: 正解値に近いもっともらしい誤答（$+1, -1, +10$ や計算ミスしやすい値）を自動生成。
- **音量制御ロジック**:
  - `useDeviceVolume == true` の場合: `AudioManager` の音量変更を行わず、現在のシステム `STREAM_ALARM` の音量で鳴動。
  - `useDeviceVolume == false` の場合: `AudioManager` の最大音量に対する `volumePercent` の比率を算出して適用。鳴動中は音量ダウンキーの操作を検知し、指定音量へ即座に復帰させて音量をキープ。
- **ローカル永続化**:
  - Roomデータベースを採用し、アラーム設定を確実に保存。

---

## 4. Technical Architecture & Data Strategy

### System Architecture Diagram

```
┌────────────────────────────────────────────────────────┐
│                      MathWake UI                       │
├──────────────────────────┬─────────────────────────────┤
│      AlarmListScreen     │      ActiveAlarmActivity    │
│  - Alarm Cards & Switch  │  - Fullscreen over Lock     │
│  - Add/Edit Alarm Sheet  │  - Volume Control & Monitor │
│  - Volume Mode Setting   │  - Math Quiz Card & Options │
│  - Math Training Mode    │                             │
└────────────┬─────────────┴──────────────▲──────────────┘
             │                            │
             ▼                            │
┌──────────────────────────┐   ┌──────────┴──────────────┐
│      AlarmViewModel      │   │       AlarmService      │
│  - CRUD Alarms           │   │  - Foreground Service   │
│  - Schedule AlarmManager │   │  - MediaPlayer Loop     │
└────────────┬─────────────┘   │  - Volume Enforcer Loop │
             │                 └──────────▲──────────────┘
             ▼                            │
┌──────────────────────────┐   ┌──────────┴──────────────┐
│      Room Database       │   │   AlarmBroadcastReceiver│
│  - AlarmEntity & Dao     │◄──┤  - Receives Alarm Intent│
└──────────────────────────┘   └─────────────────────────┘
```

### Data Model (`AlarmEntity`)
- `id: Long` (主キー)
- `hour: Int`, `minute: Int`
- `daysOfWeek: Int` (曜日フラグ)
- `isEnabled: Boolean`
- `difficulty: String` ("EASY", "MEDIUM", "HARD")
- `requiredCorrectCount: Int` (1, 3, 5)
- `useDeviceVolume: Boolean` (true: 端末音量準拠, false: アプリ独自指定)
- `volumePercent: Int` (0〜100, デフォルト80)
- `label: String`
- `vibrate: Boolean`

### Escape-Prevention Mechanisms
1. **Lock Screen Overlay**: `setShowWhenLocked(true)`, `setTurnScreenOn(true)`, `window.addFlags(FLAG_KEEP_SCREEN_ON)`.
2. **Back Navigation**: `onBackPressedDispatcher` および Compose `BackHandler` で何もしないように無効化。
3. **Volume Lockdown**: アプリ独自音量モード時、`AudioManager` の音量ストリームを定期チェックし、音量が低下した場合は即座に設定値へ復帰 + ハードウェア音量キーイベント（`dispatchKeyEvent`）の消費。
4. **Service Persistence**: 高優先度フォアグラウンドサービス（`AlarmService`）でバックグラウンドでも鳴動が持続し、通知タップで即座に計算画面へ復帰。
