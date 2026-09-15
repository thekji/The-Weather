package com.the.weather.post.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.the.weather.post.exception.PostApiException;

class PostControllerTests {

    @Test
    void parsesWeatherAccuracyRatingFromMultipartText() {
        assertThat(PostController.parseWeatherAccuracyRating("1")).isEqualTo(1);
        assertThat(PostController.parseWeatherAccuracyRating("5")).isEqualTo(5);
    }

    @Test
    void missingWeatherAccuracyRatingFallsThroughToServiceValidation() {
        assertThat(PostController.parseWeatherAccuracyRating(null)).isNull();
        assertThat(PostController.parseWeatherAccuracyRating(" ")).isNull();
    }

    @Test
    void nonNumericWeatherAccuracyRatingReturnsBadRequest() {
        assertThatThrownBy(() -> PostController.parseWeatherAccuracyRating("sometimes"))
                .isInstanceOf(PostApiException.class)
                .extracting("status.value").isEqualTo(400);
    }
}
