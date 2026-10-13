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
     * 批量删除节点
     * 逐条复用单条删除逻辑，聚合成功/失败明细
     * @param ids 节点ID列表
     * @return 批量结果
     */
    R batchDeleteNodes(java.util.List<Long> ids);

    Node getNodeById(Long id);

    R getInstallCommand(Long id);

}
