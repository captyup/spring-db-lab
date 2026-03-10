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

    fun longTransactionRefactored(orderId: Long) {
        // Step 1: 先更新 DB
        updateStatus(orderId, "PROCESSING")

        // Step 2: 外部 API 呼叫 (不佔用事務)
        Thread.sleep(5000)
    }

    @Transactional
    fun updateStatus(orderId: Long, status: String) {
        val order = orderRepository.findById(orderId).orElseThrow()
        order.status = status
        orderRepository.save(order)
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

    @Transactional(readOnly = true)
    fun nPlusOneRefactored(): List<String> {
        val orders = orderRepository.findAllWithItemsJoinFetch() // 1 條 SQL (Join Fetch)
        return orders.map { order ->
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

    @Transactional
    fun dirtyCheckingRefactored(orderId: Long) {
        val order = orderRepository.findById(orderId).orElseThrow()
        // 手動脫離 Persistence Context
        entityManager.detach(order)
        order.status = "TEMP_STATUS_FOR_CALCULATION"
        // 不會觸發更新
    }


    // 5. 自我調用失效 (Self-invocation)
    // ---------------------------------------------------------
    fun selfInvocationVulnerable(orderId: Long) {
        // 直接調用內部的 @Transactional 方法，AOP 代理失效，不會開啟事務
        updateOrderInTransaction(orderId)
    }

    @Transactional
    fun selfInvocationRefactored(orderId: Long) {
        // 方案一：從 ApplicationContext 取得代理物件
        val proxy = applicationContext.getBean(LabService::class.java)
        proxy.updateOrderInTransaction(orderId)
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
    
    // 修復方案：在 Service 層完成 DTO 轉換
    @Transactional(readOnly = true)
    fun getOrderDto(orderId: Long): OrderDto {
        val order = orderRepository.findById(orderId).orElseThrow()
        return OrderDto(
            orderNumber = order.orderNumber,
            items = order.items.map { it.productName }
        )
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

    @Transactional(rollbackFor = [Exception::class])
    @Throws(Exception::class)
    fun rollbackRefactored(orderId: Long) {
        val order = orderRepository.findById(orderId).orElseThrow()
        order.status = "SAFE_STATUS"
        orderRepository.save(order)
        
        throw Exception("Oops! I will rollback now.")
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

    private fun writeActionLog(orderId: Long, action: String) {
        // 模擬寫入 Log 時發生資料庫庫或其他錯誤
        throw RuntimeException("為訂單 [$orderId] 寫入 Log [$action] 失敗！發生錯誤導致整筆交易回滾。")
    }

    @Transactional
    fun saveAndFlushRefactored(orderId: Long) {
        val order = orderRepository.findById(orderId).orElseThrow()
        order.status = "PAID"
        
        // 直接 save 即可，不需要 flush，Transaction Commit 時自然會 Flush
        orderRepository.save(order)
        
        try {
            // 必須透過代理物件呼叫，才能讓 REQUIRES_NEW 生效
            val proxy = applicationContext.getBean(LabService::class.java)
            proxy.writeActionLogRequiresNew(orderId, "Order paid")
        } catch (e: Exception) {
            // 攔截異常，避免回傳至外層事務
            println("Caught exception: ${e.message}")
        }
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    fun writeActionLogRequiresNew(orderId: Long, action: String) {
        // 模擬寫入 Log 發生異常
        throw RuntimeException("寫入 Log 失敗！但因為是 REQUIRES_NEW，只會 Rollback 自己，不會影響訂單的更新。")
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

data class OrderDto(val orderNumber: String, val items: List<String>)
