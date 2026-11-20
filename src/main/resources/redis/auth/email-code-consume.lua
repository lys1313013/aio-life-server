-- 返回 1 表示已消费，-1 表示达到错误上限，0 表示无效或过期。
local state = redis.call('HMGET', KEYS[1], 'recipient', 'code')
if state[1] ~= ARGV[1] or not state[2] then
    return 0
end
if state[2] == ARGV[2] then
    redis.call('DEL', KEYS[1])
    return 1
end
if redis.call('HINCRBY', KEYS[1], 'failures', 1) >= tonumber(ARGV[3]) then
    redis.call('DEL', KEYS[1])
    return -1
end
return 0
