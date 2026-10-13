package com.admin.common.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 用户隧道权限批量分配数据传输对象
 * 用于一次性为同一用户分配多个隧道权限，共享同一套流量/数量/到期配置
 */
@Data
public class UserTunnelBatchDto {

    /**
     * 目标用户ID
     */
    @NotNull(message = "用户ID不能为空")
    private Integer userId;

    /**
     * 待分配的隧道ID列表
     */
    @NotNull(message = "隧道ID列表不能为空")
    private List<Integer> tunnelIds;

    /**
     * 流量限制（字节），默认0
     */
    private Long flow;

    /**
     * 转发数量限制，默认0
     */
    private Integer num;

    /**
     * 流量重置时间（时间戳），默认0
     */
    private Long flowResetTime;

    /**
     * 到期时间（时间戳），默认0
     */
    private Long expTime;

    /**
     * 限速规则ID（可选，null表示不限速）
     */
    private Integer speedId;
}
