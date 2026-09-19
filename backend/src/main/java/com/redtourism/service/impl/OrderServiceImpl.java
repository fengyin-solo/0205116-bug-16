package com.redtourism.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.redtourism.entity.OrderInfo;
import com.redtourism.entity.User;
import com.redtourism.mapper.OrderInfoMapper;
import com.redtourism.mapper.UserMapper;
import com.redtourism.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.UUID;

@Service
public class OrderServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public OrderInfo createOrder(OrderInfo order) {
        order.setOrderNo("ORD" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        order.setStatus("PENDING");
        save(order);
        return order;
    }

    @Override
    public boolean cancelOrder(Long orderId, Long userId) {
        OrderInfo order = getById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作此订单");
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new RuntimeException("当前订单状态不可取消");
        }
        order.setStatus("CANCELLED");
        return updateById(order);
    }

    @Override
    public boolean payOrder(Long orderId, String payMethod, Long userId) {
        OrderInfo order = getById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作此订单");
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new RuntimeException("当前订单状态不可支付");
        }
        order.setStatus("PAID");
        order.setPayMethod(payMethod);
        order.setPayTime(new Date());
        return updateById(order);
    }

    @Override
    public boolean refundOrder(Long orderId, Long userId) {
        OrderInfo order = getById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作此订单");
        }
        if (!"PAID".equals(order.getStatus())) {
            throw new RuntimeException("当前订单状态不可退款");
        }
        order.setStatus("REFUNDED");
        return updateById(order);
    }

    @Override
    public IPage<OrderInfo> listUserOrders(int page, int size, Long userId, String orderType, String status) {
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getUserId, userId);
        if (StringUtils.hasText(orderType)) {
            wrapper.eq(OrderInfo::getOrderType, orderType);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(OrderInfo::getStatus, status);
        }
        wrapper.orderByDesc(OrderInfo::getCreateTime);
        IPage<OrderInfo> result = page(new Page<>(page, size), wrapper);
        result.getRecords().forEach(this::fillUsername);
        return result;
    }

    @Override
    public IPage<OrderInfo> listAllOrders(int page, int size, String orderType, String status) {
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(orderType)) {
            wrapper.eq(OrderInfo::getOrderType, orderType);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(OrderInfo::getStatus, status);
        }
        wrapper.orderByDesc(OrderInfo::getCreateTime);
        IPage<OrderInfo> result = page(new Page<>(page, size), wrapper);
        result.getRecords().forEach(this::fillUsername);
        return result;
    }

    /** 填充下单人展示名，列表与详情口径一致；用户已被删除时回退为“用户#id” */
    private void fillUsername(OrderInfo order) {
        if (order.getUserId() == null) return;
        User u = userMapper.selectById(order.getUserId());
        if (u != null) {
            order.setUsername(u.getNickname() != null && !u.getNickname().isEmpty()
                    ? u.getNickname() : u.getUsername());
        } else {
            order.setUsername("用户#" + order.getUserId());
        }
    }
}
