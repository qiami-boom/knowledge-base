package com.example.kb.review;

import org.springframework.stereotype.Service;

/**
 * 固定阶梯调度算法 —— ReviewScheduler 接口的当前实现
 * 规则：REMEMBERED 升一阶（第 7 阶封顶保持）；FUZZY 原地；FORGOT 回炉第 1 阶
 * 阶梯天数 levels：第 1~7 阶 = 1/2/4/7/15/30/60 天；数组下标 = 阶数 - 1（阶从 1 起数，下标从 0 起算）
 * 纯计算器：只认参数、不碰数据库，所以测试和替换都不需要连库
 */
@Service
public class FixedLadderScheduler implements ReviewScheduler {
    
    @Override
    public ScheduleResult next(int currentStage,RecallQuality quality){
        int[] levels ={1,2,4,7,15,30,60};
        int nextStage;
        switch(quality){
            case REMEMBERED:
                if(currentStage < levels.length)
                    nextStage = currentStage + 1;
                else
                    nextStage = currentStage;
                break;
            case FUZZY:
                nextStage = currentStage;
                break;
            case FORGOT:
                nextStage = 1;
                break;
            default:
                nextStage = currentStage;
                break;
        }
        return new ScheduleResult(nextStage,levels[nextStage - 1]);
    }
}
