package com.example.lab

import com.example.lab.service.LabService
import com.example.lab.repository.OrderRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.OptimisticLockingFailureException
import java.math.BigDecimal
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import kotlin.test.assertFails

@SpringBootTest
class ConcurrencyTest @Autowired constructor(
    private val labService: LabService,
    private val orderRepository: OrderRepository
) {

    @Test
    fun `demo optimistic locking failure`() {
        labService.initData()
        val order = orderRepository.findAll().first()
        
        // 使用 CountDownLatch 確保兩個執行緒「同時」起跑，而非依靠 Thread.sleep
        val startLatch = CountDownLatch(1)
        
        // 執行緒 1：嘗試將金額更新為 150
        val future1 = CompletableFuture.runAsync {
            startLatch.await()
            labService.optimisticLockingVulnerable(order.id, BigDecimal("150.00"))
        }
        
        // 執行緒 2：嘗試將金額更新為 200
        val future2 = CompletableFuture.runAsync {
            startLatch.await()
            labService.optimisticLockingVulnerable(order.id, BigDecimal("200.00"))
        }

        // 發射起跑信號
        startLatch.countDown()

        // 預期其中一個執行緒會因為版本衝突（Optimistic Locking）而失敗
        val exception = assertFails {
            CompletableFuture.allOf(future1, future2).join()
        }
        
        // 驗證異常類型（CompletableFuture 會將原始異常包在 CompletionException 的 cause 中）
        val isOptimisticLockError = exception.cause is OptimisticLockingFailureException
        
        assert(isOptimisticLockError) { 
            "應該要拋出 OptimisticLockingFailureException，但卻拋出了: ${exception.cause}" 
        }
        
        println("✅ Optimistic Locking Demo Success: Confirmed version conflict and caught specific exception!")
    }
}
