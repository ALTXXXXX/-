package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {

    private StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    //设置TTL过期时间
    public void set(String key, Object value, Long time, TimeUnit timeUnit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }
    //设置逻辑过期
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit){
       //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    public <R,ID>  R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback,Long time, TimeUnit unit){
        //从redis查询对应的缓存
        String key = keyPrefix + id;
        String json =  stringRedisTemplate.opsForValue().get(key);
        //存在则直接返回缓存
        if(StrUtil.isNotBlank(json)){

            return JSONUtil.toBean(json, type);
        }
        //判断命中是否为空值
        if(json != null){
            return null;
        }
        //不存在先查询数据库
        R r = dbFallback.apply(id);
        //数据库数据不存在则返回错误
        if(r==null){
            //空值写入redis
            stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }

        //存在则将查到的信息写入redis
      this.set(key,r,time,unit);
        //将信息返回
        return r;
    }


    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    public <R,ID> R queryWithLogicalExpire(String keyPrefix,ID id,Class<R> type,Function<ID,R> dbFallback,Long time, TimeUnit unit){
        //从redis查询对应的缓存
        String key = keyPrefix + id;
        String json =  stringRedisTemplate.opsForValue().get(key);
        //不存在则直接返回null
        if(StrUtil.isBlank(json)){
            return null;
        }
        //命中，则获取缓存
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(),type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //查看缓存是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //缓存未过期，则直接返回缓存数据
            return r;
        }

        //缓存过期，则获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        if(tryLock(lockKey)){
            //互斥锁未被占用，则开辟新线程，完成缓存重建 (另外应该再检测一次redis缓存未过期)
            String jsonTest =  stringRedisTemplate.opsForValue().get(key);
            RedisData redisDataTest = JSONUtil.toBean(jsonTest, RedisData.class);
            LocalDateTime expireTimeTest = redisDataTest.getExpireTime();
            if(!expireTimeTest.isAfter(LocalDateTime.now())){
                CACHE_REBUILD_EXECUTOR.execute(() -> {
                    try {
                        //查询数据库
                        R r1 = dbFallback.apply(id);
                        //写入redis
                        this.setWithLogicalExpire(key,r1,time,unit);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        //释放锁
                        unLock(lockKey);
                    }
                });

            }
        }
        //将信息返回
        return r;
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

}
