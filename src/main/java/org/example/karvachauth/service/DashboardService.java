package org.example.karvachauth.service;

import java.util.Map;

public interface DashboardService {

    Map<String, Object> getMetricCards(String flow, String range, String startDate, String endDate);

    Map<String, Object> getOutcomes(String flow, String range, String startDate, String endDate);

    Map<String, Object> getFlowDropOff(String flow, String range, String startDate, String endDate);

    Map<String, Object> getActivity(String flow, String range, String startDate, String endDate, int limit);

    Map<String, Object> getPathsSummary(String range, String startDate, String endDate);
}