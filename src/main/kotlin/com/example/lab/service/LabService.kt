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
