package com.urlshortener.url;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.urlshortener.AbstractIntegrationTest;
import com.urlshortener.user.Role;
import com.urlshortener.user.User;
import com.urlshortener.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@AutoConfigureMockMvc
@Transactional
class RedirectControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = new User("redirect-owner@example.com", passwordEncoder.encode("Password1!"), Role.USER);
        userRepository.save(owner);
    }

    @Test
    void redirect_returns302_andLocationHeader_forActiveUrl() throws Exception {
        ShortUrl url = new ShortUrl("abc1234", "https://example.com", owner,
                Instant.now().plusSeconds(3600), false);
        shortUrlRepository.save(url);

        mockMvc.perform(get("/r/abc1234"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com"));
    }

    @Test
    void redirect_returns404_forUnknownCode() throws Exception {
        mockMvc.perform(get("/r/unknown9"))
                .andExpect(status().isNotFound());
    }

    @Test
    void redirect_returns410_forInactiveUrl() throws Exception {
        ShortUrl url = new ShortUrl("inactive1", "https://example.com", owner,
                Instant.now().plusSeconds(3600), false);
        url.deactivate();
        shortUrlRepository.save(url);

        mockMvc.perform(get("/r/inactive1"))
                .andExpect(status().isGone());
    }

    @Test
    void redirect_returns410_forExpiredUrl() throws Exception {
        ShortUrl url = new ShortUrl("expired1", "https://example.com", owner,
                Instant.now().minusSeconds(1), false);
        url.markExpired();
        shortUrlRepository.save(url);

        mockMvc.perform(get("/r/expired1"))
                .andExpect(status().isGone());
    }

    @Test
    void redirect_returns400_forInvalidShortCode() throws Exception {
        mockMvc.perform(get("/r/invalid code!"))
                .andExpect(status().isBadRequest());
    }
}
