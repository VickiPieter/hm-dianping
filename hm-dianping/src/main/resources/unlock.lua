--获取key
--local key = "lock:order:5"
--获取当前线程id
--local threadId = Thread.currentThread().getId()
--local id = redis.call('get', 'key')
--获取某个值
--redis.call('set', keys[1], argv[1])

-- 这里的 KEYS[1] 就是锁的key，这里的ARGV[1] 就是当前线程标示
-- 获取锁中的标示，判断是否与当前线程标示一致
if (redis.call('GET', KEYS[1]) == ARGV[1]) then
    -- 一致，则删除锁
    return redis.call('DEL', KEYS[1])
end
-- 不一致，则直接返回
return 0