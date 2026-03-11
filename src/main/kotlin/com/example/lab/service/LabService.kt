package com.example.lab.service

import com.example.lab.entity.Order
import com.example.lab.entity.OrderItem
import com.example.lab.repository.OrderRepository
import jakarta.persistence.EntityManager
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class LabService(
    private val orderRepository: OrderRepository,
    private val entityManager: EntityManager,
    private val applicationContext: ApplicationContext
) {

    // 1. 長事務 (Long Transaction)
    // ---------------------------------------------------------
    @Transactional
    fun longTransactionVulnerable(orderId: Long) {
        val order = orderRepository.findById(orderId).orElseThrow()
        order.status = "PROCESSING"
        orderRepository.save(order)

        // 模擬外部 API 呼叫延遲 5 秒，此時 DB Connection 仍被佔用
        Thread.sleep(5000)
    }


    // 2. N+1 查詢問題 (N+1 Query)
    // ---------------------------------------------------------
    @Transactional(readOnly = true)
    fun nPlusOneVulnerable(): List<String> {
        val orders = orderRepository.findAll() // 1 條 SQL
        return orders.map { order ->
            // 存取 LAZY 加載的 items，每個 order 都觸發一條額外 SQL
            order.items.joinToString { it.productName } 
        }
    }


    // 3. 併發衝突 (Optimistic Locking)
    // ---------------------------------------------------------
    @Transactional
    fun optimisticLockingVulnerable(orderId: Long, amount: BigDecimal) {
        val order = orderRepository.findById(orderId).orElseThrow()
        // 模擬多執行緒競爭，不處理版本衝突
        order.amount = amount
        orderRepository.save(order)
    }


    // 4. 髒檢查副作用 (Dirty Checking)
    // ---------------------------------------------------------
    @Transactional
    fun dirtyCheckingVulnerable(orderId: Long) {
        val order = orderRepository.findById(orderId).orElseThrow()
        // 只想做個計算，沒有要存檔
        order.status = "TEMP_STATUS_FOR_CALCULATION"
        
        // 注意：這裡沒呼叫 save()，但交易結束時 Hibernate 會自動 Flush 並 Update DB！
    }


    // 5. 自我調用失效 (Self-invocation)
    // ---------------------------------------------------------
    fun selfInvocationVulnerable(orderId: Long) {
        // 直接調用內部的 @Transactional 方法，AOP 代理失效，不會開啟事務
        updateOrderInTransaction(orderId)
    }

    @Transactional
    fun updateOrderInTransaction(orderId: Long) {
        val order = orderRepository.findById(orderId).orElseThrow()
        order.status = "UPDATED_BY_AOP"
    }


    // 6. 懶加載異常 (Lazy Init Exception)
    // ---------------------------------------------------------
    @Transactional(readOnly = true)
    fun getOrderEntity(orderId: Long): Order {
        return orderRepository.findById(orderId).orElseThrow()
    }
    

    // 7. 事務回滾陷阱 (Transaction Rollback Trap)
    // ---------------------------------------------------------
    @Transactional
    @Throws(Exception::class)
    fun rollbackVulnerable(orderId: Long) {
        val order = orderRepository.findById(orderId).orElseThrow()
        order.status = "SHOULD_NOT_SEE_THIS"
        orderRepository.save(order)
        
        // 拋出 Checked Exception (Exception)
        // 預設情況下，Spring 不會回滾！
        throw Exception("Oops! Checked Exception occurred.")
    }

    // 8. saveAndFlush 迷思 (saveAndFlush Misconception)
    // ---------------------------------------------------------
    @Transactional
    fun saveAndFlushMisconception(orderId: Long) {
        val order = orderRepository.findById(orderId).orElseThrow()
        order.status = "PAID"
        
        // 工程師以為用 saveAndFlush 就可以確保訂單狀態有被「獨立」更新入庫
        // 因為它會立刻觸發 UPDATE SQL 發送到資料庫，但其實還「尚未 Commit」
        orderRepository.saveAndFlush(order)
        
        // 模擬後續操作：寫入 log table 發生錯誤 (例如違反 Constraints 或網路異常)
        // 因為還在同一個 @Transactional 中，這會導致整筆交易回滾 (Rollback)
        // 儘管前面已經 flush 送出了訂單狀態更新的 SQL，訂單狀態依然會退回原狀！證明 flush ≠ 獨立入庫定案
        writeActionLog(orderId, "Order paid")
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    fun writeActionLogRequiresNew(orderId: Long, action: String) {
        // 模擬寫入 Log 發生異常
        throw RuntimeException("寫入 Log 失敗！但因為是 REQUIRES_NEW，只會 Rollback 自己，不會影響訂單的更新。")
    }

    private fun writeActionLog(orderId: Long, action: String) {
        // 模擬寫入 Log 時發生資料庫庫或其他錯誤
        throw RuntimeException("為訂單 [$orderId] 寫入 Log [$action] 失敗！發生錯誤導致整筆交易回滾。")
    }

    // 9. 一級快取與批量更新 (First-Level Cache & Bulk Update)
    // ---------------------------------------------------------
    @Transactional
    fun firstLevelCacheVulnerable(orderId: Long): String {
        // 1. 先讀取實體到一級快取 (Persistence Context)
        val order = orderRepository.findById(orderId).orElseThrow()
        val oldStatus = order.status // 例如 "NEW"

        // 2. 透過 @Modifying JPQL 直接更新資料庫，這不會同步到一級快取
        orderRepository.updateOrderStatus(orderId, "UPDATED_BY_JPQL")

        // 3. 再次讀取「同一個 ID」的實體
        // 由於 ID 1 已經在一級快取中，Hibernate 會直接回傳快取物件，而不去查資料庫
        val staleOrder = orderRepository.findById(orderId).orElseThrow()
        
        return "Before: $oldStatus, After (Stale Cache): ${staleOrder.status}"
    }

    @Transactional
    fun firstLevelCacheRefactored(orderId: Long): String {
        // 1. 先讀取
        val order = orderRepository.findById(orderId).orElseThrow()
        val oldStatus = order.status

        // 2. 透過帶有 clearAutomatically = true 的 JPQL 更新
        orderRepository.updateOrderStatusClearCache(orderId, "UPDATED_WITH_CLEAR")

        // 3. 再次讀取
        // 因為一級快取已被清空，Hibernate 必須重新發送 SQL 查詢資料庫
        val freshOrder = orderRepository.findById(orderId).orElseThrow()

        return "Before: $oldStatus, After (Fresh Data): ${freshOrder.status}"
    }

    // 初始化測試資料
    @Transactional
    fun initData() {
        orderRepository.deleteAll()
        val order1 = Order(orderNumber = "ORD-001", status = "NEW", amount = BigDecimal("100.00"))
        order1.items.add(OrderItem(productName = "Item A", price = BigDecimal("50.00"), order = order1))
        order1.items.add(OrderItem(productName = "Item B", price = BigDecimal("50.00"), order = order1))
        
        val order2 = Order(orderNumber = "ORD-002", status = "NEW", amount = BigDecimal("200.00"))
        order2.items.add(OrderItem(productName = "Item C", price = BigDecimal("200.00"), order = order2))
        
        orderRepository.saveAll(listOf(order1, order2))
    }
}
