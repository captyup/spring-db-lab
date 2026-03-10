package com.example.lab.repository

import com.example.lab.entity.Order
import com.example.lab.entity.OrderItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface OrderRepository : JpaRepository<Order, Long>

@Repository
interface OrderItemRepository : JpaRepository<OrderItem, Long>
