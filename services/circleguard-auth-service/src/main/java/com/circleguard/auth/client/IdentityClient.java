package com.circleguard.auth.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@Component
public class IdentityClient {
    // In a real microservice, this would use Feign or WebClient
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String IDENTITY_MAP_PATH = "/api/v1/identities/map";

    @Value("${identity.url}")
    private String identityUrl;

    public UUID getAnonymousId(String realIdentity) {
        Map<String, String> request = Map.of("realIdentity", realIdentity);
        Map response = restTemplate.postForObject(identityUrl + IDENTITY_MAP_PATH, request, Map.class);
        return UUID.fromString(response.get("anonymousId").toString());
    }
}
