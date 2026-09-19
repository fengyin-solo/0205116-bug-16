package com.redtourism.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.redtourism.entity.Hotel;
import com.redtourism.mapper.HotelMapper;
import com.redtourism.service.HotelService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class HotelServiceImpl extends ServiceImpl<HotelMapper, Hotel> implements HotelService {

    @Override
    public IPage<Hotel> listHotels(int page, int size, String keyword, String orderBy) {
        LambdaQueryWrapper<Hotel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Hotel::getStatus, 1);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Hotel::getName, keyword)
                    .or().like(Hotel::getLocation, keyword));
        }
        if ("price".equals(orderBy)) {
            wrapper.orderByAsc(Hotel::getPrice);
        } else if ("rating".equals(orderBy)) {
            wrapper.orderByDesc(Hotel::getRating);
        } else {
            wrapper.orderByDesc(Hotel::getCreateTime);
        }
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public Hotel getDetail(Long id) {
        Hotel hotel = getById(id);
        // 与列表口径一致：下架酒店不对外返回详情
        if (hotel == null || hotel.getStatus() == null || hotel.getStatus() != 1) {
            return null;
        }
        return hotel;
    }
}
