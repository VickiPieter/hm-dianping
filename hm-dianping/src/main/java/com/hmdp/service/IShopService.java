package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 */
public interface IShopService extends IService<Shop> {

    /**
     * 通过id查询商户信息
     * @param id 商铺id
     * @return Shop对象
     */
    Result queryById(Long id);

    /**
     * 更新店铺信息
     * @param shop 店铺对象
     * @return
     */
    Result update(Shop shop);

    /**
     * 根据店铺类型和地理坐标查询附近店铺
     * @param typeId 店铺id
     * @param current 当前页面
     * @param x 经度
     * @param y 纬度
     * @return 查询到的符合条件的附近的店铺
     */
    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
