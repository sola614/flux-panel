package com.admin.common.task;

import com.admin.common.utils.GostUtil;
import com.admin.entity.Forward;
import com.admin.entity.Tunnel;
import com.admin.entity.User;
import com.admin.entity.UserTunnel;
import com.admin.service.ForwardService;
import com.admin.service.TunnelService;
import com.admin.service.UserService;
import com.admin.service.UserTunnelService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Configuration
@EnableScheduling
public class ResetFlowAsync {

    @Resource
    UserService userService;

    @Resource
    UserTunnelService userTunnelService;

    @Resource
    ForwardService forwardService;

    @Resource
    TunnelService tunnelService;

    /**
     * 每天0点执行流量重置任务
     * 查询出用户和隧道的重置流量日期是今天的数据，将上下流量重置为0
     * 考虑当月是29、30天，但是选择是31的这种边界情况
     * 
     * 并发安全说明：
     * - 使用setSql()进行原子SQL更新，只更新流量字段(in_flow, out_flow)
     * - 不会影响DelayQueueManager的到期任务对status等其他字段的更新
     * - 避免了并发修改导致的数据覆盖问题
     */
    @Scheduled(cron = "5 0 0 * * ?")
    public void reset_flow(){
        log.info("开始执行流量重置任务");
        
        try {
            // 获取当前日期信息
            LocalDate today = LocalDate.now();
            int currentDay = today.getDayOfMonth(); // 当前是几号
            int lastDayOfMonth = today.lengthOfMonth(); // 当月最后一天
            
            log.info("当前日期: {}, 当月第{}天, 当月最后一天: {}", today, currentDay, lastDayOfMonth);
            
            // 重置用户流量
            resetUserFlow(currentDay, lastDayOfMonth);
            
            // 重置用户隧道流量
            resetUserTunnelFlow(currentDay, lastDayOfMonth);

            // 按隧道配置的重置日期，重置该隧道下所有转发的流量
            resetForwardFlowByTunnel(currentDay, lastDayOfMonth);

            log.info("流量重置任务执行完成");


            // 处理过期账号
            user();

            // 处理过期隧道
            userTunnel();

            log.info("到期任务执行完成");
            
        } catch (Exception e) {
            log.info("定时任务执行失败", e);
        }
    }
    
    /**
     * 重置用户流量
     * @param currentDay 当前日期（几号）
     * @param lastDayOfMonth 当月最后一天
     */
    private void resetUserFlow(int currentDay, int lastDayOfMonth) {
        try {
            // flowResetTime字段存储的是0-31的数字，0表示不重置，1-31表示每月第几号重置
            // 构建查询条件：重置日期等于今天，或者重置日期大于当月最大天数且今天是月末
            // 排除flowResetTime为0的记录（不重置）
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.ne("flow_reset_time", 0); // 排除不重置的用户
            
            if (currentDay == lastDayOfMonth) {
                // 如果今天是月末，查询重置日期等于今天或者大于当月最大天数的记录
                // 例如：当月30天，但用户设置31号重置，则在30号执行重置
                queryWrapper.and(wrapper -> wrapper.eq("flow_reset_time", currentDay)
                                                  .or().gt("flow_reset_time", lastDayOfMonth));
            } else {
                // 否则只查询重置日期等于今天的记录
                queryWrapper.eq("flow_reset_time", currentDay);
            }
            
            // 查询需要重置的用户
            List<User> usersToReset = userService.list(queryWrapper);
            
            if (usersToReset.isEmpty()) {
                log.info("没有需要重置流量的用户");
                return;
            }
            
            log.info("找到{}个需要重置流量的用户", usersToReset.size());
            
            // 批量重置用户流量 - 使用SQL原子操作避免与到期任务的并发冲突
            for (User user : usersToReset) {
                UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("id", user.getId())
                           .setSql("in_flow = 0, out_flow = 0"); // 使用SQL原子操作，只更新流量字段
                
                boolean success = userService.update(null, updateWrapper);
                if (success) {
                    log.info("用户[ID: {}, 用户名: {}]流量重置成功，重置日期: 每月{}号", 
                           user.getId(), user.getUser(), user.getFlowResetTime());
                } else {
                    log.info("用户[ID: {}, 用户名: {}]流量重置失败", user.getId(), user.getUser());
                }
            }
            
        } catch (Exception e) {
            log.info("重置用户流量失败", e);
        }
    }
    
    /**
     * 重置用户隧道流量
     * @param currentDay 当前日期（几号）
     * @param lastDayOfMonth 当月最后一天
     */
    private void resetUserTunnelFlow(int currentDay, int lastDayOfMonth) {
        try {
            // flowResetTime字段存储的是0-31的数字，0表示不重置，1-31表示每月第几号重置
            // 构建查询条件：重置日期等于今天，或者重置日期大于当月最大天数且今天是月末
            // 排除flowResetTime为0的记录（不重置）
            QueryWrapper<UserTunnel> queryWrapper = new QueryWrapper<>();
            queryWrapper.ne("flow_reset_time", 0); // 排除不重置的用户隧道
            
            if (currentDay == lastDayOfMonth) {
                // 如果今天是月末，查询重置日期等于今天或者大于当月最大天数的记录
                // 例如：当月30天，但用户设置31号重置，则在30号执行重置
                queryWrapper.and(wrapper -> wrapper.eq("flow_reset_time", currentDay)
                                                  .or().gt("flow_reset_time", lastDayOfMonth));
            } else {
                // 否则只查询重置日期等于今天的记录
                queryWrapper.eq("flow_reset_time", currentDay);
            }
            
            // 查询需要重置的用户隧道
            List<UserTunnel> userTunnelsToReset = userTunnelService.list(queryWrapper);
            
            if (userTunnelsToReset.isEmpty()) {
                log.info("没有需要重置流量的用户隧道");
                return;
            }
            
            log.info("找到{}个需要重置流量的用户隧道", userTunnelsToReset.size());
            
            // 批量重置用户隧道流量 - 使用SQL原子操作避免与到期任务的并发冲突
            for (UserTunnel userTunnel : userTunnelsToReset) {
                UpdateWrapper<UserTunnel> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("id", userTunnel.getId())
                           .setSql("in_flow = 0, out_flow = 0"); // 使用SQL原子操作，只更新流量字段
                
                boolean success = userTunnelService.update(null, updateWrapper);
                if (success) {
                    log.info("用户隧道[ID: {}, 用户ID: {}, 隧道ID: {}]流量重置成功，重置日期: 每月{}号", 
                           userTunnel.getId(), userTunnel.getUserId(), userTunnel.getTunnelId(), userTunnel.getFlowResetTime());
                } else {
                    log.info("用户隧道[ID: {}, 用户ID: {}, 隧道ID: {}]流量重置失败",
                            userTunnel.getId(), userTunnel.getUserId(), userTunnel.getTunnelId());
                }
            }
            
        } catch (Exception e) {
            log.info("重置用户隧道流量失败", e);
        }
    }


    /**
     * 按隧道重置其下所有转发的流量
     *
     * <p>隧道上的 {@code flow_reset_time} 到达指定日期时，同步清零该隧道下
     * 所有转发（{@code forward.tunnel_id} 匹配）的 {@code in_flow} / {@code out_flow}。
     * 规则与用户 / 用户隧道一致：0 表示不重置，1-31 表示每月第几号，
     * 且当"配置日期大于当月天数"时在当月最后一天执行。
     *
     * @param currentDay     当前日期（几号）
     * @param lastDayOfMonth 当月最后一天
     */
    private void resetForwardFlowByTunnel(int currentDay, int lastDayOfMonth) {
        try {
            QueryWrapper<Tunnel> queryWrapper = new QueryWrapper<>();
            queryWrapper.ne("flow_reset_time", 0); // 排除不重置的隧道

            if (currentDay == lastDayOfMonth) {
                // 今天是月末：重置日期等于今天，或配置日期大于当月天数（如 2 月配了 31 号）
                queryWrapper.and(wrapper -> wrapper.eq("flow_reset_time", currentDay)
                                                  .or().gt("flow_reset_time", lastDayOfMonth));
            } else {
                queryWrapper.eq("flow_reset_time", currentDay);
            }

            List<Tunnel> tunnelsToReset = tunnelService.list(queryWrapper);
            if (tunnelsToReset.isEmpty()) {
                log.info("没有需要重置转发流量的隧道");
                return;
            }

            List<Long> tunnelIds = tunnelsToReset.stream()
                    .map(Tunnel::getId)
                    .collect(Collectors.toList());

            log.info("找到{}个需要重置转发流量的隧道: {}", tunnelIds.size(), tunnelIds);

            // 一次性原子清零这些隧道下所有转发的流量字段，避免逐条读改写
            UpdateWrapper<Forward> updateWrapper = new UpdateWrapper<>();
            updateWrapper.in("tunnel_id", tunnelIds)
                         .setSql("in_flow = 0, out_flow = 0");

            boolean success = forwardService.update(null, updateWrapper);
            if (success) {
                log.info("隧道 {} 下的转发流量重置成功", tunnelIds);
            } else {
                log.info("隧道 {} 下的转发流量重置失败", tunnelIds);
            }

        } catch (Exception e) {
            log.info("重置隧道下转发流量失败", e);
        }
    }

    public void user(){
        // 查询过期用户
        List<User> user_list = userService.list(new QueryWrapper<User>().ne("role_id", 0).eq("status", 1).isNotNull("exp_time").lt("exp_time", new Date().getTime()));
        for (User user : user_list) {
            // 查询对应转发
            List<Forward> forwardList = forwardService.list(new QueryWrapper<Forward>().eq("user_id", user.getId()).eq("status", 1));
            for (Forward forward : forwardList) {
                UserTunnel userTunnel = userTunnelService.getOne(new QueryWrapper<UserTunnel>().eq("user_id", forward.getUserId()).eq("tunnel_id", forward.getTunnelId()));
                if (userTunnel != null) {
                    pauseForwardService(forward, userTunnel.getId());
                    forward.setStatus(0);
                    forwardService.updateById(forward);
                }
            }
            user.setStatus(0);
            userService.updateById(user);
        }
    }


    public void userTunnel(){
        // 查询过期隧道
        List<UserTunnel> user_tunnel_list = userTunnelService.list(new QueryWrapper<UserTunnel>().eq("status", 1).isNotNull("exp_time").lt("exp_time", new Date().getTime()));
        // 查询对应转发
        for (UserTunnel userTunnel : user_tunnel_list) {
            List<Forward> forwardList = forwardService.list(new QueryWrapper<Forward>().eq("tunnel_id", userTunnel.getTunnelId()).eq("user_id", userTunnel.getUserId()).eq("status", 1));
            for (Forward forward : forwardList) {
                pauseForwardService(forward, userTunnel.getId());
                forward.setStatus(0);
                forwardService.updateById(forward);
            }
            userTunnel.setStatus(0);
            userTunnelService.updateById(userTunnel);
        }
    }


    private void pauseForwardService(Forward forward, Integer userTunnelId) {
        Tunnel tunnel = tunnelService.getById(forward.getTunnelId());
        if (tunnel == null) return;

        GostUtil.PauseService(tunnel.getInNodeId(), buildServiceName(forward.getId(), forward.getUserId(), userTunnelId));
        if (tunnel.getType() == 2){
            GostUtil.PauseRemoteService(tunnel.getOutNodeId(), buildServiceName(forward.getId(), forward.getUserId(), userTunnelId));
        }
    }


    private String buildServiceName(Long forwardId, Integer userId, Integer userTunnelId) {
        return forwardId + "_" + userId + "_" + userTunnelId;
    }
}
