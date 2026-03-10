package com.example.lab.repository

import com.example.lab.entity.Order
import com.example.lab.entity.OrderItem
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface OrderRepository : JpaRepository<Order, Long> {
    
    // 用於修復 N+1 問題：使用 Fetch Join
    @Query("SELECT o FROM Order o JOIN FETCH o.items")
    fun findAllWithItemsJoinFetch(): List<Order>

    // 用於修復 N+1 問題：使用 EntityGraph
    @EntityGraph(attributePaths = ["items"])
    @Query("SELECT o FROM Order o")
    fun findAllWithItemsEntityGraph(): List<Order>
}

@Repository
interface OrderItemRepository : JpaRepository<OrderItem, Long>
