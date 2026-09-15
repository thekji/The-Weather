package com.the.weather.analytics.service;

import com.the.weather.analytics.dto.AnalyticsRefreshResponseDto;
import com.the.weather.analytics.dto.AnalyticsRefreshStatusDto;
import com.the.weather.analytics.dto.AnalyticsSummaryDto;

public interface AnalyticsService {

    AnalyticsRefreshResponseDto refresh();

    AnalyticsRefreshStatusDto refreshStatus();

    AnalyticsSummaryDto summary();
}
