package com.admin.common.utils;

import com.admin.common.lang.R;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 批量操作结果工具类
 * 统一批量删除/批量处理的返回结构：
 * {
 *   "total": 总数,
 *   "success": 成功数,
 *   "failed": 失败数,
 *   "failures": [ { "id": 记录ID, "reason": 失败原因 } ]
 * }
 */
public final class BatchResultUtil {

    private BatchResultUtil() {
    }

    /**
     * 构建单条失败明细
     *
     * @param id     记录ID
     * @param reason 失败原因
     * @return 失败明细
     */
    public static Map<String, Object> failure(Object id, String reason) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", id);
        item.put("reason", (reason == null || reason.isEmpty()) ? "未知错误" : reason);
        return item;
    }

    /**
     * 构建批量操作统一返回结果
     *
     * @param total    总数
     * @param success  成功数
     * @param failures 失败明细列表
     * @return 统一结果
     */
    public static R result(int total, int success, List<Map<String, Object>> failures) {
        Map<String, Object> data = new HashMap<>();
        data.put("total", total);
        data.put("success", success);
        data.put("failed", failures == null ? 0 : failures.size());
        data.put("failures", failures);
        return R.ok(data);
    }
}
