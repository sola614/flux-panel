package com.admin.service;

import com.admin.common.dto.UserTunnelBatchDto;
import com.admin.common.dto.UserTunnelDto;
import com.admin.common.dto.UserTunnelQueryDto;
import com.admin.common.dto.UserTunnelUpdateDto;
import com.admin.common.lang.R;
import com.admin.entity.UserTunnel;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 用户隧道权限服务类
 * </p>
 *
 * @author QAQ
 * @since 2025-06-03
 */
public interface UserTunnelService extends IService<UserTunnel> {

    /**
     * 分配用户隧道权限
     * @param userTunnelDto 用户隧道权限数据
     * @return 结果
     */
    R assignUserTunnel(UserTunnelDto userTunnelDto);
    
    /**
     * 查询用户隧道权限列表
     * @param queryDto 查询条件
     * @return 结果
     */
    R getUserTunnelList(UserTunnelQueryDto queryDto);
    
    /**
     * 删除用户隧道权限
     * @param id ID
     * @return 结果
     */
    R removeUserTunnel(Integer id);

    /**
     * 批量分配用户隧道权限
     * 为同一用户一次性分配多个隧道，共享同一套流量/数量/到期配置
     * @param batchDto 批量分配数据
     * @return 批量结果（含成功/失败明细）
     */
    R batchAssignUserTunnels(UserTunnelBatchDto batchDto);

    /**
     * 批量删除用户隧道权限
     * 逐条校验权限归属该用户后删除，聚合成功/失败明细
     * @param userId 用户ID
     * @param ids    用户隧道权限ID列表
     * @return 批量结果（含成功/失败明细）
     */
    R batchRemoveUserTunnels(Integer userId, java.util.List<Integer> ids);

    
    /**
     * 更新用户隧道权限（包含流量、流量重置时间、到期时间）
     * @param updateDto 更新数据
     * @return 结果
     */
    R updateUserTunnel(UserTunnelUpdateDto updateDto);

}
