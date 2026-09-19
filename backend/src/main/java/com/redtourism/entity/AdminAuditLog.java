package com.redtourism.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 管理端操作审计记录。
 * 所有管理端写操作（用户、订单、留言、角色权限等）都落一条记录，确保事后可查“谁在什么时候做了什么”。
 */
@Data
@TableName("admin_audit_log")
public class AdminAuditLog implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人ID（未登录的越权尝试也可能为空） */
    private Long operatorId;
    /** 操作人用户名 */
    private String operatorName;
    /** 操作人角色快照 */
    private String operatorRole;

    /** 业务模块：USER/ORDER/COMMENT/ROLE/FEEDBACK/... */
    private String module;
    /** 动作：DELETE/REFUND/SAVE/... */
    private String action;
    /** 操作目标描述，如 orderId=12 */
    private String target;
    /** 结果：SUCCESS / DENIED */
    private String result;
    /** 详情或拒绝原因 */
    private String detail;
    /** 请求来源IP */
    private String ip;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
