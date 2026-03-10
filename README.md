# 🚀 Spring Boot 資料庫操作實驗室 (Lab)

這是一個專為資深開發者設計的教學實驗室，旨在展示並解決 Spring Boot / JPA / Hibernate 在高併發與大數據量場景下的 6+1 個經典痛點。

## 📋 目錄
1. [環境初始化](#-環境初始化)
2. [實驗 1：長事務 (Long Transaction)](#實驗-1長事務-long-transaction)
3. [實驗 2：N+1 查詢問題 (N+1 Query)](#實驗-2n1-查詢問題-n1-query)
4. [實驗 3：併發衝突 (Optimistic Locking)](#實驗-3併發衝突-optimistic-locking)
5. [實驗 4：髒檢查副作用 (Dirty Checking)](#實驗-4髒檢查副作用-dirty-checking)
6. [實驗 5：自我調用失效 (Self-invocation)](#實驗-5自我調用失效-self-invocation)
7. [實驗 6：懶加載異常 (Lazy Init Exception)](#實驗-6懶加載異常-lazy-init-exception)
8. [實驗 7：事務回滾陷阱 (Transaction Rollback Trap)](#實驗-7事務回滾陷阱-transaction-rollback-trap)
9. [⚠️ Kotlin Data Class 警告](#-kotlin-data-class-與-jpa-的「身份」陷阱)
10. [🏆 最佳實踐總結](#-最佳實踐總結金流處理檢查清單)

---

## 🛠 環境初始化

### 1. 啟動應用程式
本實驗室預設使用 **H2 In-Memory Database** (MySQL 相容模式)，無需安裝 Docker。
確保你的 JDK 版本為 21，直接執行：
```bash
./gradlew bootRun
```

### 2. 查看資料庫 (H2 Console)
你可以透過瀏覽器查看即時數據變動：
- URL: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:spring_db_lab`
- User: `sa` / Password: (留空)

### 3. 初始化測試資料
在開始任何實驗前，請先呼叫此 API 建立基礎數據：
```bash
curl -X POST http://localhost:8080/api/lab/init
```

---

## 🧪 實驗環節

### 實驗 1：長事務 (Long Transaction)
*   **痛點說明**：在 `@Transactional` 中執行耗時的非資料庫操作（如外部 API 呼叫、文件處理），會導致資料庫連線長時間被佔用，進而耗盡 Connection Pool。
*   **Step A (問題代碼)**：參考 `LabService.longTransactionVulnerable`。
*   **Step B (Demo 方式)**：
    首先，確保 `application.properties` 中限制了連線池大小（例如 `maximum-pool-size=2`）。
    接著，同時發送 3 個併發請求：
    ```bash
    # 同時發送 3 個請求，前 2 個會佔滿連線池，第 3 個會因為超時而失敗 (HTTP 500)
    curl -X POST "http://localhost:8080/api/lab/long-tx/vulnerable?id=1" & \
    curl -X POST "http://localhost:8080/api/lab/long-tx/vulnerable?id=1" & \
    curl -v -X POST "http://localhost:8080/api/lab/long-tx/vulnerable?id=1" & \
    wait
    ```
*   **Step C (修復方案)**：將外部 API 呼叫移出事務塊。

### 實驗 2：N+1 查詢問題 (N+1 Query)
*   **痛點說明**：當查詢多筆資料且每筆資料都有關聯物件時，Lazy Loading 會導致迴圈內不斷發送額外的 SELECT 語句。
*   **Step A (問題代碼)**：參考 `LabService.nPlusOneVulnerable`。
*   **Step B (Demo 方式)**：
    ```bash
    # 觀察控制台日誌：會看到 1 條查詢 Order 的 SQL + N 條查詢 OrderItem 的 SQL
    curl http://localhost:8080/api/lab/n-plus-one/vulnerable
    ```
*   **Step C (修復方案)**：使用 `JOIN FETCH` 或 `@EntityGraph`。參考 `OrderRepository.findAllWithItemsJoinFetch`。

### 實驗 3：併發衝突 (Optimistic Locking)
*   **痛點說明**：在高併發修改下，若無鎖機制，後發生的更新會覆蓋先發生的更新（遺失更新問題）。
*   **Step A (問題代碼)**：參考 `LabServicDDe.optimisticLockingVulnerable`。
*   **Step B (Demo 方式)**：
    執行整合測試 `ConcurrencyTest.kt`。測試會模擬兩個 Thread 同時修改同一個訂單。
*   **Step C (修復方案)**：在 Entity 使用 `@Version` 實作樂觀鎖。當衝突發生時，Spring 會拋出 `OptimisticLockingFailureException`。

### 實驗 4：髒檢查副作用 (Dirty Checking)
*   **痛點說明**：Hibernate 的自動髒檢查機制會將 Persistence Context 中所有 Managed 狀態的變更同步回資料庫，即使你沒呼叫 `save()`。
*   **Step A (問題代碼)**：參考 `LabService.dirtyCheckingVulnerable`。
*   **Step B (Demo 方式)**：
    ```bash
    curl -X POST "http://localhost:8080/api/lab/dirty-checking/vulnerable?id=1"
    # 檢查資料庫：你會發現狀態意外地被更改了
    ```
*   **Step C (修復方案)**：使用 `entityManager.detach(entity)` 或在唯讀場景使用 `@Transactional(readOnly = true)`。

### 實驗 5：自我調用失效 (Self-invocation)
*   **痛點說明**：Spring 的 `@Transactional` 是基於 AOP 代理。當類別內部方法直接呼叫另一個 `@Transactional` 方法時，代理會失效，導致事務不生效。
*   **Step A (問題代碼)**：參考 `LabService.selfInvocationVulnerable`。
*   **Step B (Demo 方式)**：
    ```bash
    curl -X POST "http://localhost:8080/api/lab/self-invocation/vulnerable?id=1"
    # 觀察：如果方法內部拋出異常，資料庫將不會 Rollback
    ```
*   **Step C (修復方案)**：注入 `ApplicationContext` 獲取代理物件，或將事務邏輯拆分到另一個 Service。

### 實驗 6：懶加載異常 (Lazy Init Exception)
*   **痛點說明**：在 Service 層交易結束後，嘗試在 View/Controller 層存取尚未加載的 Lazy 屬性。
*   **Step A (問題代碼)**：參考 `LabController.lazyInitVulnerable`。
*   **Step B (Demo 方式)**：
    ```bash
    # 觀察：前端會收到 500 錯誤，日誌顯示 LazyInitializationException: could not initialize proxy
    curl "http://localhost:8080/api/lab/lazy-init/vulnerable?id=1"
    ```
*   **Step C (修復方案)**：在 Service 層完成 DTO 轉換，或關閉 OSIV 並顯式加載數據。

### 實驗 7：事務回滾陷阱 (Transaction Rollback Trap)
*   **痛點說明**：Spring 預設只對 `RuntimeException` 及其子類進行回滾。若拋出 `Exception` (Checked Exception)，事務會提交，這在需要回滾的業務場景非常危險。
*   **Step A (問題代碼)**：參考 `LabService.rollbackVulnerable`。
*   **Step B (Demo 方式)**：
    ```bash
    curl -X POST "http://localhost:8080/api/lab/rollback/vulnerable?id=1"
    # 觀察：雖然拋出了異常，但去資料庫看，狀態居然已經變更了！
    ```
*   **Step C (修復方案)**：使用 `@Transactional(rollbackFor = [Exception::class])`。

---

## ⚠️ Kotlin Data Class 與 JPA 的「身份」陷阱

在 Kotlin 中，切記 **不要** 在 JPA Entity 上使用 `data class`：
1.  **Lazy Loading 失效**：`data class` 自動生成的 `toString/hashCode` 會存取所有欄位，意外觸發 Lazy Loading 的關聯屬性，導致 SQL 爆炸。
2.  **身份識別錯誤**：如果 Entity 的 `hashCode` 基於會變動的欄位，當 ID 為空（剛建立尚未存檔）或 ID 改變時，該物件在 `Set` 或 `Map` 中的行為會出錯。

**最佳實踐**：使用普通 `class` 並手動定義 `equals` 僅比對 `id`。

---

## 🏆 最佳實踐總結：金流處理檢查清單

1.  **[ ] 事務與外部 API 隔離**：金流第三方支付（綠界、藍新）的呼叫邏輯嚴禁放在 `@Transactional` 內。
2.  **[ ] 樂觀鎖必備**：涉及帳戶餘額、庫存或狀態變更的欄位，必須使用 `@Version`。
3.  **[ ] 避免 N+1**：所有的關聯查詢（List API）應顯式使用 `FETCH JOIN`，避免爆炸性 SQL。
4.  **[ ] 關閉 OSIV**：在 `application.properties` 設置 `spring.jpa.open-in-view=false`。
5.  **[ ] 事務粒度**：保持 Transaction 儘可能短小。
6.  **[ ] 唯讀標註**：對於查詢操作，一律標註 `@Transactional(readOnly = true)`。

---
*Generated by Gemini CLI Lab Coach*
