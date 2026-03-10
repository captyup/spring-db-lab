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
*   **代碼**：`LabService.longTransactionVulnerable`。
*   **Demo**：同時發送 3 個併發請求，觀察 Connection Timeout。
*   **挑戰**：如何重構此方法，讓連線在執行耗時操作前就釋放？

### 實驗 2：N+1 查詢問題 (N+1 Query)
*   **痛點**：查詢多筆資料時，Lazy Loading 導致迴圈內不斷發送額外的 SELECT。
*   **代碼**：`LabService.nPlusOneVulnerable`。
*   **Demo**：觀察日誌，查詢 2 筆訂單卻產生了 3 條 SQL。
*   **挑戰**：如何改寫 Repository 或查詢方式，將 SQL 數量降為 1 條？

### 實驗 3：併發衝突 (Optimistic Locking)
*   **痛點**：高併發下，後發生的更新會覆蓋先發生的更新（遺失更新）。
*   **代碼**：`LabService.optimisticLockingVulnerable`。
*   **Demo**：執行測試 `ConcurrencyTest.kt`。
*   **挑戰**：如何在 Entity 中引入版本控制，讓第二次更新拋出異常？

### 實驗 4：髒檢查副作用 (Dirty Checking)
*   **痛點**：Hibernate 自動將 Managed 狀態的變更同步回資料庫，即使沒呼叫 `save()`。
*   **代碼**：`LabService.dirtyCheckingVulnerable`。
*   **Demo**：呼叫端點後檢查資料庫，狀態是否被意外更改？
*   **挑戰**：在不刪除修改邏輯的前提下，如何阻止自動更新？

### 實驗 5：自我調用失效 (Self-invocation)
*   **痛點**：內部方法直接呼叫另一個 `@Transactional` 方法時，代理失效，導致事務不生效。
*   **代碼**：`LabService.selfInvocationVulnerable`。
*   **挑戰**：如何透過 AOP 代理呼叫同類別內的方法？

### 實驗 6：懶加載異常 (Lazy Init Exception)
*   **痛點**：Session 關閉後存取 Lazy 屬性。
*   **代碼**：`LabController.lazyInitVulnerable`。
*   **挑戰**：如何避免在 Controller 層存取 Entity 關聯，或者確保 Session 持續？

### 實驗 7：事務回滾陷阱 (Transaction Rollback Trap)
*   **痛點**：Spring 預設不對 Checked Exception 進行回滾。
*   **代碼**：`LabService.rollbackVulnerable`。
*   **挑戰**：如何設定讓特定的 Exception 觸發 Rollback？

---
**💡 提示**：若需要參考正確答案，請切換至 `solutions` 分支。
```bash
git checkout solutions
```
