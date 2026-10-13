package com.admin.service;

import com.admin.common.dto.NodeDto;
import com.admin.common.dto.NodeUpdateDto;
import com.admin.common.lang.R;
import com.admin.entity.Node;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author QAQ
 * @since 2025-06-03
 */
public interface NodeService extends IService<Node> {

    R createNode(NodeDto nodeDto);

    R getAllNodes();

    R updateNode(NodeUpdateDto nodeUpdateDto);

    R deleteNode(Long id);

    /**
     * 删除节点（支持级联）
     * @param id 节点ID
     * @param cascade 为 true 时，同步删除使用该节点的隧道，以及这些隧道下的转发与用户权限
     * @return 结果
     */
    R deleteNode(Long id, boolean cascade);

    /**
     * 批量删除节点
     * 逐条复用单条删除逻辑，聚合成功/失败明细
     * @param ids 节点ID列表
     * @return 批量结果
     */
    R batchDeleteNodes(java.util.List<Long> ids);

    /**
     * 批量删除节点（支持级联）
     * @param ids 节点ID列表
     * @param cascade 为 true 时，同步删除相关隧道及其转发与用户权限
     * @return 批量结果
     */
    R batchDeleteNodes(java.util.List<Long> ids, boolean cascade);

    Node getNodeById(Long id);

    R getInstallCommand(Long id);

}
