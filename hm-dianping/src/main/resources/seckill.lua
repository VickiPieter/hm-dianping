-- 获取优惠券id
local voucherId = ARGV[1]
-- 获取用户id
local userId = ARGV[2]
-- 获取订单id
local orderId = ARGV[3]

-- 通过库存key来查询库存量
local stockKey = 'seckill:stock:' .. voucherId
-- 订单key，查询订单中的信息
local orderKey = 'seckill:order:' .. voucherId
-- 组队列名
local groupKey = 'stream:orders'

-- 判断库存是否充足
if(tonumber(redis.call('get', stockKey)) == 0) then
    -- 库存不充足，返回1
    return 1
end

-- 判断用户是否下过单了
if(redis.call('sismember', orderKey, userId) == 1) then
    -- 说明用户已经下过单了，返回2
    return 2
end
-- 没下单，则扣减库存
redis.call('incrby', stockKey, -1)
-- 将userId存进当前优惠券的set集合中
redis.call('sadd', orderKey, userId)
-- 将订单消息发送到stream消息队列中
redis.call('xadd', groupKey, '*', 'voucherId', voucherId, 'userId', userId, 'id', orderId)
-- 返回0
return 0