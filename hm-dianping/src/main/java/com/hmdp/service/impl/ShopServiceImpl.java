package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheCline;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Service
@RequiredArgsConstructor
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = new ThreadPoolExecutor(
            // 核心线程数
            10,
            // 最大池大小
            10,
            // 保持活力
            0L,
            // 单位
            TimeUnit.MILLISECONDS,
            // 工作队列
            new LinkedBlockingQueue<Runnable>()
    );

    private final CacheCline cacheCline;

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
        // queryWithPassThrough(id);

        // 互斥锁解决缓存击穿
        // Shop shop = queryWithMutex(id);
        // Shop shop = queryWithLogicExpire(id);

        Shop shop = cacheCline
                .queryWithLogicExpire(CACHE_SHOP_KEY, id.toString(), id,
                        Shop.class, CACHE_SHOP_TTL, TimeUnit.MINUTES, this::getById, LOCK_SHOP_KEY);

        if (shop == null) {
            return Result.fail("店铺不存在!");
        }

        // 返回店铺信息
        return Result.ok(shop);
    }

    /**
     * 逻辑过期时间来设置热点key
     *
     * @param id 店铺id
     * @return 返回店铺信息
     */
    public Shop queryWithLogicExpire(Long id) {
        // 从redis中查询
        String shopCache = (String) stringRedisTemplate.opsForHash().get(CACHE_SHOP_KEY + id, id.toString());

        // 判断是否命中数据
        if (StrUtil.isBlank(shopCache)) {
            // 若没有命中，直接返回null值
            return null;
        }

        // 命中，将shopCache反序列化
        RedisData redisData = JSONUtil.toBean(shopCache, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 判断逻辑时间是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 未过期，直接返回店铺信息
            return shop;
        }

        // 过期，进行缓存重建
        // 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Boolean isLock = tryLock(lockKey);

        // 判断是否获得互斥锁
        if (isLock) {
            // 判断逻辑时间是否过期
            if (expireTime.isAfter(LocalDateTime.now())) {
                // 未过期，直接返回店铺信息
                return shop;
            } else {
                // 开启独立线程去处理缓存重建
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    try {
                        // 重建缓存
                        this.saveShopRedis(id, 30 * 60L);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        // 释放锁
                        unlock(lockKey);
                    }
                });
            }
        }

        // 返回过时的店铺信息
        return shop;
    }

    /**
     * 因为没有后台管理系统，所以这里手动模拟提前录入需要秒杀的热键信息
     *
     * @param id            店铺id
     * @param expireSeconds 热键的逻辑过期时间（秒）
     */
    public void saveShopRedis(Long id, Long expireSeconds) throws InterruptedException {
        // 获取店铺信息
        Shop shop = getById(id);
        // 封装店铺信息
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));

        // 存入redis缓存
        stringRedisTemplate.opsForHash().put(CACHE_SHOP_KEY + id, id.toString(), JSONUtil.toJsonStr(redisData));
    }

    public Shop queryWithMutex(Long id) {
        // 从redis中查询
        String shopCache = (String) stringRedisTemplate.opsForHash().get(CACHE_SHOP_KEY + id, id.toString());

        // 判断是否存在
        if (StrUtil.isNotBlank(shopCache)) {
            // 存在，则返回
            Shop shop = JSONUtil.toBean(shopCache, Shop.class);
            return shop;
        }

        // 判断数据是否是空值
        if (shopCache != null) {
            return null;
        }

        // 实现缓存重建
        // 获取互斥锁
        String key = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            Boolean lock = tryLock(key);

            // 判断是否获取到锁
            if (!lock) {
                // 休眠50毫秒后再试
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            // 不存在，则去数据库查询
            shop = getById(id);

            // 数据库中不存在，返回错误
            if (shop == null) {
                // 将null值写入redis作为缓存
                stringRedisTemplate.opsForHash().put(CACHE_SHOP_KEY + id, id.toString(), "");
                stringRedisTemplate.expire(CACHE_SHOP_KEY + id, CACHE_NULL_TTL, TimeUnit.MINUTES);

                // 返回错误信息
                return null;
            }

            // 若存在，则写回redis缓存，在返回商铺信息
            String jsonShop = JSONUtil.toJsonStr(shop);
            stringRedisTemplate.opsForHash().put(CACHE_SHOP_KEY + id, id.toString(), jsonShop);
            stringRedisTemplate.expire(CACHE_SHOP_KEY + id, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            // 释放锁
            unlock(key);
        }

        // 返回店铺信息
        return shop;
    }

    /**
     * 缓存穿透
     *
     * @param id 商铺id
     * @return 返回Shop对象
     */
    public Shop queryWithPassThrough(Long id) {
        // 从redis中查询
        String shopCache = (String) stringRedisTemplate.opsForHash().get(CACHE_SHOP_KEY + id, id.toString());

        // 判断是否存在
        if (StrUtil.isNotBlank(shopCache)) {
            // 存在，则返回
            Shop shop = JSONUtil.toBean(shopCache, Shop.class);
            return shop;
        }

        // 判断数据是否是空值
        if (shopCache != null) {
            return null;
        }

        // 不存在，则去数据库查询
        Shop shop = getById(id);

        // 数据库中不存在，返回错误
        if (shop == null) {
            // 将null值写入redis作为缓存
            stringRedisTemplate.opsForHash().put(CACHE_SHOP_KEY + id, id.toString(), "");
            stringRedisTemplate.expire(CACHE_SHOP_KEY + id, CACHE_NULL_TTL, TimeUnit.MINUTES);

            // 返回错误信息
            return null;
        }

        // 若存在，则写回redis缓存，在返回商铺信息
        String jsonShop = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForHash().put(CACHE_SHOP_KEY + id, id.toString(), jsonShop);
        stringRedisTemplate.expire(CACHE_SHOP_KEY + id, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 返回店铺信息
        return shop;
    }

    /**
     * 尝试获取互斥锁，解决多线程并发请求
     *
     * @param key 用于后续释放
     * @return Boolean值
     */
    private Boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     *
     * @param key
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    @Override
    public Result update(Shop shop) {
        // 判断商铺id是否为空，为空返回错误，不进行操作
        if (shop.getId() == null) {
            return Result.fail("店铺信息更新错误，请稍后重试!");
        }
        // 更新数据库数据
        updateById(shop);

        // 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());

        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1.查询是否需要附近店铺信息
        if (x == null || y == null) {
            // 不需要坐标查询，按数据库查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }

        // 2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // 3.从redis中获取附近店铺信息
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(SHOP_GEO_KEY + typeId,
                GeoReference.fromCoordinate(x, y), new Distance(5, Metrics.KILOMETERS)
                , RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));

        // 4.解析数据 包含typeID、distance
        if (results == null) {
            // 没有店铺信息，返回空集合
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();
        if (content.size() <= from) {
            return Result.ok(Collections.emptyList());
        }
        // 4.1 将店铺id保存到一个集合中
        List<Long> shopIds = new ArrayList<>(content.size());
        // 4.2 店铺id要和距离一一对应，使用map集合实现
        Map<String, Distance> map = new HashMap<>(content.size());
        // 4.3 截取from~end的内容
        content.stream().skip(from).forEach(geoLocationGeoResult -> {
            // 获取店铺id
            String shopId = geoLocationGeoResult.getContent().getName();
            shopIds.add(Long.valueOf(shopId));
            // 获取距离个人的店铺距离
            Distance distance = geoLocationGeoResult.getDistance();
            map.put(shopId, distance);
        });

        // 5.根据id查询店铺信息
        String idStr = StrUtil.join(",", shopIds);
        List<Shop> shops = query().in("id", shopIds).last("ORDER BY FIELD(id," + idStr + ")").list();
        shops.forEach(shop -> {
            shop.setDistance(map.get(shop.getId().toString()).getValue());
        });

        // 6.返回符合条件的店铺数据
        return Result.ok(shops);
    }
}
