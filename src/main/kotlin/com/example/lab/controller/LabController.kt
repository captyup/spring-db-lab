package com.example.lab.controller

import com.example.lab.service.LabService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/lab")
class LabController(private val labService: LabService) {

    @PostMapping("/init")
    fun init() = labService.initData()

    // 1. Long Transaction
    @PostMapping("/long-tx/vulnerable")
    fun longTxVulnerable(@RequestParam id: Long) = labService.longTransactionVulnerable(id)

    // 2. N+1
    @GetMapping("/n-plus-one/vulnerable")
    fun nPlusOneVulnerable() = labService.nPlusOneVulnerable()

    // 4. Dirty Checking
    @PostMapping("/dirty-checking/vulnerable")
    fun dirtyCheckingVulnerable(@RequestParam id: Long) = labService.dirtyCheckingVulnerable(id)

    // 5. Self-invocation
    @PostMapping("/self-invocation/vulnerable")
    fun selfInvocationVulnerable(@RequestParam id: Long) = labService.selfInvocationVulnerable(id)

    // 6. Lazy Init Exception
    @GetMapping("/lazy-init/vulnerable")
    fun lazyInitVulnerable(@RequestParam id: Long): String {
        val order = labService.getOrderEntity(id)
        // 此處 Session 已關閉，存取 items 會噴錯
        return "Items count: ${order.items.size}"
    }

    // 7. Rollback Trap
    @PostMapping("/rollback/vulnerable")
    fun rollbackVulnerable(@RequestParam id: Long) = labService.rollbackVulnerable(id)

    // 8. saveAndFlush Misconception
    @PostMapping("/save-and-flush/vulnerable")
    fun saveAndFlushMisconception(@RequestParam id: Long) = labService.saveAndFlushMisconception(id)

    // 9. First-Level Cache & Bulk Update
    @PostMapping("/first-level-cache/vulnerable")
    fun firstLevelCacheVulnerable(@RequestParam id: Long) = labService.firstLevelCacheVulnerable(id)

    @PostMapping("/first-level-cache/refactored")
    fun firstLevelCacheRefactored(@RequestParam id: Long) = labService.firstLevelCacheRefactored(id)
}
