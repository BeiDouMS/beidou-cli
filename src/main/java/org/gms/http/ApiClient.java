package org.gms.http;

import org.gms.auth.AuthManager;
import org.gms.config.CliConfig;
import org.gms.model.SubmitBody;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.gms.util.JsonUtils.*;

public class ApiClient {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final int MAX_LOGIN_ATTEMPTS = 3;

    private final CliConfig config;
    private final AuthManager authManager;

    public ApiClient(CliConfig config) {
        this.config = config;
        this.authManager = new AuthManager(config);
    }

    public void call(String method, String path, String bodyJson) {
        // /auth/** 路径免认证
        var needAuth = !path.startsWith("/auth/");
        var token = needAuth ? authManager.ensureToken() : null;
        var loginAttempts = 1;

        while (true) {
            try {
                var response = sendRequest(method, path, bodyJson, token);
                var responseBody = response.body();

                if (response.statusCode() == 401 && needAuth) {
                    if (loginAttempts >= MAX_LOGIN_ATTEMPTS) {
                        System.err.println("认证失败: 已重试 " + MAX_LOGIN_ATTEMPTS + " 次");
                        System.err.println("服务端响应: " + responseBody);
                        System.exit(1);
                    }
                    authManager.clearToken();
                    token = authManager.forceLogin();
                    loginAttempts++;
                    continue;
                }

                // 输出结果
                System.out.println(toPrettyJson(readTree(responseBody)));
                return;
            } catch (Exception e) {
                System.err.println("请求失败: " + e.getMessage());
                System.exit(1);
            }
        }
    }

    /** 给 batch 用的版本，不 System.exit，失败时抛异常 */
    public void callForBatch(String method, String path, String bodyJson) {
        var needAuth = !path.startsWith("/auth/");
        var token = needAuth ? authManager.ensureToken() : null;
        var loginAttempts = 1;

        while (true) {
            try {
                var response = sendRequest(method, path, bodyJson, token);
                var responseBody = response.body();

                if (response.statusCode() == 401 && needAuth) {
                    if (loginAttempts >= MAX_LOGIN_ATTEMPTS) {
                        throw new RuntimeException("认证失败: 已重试 " + MAX_LOGIN_ATTEMPTS + " 次, body=" + responseBody);
                    }
                    authManager.clearToken();
                    token = authManager.forceLogin();
                    loginAttempts++;
                    continue;
                }

                System.out.println(toPrettyJson(readTree(responseBody)));
                return;
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(method + " " + path + " 请求异常: " + e.getMessage(), e);
            }
        }
    }

    private HttpResponse<String> sendRequest(String method, String path, String bodyJson, String token)
            throws IOException, InterruptedException {
        var uri = URI.create(config.getServer() + path);
        var builder = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + token);

        if (bodyJson != null && !bodyJson.isBlank()) {
            // 包装到 SubmitBody.data
            String wrapped;
            try {
                var dataNode = readTree(bodyJson);
                wrapped = toJson(new SubmitBody(dataNode));
            } catch (Exception e) {
                // 非 JSON 时原样放入 data
                wrapped = toJson(new SubmitBody(bodyJson));
            }
            var bodyPublisher = HttpRequest.BodyPublishers.ofString(wrapped);
            builder.header("Content-Type", "application/json");
            switch (method.toUpperCase()) {
                case "POST" -> builder.POST(bodyPublisher);
                case "PUT" -> builder.PUT(bodyPublisher);
                case "PATCH" -> builder.method("PATCH", bodyPublisher);
                case "DELETE" -> builder.method("DELETE", bodyPublisher);
                default -> builder.method(method.toUpperCase(), bodyPublisher);
            }
        } else {
            if ("GET".equalsIgnoreCase(method)) {
                builder.GET();
            } else if ("DELETE".equalsIgnoreCase(method)) {
                builder.DELETE();
            } else {
                builder.POST(HttpRequest.BodyPublishers.noBody());
            }
        }

        builder.timeout(Duration.ofSeconds(30));
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
