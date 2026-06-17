package com.smartparking.reservation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class RedisConfig {

    /**
     * Atomic compare-and-delete Lua script.
     * Releases the lock only if the caller still owns it (value matches).
     * Prevents releasing a lock acquired by a different caller after TTL expiry.
     */
    @Bean
    public RedisScript<Long> releaseLockScript() {
        String script = """
                if redis.call('get', KEYS[1]) == ARGV[1]
                  then return redis.call('del', KEYS[1])
                  else return 0
                end
                """;
        return RedisScript.of(script, Long.class);
    }
}
