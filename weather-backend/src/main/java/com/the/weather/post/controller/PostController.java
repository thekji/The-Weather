package com.the.weather.post.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.the.weather.post.dto.CreatePostRequest;
import com.the.weather.post.dto.PostPageResponse;
import com.the.weather.post.dto.PostResponse;
import com.the.weather.post.exception.PostApiException;
import com.the.weather.post.service.PostService;
import com.the.weather.security.AuthenticatedUser;

@RestController
@RequestMapping("/api")
public class PostController {
    private final PostService service;

    public PostController(PostService service) { this.service = service; }

    @PostMapping(value = "/locations/{locationID}/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse create(Authentication authentication, @PathVariable String locationID,
            @RequestParam String description, @RequestParam String locationName,
            @RequestParam String address, @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam(required = false) String weatherAccuracyRating,
            @RequestPart(required = false) List<MultipartFile> images) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return service.create(user.userID(), user.username(), locationID,
                new CreatePostRequest(description, locationName, address, latitude, longitude,
                        parseWeatherAccuracyRating(weatherAccuracyRating)), images);
    }

    @GetMapping("/locations/{locationID}/posts")
    public PostPageResponse byLocation(Principal principal, @PathVariable String locationID,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        return service.byLocation(viewerID(principal), locationID, limit, cursor);
    }

    @GetMapping("/posts")
    public PostPageResponse communityFeed(Principal principal,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        return service.communityFeed(viewerID(principal), limit, cursor);
    }

    @GetMapping("/posts/{postID}")
    public PostResponse find(Principal principal, @PathVariable String postID) {
        return service.find(viewerID(principal), postID);
    }

    @DeleteMapping("/posts/{postID}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Principal principal, @PathVariable String postID) {
        service.delete(principal.getName(), postID);
    }

    static Integer parseWeatherAccuracyRating(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            throw new PostApiException(HttpStatus.BAD_REQUEST, "INVALID_POST",
                    "Weather accuracy rating must be between 1 and 5.");
        }
    }

    private static String viewerID(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
