package com.redtourism.controller;

import com.redtourism.common.Result;
import com.redtourism.entity.Faq;
import com.redtourism.service.FaqService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/faq")
public class FaqController {

    @Autowired
    private FaqService faqService;

    @GetMapping("/list")
    public Result<List<Faq>> list() {
        return Result.success(faqService.listAll());
    }

    @GetMapping("/detail")
    public Result<Faq> detail(@RequestParam Long id) {
        return Result.success(faqService.getById(id));
    }

    @GetMapping("/ask")
    public Result<String> ask(@RequestParam String question) {
        return Result.success(faqService.autoReply(question));
    }
}
