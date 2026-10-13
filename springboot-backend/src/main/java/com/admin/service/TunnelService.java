package com.admin.service;

import com.admin.common.dto.TunnelDto;
import com.admin.common.dto.TunnelUpdateDto;

import com.admin.common.lang.R;
import com.admin.entity.Tunnel;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 隧道服务类
 * </p>
 *
 * @author QAQ
 * @since 2025-06-03
 */
public interface TunnelService extends IService<Tunnel> {

    /**
     * 创建隧道
     * @param tunnelDto 隧道数据
     * @return 结果
     */
    R createTunnel(TunnelDto tunnelDto);

    /**
     * 获取隧道列表
     * @return 结果
     */
    R getAllTunnels();

    /**
     * 更新隧道（只允许修改名称、流量计费、端口范围）
     * @param tunnelUpdateDto 更新数据
     * @return 结果
     */
    R updateTunnel(TunnelUpdateDto tunnelUpdateDto);

    /**
     * 删除隧道
     * @param id 隧道ID
     * @return 结果
     */
    R deleteTunnel(Long id);

    /**
     * 删除隧道（支持级联）
     * @param id 隧道ID
     * @param cascade 为 true 时，同步删除该隧道下的所有转发与用户权限
     * @return 结果
     */
    R deleteTunnel(Long id, boolean cascade);

    /**
     * 批量删除隧道
     * 逐条复用单条删除逻辑，聚合成功/失败明细
     * @param ids 隧道ID列表
     * @return 批量结果
     */
    R batchDeleteTunnels(java.util.List<Long> ids);

    /**
     * 批量删除隧道（支持级联）
     * @param ids 隧道ID列表
     * @param cascade 为 true 时，同步删除各隧道下的所有转发与用户权限
     * @return 批量结果
     */
    R batchDeleteTunnels(java.util.List<Long> ids, boolean cascade);

    /**
     * 获取用户可用的隧道列表
     * @return 结果
     */
    R userTunnel();

    /**
     * 隧道诊断功能
     * @param tunnelId 隧道ID
     * @return 诊断结果
     */
    R diagnoseTunnel(Long tunnelId);
}
