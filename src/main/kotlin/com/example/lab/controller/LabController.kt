package com.example.lab.controller

import com.example.lab.service.LabService
import com.example.lab.service.OrderDto
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

@RestController
@RequestMapping("/api/lab")
class LabController(private val labService: LabService) {

    @PostMapping("/init")
    fun init() = labService.initData()

    // 1. Long Transaction
    @PostMapping("/long-tx/vulnerable")
    fun longTxVulnerable(@RequestParam id: Long) = labService.longTransactionVulnerable(id)

    @PostMapping("/long-tx/refactored")
    fun longTxRefactored(@RequestParam id: Long) = labService.longTransactionRefactored(id)

    // 2. N+1
    @GetMapping("/n-plus-one/vulnerable")
    fun nPlusOneVulnerable() = labService.nPlusOneVulnerable()

    @GetMapping("/n-plus-one/refactored")
    fun nPlusOneRefactored() = labService.nPlusOneRefactored()

    // 4. Dirty Checking
    @PostMapping("/dirty-checking/vulnerable")
    fun dirtyCheckingVulnerable(@RequestParam id: Long) = labService.dirtyCheckingVulnerable(id)

    @PostMapping("/dirty-checking/refactored")
    fun dirtyCheckingRefactored(@RequestParam id: Long) = labService.dirtyCheckingRefactored(id)

    // 5. Self-invocation
    @PostMapping("/self-invocation/vulnerable")
    fun selfInvocationVulnerable(@RequestParam id: Long) = labService.selfInvocationVulnerable(id)

    @PostMapping("/self-invocation/refactored")
    fun selfInvocationRefactored(@RequestParam id: Long) = labService.selfInvocationRefactored(id)

    // 6. Lazy Init Exception
    @GetMapping("/lazy-init/vulnerable")
    fun lazyInitVulnerable(@RequestParam id: Long): String {
        val order = labService.getOrderEntity(id)
        // 此處 Session 已關閉，存取 items 會噴錯
        return "Items count: ${order.items.size}"
    }

    @GetMapping("/lazy-init/refactored")
    fun lazyInitRefactored(@RequestParam id: Long): OrderDto {
        return labService.getOrderDto(id)
    }

    // 7. Rollback Trap
    @PostMapping("/rollback/vulnerable")
    fun rollbackVulnerable(@RequestParam id: Long) = labService.rollbackVulnerable(id)

    @PostMapping("/rollback/refactored")
    fun rollbackRefactored(@RequestParam id: Long) = labService.rollbackRefactored(id)
}
