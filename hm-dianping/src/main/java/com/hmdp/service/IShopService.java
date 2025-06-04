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
}
