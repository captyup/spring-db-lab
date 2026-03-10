# Gemini Context: Spring Boot Database Lab

這個專案是一個專門用來實驗與展示 Spring Boot / JPA / Hibernate 常見問題的教學實驗室 (Lab)。它涵蓋了長事務、N+1 查詢、併發衝突、髒檢查、自我調用失效、懶加載異常等經典技術陷阱。

## 🚀 專案概觀
- **技術棧**: Kotlin 1.9+, Spring Boot 3.5.x, Spring Data JPA, Hibernate, H2 Database (In-Memory).
- **核心架構**: 標準的 Spring Boot 三層架構 (Controller -> Service -> Repository/Entity)。
- **教學目標**: 透過故意寫出具備缺陷 (Vulnerable) 的程式碼，引導開發者理解底層原理並學習如何修復。

## 🛠 建置與執行
- **啟動應用程式**: `./gradlew bootRun`
- **執行測試**: `./gradlew test`
- **資料庫主控台**: 啟動後可存取 `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:spring_db_lab`)
- **初始化資料**: 在進行任何實驗 API 呼叫前，需先執行初始化：
  ```bash
  curl -X POST http://localhost:8080/api/lab/init
  ```

## 🧪 實驗模組對照表
| 實驗編號 | 主題 | 核心程式碼位置 (Service/Controller) |
| :--- | :--- | :--- |
| 1 | 長事務 (Long Transaction) | `LabService.longTransactionVulnerable` |
| 2 | N+1 查詢問題 | `LabService.nPlusOneVulnerable` |
| 3 | 併發衝突 (樂觀鎖) | `LabService.optimisticLockingVulnerable` |
| 4 | 髒檢查副作用 | `LabService.dirtyCheckingVulnerable` |
| 5 | 自我調用失效 | `LabService.selfInvocationVulnerable` |
| 6 | 懶加載異常 (Lazy Init) | `LabController.lazyInitVulnerable` |
| 7 | 事務回滾陷阱 | `LabService.rollbackVulnerable` |
| 8 | saveAndFlush 迷思 | `LabService.saveAndFlushMisconception` |

## ⚠️ 開發規範與慣例
- **Entity 開放性**: 專案使用了 `kotlin("plugin.jpa")` 與 `allOpen` 外掛，確保 JPA Entity 類別及其屬性在執行期是可開啟 (open) 的，以利 Hibernate 建立代理。
- **資料庫配置**: 預設使用 H2 MySQL 模式，並透過 `HikariCP` 限制連線池大小為 2 (用於實驗 1)。
- **事務管理**: 大部分的業務邏輯均標註為 `@Transactional`。在進行實驗 5 (Self-invocation) 時需注意 AOP 代理失效的問題。
- **測試驅動**: 併發衝突的驗證建議透過 `ConcurrencyTest.kt` 進行自動化測試，而非僅依賴 API 呼叫。
- **修復路徑**: 所有的修復建議都在 `README.md` 中有詳細描述，若需要查看標準答案，請參考 `solutions` 分支。

## 💡 給 Gemini CLI 的提示
- 當使用者提到特定的「實驗編號」或「問題名稱」時，應優先檢查 `LabService.kt` 中對應的方法。
- 在修改 Entity (如增加 `@Version`) 後，應執行 `./gradlew test` 以驗證變更。
- 進行 Backlog Issue 時，請務必在 commit message 中包含 Issue Key。
