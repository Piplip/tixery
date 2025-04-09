package com.nkd.accountservice.service.impl;

import com.google.analytics.data.v1beta.*;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.nkd.accountservice.domain.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jooq.types.UInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.nkd.accountservice.tables.Profile.PROFILE;
import static com.nkd.accountservice.tables.Role.ROLE;
import static com.nkd.accountservice.tables.UserAccount.USER_ACCOUNT;
import static com.nkd.accountservice.tables.UserData.USER_DATA;
import static org.jooq.impl.DSL.field;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminService {

    private final DSLContext context;
    private final ResourceLoader resourceLoader;

    @Value("${google.analytics.credentials.path:classpath:credentials/service-account.json}")
    private String credentialsPath;

    @Value("${google.analytics.property.id:456648823}")
    private String defaultPropertyId;

    public Map<String, Object> loadUsers(Integer page, Integer size){
        var users = context.select(USER_ACCOUNT.ACCOUNT_ID, USER_ACCOUNT.ACCOUNT_EMAIL, ROLE.ROLE_NAME, USER_DATA.FULL_NAME, USER_ACCOUNT.ACCOUNT_STATUS)
                .from(USER_ACCOUNT.join(ROLE).on(USER_ACCOUNT.ROLE_ID.eq(ROLE.ROLE_ID))
                        .leftJoin(PROFILE).on(USER_ACCOUNT.DEFAULT_PROFILE_ID.eq(PROFILE.PROFILE_ID))
                        .leftJoin(USER_DATA).on(PROFILE.USER_DATA_ID.eq(USER_DATA.USER_DATA_ID)))
                .orderBy(USER_ACCOUNT.ACCOUNT_ID.asc())
                .limit(size)
                .offset((page - 1) * size)
                .fetchMaps();

        var total = context.selectCount()
                .from(USER_ACCOUNT)
                .fetchOptionalInto(Integer.class).orElseThrow(() -> new RuntimeException("Failed to fetch total count"));

        return Map.of("users", users, "total", total);
    }

    public Map<String, Object> loadUserDetail(String role, String userID){
        var selectSentence = switch (role) {
            case "host" -> context.select(USER_ACCOUNT.ACCOUNT_EMAIL, ROLE.ROLE_NAME, PROFILE.asterisk(), USER_DATA.asterisk().except(USER_DATA.USER_DATA_ID))
                    .from(USER_ACCOUNT.join(ROLE).on(USER_ACCOUNT.ROLE_ID.eq(ROLE.ROLE_ID))
                            .leftJoin(PROFILE).on(USER_ACCOUNT.DEFAULT_PROFILE_ID.eq(PROFILE.PROFILE_ID))
                            .leftJoin(USER_DATA).on(PROFILE.USER_DATA_ID.eq(USER_DATA.USER_DATA_ID)));
            case "attendee", "admin" -> context.select(USER_ACCOUNT.ACCOUNT_ID, USER_ACCOUNT.ACCOUNT_EMAIL, ROLE.ROLE_NAME,
                            USER_DATA.asterisk(), PROFILE.PROFILE_IMAGE_URL, PROFILE.NOTIFY_PREFERENCES)
                    .from(USER_ACCOUNT.join(ROLE).on(USER_ACCOUNT.ROLE_ID.eq(ROLE.ROLE_ID))
                            .leftJoin(PROFILE).on(USER_ACCOUNT.DEFAULT_PROFILE_ID.eq(PROFILE.PROFILE_ID))
                            .leftJoin(USER_DATA).on(PROFILE.USER_DATA_ID.eq(USER_DATA.USER_DATA_ID)));
            default -> throw new IllegalArgumentException("Invalid role: " + role);
        };

        return selectSentence
                .where(USER_ACCOUNT.ACCOUNT_ID.eq(UInteger.valueOf(userID)))
                .fetchOneMap();
    }

    public Response deleteUser(String userID) {
        var deleted = context.deleteFrom(USER_ACCOUNT)
                .where(USER_ACCOUNT.ACCOUNT_ID.eq(UInteger.valueOf(userID)))
                .execute();

        if (deleted == 0) {
            return new Response(HttpStatus.NOT_FOUND.name(), "User not found", null);
        }

        return new Response(HttpStatus.OK.name(), "User deleted successfully", null);
    }

    public Map<String, Object> getOverviewMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        try {
            List<Map<String, Object>> usersByCountry = context.select(
                            USER_DATA.NATIONALITY,
                            DSL.count().as("count")
                    )
                    .from(USER_ACCOUNT)
                    .join(PROFILE).on(USER_ACCOUNT.DEFAULT_PROFILE_ID.eq(PROFILE.PROFILE_ID))
                    .join(USER_DATA).on(PROFILE.USER_DATA_ID.eq(USER_DATA.USER_DATA_ID))
                    .where(USER_DATA.NATIONALITY.isNotNull())
                    .groupBy(USER_DATA.NATIONALITY)
                    .orderBy(field("count").desc())
                    .limit(10)
                    .fetchMaps();

            metrics.put("usersByCountry", usersByCountry);

        } catch (Exception e) {
            log.error("Error retrieving admin overview metrics", e);
            metrics.put("error", "Failed to retrieve metrics: " + e.getMessage());
        }

        return metrics;
    }

    public Map<String, Object> getAnalytics(String propertyId, String startDate, String endDate) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> trafficData = new ArrayList<>();

        String analyticsPropertyId = propertyId != null ? propertyId : defaultPropertyId;

        try {
            GoogleCredentials credentials = loadGoogleCredentials();

            BetaAnalyticsDataSettings settings = BetaAnalyticsDataSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                    .build();

            try (BetaAnalyticsDataClient analyticsData = BetaAnalyticsDataClient.create(settings)) {
                RunReportRequest request = RunReportRequest.newBuilder()
                        .setProperty("properties/" + analyticsPropertyId)
                        .addDimensions(Dimension.newBuilder().setName("sessionSource"))
                        .addDimensions(Dimension.newBuilder().setName("date"))
                        .addMetrics(Metric.newBuilder().setName("sessions"))
                        .addMetrics(Metric.newBuilder().setName("activeUsers"))
                        .addDateRanges(DateRange.newBuilder()
                                .setStartDate(startDate != null ? startDate : "30daysAgo")
                                .setEndDate(endDate != null ? endDate : "today"))
                        .build();

                RunReportResponse response = analyticsData.runReport(request);

                Map<String, Map<String, Object>> sourceMap = new HashMap<>();

                for (Row row : response.getRowsList()) {
                    String source = row.getDimensionValues(0).getValue();
                    String date = row.getDimensionValues(1).getValue();
                    int sessions = Integer.parseInt(row.getMetricValues(0).getValue());
                    int users = Integer.parseInt(row.getMetricValues(1).getValue());

                    String category;
                    if (source.isEmpty() || "direct".equalsIgnoreCase(source) || "(direct)".equals(source)) {
                        category = "Direct";
                    } else if ("google".equalsIgnoreCase(source) || source.contains("search") ||
                            "organic".equalsIgnoreCase(source)) {
                        category = "Organic";
                    } else if (source.contains("facebook") || source.contains("instagram") ||
                            source.contains("twitter") || source.contains("linkedin")) {
                        category = "Social";
                    } else if (source.contains("email") || source.contains("newsletter")) {
                        category = "Email";
                    } else {
                        category = "Referral";
                    }

                    Map<String, Object> sourceData = sourceMap.computeIfAbsent(category, k -> {
                        Map<String, Object> data = new HashMap<>();
                        data.put("source", category);
                        data.put("totalSessions", 0);
                        data.put("totalUsers", 0);
                        data.put("dailyData", new TreeMap<String, Map<String, Integer>>());
                        return data;
                    });

                    sourceData.put("totalSessions", (int)sourceData.get("totalSessions") + sessions);
                    sourceData.put("totalUsers", (int)sourceData.get("totalUsers") + users);

                    Map<String, Map<String, Integer>> dailyData = (Map<String, Map<String, Integer>>) sourceData.get("dailyData");
                    Map<String, Integer> dayMetrics = dailyData.computeIfAbsent(date, k -> new HashMap<>());
                    dayMetrics.put("sessions", dayMetrics.getOrDefault("sessions", 0) + sessions);
                    dayMetrics.put("users", dayMetrics.getOrDefault("users", 0) + users);
                }

                for (Map<String, Object> sourceData : sourceMap.values()) {
                    Map<String, Map<String, Integer>> dailyDataMap = (Map<String, Map<String, Integer>>) sourceData.get("dailyData");

                    List<String> dates = new ArrayList<>(dailyDataMap.keySet());
                    List<Integer> sessionsData = new ArrayList<>();
                    List<Integer> usersData = new ArrayList<>();

                    for (String date : dates) {
                        Map<String, Integer> metrics = dailyDataMap.get(date);
                        sessionsData.add(metrics.getOrDefault("sessions", 0));
                        usersData.add(metrics.getOrDefault("users", 0));
                    }

                    sourceData.remove("dailyData");
                    sourceData.put("dates", dates);
                    sourceData.put("sessions", sessionsData);
                    sourceData.put("users", usersData);

                    trafficData.add(sourceData);
                }

                result.put("trafficData", trafficData);
                result.put("startDate", startDate != null ? startDate : "30daysAgo");
                result.put("endDate", endDate != null ? endDate : "today");
                result.put("propertyId", analyticsPropertyId);
            }

        } catch (IOException e) {
            log.error("Error loading Google Analytics credentials", e);
            result.put("error", "Failed to load Google Analytics credentials: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error retrieving Google Analytics data", e);
            result.put("error", "Failed to retrieve analytics data: " + e.getMessage());
        }

        return result;
    }

    private GoogleCredentials loadGoogleCredentials() throws IOException {
        try {
            Resource resource = resourceLoader.getResource(credentialsPath);
            try (InputStream credentialsStream = resource.getInputStream()) {
                return GoogleCredentials.fromStream(credentialsStream)
                        .createScoped(Collections.singletonList("https://www.googleapis.com/auth/analytics.readonly"));
            }
        } catch (IOException e) {
            log.warn("Failed to load Google Analytics credentials from path: {}", credentialsPath);

            String credentialsJson = System.getenv("GOOGLE_ANALYTICS_CREDENTIALS");
            if (credentialsJson != null && !credentialsJson.isEmpty()) {
                try (InputStream jsonStream = new ByteArrayInputStream(
                        credentialsJson.getBytes(StandardCharsets.UTF_8))) {
                    return GoogleCredentials.fromStream(jsonStream)
                            .createScoped(Collections.singletonList("https://www.googleapis.com/auth/analytics.readonly"));
                }
            }

            log.warn("Falling back to Application Default Credentials");
            return GoogleCredentials.getApplicationDefault()
                    .createScoped(Collections.singletonList("https://www.googleapis.com/auth/analytics.readonly"));
        }
    }
}
