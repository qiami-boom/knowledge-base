package com.example.kb.review;

import org.springframework.stereotype.Service;

/**
 * 复习曲线
 */

@Service
public class FixedLadderScheduler implements ReviewScheduler {
    /**
     * 复习阶级
     * @param currentStage
     * @param quality
     * @return
     */
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
