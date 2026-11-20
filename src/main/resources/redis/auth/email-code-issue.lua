-- KEYS: 邮箱冷却、IP 日计数、邮箱日计数、全局日计数、验证码状态。
-- ARGV: 三个日配额、次日零点毫秒时间戳、冷却秒数、收件地址标识、验证码、有效秒数。
local time = redis.call('TIME')
local nowMillis = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
local midnightMillis = tonumber(ARGV[4])
local retryMillis = 0
if redis.call('EXISTS', KEYS[1]) == 1 then
    retryMillis = math.max(1, redis.call('PTTL', KEYS[1]))
end
for i = 2, 4 do
    if tonumber(redis.call('GET', KEYS[i]) or '0') >= tonumber(ARGV[i - 1]) then
        retryMillis = math.max(retryMillis, 1, midnightMillis - nowMillis)
    end
end
if retryMillis > 0 then
    return retryMillis
end

for i = 2, 4 do
    if redis.call('INCR', KEYS[i]) == 1 then
        -- 日期已进入 key，保留短暂余量以覆盖零点附近已进入处理的请求。
        redis.call('PEXPIREAT', KEYS[i], midnightMillis + 3600000)
    end
end
redis.call('SET', KEYS[1], 1, 'EX', ARGV[5])
redis.call('HSET', KEYS[5], 'recipient', ARGV[6], 'code', ARGV[7], 'failures', 0)
redis.call('EXPIRE', KEYS[5], ARGV[8])
return 0
