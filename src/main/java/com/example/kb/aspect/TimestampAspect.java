package com.example.kb.aspect;


import com.example.kb.entity.Knowledge;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;


/**
 * 自动填充时间
 */
@Aspect
@Component
@Slf4j
@Order(1)
public class TimestampAspect {
    /**
     * 环绕通知：先补时间，再放行。
     * 参数名 autoTimestamp 必须和 @annotation(...) 里的名字一致，否则绑定不上。
     */
    @Around("@annotation(autoTimestamp)")
    public Object fillTime (ProceedingJoinPoint pjp,AutoTimestamp autoTimestamp)
        throws Throwable{
        int count = 0;
        for(Object arg : pjp.getArgs()){
            count += fill(arg);
        }
        if (count > 0) {
            log.debug("[AOP] 自动填充时间：{} 个字段（{}）", count, pjp.getSignature().toShortString());
        }
        return pjp.proceed();
    }


    /**
     * 判断数量，看看里面是不是Knowledge
     * @param arg
     * @return 需要补的字段的个数
     */
    private int fill (Object arg) {
        if (arg instanceof Knowledge k) {
            return fillOne(k);
        }
        if (arg instanceof List<?> list) {
            int n = 0;
            for (Object o : list) {
                if (o instanceof Knowledge k) n += fillOne(k);
            }
            return n;
        }
        return 0;
    }

    /**
     *  填充时间戳
     * @param k
     * @return
     */
    private int fillOne(Knowledge k) {
        LocalDateTime now = LocalDateTime.now();
        int n = 0;
        if (k.getCreatedAt() == null) { k.setCreatedAt(now); n++; }
        if (k.getUpdatedAt() == null) { k.setUpdatedAt(now); n++; }
        return n;
    }


}
