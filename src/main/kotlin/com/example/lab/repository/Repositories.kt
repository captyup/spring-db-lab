package com.example.lab.repository

import com.example.lab.entity.Order
import com.example.lab.entity.OrderItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface OrderRepository : JpaRepository<Order, Long> {
    // 9. 用於一級快取陷阱：直接用 JPQL 更新，不會清理快取
    @Modifying
    @Query("UPDATE Order o SET o.status = :status WHERE o.id = :id")
    fun updateOrderStatus(id: Long, status: String)

    // 修復方案：加上 clearAutomatically = true 讓 EntityManager 裡的快取失效
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Order o SET o.status = :status WHERE o.id = :id")
    fun updateOrderStatusClearCache(id: Long, status: String)
}

@Repository
interface OrderItemRepository : JpaRepository<OrderItem, Long>
