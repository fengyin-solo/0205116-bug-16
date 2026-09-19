package com.redtourism.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("order_info")
public class OrderInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String orderType;
    private Long targetId;
    private String orderNo;
    private BigDecimal amount;
    private String status;
    private String payMethod;
    private Date payTime;
    private String targetName;
    private Integer quantity;
    private Date checkInDate;
    private Date checkOutDate;
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /** 下单人展示名，仅列表/详情展示用，不入库 */
    @TableField(exist = false)
    private String username;
}
