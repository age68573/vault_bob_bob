# Northstar Windows 啟動手冊

本專案以 WAR 格式部署至 JBoss EAP 8。應用程式啟動時會使用 Vault AppRole
取得短效 token，再讀取 KV v2 路徑：

```text
secret/data/northstar-customer-center/config
```

WAR 已內建 Vault CA 憑證，不需要修改 Windows 憑證存放區或 JBoss truststore。
正常停止 JBoss 時，應用程式會呼叫 `auth/token/revoke-self` 撤銷 Vault token。

## 啟動前準備

1. 確認 JBoss 尚未啟動。
2. 取得 AppRole 的 `role_id`。
3. 取得新的 `secret_id`。
4. 使用 `cmd.exe` 開啟命令提示字元。

目前 AppRole 預期使用單次有效的 `secret_id`。每次重新啟動或重新部署前，建議
產生新的 `secret_id`。

## 啟動指令

在同一個 `cmd.exe` 視窗依序執行：

```bat
set "NORTHSTAR_VAULT_ADDRESS=https://10.107.85.84:8200"
set "NORTHSTAR_VAULT_ROLE_ID=填入-role-id"
set "NORTHSTAR_VAULT_SECRET_ID=填入新的-secret-id"

set NORTHSTAR_VAULT
call C:\jboss-eap-8.0\bin\standalone.bat -c standalone.xml
```

請依實際安裝位置修改 `C:\jboss-eap-8.0`。`set NORTHSTAR_VAULT` 應顯示三個
環境變數，確認後再啟動 JBoss。

不要使用 `setx`，避免將 `secret_id` 長期寫入 Windows 使用者或系統設定。
不要將實際的 `role_id` 或 `secret_id` 寫入 `.bat`、Git 或 WAR。

## 互動輸入

若不想將憑證保留在命令歷史中，可改為啟動時輸入：

```bat
set "NORTHSTAR_VAULT_ADDRESS=https://10.107.85.84:8200"
set /p "NORTHSTAR_VAULT_ROLE_ID=Vault role_id: "
set /p "NORTHSTAR_VAULT_SECRET_ID=Vault secret_id: "

call C:\jboss-eap-8.0\bin\standalone.bat -c standalone.xml
```

`set /p` 輸入時畫面仍會顯示內容。如需隱藏輸入，應由部署工具注入環境變數。

## Vault 位址限制

WAR 內建的憑證 SAN 包含：

```text
jeremy-vault1.palsys.com.tw
10.107.85.84
127.0.0.1
```

`NORTHSTAR_VAULT_ADDRESS` 必須使用以上主機名稱或 IP，否則 TLS 主機名稱驗證
會失敗。若 Vault CA 更新，需要重新建置及部署 WAR。

## 部署 WAR

建置 WAR：

```bash
mvn clean package
```

產物位置：

```text
target\northstar-customer-center.war
```

在 JBoss 管理介面部署最新 WAR。環境變數必須在啟動 `standalone.bat` 前設定；
既有 Java 程序不會取得後續新增或修改的環境變數。

## 常見錯誤

### Missing credential configuration

JBoss 程序沒有讀到 `NORTHSTAR_VAULT_ROLE_ID` 或 `NORTHSTAR_VAULT_SECRET_ID`。
停止 JBoss，在同一個 `cmd.exe` 視窗重新設定環境變數後再啟動。

### Unable to AppRole login

先確認 Vault 位址、`role_id` 與 `secret_id`。若先前已嘗試部署，單次使用的
`secret_id` 可能已失效，請產生新的 `secret_id` 再重試。

### PKIX path building failed

確認部署的是最新 WAR，並確認 WAR 內包含：

```text
WEB-INF/classes/vault-ca.crt
```

另外確認 `NORTHSTAR_VAULT_ADDRESS` 使用憑證 SAN 內的主機名稱或 IP。

## 支援的環境變數

| 環境變數 | 必填 | 預設值 | 說明 |
|---|---|---|---|
| `NORTHSTAR_VAULT_ADDRESS` | 是 | 無 | Vault HTTPS 位址 |
| `NORTHSTAR_VAULT_ROLE_ID` | 是 | 無 | AppRole role ID |
| `NORTHSTAR_VAULT_SECRET_ID` | 是 | 無 | AppRole secret ID |
| `NORTHSTAR_VAULT_NAMESPACE` | 否 | 空字串 | Vault namespace |
| `NORTHSTAR_VAULT_MOUNT` | 否 | `secret` | KV secret engine mount |
| `NORTHSTAR_VAULT_SECRET_PATH` | 否 | `northstar-customer-center/config` | KV secret 路徑 |
