package com.example.kb.service.dto;

public record ImportResult(
    int total,
    int scoredCount,
    int importedCount
){
}
