# Northstar Customer Center

Java 17、Jakarta EE 10 與 MongoDB 客戶營運網站，使用 WAR 格式部署於 JBoss EAP 8。

## 功能

- 營運管理員登入與 JWT 驗證
- MongoDB 健康狀態檢查
- 客戶清單查詢
- 建立客戶資料
- 建立客戶後透過 SMTP relay 寄送通知信
- SMTP 寄送失敗時在 Dashboard 顯示告警

## Dashboard 操作

1. 開啟 `http://localhost:8080/northstar-customer-center/`。
2. 按下「登入取得 JWT」。
3. 按下「驗證目前 JWT」，確認 JWT 簽章有效。
4. 新增客戶並輸入收件者 email。
5. 確認客戶資料已建立，並查看 SMTP 寄送結果通知。
6. 按下「清除 JWT」，確認客戶 API 因缺少 JWT 而無法載入資料。

## Build 與部署

應用程式啟動時會使用 AppRole 向 Vault 取得短效 service token，並讀取 KV v2
路徑 `secret/data/northstar-customer-center/config`。正常停止時，應用程式會呼叫
`auth/token/revoke-self` 撤銷 token。`role_id`、`secret_id` 與 Vault token 都不會
打包至 WAR。

Vault CA 憑證位於 `src/main/resources/vault-ca.crt`，建置時會包入 WAR。應用程式
會為 Vault HTTP client 建立專用 truststore，不需要修改 JBoss 或 Windows 的憑證
設定。TLS 主機名稱驗證仍會執行，因此 `NORTHSTAR_VAULT_ADDRESS` 必須使用憑證
SAN 所列出的主機名稱或 IP。CA 更新後，需要重新建置及部署 WAR。

先由 Vault 管理員建立唯讀 policy 與 AppRole：

```bash
vault policy write northstar-readonly - <<'EOF'
path "secret/data/northstar-customer-center/config" {
  capabilities = ["read"]
}
EOF

vault auth enable approle

vault write auth/approle/role/northstar \
  token_policies="northstar-readonly" \
  token_type="service" \
  token_ttl="15m" \
  token_max_ttl="1h" \
  secret_id_ttl="24h" \
  secret_id_num_uses=1

vault read -field=role_id auth/approle/role/northstar/role-id
vault write -f -field=secret_id auth/approle/role/northstar/secret-id
```

每次重新部署前，部署流程都需要提供新的單次使用 `secret_id`。Linux 主機可將
AppRole 憑證放在外部檔案，並限制只有 JBoss 執行帳號可讀：

```bash
chmod 600 /run/northstar/role-id /run/northstar/secret-id

export JAVA_OPTS="$JAVA_OPTS \
  -Dnorthstar.vault.address=https://vault.example.com:8200 \
  -Dnorthstar.vault.role-id-file=/run/northstar/role-id \
  -Dnorthstar.vault.secret-id-file=/run/northstar/secret-id"
```

在 Windows 上使用 `standalone.bat` 時，也可以在啟動 JBoss 前直接輸入字串：

```bat
set "NORTHSTAR_VAULT_ADDRESS=https://vault.example.com:8200"
set /p "NORTHSTAR_VAULT_ROLE_ID=Vault role_id: "
set /p "NORTHSTAR_VAULT_SECRET_ID=Vault secret_id: "

call C:\jboss-eap-8.0\bin\standalone.bat -c standalone.xml
```

使用 `set /p` 輸入的 `secret_id` 會顯示在畫面上。若需要隱藏輸入內容，應由部署
工具注入環境變數。不要將 `secret_id` 直接寫入 `.bat`、Git 或 WAR。
環境變數必須在同一個命令提示字元視窗中，於執行 `standalone.bat` 之前設定。
若 JBoss 已在執行中，必須停止並重新啟動 JBoss；既有 Java 程序不會取得之後
新增或修改的環境變數。

可選設定：

| Java system property | 環境變數 | 預設值 |
|---|---|---|
| `northstar.vault.role-id` | `NORTHSTAR_VAULT_ROLE_ID` | 無 |
| `northstar.vault.secret-id` | `NORTHSTAR_VAULT_SECRET_ID` | 無 |
| `northstar.vault.role-id-file` | `NORTHSTAR_VAULT_ROLE_ID_FILE` | 無 |
| `northstar.vault.secret-id-file` | `NORTHSTAR_VAULT_SECRET_ID_FILE` | 無 |
| `northstar.vault.namespace` | `NORTHSTAR_VAULT_NAMESPACE` | 空字串 |
| `northstar.vault.mount` | `NORTHSTAR_VAULT_MOUNT` | `secret` |
| `northstar.vault.secret-path` | `NORTHSTAR_VAULT_SECRET_PATH` | `northstar-customer-center/config` |

完成主機與 Vault 設定後再建置及部署 WAR：

```bash
mvn clean package

"$JBOSS_HOME/bin/jboss-cli.sh" --connect \
  --command="deploy target/northstar-customer-center.war --force"
```
