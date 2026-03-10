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
9. [實驗 8：saveAndFlush 迷思 (saveAndFlush Misconception)](#實驗-8saveandflush-迷思-saveandflush-misconception)

---

## 🛠 環境初始化

### 1. 啟動應用程式
本實驗室預設使用 **H2 In-Memory Database** (MySQL 相容模式)，無需安裝 Docker。
確保你的 JDK 版本為 21，直接執行：
```bash
./gradlew bootRun
```

### 2. 初始化測試資料
在開始任何實驗前，請先呼叫此 API 建立基礎數據：
```bash
curl -X POST http://localhost:8080/api/lab/init
```

---

## 🧪 實驗環節

### 實驗 1：長事務 (Long Transaction)
*   **痛點**：在 `@Transactional` 中執行耗時的非資料庫操作，會導致 Connection Pool 耗盡。
*   **Step A (問題代碼)**：`LabService.longTransactionVulnerable`。
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
*   **挑戰**：如何重構此方法，讓連線在執行耗時操作前就釋放？

### 實驗 2：N+1 查詢問題 (N+1 Query)
*   **痛點**：查詢多筆資料時，Lazy Loading 導致迴圈內不斷發送額外的 SELECT。
*   **Step A (問題代碼)**：`LabService.nPlusOneVulnerable`。
*   **Step B (Demo 方式)**：
    ```bash
    # 觀察控制台日誌：會看到 1 條查詢 Order 的 SQL + N 條查詢 OrderItem 的 SQL
    curl http://localhost:8080/api/lab/n-plus-one/vulnerable
    ```
*   **挑戰**：如何改寫 Repository 或查詢方式，將 SQL 數量降為 1 條？

### 實驗 3：併發衝突 (Optimistic Locking)
*   **痛點**：高併發下，後發生的更新會直接覆蓋先發生的更新（遺失更新 Lost Update），且系統不會報錯。
*   **Step A (問題代碼)**：`LabService.optimisticLockingVulnerable`。
*   **Step B (複現問題)**：
    目前 `main` 分支的 `Order` Entity 尚未加上 `@Version`。執行整合測試：
    ```bash
    # 預期結果：測試會「失敗」(Fail)，因為沒有拋出預期的樂觀鎖異常，數據被無聲覆蓋了。
    ./gradlew test --tests "com.example.lab.ConcurrencyTest"
    ```
*   **Step C (修復挑戰)**：
    1. 前往 `src/main/kotlin/com/example/lab/entity/Entities.kt`。
    2. 為 `Order` 類別補上 `@Version var version: Long = 0`。
*   **Step D (驗證修復)**：
    再次執行整合測試：
    ```bash
    # 預期結果：測試會「通過」(Pass)，證實系統已成功攔截併發衝突。
    ./gradlew test --tests "com.example.lab.ConcurrencyTest"
    ```

### 實驗 4：髒檢查副作用 (Dirty Checking)
*   **痛點**：Hibernate 自動將 Managed 狀態的變更同步回資料庫，即使沒呼叫 `save()`。
*   **Step A (問題代碼)**：`LabService.dirtyCheckingVulnerable`。
*   **Step B (Demo 方式)**：
    ```bash
    curl -X POST "http://localhost:8080/api/lab/dirty-checking/vulnerable?id=1"
    # 檢查資料庫：http://localhost:8080/h2-console
    # 執行 SELECT * FROM ORDERS WHERE ID = 1; 你會發現狀態已被改為 TEMP_STATUS_FOR_CALCULATION
    ```
*   **挑戰**：在不刪除修改邏輯的前提下，如何阻止自動更新？

### 實驗 5：自我調用失效 (Self-invocation)
*   **痛點**：內部方法直接呼叫另一個 `@Transactional` 方法時，代理失效，導致事務不生效。
*   **Step A (問題代碼)**：`LabService.selfInvocationVulnerable`。
*   **Step B (Demo 方式)**：
    ```bash
    curl -X POST "http://localhost:8080/api/lab/self-invocation/vulnerable?id=1"
    # 觀察日誌：你會發現並沒有開啟新的 Transaction，且若在方法內拋出異常，資料庫不會 Rollback。
    ```
*   **挑戰**：如何透過 AOP 代理呼叫同類別內的方法？

### 實驗 6：懶加載異常 (Lazy Init Exception)
*   **痛點**：Session 關閉後存取 Lazy 屬性。
*   **Step A (問題代碼)**：`LabController.lazyInitVulnerable`。
*   **Step B (Demo 方式)**：
    ```bash
    # 觀察：前端會收到 500 錯誤，日誌顯示 LazyInitializationException
    curl "http://localhost:8080/api/lab/lazy-init/vulnerable?id=1"
    ```
*   **挑戰**：如何避免在 Controller 層存取 Entity 關聯，或者確保 Session 持續？

### 實驗 7：事務回滾陷阱 (Transaction Rollback Trap)
*   **痛點**：Spring 預設不對 Checked Exception 進行回滾。
*   **Step A (問題代碼)**：`LabService.rollbackVulnerable`。
*   **Step B (Demo 方式)**：
    ```bash
    curl -X POST "http://localhost:8080/api/lab/rollback/vulnerable?id=1"
    # 觀察：雖然拋出了異常，但檢查資料庫，狀態居然已經變更了（沒回滾）！
    ```
*   **挑戰**：如何設定讓特定的 Exception 觸發 Rollback？

### 實驗 8：saveAndFlush 迷思 (saveAndFlush Misconception)
*   **痛點**：工程師在同一個 `@Transactional` 中，先更新訂單狀態並呼叫 `saveAndFlush()`，接著執行後續動作（例如寫入 Log 表）。當寫入 Log 失敗導致例外時，常會誤以為前面的 `saveAndFlush()` 已經「入庫定案」能倖免於難；但其實只要還在同一筆 Transaction 中，遇到 Exception 一樣會「全部」回滾。
*   **Step A (實驗代碼)**：`LabService.saveAndFlushMisconception`。
*   **Step B (Demo 方式)**：
    ```bash
    curl -X POST "http://localhost:8080/api/lab/save-and-flush/vulnerable?id=1"
    # 觀察：日誌會顯示先執行了 UPDATE ORDERS 的 SQL，但隨後因為寫入 Log 發生異常導致 Rollback，最後檢查資料庫，訂單狀態實際並未變更。
    ```
*   **挑戰**：理解 `flush` 與 `commit` 的差異，以及如果需要確保留存紀錄（如 Log），該如何善用 `REQUIRES_NEW` 獨立事務。

---
**💡 提示**：若需要參考正確答案，請切換至 `solutions` 分支。
```bash
git checkout solutions
```
