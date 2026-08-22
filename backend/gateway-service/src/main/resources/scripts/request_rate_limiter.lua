-- Redis令牌桶限流Lua脚本，Spring Cloud Gateway RequestRateLimiter原生逻辑
-- 脚本入参说明：
-- KEYS[1]：tokens_key  存储当前剩余令牌数的Redis key
-- KEYS[2]：timestamp_key 存储上一次令牌桶刷新时间戳(秒)的Redis key
--
-- ARGV[1]：rate 令牌生成速率(replenishRate)，每秒补充多少令牌
-- ARGV[2]：capacity 令牌桶最大容量(burstCapacity)
-- ARGV[3]：当前时间戳，单位秒；不传则取redis服务器TIME时间
-- ARGV[4]：本次请求消耗令牌数(requestedTokens)
--
-- 返回值：{allowed(1允许/0拒绝), 剩余令牌数}

-- 开启Redis脚本复制模式，集群环境保证脚本复制到从节点
redis.replicate_commands()

-- 取出两个key
local tokens_key = KEYS[1]
local timestamp_key = KEYS[2]

-- 参数转换为数字
local rate = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
local now = tonumber(ARGV[3]) or redis.call('TIME')[1]
local requested = tonumber(ARGV[4])

-- 桶填满需要的时间 = 总容量 / 每秒生成速率
local fill_time = capacity / rate
-- Redis key过期时间，设置为填满时间的2倍，避免key长期残留
local ttl = math.floor(fill_time * 2)

-- 读取上一次剩余令牌；key不存在则初始化为满桶capacity
local last_tokens = tonumber(redis.call("get", tokens_key)) or capacity
-- 读取上一次刷新时间戳；key不存在则为0
local last_refreshed = tonumber(redis.call("get", timestamp_key)) or 0

-- 距离上次刷新过去了多少秒
local delta = math.max(0, now-last_refreshed)
-- 根据流逝时间补充令牌，不能超过桶最大容量capacity
local filled_tokens = math.min(capacity, last_tokens+(delta*rate))

-- 判断：剩余令牌是否足够本次请求消耗
local allowed = filled_tokens >= requested
-- 如果允许，扣掉本次请求令牌；不允许则令牌不变
local new_tokens = allowed and filled_tokens - requested or filled_tokens

-- ttl大于0时，更新令牌数与时间戳，并设置过期时间
if ttl > 0 then
  redis.call("setex", tokens_key, ttl, new_tokens)
  redis.call("setex", timestamp_key, ttl, now)
end

-- 返回结果：{是否允许, 更新后剩余令牌}
return { allowed and 1 or 0, new_tokens }
