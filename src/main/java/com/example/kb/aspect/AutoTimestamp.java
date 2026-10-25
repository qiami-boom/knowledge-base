package com.example.kb.aspect;


import org.aspectj.lang.annotation.Aspect;

import java.lang.annotation.*;

/**
 * aop自动时间字段补齐
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AutoTimestamp { }
