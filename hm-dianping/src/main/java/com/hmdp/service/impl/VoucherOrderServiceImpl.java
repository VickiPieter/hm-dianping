package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.hmdp.utils.RedisConstants.GROUP_NAME;
import static com.hmdp.utils.RedisConstants.LOCK_ORDER_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private final ISeckillVoucherService seckillVoucherService;

    private final RedisIdWorker redisIdWorker;

    private final StringRedisTemplate stringRedisTemplate;

    private static final String BUSINESS_NAME = "order";

    private final RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static{
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /**
     * 异步线程池来处理阻塞队列中的任务
     */
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    /**
     * 当类开始初始化完毕后，就去队列中取任务
     */
    @PostConstruct
    public void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherHandler());
    }

    /**
     * 开启任务处理
     */
    private class VoucherHandler implements Runnable{
        @Override
        public void run() {
            while (true) {
                try {
                    // 获取消息队列中的订单信息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("group1", "consumer1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(GROUP_NAME, ReadOffset.lastConsumed())
                    );
                    // 判断订单是否为null或者是空数组
                    if (list == null || list.isEmpty()) {
                        // 为空，说明没有获取到消息，继续下一次循环
                        continue;
                    }
                    // 解析数据
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder order = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), false);
                    // 创建订单
                    handlerVoucher(order);
                    // 返回ack确认值，说明消息正确接受并处理
                    stringRedisTemplate.opsForStream().acknowledge(GROUP_NAME, "group1", record.getId());
                } catch (Exception e) {
                    log.info("处理订单异常", e);
                    handlerPendingList();
                }
            }
        }

        private void handlerPendingList() {
            while (true) {
                try {
                    // 获取pending-list队列中的订单信息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("group1", "consumer1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(GROUP_NAME, ReadOffset.from("0"))
                    );
                    // 判断订单是否为null或者是空数组
                    if (list == null || list.isEmpty()) {
                        // 为空，说明pending-list没有消息，结束循环
                        break;
                    }
                    // 解析数据
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder order = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    // 创建订单
                    handlerVoucher(order);
                    // 返回ack确认值，说明消息正确接受并处理
                    stringRedisTemplate.opsForStream().acknowledge(GROUP_NAME, "group1", record.getId());
                } catch (Exception e) {
                    log.info("处理pending-list订单异常", e);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }
    }
/*

    */
/**
     * 阻塞队列
     *//*

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    */
/**
     * 开启任务处理
     *//*

    private class VoucherHandler implements Runnable{
        @Override
        public void run() {
            try {
                // 从队列中取任务(订单信息)
                VoucherOrder order = orderTasks.take();
                // 创建订单
                handlerVoucher(order);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
*/

    private void handlerVoucher(VoucherOrder order) {
        // 获取用户id
        Long userId = order.getUserId();
        RLock redisLock = redissonClient.getLock(LOCK_ORDER_KEY + userId);
        // 获取锁
        boolean isLock = redisLock.tryLock();
        // 判断获取锁是否成功
        if (!isLock) {
            // 获取锁失败，返回错误信息或重试(这里是重复下单，所以直接返回错误信息)
            return;
        }

        // 防止业务出现异常，也可以释放锁
        try {
            // 获取当前事务代理对象，如不获取，使用的是VoucherOrderServiceImpl本身，但是spring是动态代理
            // 当前使用的是多线程，执行任务的是子线程，不能从当前代理中获取代理
            // IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            proxy.createVoucherOrder(order);
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放锁
            redisLock.unlock();
        }
    }

    private IVoucherOrderService proxy;

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 获取用户id
        Long userId = UserHolder.getUser().getId();
        // 获取订单id
        Long orderId = redisIdWorker.nextId(BUSINESS_NAME);
        // 执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), orderId.toString()
        );

        // 判断结果是0(为下过此单的用户下单), 1(库存不足), 2(此用户已经下过单了)
        assert result != null;
        int r = result.intValue();
        if (r != 0) {
            // 根据结果返回不同的提示
            return Result.fail(r == 1 ? "库存不足,请勿下单" : "每位用户仅此一单,请勿重复下单");
        }
        // 获取当前代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        // 返回订单id
        return Result.ok(orderId);
    }


    /*@Override
    public Result seckillVoucher(Long voucherId) {
        // 获取用户id
        Long userId = UserHolder.getUser().getId();
        // 获取订单id
        Long orderId = redisIdWorker.nextId(BUSINESS_NAME);
        // 执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );

        // 判断结果是0(为下过此单的用户下单), 1(库存不足), 2(此用户已经下过单了)
        assert result != null;
        int r = result.intValue();
        if (r != 0) {
            // 根据结果返回不同的提示
            return Result.fail(r == 1 ? "库存不足,请勿下单" : "每位用户仅此一单,请勿重复下单");
        }

        // 将用户id和订单id存入阻塞队列，进行异步下单
        // 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 订单id
        voucherOrder.setId(redisIdWorker.nextId(SECKILL_ORDER_KEY));
        // 设置用户id
        voucherOrder.setUserId(userId);
        // 优惠券id
        voucherOrder.setVoucherId(voucherId);
        // 放入阻塞队列
        orderTasks.add(voucherOrder);
        // 获取当前代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        // 返回订单id
        return Result.ok(orderId);
    }*/

    /*@Override
    public Result seckillVoucher(Long voucherId) {
        // 查询优惠券信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        // 判断秒杀活动是否结束
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 活动尚未开始
            return Result.fail("活动尚未开始!");
        }

        // 判断秒杀活动是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 活动已经结束
            return Result.fail("活动已结束!");
        }

        // 判断库存是否充足
        if (voucher.getStock() < 1) {
            // 库存不足，返回不足信息，提示用户
            return Result.fail("库存不足!");
        }
        // 获取用户id
        Long userId = UserHolder.getUser().getId();
        // 确保事务一致性
        // SimpleRedisLock redisLock = new SimpleRedisLock(stringRedisTemplate, BUSINESS_NAME);
        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
        // 获取锁
        boolean isLock = redisLock.tryLock();
        // 判断获取锁是否成功
        if (!isLock) {
            // 获取锁失败，返回错误信息或重试(这里是重复下单，所以直接返回错误信息)
            return Result.fail("每位用户仅可下一单,请勿重复下单!");
        }

        // 防止业务出现异常，也可以释放锁
        try {
            // 获取当前事务代理对象，如不获取，使用的是VoucherOrderServiceImpl本身，但是spring是动态代理
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放锁
            redisLock.unlock();
        }

    }*/

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 一人一单
        // 获取用户id
        // Long userId = UserHolder.getUser().getId();
        Long userId = voucherOrder.getUserId();
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        if (count > 0) {
            // 说明用户已经购买过了
            log.info("用户已经购买过了");
            return;
        }

        // 充足则扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                // 用于确保库存不会超卖的情况
                .gt("stock", 0)
                .update();

        // 判断库存是否扣减成功
        if (!success) {
            // 未成功，返回异常信息
            log.info("库存不足");
            return;
        }
        // 写回数据库
        save(voucherOrder);
    }
}
