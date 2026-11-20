-- KEYS: 来源 IP、账号、账号与 IP。ARGV: 请求 ID、三个维度的阈值和窗口秒数。
local ipCount = tonumber(redis.call('GET', KEYS[1]) or '0')
if ipCount >= tonumber(ARGV[2]) then
    return math.max(1, redis.call('PTTL', KEYS[1]))
end

-- IP 计入所有请求，包括账号额度已耗尽的请求。
if ipCount == 0 then
    redis.call('SET', KEYS[1], 1, 'EX', ARGV[3])
else
    redis.call('INCR', KEYS[1])
end

local retryMillis = 0
for i = 2, 3 do
    if redis.call('SCARD', KEYS[i]) >= tonumber(ARGV[2 * i]) then
        retryMillis = math.max(retryMillis, 1, redis.call('PTTL', KEYS[i]))
    end
end
if retryMillis > 0 then
    return retryMillis
end

for i = 2, 3 do
    local isNewWindow = redis.call('EXISTS', KEYS[i]) == 0
    redis.call('SADD', KEYS[i], ARGV[1])
    if isNewWindow then
        redis.call('EXPIRE', KEYS[i], ARGV[2 * i + 1])
    end
end
return 0
