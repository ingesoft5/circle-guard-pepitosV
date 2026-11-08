package com.circleguard.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Hashtable;
import java.util.List;
import java.util.UUID;
import javax.naming.Context;
import javax.naming.NameAlreadyBoundException;
import javax.naming.NamingException;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * A directory user authenticates against OpenLDAP and receives a token that carries the LDAP role.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class LdapLoginIntegrationTest {

    private static final String BASE_DN = "dc=circleguard,dc=edu";
    private static final String USER_DN = "uid=ana,ou=people," + BASE_DN;
    private static final String JWT_SECRET = "my-super-secret-dev-key-32-chars-long-12345678";
    private static final UUID ANONYMOUS_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440042");

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final GenericContainer<?> openLdap = new GenericContainer<>("osixia/openldap:1.5.0")
            .withEnv("LDAP_ORGANISATION", "CircleGuard")
            .withEnv("LDAP_DOMAIN", "circleguard.edu")
            .withEnv("LDAP_ADMIN_PASSWORD", "admin")
            .withEnv("LDAP_TLS", "false")
            .withExposedPorts(389)
            .waitingFor(Wait.forLogMessage(".*slapd starting.*", 1).withStartupTimeout(Duration.ofMinutes(3)));

    private static HttpServer identityServer;

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        startIdentity();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.ldap.urls", LdapLoginIntegrationTest::ldapUrl);
        registry.add("spring.ldap.base", () -> BASE_DN);
        registry.add("spring.ldap.username", () -> "cn=admin," + BASE_DN);
        registry.add("spring.ldap.password", () -> "admin");
        registry.add("identity.url", () -> "http://127.0.0.1:" + identityServer.getAddress().getPort());
    }

    @BeforeAll
    static void seedDirectory() throws Exception {
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= 10; attempt++) {
            try {
                seedOnce();
                return;
            } catch (Exception failure) {
                lastFailure = failure;
                Thread.sleep(1000);
            }
        }
        throw lastFailure;
    }

    @AfterAll
    static void stopIdentity() {
        if (identityServer != null) {
            identityServer.stop(0);
        }
    }

    @Test
    void validLdapUserReceivesATokenWithRoles() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ana\",\"password\":\"secret\"}"))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .withFailMessage(result.getResponse().getContentAsString())
                .isEqualTo(200);

        String token = JsonMapper.builder()
                .build()
                .readTree(result.getResponse().getContentAsString())
                .get("token")
                .asText();
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertThat(claims.getSubject()).isEqualTo(ANONYMOUS_ID.toString());
        assertThat(claims.get("permissions", List.class)).contains("ROLE_STUDENT");
    }

    private static void seedOnce() throws NamingException {
        Hashtable<String, String> environment = new Hashtable<>();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        environment.put(Context.PROVIDER_URL, ldapUrl());
        environment.put(Context.SECURITY_AUTHENTICATION, "simple");
        environment.put(Context.SECURITY_PRINCIPAL, "cn=admin," + BASE_DN);
        environment.put(Context.SECURITY_CREDENTIALS, "admin");

        DirContext directory = new InitialDirContext(environment);
        try {
            create(directory, "ou=people," + BASE_DN, organizationalUnit("people"));
            create(directory, "ou=groups," + BASE_DN, organizationalUnit("groups"));
            create(directory, USER_DN, user());
            create(directory, "cn=STUDENT,ou=groups," + BASE_DN, studentGroup());
        } finally {
            directory.close();
        }
    }

    private static void create(DirContext directory, String dn, BasicAttributes attributes) throws NamingException {
        try {
            directory.createSubcontext(dn, attributes);
        } catch (NameAlreadyBoundException alreadyPresent) {
            // A previous attempt created this entry while slapd was still opening.
        }
    }

    private static void startIdentity() {
        if (identityServer != null) {
            return;
        }
        try {
            identityServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        identityServer.createContext("/api/v1/identities/map", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = ("{\"anonymousId\":\"" + ANONYMOUS_ID + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        identityServer.start();
    }

    private static String ldapUrl() {
        return "ldap://" + openLdap.getHost() + ":" + openLdap.getMappedPort(389);
    }

    private static BasicAttributes organizationalUnit(String name) {
        BasicAttributes attributes = new BasicAttributes(true);
        BasicAttribute objectClass = new BasicAttribute("objectClass");
        objectClass.add("top");
        objectClass.add("organizationalUnit");
        attributes.put(objectClass);
        attributes.put("ou", name);
        return attributes;
    }

    private static BasicAttributes user() {
        BasicAttributes attributes = new BasicAttributes(true);
        BasicAttribute objectClass = new BasicAttribute("objectClass");
        objectClass.add("top");
        objectClass.add("person");
        objectClass.add("organizationalPerson");
        objectClass.add("inetOrgPerson");
        attributes.put(objectClass);
        attributes.put("uid", "ana");
        attributes.put("cn", "Ana Perez");
        attributes.put("sn", "Perez");
        attributes.put("userPassword", "secret".getBytes(StandardCharsets.UTF_8));
        return attributes;
    }

    private static BasicAttributes studentGroup() {
        BasicAttributes attributes = new BasicAttributes(true);
        BasicAttribute objectClass = new BasicAttribute("objectClass");
        objectClass.add("top");
        objectClass.add("groupOfNames");
        attributes.put(objectClass);
        attributes.put("cn", "STUDENT");
        attributes.put("member", USER_DN);
        return attributes;
    }
}
