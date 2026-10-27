package com.example.kb.review;

/**
 * 调度结果 —— FixedladderSchedule只做计算器，而这个类用来做Fix类里的方法的数据载体 用来接收和返回数据
 * 串联：FixedLadderScheduler.next() 算完产出它 → ReviewService 拆出 stage 和 intervalDays
 *      → 换算成 nextReviewAt 写回数据库
 * 为什么用 record：两个值天然成对返回，只读防改，一行写完
 */
public record ScheduleResult(int stage,int intervalDays) {}
