package com.the.weather.feedback.controller;

import java.security.Principal;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.the.weather.feedback.dto.FeedbackRequest;
import com.the.weather.feedback.dto.FeedbackResponse;
import com.the.weather.feedback.service.FeedbackService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/posts/{postID}/feedback")
@Tag(name = "Post feedback", description = "Authenticated community usefulness feedback")
@SecurityRequirement(name = "bearerAuth")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PutMapping
    @Operation(summary = "Set or change my feedback for a community post")
    public FeedbackResponse set(
            Principal principal,
            @PathVariable String postID,
            @Valid @RequestBody FeedbackRequest request) {
        return feedbackService.set(principal.getName(), postID, request);
    }

    @DeleteMapping
    @Operation(summary = "Remove my feedback from a community post")
    public FeedbackResponse remove(Principal principal, @PathVariable String postID) {
        return feedbackService.remove(principal.getName(), postID);
    }
}
