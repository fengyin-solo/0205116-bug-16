package com.redtourism.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.redtourism.common.Constants;
import com.redtourism.common.Result;
import com.redtourism.common.SessionUtils;
import com.redtourism.entity.*;
import com.redtourism.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.redtourism.mapper.UserCustomRouteMapper;
import com.redtourism.mapper.ScenicSpotImageMapper;
import com.redtourism.mapper.UserMapper;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理端接口。路径级鉴权（/api/admin/** 仅 ADMIN，spot/dashboard 允许 STAFF）
 * 由 SecurityConfig 统一完成，这里只处理业务规则，并用审计日志记录处理人。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private UserService userService;
    @Autowired
    private ScenicSpotService spotService;
    @Autowired
    private RouteService routeService;
    @Autowired
    private CultureService cultureService;
    @Autowired
    private HotelService hotelService;
    @Autowired
    private FoodService foodService;
    @Autowired
    private InteractionService interactionService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private MessageService messageService;
    @Autowired
    private FaqService faqService;
    @Autowired
    private AuditLogService auditLogService;
    @Autowired
    private UserCustomRouteMapper customRouteMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ScenicSpotImageMapper spotImageMapper;
    @Autowired
    private com.redtourism.mapper.SpotSuggestionMapper spotSuggestionMapper;
    @Autowired
    private com.redtourism.mapper.ServiceChatMapper chatMapper;

    // ==================== 用户管理 ====================

    @GetMapping("/user/list")
    public Result<IPage<User>> userList(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size,
                                         @RequestParam(required = false) String role,
                                         @RequestParam(required = false) Integer status,
                                         @RequestParam(required = false) String keyword) {
        IPage<User> result = userService.listUsers(page, size, role, status, keyword);
        // 列表口径与详情一致：任何时候都不对外返回密码
        result.getRecords().forEach(u -> u.setPassword(null));
        return Result.success(result);
    }

    @GetMapping("/user/toggleStatus")
    public Result<String> toggleUserStatus(@RequestParam Long id,
                                           HttpSession session, HttpServletRequest request) {
        User operator = requireAdmin(session);
        User target = userService.getById(id);
        if (target == null) return Result.error("用户不存在");
        if (target.getId().equals(operator.getId())) {
            return Result.error("不能禁用当前登录账号");
        }
        userService.toggleStatus(id);
        audit(operator, "USER", "TOGGLE_STATUS", "userId=" + id,
                "账号状态切换为 " + (target.getStatus() == Constants.STATUS_ENABLED ? "禁用" : "正常"), request);
        return Result.success("操作成功", null);
    }

    @GetMapping("/user/delete")
    public Result<String> deleteUser(@RequestParam Long id,
                                     HttpSession session, HttpServletRequest request) {
        User operator = requireAdmin(session);
        User target = userService.getById(id);
        if (target == null) return Result.error("用户不存在");
        if (target.getId().equals(operator.getId())) {
            return Result.error("不能删除当前登录账号");
        }
        userService.removeById(id);
        audit(operator, "USER", "DELETE", "userId=" + id,
                "删除用户 " + target.getUsername(), request);
        return Result.success("删除成功", null);
    }

    @GetMapping("/user/save")
    public Result<String> saveUser(@RequestParam(required = false) Long id,
                                    @RequestParam String username,
                                    @RequestParam(required = false) String nickname,
                                    @RequestParam(required = false) String phone,
                                    @RequestParam(required = false) String password,
                                    @RequestParam(required = false, defaultValue = "USER") String role,
                                    HttpSession session, HttpServletRequest request) {
        User operator = requireAdmin(session);
        if (!isAllowedRole(role)) {
            auditLogService.log(operator, "USER", "SAVE", "username=" + username,
                    "DENIED", "非法角色参数: " + role, request);
            return Result.error("非法角色");
        }
        User user;
        if (id != null) {
            user = userService.getById(id);
            if (user == null) return Result.error("用户不存在");
        } else {
            user = new User();
            user.setUsername(username);
            if (password == null || password.isEmpty()) return Result.error("新用户必须设置密码");
            user.setPassword(password);
        }
        if (nickname != null) user.setNickname(nickname);
        if (phone != null) user.setPhone(phone);
        if (password != null && !password.isEmpty()) user.setPassword(password);
        user.setRole(role);
        userService.saveOrUpdate(user);
        audit(operator, "USER", "SAVE", "userId=" + user.getId(),
                (id == null ? "新建用户 " : "修改用户 ") + username + "，角色=" + role, request);
        return Result.success("保存成功", null);
    }

    @GetMapping("/user/resetPassword")
    public Result<String> adminResetPassword(@RequestParam Long id, @RequestParam String newPassword,
                                             HttpSession session, HttpServletRequest request) {
        User operator = requireAdmin(session);
        User user = userService.getById(id);
        if (user == null) return Result.error("用户不存在");
        if (newPassword == null || newPassword.length() < 6) {
            return Result.error("新密码长度不能少于6位");
        }
        user.setPassword(newPassword);
        userService.updateById(user);
        audit(operator, "USER", "RESET_PASSWORD", "userId=" + id,
                "重置用户 " + user.getUsername() + " 的密码", request);
        return Result.success("密码已重置", null);
    }

    @GetMapping("/user/export")
    public void exportUsers(HttpServletResponse response) throws Exception {
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename=users.csv");
        PrintWriter writer = response.getWriter();
        writer.write("﻿");
        writer.println("ID,用户名,昵称,手机号,角色,状态,注册时间");
        List<User> users = userService.list();
        for (User u : users) {
            writer.println(String.format("%d,%s,%s,%s,%s,%s,%s",
                u.getId(),
                csvSafe(u.getUsername()), csvSafe(u.getNickname()),
                csvSafe(u.getPhone()), u.getRole(),
                u.getStatus() == 1 ? "正常" : "禁用",
                u.getCreateTime() != null ? u.getCreateTime().toString() : ""));
        }
        writer.flush();
    }

    private boolean isAllowedRole(String role) {
        return Constants.ROLE_USER.equals(role)
                || Constants.ROLE_ADMIN.equals(role)
                || Constants.ROLE_STAFF.equals(role);
    }

    private String csvSafe(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    // ==================== 景点管理 ====================

    @GetMapping("/spot/list")
    public Result<IPage<ScenicSpot>> spotList(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               HttpSession session) {
        User operator = requireStaffOrAdmin(session);
        LambdaQueryWrapper<ScenicSpot> w = new LambdaQueryWrapper<>();
        if (isStaff(operator)) {
            w.eq(ScenicSpot::getStaffId, operator.getId());
        }
        w.orderByDesc(ScenicSpot::getCreateTime);
        return Result.success(spotService.page(new Page<>(page, size), w));
    }

    @GetMapping("/spot/save")
    public Result<String> saveSpot(@RequestParam(required = false) Long id,
                                    @RequestParam String name,
                                    @RequestParam(required = false) String description,
                                    @RequestParam(required = false) String location,
                                    @RequestParam(required = false) String region,
                                    @RequestParam(required = false) String theme,
                                    @RequestParam(required = false) String openTime,
                                    @RequestParam(required = false) BigDecimal ticketPrice,
                                    @RequestParam(required = false) String trafficInfo,
                                    @RequestParam(required = false) String historyBackground,
                                    @RequestParam(required = false) String revolutionEvent,
                                    @RequestParam(required = false) String personStory,
                                    @RequestParam(required = false) String coverImage,
                                    @RequestParam(required = false) Double longitude,
                                    @RequestParam(required = false) Double latitude,
                                    @RequestParam(required = false) String ticketReservation,
                                    @RequestParam(required = false) String suggestedDuration,
                                    @RequestParam(required = false) String itemsToBring,
                                    HttpSession session, HttpServletRequest request) {
        User operator = requireStaffOrAdmin(session);
        ScenicSpot spot = id != null ? spotService.getById(id) : new ScenicSpot();
        if (spot == null) spot = new ScenicSpot();
        if (id != null && !canOperateSpot(operator, spot)) {
            return Result.error("无权操作该景点");
        }
        spot.setName(name);
        if (description != null) spot.setDescription(description);
        if (location != null) spot.setLocation(location);
        if (region != null) spot.setRegion(region);
        if (theme != null) spot.setTheme(theme);
        if (openTime != null) spot.setOpenTime(openTime);
        if (ticketPrice != null) spot.setTicketPrice(ticketPrice);
        if (trafficInfo != null) spot.setTrafficInfo(trafficInfo);
        if (historyBackground != null) spot.setHistoryBackground(historyBackground);
        if (revolutionEvent != null) spot.setRevolutionEvent(revolutionEvent);
        if (personStory != null) spot.setPersonStory(personStory);
        if (coverImage != null) spot.setCoverImage(coverImage);
        if (longitude != null) spot.setLongitude(longitude);
        if (latitude != null) spot.setLatitude(latitude);
        if (ticketReservation != null) spot.setTicketReservation(ticketReservation);
        if (suggestedDuration != null) spot.setSuggestedDuration(suggestedDuration);
        if (itemsToBring != null) spot.setItemsToBring(itemsToBring);
        if (id == null) {
            spot.setStatus(1);
            spot.setViewCount(0L);
            spot.setFavoriteCount(0L);
            spot.setAvgRating(0.0);
            spot.setCommentCount(0L);
            if (isStaff(operator)) {
                spot.setStaffId(operator.getId());
            }
        } else if (isStaff(operator) && spot.getStaffId() == null) {
            spot.setStaffId(operator.getId());
        }
        spotService.saveOrUpdate(spot);
        audit(operator, "SPOT", "SAVE", "spotId=" + spot.getId(),
                (id == null ? "新建景点 " : "修改景点 ") + name, request);
        return Result.success("保存成功", null);
    }

    @GetMapping("/spot/delete")
    public Result<String> deleteSpot(@RequestParam Long id, HttpSession session,
                                     HttpServletRequest request) {
        User operator = requireStaffOrAdmin(session);
        ScenicSpot spot = spotService.getById(id);
        if (spot == null) return Result.error("景点不存在");
        if (!canOperateSpot(operator, spot)) return Result.error("无权操作该景点");
        spotService.removeById(id);
        audit(operator, "SPOT", "DELETE", "spotId=" + id,
                "删除景点 " + spot.getName(), request);
        return Result.success("删除成功", null);
    }

    @GetMapping("/spot/toggleStatus")
    public Result<String> toggleSpotStatus(@RequestParam Long id, HttpSession session,
                                           HttpServletRequest request) {
        User operator = requireStaffOrAdmin(session);
        ScenicSpot spot = spotService.getById(id);
        if (spot == null) return Result.error("景点不存在");
        if (!canOperateSpot(operator, spot)) return Result.error("无权操作该景点");
        spot.setStatus(spot.getStatus() == 1 ? 0 : 1);
        spotService.updateById(spot);
        audit(operator, "SPOT", "TOGGLE_STATUS", "spotId=" + id,
                "景点[" + spot.getName() + "]状态改为" + (spot.getStatus() == 1 ? "上架" : "下架"), request);
        return Result.success("状态已更新", null);
    }

    @GetMapping("/spot/addImage")
    public Result<String> addSpotImage(@RequestParam Long spotId,
                                        @RequestParam String imageUrl,
                                        @RequestParam(required = false) Integer sortOrder,
                                        HttpSession session, HttpServletRequest request) {
        User operator = requireStaffOrAdmin(session);
        ScenicSpot spot = spotService.getById(spotId);
        if (spot == null) return Result.error("景点不存在");
        if (!canOperateSpot(operator, spot)) return Result.error("无权操作该景点");
        spotService.addImage(spotId, imageUrl, sortOrder);
        audit(operator, "SPOT", "ADD_IMAGE", "spotId=" + spotId, imageUrl, request);
        return Result.success("图片添加成功", null);
    }

    @GetMapping("/spot/deleteImage")
    public Result<String> deleteSpotImage(@RequestParam Long imageId, HttpSession session,
                                          HttpServletRequest request) {
        User operator = requireStaffOrAdmin(session);
        ScenicSpotImage image = spotImageMapper.selectById(imageId);
        if (image == null) return Result.error("图片不存在");
        ScenicSpot spot = spotService.getById(image.getSpotId());
        if (spot == null) return Result.error("景点不存在");
        if (!canOperateSpot(operator, spot)) return Result.error("无权操作该景点");
        spotService.deleteImage(imageId);
        audit(operator, "SPOT", "DELETE_IMAGE", "imageId=" + imageId,
                "spotId=" + image.getSpotId(), request);
        return Result.success("图片删除成功", null);
    }

    @GetMapping("/spot/stats")
    public Result<Map<String, Object>> spotStats(@RequestParam Long id, HttpSession session) {
        requireStaffOrAdmin(session);
        ScenicSpot spot = spotService.getById(id);
        Map<String, Object> stats = new HashMap<>();
        if (spot != null) {
            stats.put("viewCount", spot.getViewCount());
            stats.put("favoriteCount", spot.getFavoriteCount());
            stats.put("avgRating", spot.getAvgRating());
            stats.put("commentCount", spot.getCommentCount());
        }
        return Result.success(stats);
    }

    private boolean isStaff(User operator) {
        return operator != null && Constants.ROLE_STAFF.equals(operator.getRole());
    }

    private boolean canOperateSpot(User operator, ScenicSpot spot) {
        if (operator == null || spot == null) return false;
        if (Constants.ROLE_ADMIN.equals(operator.getRole())) return true;
        return isStaff(operator) && spot.getStaffId() != null && spot.getStaffId().equals(operator.getId());
    }

    // ==================== 线路管理 ====================

    @GetMapping("/route/save")
    public Result<String> saveRoute(@RequestParam(required = false) Long id,
                                     @RequestParam String name,
                                     @RequestParam(required = false) String description,
                                     @RequestParam(required = false) Integer days,
                                     @RequestParam(required = false) String theme,
                                     @RequestParam(required = false) String coverImage,
                                     @RequestParam(required = false) String trafficSuggestion,
                                     @RequestParam(required = false) String hotelSuggestion,
                                     @RequestParam(required = false) BigDecimal budget,
                                     HttpSession session, HttpServletRequest request) {
        User operator = requireAdmin(session);
        Route route = id != null ? routeService.getById(id) : new Route();
        if (route == null) route = new Route();
        route.setName(name);
        if (description != null) route.setDescription(description);
        if (days != null) route.setDays(days);
        if (theme != null) route.setTheme(theme);
        if (coverImage != null) route.setCoverImage(coverImage);
        if (trafficSuggestion != null) route.setTrafficSuggestion(trafficSuggestion);
        if (hotelSuggestion != null) route.setHotelSuggestion(hotelSuggestion);
        if (budget != null) route.setBudget(budget);
        if (id == null) {
            route.setViewCount(0L);
            route.setFavoriteCount(0L);
        }
        routeService.saveOrUpdate(route);
        audit(operator, "ROUTE", "SAVE", "routeId=" + route.getId(),
                (id == null ? "新建线路 " : "修改线路 ") + name, request);
        return Result.success("保存成功", null);
    }

    @GetMapping("/route/delete")
    public Result<String> deleteRoute(@RequestParam Long id,
                                      HttpSession session, HttpServletRequest request) {
        User operator = requireAdmin(session);
        Route route = routeService.getById(id);
        routeService.removeById(id);
        audit(operator, "ROUTE", "DELETE", "routeId=" + id,
                route != null ? "删除线路 " + route.getName() : null, request);
        return Result.success("删除成功", null);
    }

    // ==================== 红色文化管理 ====================

    @GetMapping("/culture/save")
    public Result<String> saveCulture(@RequestParam(required = false) Long id,
                                       @RequestParam String title,
                                       @RequestParam(required = false) String content,
                                       @RequestParam(required = false) Long categoryId,
                                       @RequestParam(required = false) String coverImage,
                                       @RequestParam(required = false) String author,
                                       HttpSession session, HttpServletRequest request) {
        User operator = requireAdmin(session);
        CultureContent culture = id != null ? cultureService.getById(id) : new CultureContent();
        if (culture == null) culture = new CultureContent();
        culture.setTitle(title);
        if (content != null) culture.setContent(content);
        if (categoryId != null) culture.setCategoryId(categoryId);
        if (coverImage != null) culture.setCoverImage(coverImage);
        if (author != null) culture.setAuthor(author);
        if (id == null) {
            culture.setViewCount(0L);
            culture.setFavoriteCount(0L);
            culture.setLikeCount(0L);
        }
        cultureService.saveOrUpdate(culture);
        audit(operator, "CULTURE", "SAVE", "cultureId=" + culture.getId(),
                (id == null ? "新建文化内容 " : "修改文化内容 ") + title, request);
        return Result.success("保存成功", null);
    }

    @GetMapping("/culture/delete")
    public Result<String> deleteCulture(@RequestParam Long id,
                                        HttpSession session, HttpServletRequest request) {
        User operator = requireAdmin(session);
        CultureContent culture = cultureService.getById(id);
        cultureService.removeById(id);
        audit(operator, "CULTURE", "DELETE", "cultureId=" + id,
                culture != null ? "删除文化内容 " + culture.getTitle() : null, request);
        return Result.success("删除成功", null);
    }

    @GetMapping("/culture/saveCategory")
    public Result<String> saveCultureCategory(@RequestParam(required = false) Long id,
                                               @RequestParam String name,
                                               @RequestParam(required = false, defaultValue = "0") Long parentId,
                                               @RequestParam(required = false, defaultValue = "0") Integer sortOrder,
                                               HttpSession session, HttpServletRequest request) {
        User operator = requireAdmin(session);
        CultureCategory cat = new CultureCategory();
        cat.setId(id);
        cat.setName(name);
        cat.setParentId(parentId);
        cat.setSortOrder(sortOrder);
        cultureService.saveCategory(cat);
        audit(operator, "CULTURE", "SAVE_CATEGORY", "categoryId=" + id, "保存分类 " + name, request);
        return Result.success("保存成功", null);
    }

    @GetMapping("/culture/deleteCategory")
    public Result<String> deleteCultureCategory(@RequestParam Long id,
                                                HttpSession session, HttpServletRequest request) {
        User operator = requireAdmin(session);
        cultureService.deleteCategory(id);
        audit(operator, "CULTURE", "DELETE_CATEGORY", "categoryId=" + id, null, request);
        return Result.success("删除成功", null);
    }

    // ==================== 酒店管理 ====================

    @GetMapping("/hotel/save")
    public Result<String> saveHotel(@RequestParam(required = false) Long id,
                                     @RequestParam String name,
                                     @RequestParam(required = false) String description,
                                     @RequestParam(required = false) String location,
                                     @RequestParam(required = false) String coverImage,
                                     @RequestParam(required = false) BigDecimal price,
                                     @RequestParam(required = false) Integer hasBreakfast,
                                     @RequestParam(required = false) Integer hasRoomService,
                                     @RequestParam(required = false) String phone,
                                     @RequestParam(required = false) Double longitude,
                                     @RequestParam(required = false) Double latitude,
                                     HttpSession session, HttpServletRequest request) {
        User operator = requireAdmin(session);
        Hotel hotel = id != null ? hotelService.getById(id) : new Hotel();
        if (hotel == null) hotel = new Hotel();
        hotel.setName(name);
        if (description != null) hotel.setDescription(description);
        if (location != null) hotel.setLocation(location);
        if (coverImage != null) hotel.setCoverImage(coverImage);
        if (price != null) hotel.setPrice(price);
        if (hasBreakfast != null) hotel.setHasBreakfast(hasBreakfast);
        if (hasRoomService != null) hotel.setHasRoomService(hasRoomService);
        if (phone != null) hotel.setPhone(phone);
        if (longitude != null) hotel.setLongitude(longitude);
        if (latitude != null) hotel.setLatitude(latitude);
        if (id == null) {
            hotel.setStatus(1);
            hotel.setRating(0.0);
        }
        hotelService.saveOrUpdate(hotel);
        audit(operator, "HOTEL", "SAVE", "hotelId=" + hotel.getId(),
                (id == null ? "新建酒店 " : "修改酒店 ") + name, request);
        return Result.success("保存成功", null);
    }

    @GetMapping("/hotel/delete")
    public Result<String> deleteHotel(@RequestParam Long id,
                                      HttpSession session, HttpServletRequest request) {
        User operator = requireAdmin(session);
        Hotel hotel = hotelService.getById(id);
        hotelService.removeById(id);
        audit(operator, "HOTEL", "DELETE", "hotelId=" + id,
                hotel != null ? "删除酒店 " + hotel.getName() : null, request);
        return Result.success("删除成功", null);
    }

    // ==================== 美食管理 ====================

    @GetMapping("/food/save")
    public Result<String> saveFood(@RequestParam(required = false) Long id,
                                    @RequestParam String name,
                                    @RequestParam(required = false) String description,
                                    @RequestParam(required = false) String category,
                                    @RequestParam(required = false) BigDecimal price,
                                    @RequestParam(required = false) String coverImage,
                                    @RequestParam(required = false) Long storeId,
                                    HttpSession session, HttpServletRequest request) {
        User operator = requireAdmin(session);
        Food food = id != null ? foodService.getById(id) : new Food();
        if (food == null) food = new Food();
        food.setName(name);
        if (description != null) food.setDescription(description);
        if (category != null) food.setCategory(category);
        if (price != null) food.setPrice(price);
        if (coverImage != null) food.setCoverImage(coverImage);
        if (storeId != null) food.setStoreId(storeId);
        foodService.saveOrUpdate(food);
        audit(operator, "FOOD", "SAVE", "foodId=" + food.getId(),
                (id == null ? "新建美食 " : "修改美食 ") + name, request);
        return Result.success("保存成功", null);
    }

    @GetMapping("/food/delete")
    public Result<String> deleteFood(@RequestParam Long id,
                                     HttpSession session, HttpServletRequest request) {
        User operator = requireAdmin(session);
        Food food = foodService.getById(id);
        foodService.removeById(id);
        audit(operator, "FOOD", "DELETE", "foodId=" + id,
                food != null ? "删除美食 " + food.getName() : null, request);
        return Result.success("删除成功", null);
    }

    @GetMapping("/food/saveStore")
    public Result<String> saveFoodStore(@RequestParam(required = false) Long id,
                                         @RequestParam String name,
                                         @RequestParam(required = false) String location,
                                         @RequestParam(required = false) String category,
                                         @RequestParam(required = false) String hygieneLevel,
                                         @RequestParam(required = false) String phone,
                                         @RequestParam(required = false) String coverImage,
                                         @RequestParam(required = false) Double longitude,
                                         @RequestParam(required = false) Double latitude,
                                         HttpSession session, HttpServletRequest request) {
        User operator = requireAdmin(session);
        FoodStore store = new FoodStore();
        store.setId(id);
        store.setName(name);
        if (location != null) store.setLocation(location);
        if (category != null) store.setCategory(category);
        if (hygieneLevel != null) store.setHygieneLevel(hygieneLevel);
        if (phone != null) store.setPhone(phone);
        if (coverImage != null) store.setCoverImage(coverImage);
        if (longitude != null) store.setLongitude(longitude);
        if (latitude != null) store.setLatitude(latitude);
        foodService.saveStore(store);
        audit(operator, "FOOD", "SAVE_STORE", "storeId=" + id, "保存门店 " + name, request);
        return Result.success("保存成功", null);
    }

    @GetMapping("/food/deleteStore")
    public Result<String> deleteFoodStore(@RequestParam Long id,
                                          HttpSession session, HttpServletRequest request) {
        User operator = requireAdmin(session);
        foodService.deleteStore(id);
        audit(operator, "FOOD", "DELETE_STORE", "storeId=" + id, null, request);
        return Result.success("删除成功", null);
    }

    // ==================== 留言管理 ====================

    @GetMapping("/comment/list")
    public Result<IPage<Comment>> commentList(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestParam(required = false) String keyword) {
        return Result.success(interactionService.listAllComments(page, size, keyword));
    }

    @GetMapping("/comment/reply")
    public Result<String> replyComment(@RequestParam Long id,
                                        @RequestParam String replyContent,
                                        HttpSession session, HttpServletRequest request) {
        User admin = requireAdmin(session);
        Comment comment = interactionService.getCommentById(id);
        if (comment == null) return Result.error("留言不存在");
        interactionService.replyComment(id, replyContent, admin.getId());
        if (comment.getUserId() != null) {
            messageService.sendMessage(comment.getUserId(), "您的留言收到了回复",
                    "管理员回复了您的留言：" + replyContent);
        }
        audit(admin, "COMMENT", "REPLY", "commentId=" + id, null, request);
        return Result.success("回复成功", null);
    }

    @GetMapping("/comment/delete")
    public Result<String> deleteComment(@RequestParam Long id,
                                        HttpSession session, HttpServletRequest request) {
        User admin = requireAdmin(session);
        Comment comment = interactionService.getCommentById(id);
        if (comment == null) return Result.error("留言不存在");
        interactionService.deleteComment(id);
        audit(admin, "COMMENT", "DELETE", "commentId=" + id,
                "删除留言：" + comment.getContent(), request);
        return Result.success("删除成功", null);
    }

    // ==================== 订单管理 ====================

    /** 管理端订单允许的状态流转 */
    private static final Map<String, Set<String>> ORDER_TRANSITIONS = new HashMap<>();
    static {
        ORDER_TRANSITIONS.put("CANCELLED", new HashSet<>(Arrays.asList("PENDING")));
        ORDER_TRANSITIONS.put("COMPLETED", new HashSet<>(Arrays.asList("PAID")));
        ORDER_TRANSITIONS.put("REFUNDED", new HashSet<>(Arrays.asList("PAID", "COMPLETED")));
    }

    @GetMapping("/order/list")
    public Result<IPage<OrderInfo>> orderList(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestParam(required = false) String orderType,
                                               @RequestParam(required = false) String status) {
        return Result.success(orderService.listAllOrders(page, size, orderType, status));
    }

    @GetMapping("/order/cancel")
    public Result<String> adminCancelOrder(@RequestParam Long orderId,
                                           HttpSession session, HttpServletRequest request) {
        return changeOrderStatus(orderId, "CANCELLED", "取消", session, request);
    }

    @GetMapping("/order/refund")
    public Result<String> adminRefundOrder(@RequestParam Long orderId,
                                           HttpSession session, HttpServletRequest request) {
        return changeOrderStatus(orderId, "REFUNDED", "退款", session, request);
    }

    @GetMapping("/order/complete")
    public Result<String> adminCompleteOrder(@RequestParam Long orderId,
                                             HttpSession session, HttpServletRequest request) {
        return changeOrderStatus(orderId, "COMPLETED", "完成", session, request);
    }

    private Result<String> changeOrderStatus(Long orderId, String targetStatus, String actionText,
                                             HttpSession session, HttpServletRequest request) {
        User admin = requireAdmin(session);
        OrderInfo order = orderService.getById(orderId);
        if (order == null) return Result.error("订单不存在");
        Set<String> allowedFrom = ORDER_TRANSITIONS.get(targetStatus);
        if (allowedFrom == null || !allowedFrom.contains(order.getStatus())) {
            auditLogService.log(admin, "ORDER", targetStatus, "orderId=" + orderId,
                    "DENIED", "当前状态 " + order.getStatus() + " 不允许" + actionText, request);
            return Result.error("当前订单状态不可" + actionText);
        }
        order.setStatus(targetStatus);
        if ("REFUNDED".equals(targetStatus) || "COMPLETED".equals(targetStatus)) {
            order.setPayTime(order.getPayTime() != null ? order.getPayTime() : new Date());
        }
        orderService.updateById(order);
        audit(admin, "ORDER", targetStatus, "orderId=" + orderId,
                "订单 " + order.getOrderNo() + " 状态 " + order.getStatus() + " -> " + targetStatus, request);
        return Result.success("已" + actionText);
    }

    @GetMapping("/order/delete")
    public Result<String> adminDeleteOrder(@RequestParam Long orderId,
                                           HttpSession session, HttpServletRequest request) {
        User admin = requireAdmin(session);
        OrderInfo order = orderService.getById(orderId);
        if (order == null) return Result.error("订单不存在");
        orderService.removeById(orderId);
        audit(admin, "ORDER", "DELETE", "orderId=" + orderId,
                "删除订单 " + order.getOrderNo(), request);
        return Result.success("已删除");
    }

    // ==================== FAQ管理 ====================

    @GetMapping("/faq/save")
    public Result<String> saveFaq(@RequestParam(required = false) Long id,
                                   @RequestParam String question,
                                   @RequestParam String answer,
                                   @RequestParam(required = false, defaultValue = "0") Integer sortOrder,
                                   HttpSession session, HttpServletRequest request) {
        User operator = requireAdmin(session);
        Faq faq = new Faq();
        faq.setId(id);
        faq.setQuestion(question);
        faq.setAnswer(answer);
        faq.setSortOrder(sortOrder);
        faqService.saveOrUpdate(faq);
        audit(operator, "FAQ", "SAVE", "faqId=" + id, question, request);
        return Result.success("保存成功", null);
    }

    @GetMapping("/faq/delete")
    public Result<String> deleteFaq(@RequestParam Long id,
                                    HttpSession session, HttpServletRequest request) {
        User operator = requireAdmin(session);
        Faq faq = faqService.getById(id);
        faqService.removeById(id);
        audit(operator, "FAQ", "DELETE", "faqId=" + id,
                faq != null ? faq.getQuestion() : null, request);
        return Result.success("删除成功", null);
    }

    // ==================== 消息管理 ====================

    @GetMapping("/message/send")
    public Result<String> sendMessage(@RequestParam Long userId,
                                       @RequestParam String title,
                                       @RequestParam String content,
                                       HttpSession session, HttpServletRequest request) {
        User operator = requireAdmin(session);
        messageService.sendMessage(userId, title, content);
        audit(operator, "MESSAGE", "SEND", "userId=" + userId, title, request);
        return Result.success("发送成功", null);
    }

    // ==================== 用户自定义线路审核 ====================

    @GetMapping("/customRoute/list")
    public Result<com.baomidou.mybatisplus.core.metadata.IPage<UserCustomRoute>> customRouteList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserCustomRoute> w =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) w.eq(UserCustomRoute::getStatus, status);
        else w.eq(UserCustomRoute::getStatus, "SUBMITTED");
        w.orderByDesc(UserCustomRoute::getCreateTime);
        com.baomidou.mybatisplus.core.metadata.IPage<UserCustomRoute> result =
                customRouteMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), w);
        result.getRecords().forEach(r -> {
            User u = userMapper.selectById(r.getUserId());
            if (u != null) r.setUsername(u.getNickname() != null ? u.getNickname() : u.getUsername());
        });
        return Result.success(result);
    }

    @GetMapping("/customRoute/approve")
    public Result<String> approveCustomRoute(@RequestParam Long id,
                                             HttpSession session, HttpServletRequest request) {
        User operator = requireAdmin(session);
        UserCustomRoute route = customRouteMapper.selectById(id);
        if (route == null) return Result.error("线路不存在");
        route.setStatus("APPROVED");
        customRouteMapper.updateById(route);
        messageService.sendMessage(route.getUserId(), "您的自定义线路已被采纳为官方推荐",
                "恭喜！您创建的线路【" + route.getName() + "】已通过审核，被纳入官方推荐线路。");
        audit(operator, "CUSTOM_ROUTE", "APPROVE", "routeId=" + id, route.getName(), request);
        return Result.success("已通过", null);
    }

    @GetMapping("/customRoute/reject")
    public Result<String> rejectCustomRoute(@RequestParam Long id, @RequestParam String reason,
                                            HttpSession session, HttpServletRequest request) {
        User operator = requireAdmin(session);
        UserCustomRoute route = customRouteMapper.selectById(id);
        if (route == null) return Result.error("线路不存在");
        route.setStatus("REJECTED");
        route.setRejectReason(reason);
        customRouteMapper.updateById(route);
        messageService.sendMessage(route.getUserId(), "您的自定义线路未通过审核",
                "您提交的线路【" + route.getName() + "】未通过审核。原因：" + reason);
        audit(operator, "CUSTOM_ROUTE", "REJECT", "routeId=" + id,
                route.getName() + "，原因：" + reason, request);
        return Result.success("已驳回", null);
    }

    @GetMapping("/customRoute/pendingCount")
    public Result<Long> customRoutePendingCount() {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserCustomRoute> w =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        w.eq(UserCustomRoute::getStatus, "SUBMITTED");
        return Result.success(customRouteMapper.selectCount(w));
    }

    // ==================== 景点更正审核 ====================

    @GetMapping("/spotSuggestion/list")
    public Result<IPage<SpotSuggestion>> spotSuggestionList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String status) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SpotSuggestion> w =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) w.eq(SpotSuggestion::getStatus, status);
        else w.eq(SpotSuggestion::getStatus, "PENDING");
        w.orderByDesc(SpotSuggestion::getCreateTime);
        IPage<SpotSuggestion> result = spotSuggestionMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), w);
        result.getRecords().forEach(r -> {
            User u = userMapper.selectById(r.getUserId());
            if (u != null) r.setUsername(u.getNickname() != null ? u.getNickname() : u.getUsername());
        });
        return Result.success(result);
    }

    @GetMapping("/spotSuggestion/approve")
    public Result<String> approveSpotSuggestion(@RequestParam Long id,
                                                HttpSession session, HttpServletRequest request) {
        User operator = requireAdmin(session);
        SpotSuggestion s = spotSuggestionMapper.selectById(id);
        if (s == null) return Result.error("不存在");
        s.setStatus("APPROVED");
        spotSuggestionMapper.updateById(s);
        ScenicSpot spot = spotService.getById(s.getSpotId());
        if (spot != null) {
            switch (s.getFieldName()) {
                case "name": spot.setName(s.getNewValue()); break;
                case "description": spot.setDescription(s.getNewValue()); break;
                case "location": spot.setLocation(s.getNewValue()); break;
                case "openTime": spot.setOpenTime(s.getNewValue()); break;
                case "ticketPrice": try { spot.setTicketPrice(new BigDecimal(s.getNewValue())); } catch(Exception e){} break;
                case "trafficInfo": spot.setTrafficInfo(s.getNewValue()); break;
                case "ticketReservation": spot.setTicketReservation(s.getNewValue()); break;
                case "suggestedDuration": spot.setSuggestedDuration(s.getNewValue()); break;
                case "itemsToBring": spot.setItemsToBring(s.getNewValue()); break;
                default: break;
            }
            spotService.updateById(spot);
        }
        messageService.sendMessage(s.getUserId(), "您的景点更正建议已通过",
                "您提交的关于【" + s.getSpotName() + "】" + s.getFieldName() + "的更正已被采纳，感谢您的贡献！");
        audit(operator, "SPOT_SUGGESTION", "APPROVE", "suggestionId=" + id,
                s.getSpotName() + "/" + s.getFieldName(), request);
        return Result.success("已通过并应用", null);
    }

    @GetMapping("/spotSuggestion/reject")
    public Result<String> rejectSpotSuggestion(@RequestParam Long id, @RequestParam String reason,
                                               HttpSession session, HttpServletRequest request) {
        User operator = requireAdmin(session);
        SpotSuggestion s = spotSuggestionMapper.selectById(id);
        if (s == null) return Result.error("不存在");
        s.setStatus("REJECTED");
        s.setRejectReason(reason);
        spotSuggestionMapper.updateById(s);
        messageService.sendMessage(s.getUserId(), "您的景点更正建议未通过",
                "您提交的关于【" + s.getSpotName() + "】的更正未通过。原因：" + reason);
        audit(operator, "SPOT_SUGGESTION", "REJECT", "suggestionId=" + id,
                s.getSpotName() + "，原因：" + reason, request);
        return Result.success("已驳回", null);
    }

    @GetMapping("/spotSuggestion/pendingCount")
    public Result<Long> spotSuggestionPendingCount() {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SpotSuggestion> w =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        w.eq(SpotSuggestion::getStatus, "PENDING");
        return Result.success(spotSuggestionMapper.selectCount(w));
    }

    // ==================== 人工客服 ====================

    @GetMapping("/chat/sessions")
    public Result<List<Map<String, Object>>> chatSessions() {
        java.util.ArrayList<Map<String, Object>> result = new java.util.ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        List<ServiceChat> all = chatMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ServiceChat>()
                        .orderByDesc(ServiceChat::getCreateTime));
        for (ServiceChat c : all) {
            if (seen.contains(c.getUserId())) continue;
            seen.add(c.getUserId());
            Map<String, Object> m = new HashMap<>();
            m.put("userId", c.getUserId());
            User u = userMapper.selectById(c.getUserId());
            m.put("username", u != null ? (u.getNickname() != null ? u.getNickname() : u.getUsername()) : "用户" + c.getUserId());
            m.put("lastMessage", c.getContent());
            m.put("lastTime", c.getCreateTime());
            result.add(m);
        }
        return Result.success(result);
    }

    @GetMapping("/chat/history")
    public Result<List<ServiceChat>> chatHistory(@RequestParam Long userId) {
        return Result.success(chatMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ServiceChat>()
                        .eq(ServiceChat::getUserId, userId)
                        .orderByAsc(ServiceChat::getCreateTime)));
    }

    @GetMapping("/chat/send")
    public Result<String> adminSendChat(@RequestParam Long userId, @RequestParam String content,
                                        HttpSession session, HttpServletRequest request) {
        User operator = requireAdmin(session);
        ServiceChat c = new ServiceChat();
        c.setUserId(userId);
        c.setSender("ADMIN");
        c.setContent(content);
        c.setCreateTime(new Date());
        chatMapper.insert(c);
        audit(operator, "CHAT", "SEND", "userId=" + userId, content, request);
        return Result.success("发送成功", null);
    }

    // ==================== 数据统计 ====================

    @GetMapping("/stats/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("userCount", userService.count());
        stats.put("spotCount", spotService.count());
        stats.put("routeCount", routeService.count());
        stats.put("cultureCount", cultureService.count());
        stats.put("hotelCount", hotelService.count());
        stats.put("foodCount", foodService.count());
        stats.put("orderCount", orderService.count());
        return Result.success(stats);
    }

    // ==================== 鉴权与审计辅助 ====================

    /** 仅 ADMIN；非管理员（含访客/普通用户）统一返回明确提示。路径层已有拦截，这里做纵深防御 */
    private User requireAdmin(HttpSession session) {
        return SessionUtils.requireAdmin(session);
    }

    private User requireStaffOrAdmin(HttpSession session) {
        return SessionUtils.requireStaffOrAdmin(session);
    }

    private void audit(User operator, String module, String action, String target,
                       String detail, HttpServletRequest request) {
        auditLogService.log(operator, module, action, target, "SUCCESS", detail, request);
    }
}
