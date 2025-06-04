package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 */
@Service
@RequiredArgsConstructor
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryByTypeList() {
        // 从redis中获取商品类型信息
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(CACHE_SHOP_TYPE_KEY);
        // 判断是否存在
        if (!entries.isEmpty()) {
            // 存在则直接返回
            List<ShopType> typeList = entries.values().stream()
                    .map(obj -> JSONUtil.toBean(obj.toString(), ShopType.class))
                    .sorted((a, b) -> Integer.compare(a.getSort(), b.getSort()))
                    .collect(Collectors.toList());
            return Result.ok(typeList);
        }

        // 若不存在，则去数据库查询
        List<ShopType> typeList = query().orderByAsc("sort").list();

        // 判断数据库中是否存在该信息
        if (typeList.isEmpty()) {
            // 若不存在，则返回错误
            return Result.fail("查询错误，请联系客服！");
        }

        // 若存在，则写回redis缓存，并直接返回
        typeList.forEach(type -> stringRedisTemplate.opsForHash()
                .put(CACHE_SHOP_TYPE_KEY, type.getId().toString(), JSONUtil.toJsonStr(type)));

        return Result.ok(typeList);
    }
}
