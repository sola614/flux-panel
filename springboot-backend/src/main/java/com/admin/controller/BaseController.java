package com.admin.controller;

import com.admin.service.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BaseController {

    @Autowired
    UserService userService;

    @Autowired
    NodeService nodeService;

    @Autowired
    UserTunnelService userTunnelService;

    @Autowired
    TunnelService tunnelService;

    @Autowired
    ForwardService forwardService;

    @Autowired
    ViteConfigService viteConfigService;

    /**
     * 解析批量操作请求中的 ids 参数
     *
     * @param params 请求参数，期望包含 ids 数组
     * @return ID 列表；参数缺失或为空时返回 null
     */
    protected List<Long> parseIds(Map<String, Object> params) {
        Object raw = params == null ? null : params.get("ids");
        if (!(raw instanceof List)) {
            return null;
        }
        List<Long> ids = new ArrayList<>();
        for (Object item : (List<?>) raw) {
            if (item == null) {
                continue;
            }
            try {
                ids.add(Long.valueOf(item.toString().trim()));
            } catch (NumberFormatException ignored) {
                // 跳过非法 ID
            }
        }
        return ids.isEmpty() ? null : ids;
    }

    /**
     * 解析批量操作请求中的 ids 参数并转为 Integer 列表
     *
     * @param params 请求参数，期望包含 ids 数组
     * @return ID 列表；参数缺失或为空时返回 null
     */
    protected List<Integer> parseIntIds(Map<String, Object> params) {
        List<Long> longIds = parseIds(params);
        if (longIds == null) {
            return null;
        }
        List<Integer> ids = new ArrayList<>();
        for (Long id : longIds) {
            ids.add(id.intValue());
        }
        return ids;
    }

}
