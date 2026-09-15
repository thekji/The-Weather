package com.the.weather.post.dto;

import java.util.List;

public record PostPageResponse(List<PostResponse> items, String nextCursor) {
}
