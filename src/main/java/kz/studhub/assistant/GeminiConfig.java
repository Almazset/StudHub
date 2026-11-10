package kz.studhub.assistant;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class GeminiConfig {
    @Bean("geminiRestClient") RestClient geminiRestClient() {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(client); factory.setReadTimeout(Duration.ofSeconds(80));
        return RestClient.builder().baseUrl("https://generativelanguage.googleapis.com/v1beta/").requestFactory(factory).build();
    }
}
