package com.example.kb.review;

/**
 * 复习自评枚举 —— 复习动作的最上游输入
 * 串联：前端点击"记住/模糊/忘记" → ReviewController 收到字符串、Spring 自动转成枚举
 *      → ReviewService 调用调度算法（reviewScheduler.next）按它决定怎么走
 * 三个值对应三种调度：REMEMBERED 升一阶 / FUZZY 原地不动 / FORGOT 回炉第 1 阶
 */
public enum RecallQuality {
    REMEMBERED,//记住
    FUZZY,//记忆模糊
    FORGOT,//忘记
}