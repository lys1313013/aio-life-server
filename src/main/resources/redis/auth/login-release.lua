-- 请求只释放自己的占位，重复释放和跨窗口释放均无副作用。
redis.call('SREM', KEYS[1], ARGV[1])
redis.call('SREM', KEYS[2], ARGV[1])
return 0
