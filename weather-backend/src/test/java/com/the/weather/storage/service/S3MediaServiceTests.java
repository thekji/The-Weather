package com.the.weather.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.the.weather.post.exception.PostApiException;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3MediaServiceTests {
    private final S3MediaService service = new S3MediaService(
            mock(S3Client.class), mock(S3Presigner.class), "bucket", 5 * 1024 * 1024, 15);

    @Test
    void acceptsZeroToThreeSupportedImages() {
        service.validateImages(List.of(image("image/jpeg"), image("image/png"), image("image/webp")));
    }

    @Test
    void rejectsFourthImage() {
        assertThatThrownBy(() -> service.validateImages(List.of(
                image("image/jpeg"), image("image/jpeg"), image("image/jpeg"), image("image/jpeg"))))
                .isInstanceOf(PostApiException.class).extracting("code").isEqualTo("TOO_MANY_IMAGES");
    }

    @Test
    void rejectsUnsupportedAndOversizedImages() {
        assertThatThrownBy(() -> service.validateImages(List.of(image("image/gif"))))
                .isInstanceOf(PostApiException.class).extracting("code").isEqualTo("INVALID_IMAGE_TYPE");
        byte[] bytes = new byte[5 * 1024 * 1024 + 1];
        assertThatThrownBy(() -> service.validateImages(List.of(
                new MockMultipartFile("images", "large.jpg", "image/jpeg", bytes))))
                .isInstanceOf(PostApiException.class).extracting("code").isEqualTo("IMAGE_TOO_LARGE");
    }

    @Test
    void generatedKeyUsesServerOwnedUuidAndValidatedExtension() {
        String key = service.upload("user_1", "post_1", image("image/webp"));
        assertThat(key).matches("community-posts/user_1/post_1/[0-9a-f-]{36}\\.webp");
    }

    private static MockMultipartFile image(String type) {
        return new MockMultipartFile("images", "client-name", type, new byte[] {1});
    }
}
