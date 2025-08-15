package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

   @Autowired
   private StringRedisTemplate stringRedisTemplate;

   @Autowired
   private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
      //缓存穿透
//        Shop shop = cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY,id, Shop.class,this::getById,RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES);
      //互斥锁解决缓存击穿
//      Shop shop =   queryWithMutex(id);
        //逻辑过期解决缓存击穿问题
        Shop shop = cacheClient.queryWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY,id,Shop.class,this::getById,20L,TimeUnit.SECONDS);

      if(shop==null){
          return Result.fail("店铺不存在!");
      }
      return  Result.ok(shop);

    }

//    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

//    public Shop queryWithLogicalExpire(Long id){
//        //从redis查询对应的缓存
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        String shopJson =  stringRedisTemplate.opsForValue().get(key);
//        //不存在则直接返回null
//        if(StrUtil.isBlank(shopJson)){
//            return null;
//        }
//        //命中，则获取缓存
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(),Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        //查看缓存是否过期
//        if(expireTime.isAfter(LocalDateTime.now())){
//            //缓存未过期，则直接返回缓存数据
//            return shop;
//        }
//        //缓存过期，则获取互斥锁
//        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
//        if(tryLock(lockKey)){
//            //互斥锁未被占用，则开辟新线程，完成缓存重建 (另外应该再检测一次redis缓存未过期)
//            String shopJsonTest =  stringRedisTemplate.opsForValue().get(key);
//            RedisData redisDataTest = JSONUtil.toBean(shopJson, RedisData.class);
//            Shop shopTest = JSONUtil.toBean((JSONObject) redisData.getData(),Shop.class);
//            LocalDateTime expireTimeTest = redisData.getExpireTime();
//            if(!expireTimeTest.isAfter(LocalDateTime.now())){
//                CACHE_REBUILD_EXECUTOR.execute(() -> {
//                    try {
//                        saveShop2Redis(id,20L);
//                    } catch (Exception e) {
//                        throw new RuntimeException(e);
//                    } finally {
//                        //释放锁
//                        unLock(lockKey);
//                    }
//                });
//
//            }
//        }
//        //将信息返回
//        return shop;
//    }

//    public Shop queryWithPassThrough(Long id){
//        //从redis查询对应的缓存
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        String shopJson =  stringRedisTemplate.opsForValue().get(key);
//        //存在则直接返回缓存
//        if(StrUtil.isNotBlank(shopJson)){
//
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        //判断命中是否为空值
//        if(shopJson != null){
//            return null;
//        }
//        //不存在先查询数据库
//        Shop shop = getById(id);
//        //数据库数据不存在则返回错误
//        if(shop==null){
//            //空值写入redis
//            stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
//            return null;
//        }
//
//        //存在则将查到的信息写入redis
//        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        //将信息返回
//        return shop;
//    }

//    private Shop queryWithMutex(Long id) {
//        //从redis查询对应的缓存
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        String shopJson =  stringRedisTemplate.opsForValue().get(key);
//        //存在则直接返回缓存
//        if(StrUtil.isNotBlank(shopJson)){
//
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        //判断命中是否为空值
//        if(shopJson != null){
//            return null;
//        }
//        //不存在则先获取互斥锁
//        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
//        Shop shop = null;
//        try {
//            boolean isLock = tryLock(lockKey);
//            if(!isLock){
//                //互斥锁被占用，则休眠一段时间后，继续查询redis缓存
//                Thread.sleep(50);
//                return queryWithPassThrough(id);
//            }
//            //没有被占用，则根据id查询数据库
//            shop = getById(id);
//            //构建重建延时
//            Thread.sleep(200);
//            //数据库数据不存在则返回错误
//            if(shop==null){
//                //空值写入redis
//                stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
//                return null;
//            }
//            //存在则将查到的信息写入redis
//            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            //将互斥锁解开
//            unLock(lockKey);
//        }
//        //将信息返回
//        return shop;
//
//    }

//    private boolean tryLock(String key){
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
//
//    private void unLock(String key){
//        stringRedisTemplate.delete(key);
//    }
//
//    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
//        //查询店铺数据
//        Shop shop = getById(id);
//        Thread.sleep(200);
//        //封装过期时间
//        RedisData  redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
//    }


    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id==null){
            return Result.fail("店铺id不能为空");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}
