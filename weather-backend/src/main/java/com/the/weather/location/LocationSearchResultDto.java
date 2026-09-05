package com.the.weather.location;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A location returned by place or address search")
public record LocationSearchResultDto(
        @Schema(example = "RMIT University Vietnam") String name,
        @Schema(example = "702 Nguyen Van Linh, District 7, Ho Chi Minh City, Vietnam") String address,
        @Schema(example = "10.729") double latitude,
        @Schema(example = "106.694") double longitude) {
}
