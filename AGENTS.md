# AGENTS.md

MathWake（二度寝防止アラーム）を開発するエージェント向けのガイドです。
コードを変更する前に、このファイルと `app/src/main` 配下の実装を確認してください。

## 1. プロジェクト概要

- **アプリ名**: MathWake
- **目的**: 数学問題に指定回数正解するまで停止できないアラーム。二度寝・無意識の停止・脱出を防ぐ。
- **技術スタック**: Kotlin / Jetpack Compose (Material 3) / Room / AlarmManager / Foreground Service / Device Admin / Accessibility Service / CameraX / ZXing (QR)
- **UI 言語**: 日本語（`strings.xml` および Compose 内の表示文字列）
- **コードコメント**: 英語を基本とする
- **リポジトリ**: `Minashin1120/alarm-pro-android`（`main` ブランチ）

### ビルド構成

| 項目 | 値 |
|---|---|
| `namespace` | `com.example` |
| `applicationId` | `com.aistudio.mathwake.qvxn` |
| `minSdk` | 24 |
| `compileSdk` | 36.1 |
| `targetSdk` | 36 |
| Java / Kotlin target | Java 11（CI は JDK 17） |
| Kotlin | 2.2.10 |
| AGP | 9.1.1 |
| Compose BOM | 2024.09.00 |
| Room | 2.7.0（DB version 2） |
| CameraX / ZXing | 1.5.0 / 3.5.3（QRコード解除） |

> `namespace` は `com.example`、実際のパッケージ名は `com.aistudio.mathwake.qvxn` です。
> ソースの import は `com.example.*`、`R` は `com.example.R` を使います。

## 2. 絶対に守るルール

1. **この環境では Gradle ビルドを実行しない。**
   ビルド検証は GitHub Actions で行う。ユーザーから明示的な許可がある場合のみ
   `./gradlew` を実行する。
2. **`applicationId` を変更しない。**
   変更すると既存ユーザーのアップデート、Device Owner、PendingIntent、保存データが壊れる。
3. **署名設定と `ci/debug.keystore` を変更・再生成しない。**
   CI は固定の共有デバッグ keystore を使う。再生成すると既存 APK の上書きインストールが失敗する。
4. **秘密情報をコミットしない。**
   `.env`、API キー、GitHub トークン、署名パスワードなどを追加・出力しない。
5. **脱出防止機能を勝手に削除・弱体化しない。**
   Device Owner、Accessibility Service、セットアップ UI、ADB ヘルパーはこのアプリの中核機能。
6. **実機で確認していない動作を「確認済み」と書かない。**
   特に電源ボタン・通知シェード・顔認証まわりは端末依存が大きい。
7. **`KEYCODE_POWER` をアプリ側で消費すれば電源メニューを防げる、と主張しない。**
   通常のアプリや Accessibility Service には電源キーが届かない機種が多く、これは補助的な実装。

## 3. ビルドと CI

### 許可された場合のローカルコマンド

```bash
./gradlew assembleDebug        # APK 生成
./gradlew testDebugUnitTest    # JVM ユニットテスト
./gradlew lintDebug            # Lint
```

### GitHub Actions

- `.github/workflows/android.yml`
  - `main` / `master` / `develop` への push（`app/**` など変更時）で実行
  - `assembleDebug` に続けて `testDebugUnitTest`（JVM ユニットテスト）を実行
  - `assembleDebug` 後、非 PR 実行では **`ci-run-<run_number>`** の prerelease を作成
  - APK は Release の `app-debug.apk` から取得できる
  - 古い `app-debug` artifact は自動削除される
- `.github/workflows/release.yml`
  - `vX.Y.Z` タグ、または手動実行で署名付き Release APK を作成

### 変更後の標準フロー

1. 変更内容を確認し、`git diff --check` で空白エラーを確認
2. コミット（例: `fix: ...` / `feat: ...` / `ci: ...`）
3. `./scripts/push-with-ssh.sh` で SSH push（`git push origin main` でも可）
   - SSHキーの選択／生成、push、GitHub Actions の完了、`ci-run-<N>` の確認まで自動化する
4. Actions が失敗した場合はログを読んで修正
5. 成功したら Release `ci-run-<N>` の URL をユーザーに伝える

**AGENTS.md やドキュメントのみの変更は CI の path filter 対象外**のため、ビルドは走らない。

### SSH push（`scripts/push-with-ssh.sh`）

CI の確認まで含めた push はこのスクリプトを使う。

1. 初回のみ SSH キーを用意する
   - 既存キーを使う: `./scripts/push-with-ssh.sh --key ~/.ssh/id_ed25519`
   - 新規作成: `./scripts/push-with-ssh.sh --generate-key`
     → 表示された**公開鍵**を https://github.com/settings/keys に登録する
   - 自動検出順: `$MATHWAKE_SSH_KEY` → `~/.ssh/mathwake_ed25519` → `id_ed25519` → `id_rsa`
2. `./scripts/push-with-ssh.sh` で `origin` を SSH に切り替えて push
   （ポート22が使えない場合は `ssh.github.com:443` に自動フォールバック）
3. リモートの SHA 一致を確認し、そのコミットの Actions 完了と `ci-run-<N>` を待つ
4. CI を待たない: `--no-verify` ／ push せず確認だけ: `--verify-only`

- 認証に使ったキーは**リポジトリローカルの `core.sshCommand`** に保存されるため、
  以降は `git push origin main` でも動く（`.git/config` のみ。コミットはされない）。
- `scripts/**` や `*.md` だけの変更は workflow の path filter 外なので CI は走らない。
  スクリプトはこの場合 CI 確認をスキップする（`--verify-only` は常に確認する）。
- API レート制限（HTTP 403/429）が出たら
  `GITHUB_TOKEN=... ./scripts/push-with-ssh.sh` を付ける。
- 秘密鍵の中身は表示もコミットもしない。公開鍵（`.pub`）だけを共有する。

## 4. ディレクトリ構成

```
app/src/main/java/com/example/
├── MainActivity.kt                  # エントリポイント、画面切替、権限ダイアログ表示
├── ActiveAlarmActivity.kt           # 鳴動中の全画面アラーム画面、脱出防止、問題UI
├── alarm/
│   ├── AlarmScheduler.kt            # 次回発火時刻の計算と AlarmManager 登録
│   ├── AlarmReceiver.kt             # 発火時に Service と Activity を起動
│   ├── AlarmService.kt              # 前景サービス、音・バイブ・音量固定・フォーカス奪還
│   ├── BootReceiver.kt              # 再起動後に有効アラームを再登録
│   ├── LockTaskController.kt        # Device Owner のロックタスクモード制御
│   ├── PowerMenuBlockerAccessibilityService.kt # 通知シェード/電源メニュー遮断
│   └── MathWakeDeviceAdminReceiver.kt          # Device Admin / Device Owner Receiver
├── data/
│   ├── AppDatabase.kt               # Room DB（version 2, MIGRATION_1_2 あり）
│   ├── dao/AlarmDao.kt
│   ├── model/AlarmEntity.kt         # 曜日ビットマスク、難易度、音量設定
│   └── repository/AlarmRepository.kt
├── math/MathProblemGenerator.kt     # 難易度別の問題生成（純粋ロジック）
├── ui/
│   ├── components/QrScannerView.kt  # CameraX + ZXing のインアプリQRスキャナ
│   ├── screens/                     # AlarmList / AddEdit / Practice / LockdownSetup / QrCodeSetup
│   ├── theme/                       # 色・タイポグラフィ
│   └── viewmodel/AlarmViewModel.kt  # CRUD、アラーム登録、テスト鳴動
└── util/
    ├── PermissionManager.kt         # 権限状態の集約と設定画面遷移
    ├── AccessibilityController.kt   # ユーザー補助を鳴動中だけ有効化/無効化
    ├── AccessibilityServiceList.kt  # ENABLED_ACCESSIBILITY_SERVICES の純粋ロジック
    ├── QrUnlockPayload.kt           # QRペイロードの生成・検証（純粋ロジック）
    ├── QrUnlockManager.kt           # 端末内トークンの保存
    └── QrCode*.kt                   # QR生成・デコード・印刷/共有

app/src/test/java/com/example/       # JUnit4 + Robolectric
app/src/main/res/xml/                # accessibility / device_admin / backup 設定
scripts/setup-device-owner.sh        # ADB で Device Owner を設定する補助スクリプト
scripts/grant-accessibility-control.sh # ADB で WRITE_SECURE_SETTINGS を付与する
```

## 5. アラームの動作フロー

```
AlarmScheduler.scheduleAlarm()
  └─ AlarmManager (setExactAndAllowWhileIdle / 権限なし時は setAndAllowWhileIdle)
       └─ AlarmReceiver (ACTION_ALARM_TRIGGER)
            ├─ AlarmService.startForeground()   … 音・バイブ・通知・音量固定
            └─ ActiveAlarmActivity              … ロック画面上に全画面表示
                 └─ 数学問題に requiredCorrectCount 回正解
                      ├─ （qrRequired の場合）印刷したQRコードをカメラでスキャン
                      └─ dismissAlarmAndExit()   … Service 停止、lock task 終了、finishAndRemoveTask()
```

- 繰り返しアラーム（`daysOfWeek != 0`）は発火後に次回分を再登録する。
- 単発アラーム（`daysOfWeek == 0`）は発火後に `isEnabled = false` へ更新する。
- `BootReceiver` が再起動後に有効アラームを再登録する。
- `AlarmEntity.daysOfWeek` は **月曜=bit0 … 日曜=bit6** のビットマスク（127 = 毎日）。
- `AlarmEntity.qrRequired` が true のアラームは、数学に加えて**印刷QRのスキャン**が必要。
- `AlarmService.isAlarmActive` は脱出防止サービスが参照するため、鳴動状態と必ず一致させる。
- `AlarmService` は `isAlarmActive = true` の直後に Accessibility フォールバックを有効化し、
  `stopAlarm()` で無効化する（`AccessibilityController`）。常時オンにしない。
- 固定音量モードでは鳴動中だけ `STREAM_ALARM` の音量と ringer mode を上書きする。
  元の値は SharedPreferences（`mathwake_audio_restore`）に保存し、解除時に
  `restoreSystemAudioState()` で必ず戻す。ここを外すと端末のアラーム音量設定が
  アラームのたびに書き換わる。

## 6. 脱出防止（Lockdown）設計 — 最重要

### 二層構造

1. **Device Owner + ロックタスクモード（本命 / OS レベル）**
   - `LockTaskController.enter()` が `setLockTaskPackages` → `setLockTaskFeatures(LOCK_TASK_FEATURE_NONE)` → `startLockTask()` を実行。
   - 電源メニュー、通知シェード、ロック画面、Home/Overview、他アプリ起動を OS が無効化する。
   - `LOCK_TASK_FEATURE_NONE` を明示的に設定できるのは **Android 9（API 28）以降**。
     Android 8.x ではロックタスクの既定の UI 制限に依存する。
   - **Device Owner でない場合は何もしない**。画面ピン留めへのフォールバックはしない
     （確認ダイアログが出るうえ、Android 12+ では電源メニューを塞げないため）。
   - `ActiveAlarmActivity.onResume()` で進入、`dismissAlarmAndExit()` で退出する。

2. **Accessibility Service（Device Owner なしのベストエフォート / 任意）**
   - `PowerMenuBlockerAccessibilityService` が System UI のウィンドウイベントを監視。
   - 通知シェード: `TYPE_ACCESSIBILITY_OVERLAY` の透明シールドでステータスバー上のスワイプを遮断し、
     検知時は `GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE` / `GLOBAL_ACTION_BACK` で閉じる。
   - 電源メニュー: 検知後に `GLOBAL_ACTION_BACK` で閉じ、短時間の全画面タッチブロッカーを出す。
   - **電源ボタン自体は横取りできない**ため、電源メニューは「開いた直後に閉じる」までが限界。
   - **画面内容・キー入力は取得しない**。`accessibility_service_config.xml` では
     `canRetrieveWindowContent` / `canRequestFilterKeyEvents` / `canPerformGestures` をすべて
     false にし、イベントの `className` / `packageName` だけで検知する。
     これらを true にすると銀行・決済アプリが「ユーザー補助をオフにしてください」と警告するため、
     安易に戻さない。
   - **鳴動中だけ有効にする（必須）**。`AccessibilityController` が担当する。
     - `AlarmService` が `isAlarmActive = true` にした直後に
       `AccessibilityController.enableForAlarm()` を呼び、`stopAlarm()` で `disable()` する。
     - サービス自身も、アイドル状態で `canDisableWhileIdle()` が true なら `disableSelf()` する。
       アプリ更新後に設定が残っている場合や、ユーザーが手動でオンにした場合の保険。
     - `canDisableWhileIdle()` は **Device Owner** または **`WRITE_SECURE_SETTINGS`（ADBで付与）**
       のときだけ true。どちらも無い端末で `disableSelf()` すると次のアラームで再開できず、
       脱出防止が永久に壊れるため。
     - Device Owner はロックタスクで足りるので、このサービスを有効化しない（有効なら自動でオフ）。
     - `WRITE_SECURE_SETTINGS` は `./scripts/grant-accessibility-control.sh` で1回付与する
       （`pm grant` + Android 13+ は `cmd appops set ... ACCESS_RESTRICTED_SETTINGS allow`）。
       アプリ更新・再起動後も維持される。
     - `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` を書き換えるときは
       `AccessibilityServiceList` で他のサービスを消さない。元の
       `ACCESSIBILITY_ENABLED` の値も SharedPreferences に保存して戻す。
   - **`isAccessibilityTool="true"` で検出を回避しようとしない**。Google Play は障害者支援ツールに
     限定しており、審査済みの Play 配信アプリしか Play Integrity の App Access Risk から除外されない。
     サイドロードした本アプリでは意味がなく、ポリシー違反になる。
   - **常時オンにしない理由**: 決済アプリは「読み取り権限があるか」ではなく
     **非システムのユーザー補助サービスが有効か**で判定する。Play Integrity の App Access Risk は
     サイドロードアプリを `UNKNOWN_CONTROLLING` として報告するため、能力を最小にしても
     常時オンでは検出される。詳細は「既知の落とし穴」を参照。
   - **任意機能として扱う**。`allCriticalGranted` に含めない。Device Owner 設定時は不要と案内する。

### 印刷QRコード解除（物理的な起床証明）

数学だけでは起きられない人向けの、2段階目の解除条件。

- アプリが端末ごとに1つのQRコードを発行し、ユーザーが印刷してベッドから離れた場所
  （洗面所など）に貼る。鳴動中はそのQRをスキャンしないと解除できない。
- トークンは `QrUnlockManager` が SharedPreferences（`mathwake_qr_unlock`）に保存する。
  DB には保存しない。ペイロードは `MATHWAKE1:<32文字>`、判定は `QrUnlockPayload.isValid()` のみ。
- `AlarmEntity.qrRequired` はアラーム単位のフラグ。数学 → QR の順で、両方完了して初めて解除。
- QRの再発行は印刷済みの紙をすべて無効にするため、`QrCodeSetupScreen` で確認ダイアログを出す。
  ペイロード形式（`PREFIX` / `TOKEN_LENGTH` / `ALPHABET`）を変えるのも同じ影響がある。
- **QRが一度も発行されていない端末ではQR段階をスキップする**（`ActiveAlarmScreen` の
  `isQrStageRequired`）。印刷された紙が存在し得ないのに解除不能で詰むのを防ぐための保険。
- QRの有効化は「カメラ権限あり」かつ「トークン発行済み」が条件。カメラ権限を後から
  取り消されるとQR解除アラームを止められなくなるため、AddEdit と QR 画面の両方で警告する。
- スキャンは `QrScannerView`（CameraX + ZXing）でアプリ内に閉じて行う。外部アプリを
  起動しないので Device Owner のロックタスク中でも動く。フロント/バックカメラを切り替えられる。
- 印刷は `QrPrintDocumentAdapter`（`PrintManager` のPDF）、共有は `FileProvider`
  （`res/xml/file_paths.xml`）で PNG を渡す。
- **QR画像のスクリーンショット**: QRセットアップ画面は `FLAG_SECURE` でスクリーンショット／
  画面録画を禁止している。ただし別端末での撮影までは防げないため、「スクショ保存は完全に
  防げる」とUIや文書に書かない。

### 権限状態

`PermissionManager.LockdownPermissionStatus` が唯一の判定元。

- `allCriticalGranted`: Overlay / Exact Alarm / 通知 / 電池最適化除外 が揃った状態（簡易遮断モード）。
  Accessibility は**含めない**（任意機能）
- `isFullLockdownGranted`: 上記 + Device Admin + **Device Owner**（完全ロックダウン）
- `isAccessibilityControlGranted`: `WRITE_SECURE_SETTINGS` があり、鳴動中だけ自動で
  オン/オフできる状態か（ADB で1回付与）
- `isAccessibilityFallbackActive`: 任意の Accessibility フォールバックが使える状態か
  （現在有効、または自動切替の許可済み。表示専用）
- `isAccessibilityAlwaysOn`: 有効なままで自動オフできない状態か（決済アプリ警告の原因）
- `missingCount`: セットアップ UI の未設定件数（Accessibility は数えない）

権限を追加・変更したら、以下をすべて更新する。

- `PermissionManager.checkStatus()`
- `LockdownSetupDialog` の表示とボタン
- `AlarmListScreen` のバナー / アイコン色
- `MainActivity` の自動ダイアログ表示条件
- `missingCount` の計算

### Device Owner の前提

```bash
adb shell dpm set-device-owner \
  com.aistudio.mathwake.qvxn/com.example.alarm.MathWakeDeviceAdminReceiver
```

または `./scripts/setup-device-owner.sh`。

- **root 化ではない。** ADB の shell ユーザーから `DevicePolicyManager` の管理権限を与える。
- 端末にアカウントが残っている、既に Device Owner / 仕事用プロファイルがある場合は失敗する。
- 多くの端末では **ファクトリーリセットが必要**。解除も基本的に初期化が必要。
- Android 14+ で APK を直接インストールした場合、Accessibility を手動で有効にする前に
  **設定 → アプリ → MathWake → 右上メニュー →「制限付き設定を許可」** が必要。
  （`WRITE_SECURE_SETTINGS` 経由の自動切替はこの画面を経由しない）
- Device Owner ならロックタスクで足りるため、Accessibility を有効にする必要はない。
  既に有効な場合、アプリはアイドル時に自動でオフにする。
- Device Owner を使わず Accessibility フォールバックを使う場合は、
  `./scripts/grant-accessibility-control.sh` で `WRITE_SECURE_SETTINGS` を付与する。
  これが無い端末ではサービスを常時オンにするしかなく、決済アプリの警告対象になる。
- セットアップ UI の説明文は、この制約をユーザーに正しく伝える形を維持する。

### 触る際の注意

- `GLOBAL_ACTION_LOCK_SCREEN` には依存しない（SystemUI の既知不具合を避ける）。
- オーバーレイの種類を変えない。
  - ステータスバーより上に出す必要がある箇所は `TYPE_ACCESSIBILITY_OVERLAY`。
  - `TYPE_APPLICATION_OVERLAY` はステータスバーより下に出るため、通知シェード遮断には不十分。
- `LockTaskController.exit()` はアラーム終了時だけでなく、例外・画面破棄時も確実に通す。
- Device Owner の有無で UI 文言（「完全ロックダウン」/「簡易遮断モード」）が変わるため、
  動作を変えたら両方の文言を更新する。

## 7. コーディング規約

- Kotlin 公式スタイル、**2 スペースインデント**、末尾カンマを維持する。
- Compose の関数は PascalCase、それ以外は camelCase。ユーティリティは `object`。
- 画面から直接 DB を触らず、`AlarmViewModel` / Repository を経由する。
- テーマ色は `com.example.ui.theme.Color.kt` の定数を使う。画面内で色コードを直書きしない。
  - 正: `import com.example.ui.theme.Slate400`
  - 誤: `import androidx.compose.ui.theme.Slate400`
- 文字列は原則 `strings.xml` へ。画面固有の長文は既存箇所に合わせて Compose 内にあってよい。
- 既存の無関係なコードを一括整形しない。差分は目的に絞る。
- 新しい依存を追加する場合は `gradle/libs.versions.toml` に追加し、
  `app/build.gradle.kts` のコメントアウトされた依存関係の意図を尊重する。
- DB スキーマを変更する場合は `version` を上げ、`Migration` を追加する
  （`fallbackToDestructiveMigration` は使わない）。

## 8. 変更時のチェックリスト

- [ ] `namespace` / `applicationId` / 署名を変えていない
- [ ] 新しい Activity / Service / Receiver / 権限を `AndroidManifest.xml` に登録した
- [ ] 新しい権限を `PermissionManager` とセットアップ UI に反映した
- [ ] QR解除を触ったら、カメラ権限・トークン未発行時のフォールバックを壊していない
- [ ] `AlarmEntity` のフィールド追加時に既存 DB からの移行を考慮した
- [ ] `AlarmService.isAlarmActive` と画面のライフサイクルが矛盾していない
- [ ] 脱出防止の Device Owner 経路と Accessibility 経路の両方を壊していない
- [ ] Accessibility サービスをアイドル時に残していない（鳴動中だけ有効）
- [ ] 表示文字列が日本語として自然で、実装の制約と矛盾していない
- [ ] 純粋ロジック（問題生成・次回時刻計算など）にテストを追加した
- [ ] `git diff --check` が通る
- [ ] push 後、GitHub Actions の成功と Release を確認した（ローカルビルドはしない）

## 9. 既知の落とし穴

- **import ミス**: 色は `com.example.ui.theme.*`。`androidx.compose.ui.theme` には存在しない。
- **`MathProblem` のフィールド名**: `questionText`（`question` ではない）。
- **Room の初期化**: `AppDatabase.getDatabase(context)` のシングルトンを使う。
- **AlarmManager**: Android 12+ では `canScheduleExactAlarms()` を確認し、
  未許可時は inexact にフォールバックする。`SCHEDULE_EXACT_ALARM` と `USE_EXACT_ALARM` の両方が manifest にある。
- **Foreground Service**: Android 8+ は `startForegroundService()`、14+ は
  `foregroundServiceType="mediaPlayback"` と `FOREGROUND_SERVICE_MEDIA_PLAYBACK` が必須。
- **Accessibility は OS に停止され得る**: バッテリー最適化除外やサービス再起動を前提にする。
- **決済アプリの警告（重要）**: 多くの決済アプリは「ユーザー補助が有効か」だけで判定し、
  読み取り能力の有無は見ない。さらに Play Integrity の App Access Risk は、
  サイドロードされたアプリに有効な非システム Accessibility Service があると
  `UNKNOWN_CONTROLLING` を返す（Play 配信・システムアプリは `KNOWN_`、審査済みツールは除外）。
  本アプリは GitHub の APK を直接入れるため、能力を最小にしても常時オンでは検出され得る。
  - 対策は「鳴動中だけ有効化」であり、`isAccessibilityTool="true"` ではない。
  - `canRetrieveWindowContent` / `canRequestFilterKeyEvents` を true に戻さない。
  - サービスは任意扱いなので、必須化・常時オンの誘導をしない。
  - 常時オンになっている端末では、セットアップ UI のバナーから
    `WRITE_SECURE_SETTINGS` の付与（自動切替）または手動オフへ誘導する。
- **メーカー差**: Xiaomi / Samsung などは電源メニューのパッケージ名・クラス名が異なる。
  検知条件を追加するときは `isSystemUiPackage()` / `isPowerClass()` / `isShadeClass()` を拡張する。
- **`EXPAND_STATUS_BAR` は補助**: 主たる遮断は overlay と Accessibility 側。
- **QRトークンの未発行**: `EXTRA_QR_REQUIRED` だけ渡してトークンを発行しないと、鳴動中に
  解除不能になる。`ActiveAlarmScreen` はトークンが無い場合QR段階をスキップする保険を持つが、
  AddEdit 側で「トークン未発行ならQRを有効化できない」制約を外さない。
- **QRスキャンの回転**: ZXing の `PlanarYUVLuminanceSource` は回転非対応で、
  `rotateCounterClockwise()` は `UnsupportedOperationException` を投げる。カメラの
  `imageInfo.rotationDegrees` を適用しようとすると縦向き端末では毎フレーム失敗し、
  QRを映しても永久に検出されない。ZXing のQR検出は回転非依存なので、Y平面をそのまま渡す
  （`QrCodeDecoder` のコメントと `MathWakeUnitTest` の回転フレームテストを維持する）。
- **Room の DEFAULT**: `qrRequired` は `@ColumnInfo(defaultValue = "0")` と
  `MIGRATION_1_2` の `DEFAULT 0` を一致させる。片方だけ変えると起動時に
  「Migration didn't properly handle」でクラッシュする。
- **印刷QRの再発行**: ペイロードを変えると印刷済みの紙が全部無効になる。既存ユーザーの
  アラームが止められなくなるため、互換性を壊す変更をしない。
- **`.aistudio/` は生成物**: 実装計画などのメタデータ。原則編集しない。

## 10. テスト

- JVM テスト: `app/src/test/java/com/example/MathWakeUnitTest.kt`
  - 問題生成（全難易度で選択肢 4 個・重複なし・正解を含む）
  - 難易度エスカレーション
  - `AlarmEntity` の時刻・曜日サマリー・難易度ラベル
  - `AlarmScheduler.calculateNextTriggerTime()`
  - QR解除ペイロードの生成・検証（`QrUnlockPayload`）と、ZXingでの生成→デコード往復
  - Accessibility の有効サービス一覧の追加/削除（他のサービスを消さないこと）
- Robolectric と Compose UI test の依存は導入済み。UI テストを追加する場合は
  `app/src/androidTest` と既存の testTag を活用する。
- 端末依存の挙動（Device Owner / 電源メニュー / 通知シェード）は自動テストではなく、
  実機で「Device Owner あり / なし」の両方を確認する。

## 11. Git とリリース

- ブランチ: `main`
- コミットメッセージは Conventional Commits 風:
  - `fix: ...`
  - `feat: ...`
  - `ci: ...`
  - `docs: ...`
- push すると `app/**` の変更に応じて Android CI が走り、成功時に `ci-run-<N>` prerelease が作られる。
- CI 確認込みの push は `./scripts/push-with-ssh.sh`（詳細は 3 章）。
  `core.sshCommand` 設定済みなら `git push origin main` でもよい。
- 正式リリースは `vX.Y.Z` タグ（例: `v1.0.0`）で Release Build ワークフローを起動する。
- ビルド成果物（`app/build/`、`*.apk`）や `.gradle/` はコミットしない。

## 12. 参考ファイル

| 目的 | ファイル |
|---|---|
| アラーム登録・次回時刻 | `app/src/main/java/com/example/alarm/AlarmScheduler.kt` |
| 発火処理 | `app/src/main/java/com/example/alarm/AlarmReceiver.kt` |
| 鳴動・音量・フォーカス | `app/src/main/java/com/example/alarm/AlarmService.kt` |
| アラーム画面・終了処理 | `app/src/main/java/com/example/ActiveAlarmActivity.kt` |
| Device Owner 制御 | `app/src/main/java/com/example/alarm/LockTaskController.kt` |
| Accessibility 遮断 | `app/src/main/java/com/example/alarm/PowerMenuBlockerAccessibilityService.kt` |
| Accessibility の鳴動中のみ制御 | `app/src/main/java/com/example/util/AccessibilityController.kt` |
| 権限判定 | `app/src/main/java/com/example/util/PermissionManager.kt` |
| セットアップ UI | `app/src/main/java/com/example/ui/screens/LockdownSetupDialog.kt` |
| 一覧・状態バナー | `app/src/main/java/com/example/ui/screens/AlarmListScreen.kt` |
| データモデル | `app/src/main/java/com/example/data/model/AlarmEntity.kt` |
| 問題生成 | `app/src/main/java/com/example/math/MathProblemGenerator.kt` |
| Device Owner 設定 | `scripts/setup-device-owner.sh` |
| Accessibility 自動切替の許可 | `scripts/grant-accessibility-control.sh` |
| SSH push と CI 確認 | `scripts/push-with-ssh.sh` |
| CI | `.github/workflows/android.yml` |
| 署名付きリリース | `.github/workflows/release.yml` |
