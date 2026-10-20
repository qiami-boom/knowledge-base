
package com.example.kb.review;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
/**
 * 复习卡片
 * ReviewController
 */
@RestController
@RequestMapping("/api/review")
@CrossOrigin(origins = "*")
public class ReviewController {
    @Autowired
    private ReviewService reviewService;

    @Operation(summary = "获取待复习卡片")
    
    @GetMapping("/due")
    public List<ReviewCard> due(){
        return reviewService.dueCards();
    }
    @PostMapping("/{id}")
    public ReviewCard review(@PathVariable Long id, @RequestParam RecallQuality quality){
        return reviewService.review(id,quality);
    }
 
}
