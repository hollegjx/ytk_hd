package learn.gjx.tuku1.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import learn.gjx.tuku1.constant.UserConstant;
import learn.gjx.tuku1.controller.UserController;
import learn.gjx.tuku1.exception.GlobalExceptionHandler;
import learn.gjx.tuku1.mapper.UserMapper;
import learn.gjx.tuku1.model.entity.User;
import learn.gjx.tuku1.model.vo.LoginUserVO;
import learn.gjx.tuku1.service.impl.UserServiceImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Runs the real HTTP/session stack against an isolated Redis process.
 * Only database user queries are stubbed; no production users or Redis keys are touched.
 */
@EnabledIfEnvironmentVariable(named = "REDIS_SERVER_BIN", matches = ".+")
class RedisSessionIntegrationTest {

    private static Process redis;
    private static int redisPort;
    private static final ObjectMapper JSON = new ObjectMapper();

    @BeforeAll
    static void startRedis() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            redisPort = socket.getLocalPort();
        }
        Path directory = Files.createTempDirectory("tuku-session-test-");
        redis = new ProcessBuilder(System.getenv("REDIS_SERVER_BIN"),
                "--bind", "127.0.0.1", "--port", Integer.toString(redisPort),
                "--dir", directory.toString(), "--save", "", "--appendonly", "no")
                .redirectErrorStream(true)
                .redirectOutput(directory.resolve("redis.log").toFile())
                .start();
        try {
            await().atMost(Duration.ofSeconds(10)).until(() -> {
                if (!redis.isAlive()) {
                    throw new IllegalStateException("Redis exited; see " + directory.resolve("redis.log"));
                }
                try (Socket ignored = new Socket("127.0.0.1", redisPort)) {
                    return true;
                } catch (IOException e) {
                    return false;
                }
            });
        } catch (RuntimeException e) {
            redis.destroy();
            throw e;
        }
    }

    @AfterAll
    static void stopRedis() throws Exception {
        if (redis != null) {
            redis.destroy();
            if (!redis.waitFor(5, TimeUnit.SECONDS)) {
                redis.destroyForcibly();
                redis.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void loginSurvivesBackendRestartAndLogoutRevokesIt() throws Exception {
        String namespace = namespace();
        String cookie;
        String id;
        try (ServletWebServerApplicationContext first = startApplication(namespace)) {
            ResponseEntity<String> response = login(first, null);
            assertEquals(0, body(response).path("code").asInt());
            String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
            assertNotNull(setCookie);
            assertTrue(setCookie.startsWith("JSESSIONID="));
            assertTrue(setCookie.contains("Path=/api"));
            assertTrue(setCookie.contains("HttpOnly"));
            assertTrue(setCookie.contains("SameSite=Lax"));
            cookie = setCookie.split(";", 2)[0];
            id = decodeId(cookie);
            Session session = repository(first).findById(id);
            assertNotNull(session);
            assertEquals(Duration.ofMinutes(30), session.getMaxInactiveInterval());
            assertInstanceOf(LoginUserVO.class, session.getAttribute(UserConstant.USER_LOGIN_STATE));
        }

        // A new application context and HTTP server must load the session from Redis.
        try (ServletWebServerApplicationContext restarted = startApplication(namespace)) {
            ResponseEntity<String> response = call(restarted, HttpMethod.GET, "/get/login", cookie, null);
            assertEquals(0, body(response).path("code").asInt());
            assertEquals(42L, body(response).path("data").path("id").asLong());
            response = call(restarted, HttpMethod.POST, "/logout", cookie, null);
            assertEquals(0, body(response).path("code").asInt());
            assertTrue(body(response).path("data").asBoolean());
            assertNull(repository(restarted).findById(id));
            assertTrue(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE).contains("Max-Age=0"));
            assertEquals(40100, body(call(restarted, HttpMethod.GET, "/get/login", cookie, null))
                    .path("code").asInt());
        }
    }

    @Test
    void expiredAndAnonymousRequestsDoNotCreateNewSessions() throws Exception {
        try (ServletWebServerApplicationContext app = startApplication(namespace())) {
            ResponseEntity<String> anonymous = call(app, HttpMethod.GET, "/get/login", null, null);
            assertEquals(40100, body(anonymous).path("code").asInt());
            assertNull(anonymous.getHeaders().getFirst(HttpHeaders.SET_COOKIE));

            String cookie = login(app, null).getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
            String id = decodeId(cookie);
            SessionRepository<Session> repository = repository(app);
            Session session = repository.findById(id);
            session.setMaxInactiveInterval(Duration.ofSeconds(1));
            repository.save(session);
            await().atMost(Duration.ofSeconds(6)).until(() -> repository.findById(id) == null);
            assertEquals(40100, body(call(app, HttpMethod.GET, "/get/login", cookie, null))
                    .path("code").asInt());
        }
    }

    @Test
    void reloginRotatesSessionAndRevokesOldCookie() throws Exception {
        try (ServletWebServerApplicationContext app = startApplication(namespace())) {
            String oldCookie = login(app, null).getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
            String newCookie = login(app, oldCookie).getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
            assertNotEquals(oldCookie, newCookie);
            assertNull(repository(app).findById(decodeId(oldCookie)));
            assertEquals(40100, body(call(app, HttpMethod.GET, "/get/login", oldCookie, null))
                    .path("code").asInt());
            assertEquals(0, body(call(app, HttpMethod.GET, "/get/login", newCookie, null))
                    .path("code").asInt());
        }
    }

    private static ServletWebServerApplicationContext startApplication(String namespace) {
        return (ServletWebServerApplicationContext) new SpringApplicationBuilder(SessionTestApplication.class)
                .run("--spring.config.location=classpath:application-session.yml",
                        "--server.port=0", "--server.address=127.0.0.1", "--server.servlet.context-path=/api",
                        "--spring.redis.host=127.0.0.1", "--spring.redis.port=" + redisPort,
                        "--spring.redis.username=", "--spring.redis.password=", "--spring.redis.database=0",
                        "--spring.session.redis.namespace=" + namespace,
                        "--spring.session.timeout=30m", "--spring.main.banner-mode=off",
                        "--logging.level.root=WARN");
    }

    @SuppressWarnings("unchecked")
    private static SessionRepository<Session> repository(ServletWebServerApplicationContext app) {
        return (SessionRepository<Session>) app.getBean(SessionRepository.class);
    }

    private static String namespace() {
        return "tuku1:test:session:" + UUID.randomUUID();
    }

    private static String decodeId(String cookie) {
        return new String(Base64.getDecoder().decode(cookie.substring("JSESSIONID=".length())),
                StandardCharsets.UTF_8);
    }

    private static ResponseEntity<String> login(ServletWebServerApplicationContext app, String cookie) {
        return call(app, HttpMethod.POST, "/login", cookie,
                "{\"userAccount\":\"testuser\",\"userPassword\":\"password123\"}");
    }

    private static ResponseEntity<String> call(ServletWebServerApplicationContext app, HttpMethod method,
                                               String path, String cookie, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (cookie != null) {
            headers.set(HttpHeaders.COOKIE, cookie);
        }
        return new RestTemplate().exchange("http://127.0.0.1:" + app.getWebServer().getPort() + "/api/user" + path,
                method, new HttpEntity<>(body, headers), String.class);
    }

    private static JsonNode body(ResponseEntity<String> response) throws IOException {
        return JSON.readTree(response.getBody());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import({UserController.class, GlobalExceptionHandler.class})
    static class SessionTestApplication {
        @Bean
        UserMapper userMapper() {
            return mock(UserMapper.class);
        }

        @Bean
        UserServiceImpl userService() {
            User user = new User();
            user.setId(42L);
            user.setUserAccount("testuser");
            user.setUserName("Session test user");
            user.setUserRole(0);
            user.setIsDelete(0);
            UserServiceImpl service = spy(new UserServiceImpl());
            doReturn(user).when(service).getOne(any());
            doReturn(user).when(service).getById(42L);
            return service;
        }
    }
}
