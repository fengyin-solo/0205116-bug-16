package com.redtourism.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.redtourism.common.ForbiddenException;
import com.redtourism.common.Result;
import com.redtourism.common.SessionUtils;
import com.redtourism.entity.OrderInfo;
import com.redtourism.entity.User;
import com.redtourism.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @GetMapping("/create")
    public Result<OrderInfo> create(@RequestParam String orderType,
                                     @RequestParam Long targetId,
                                     @RequestParam String targetName,
                                     @RequestParam BigDecimal amount,
                                     @RequestParam(defaultValue = "1") Integer quantity,
                                     @RequestParam(required = false) String checkInDate,
                                     @RequestParam(required = false) String checkOutDate,
                                     HttpSession session) {
        User user = SessionUtils.requireUser(session);
        OrderInfo order = new OrderInfo();
        order.setUserId(user.getId());
        order.setOrderType(orderType);
        order.setTargetId(targetId);
        order.setTargetName(targetName);
        order.setAmount(amount);
        order.setQuantity(quantity);
        return Result.success("下单成功", orderService.createOrder(order));
    }

    @GetMapping("/cancel")
    public Result<String> cancel(@RequestParam Long orderId, HttpSession session) {
        User user = SessionUtils.requireUser(session);
        orderService.cancelOrder(orderId, user.getId());
        return Result.success("取消成功", null);
    }

    @GetMapping("/pay")
    public Result<String> pay(@RequestParam Long orderId,
                               @RequestParam String payMethod,
                               HttpSession session) {
        User user = SessionUtils.requireUser(session);
        orderService.payOrder(orderId, payMethod, user.getId());
        return Result.success("支付成功（模拟）", null);
    }

    @GetMapping("/refund")
    public Result<String> refund(@RequestParam Long orderId, HttpSession session) {
        User user = SessionUtils.requireUser(session);
        orderService.refundOrder(orderId, user.getId());
        return Result.success("退款成功（模拟）", null);
    }

    @GetMapping("/myList")
    public Result<IPage<OrderInfo>> myList(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size,
                                            @RequestParam(required = false) String orderType,
                                            @RequestParam(required = false) String status,
                                            HttpSession session) {
        User user = SessionUtils.requireUser(session);
        return Result.success(orderService.listUserOrders(page, size, user.getId(), orderType, status));
    }

    /** 订单详情：仅订单本人可查，访客与其他用户不可见；管理端请走 /api/admin/order/list */
    @GetMapping("/detail")
    public Result<OrderInfo> detail(@RequestParam Long id, HttpSession session) {
        User user = SessionUtils.requireUser(session);
        OrderInfo order = orderService.getById(id);
        if (order == null || !order.getUserId().equals(user.getId())) {
            throw new ForbiddenException("订单不存在或无权查看");
        }
        return Result.success(order);
    }
}
