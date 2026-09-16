package com.curio.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisConfig {

    /**
     * JSON value serializer with a type ALLOWLIST instead of Jackson's default
     * "trust any @class in the payload" polymorphic typing.
     *
     * <p>The stock {@code new GenericJackson2JsonRedisSerializer()} instantiates
     * whatever class the stored JSON names — so anyone who can write to Redis (a
     * leaked password, an exposed port, SSRF) can plant a gadget-chain payload
     * under a cache key and get code execution on the next cache read. We only
     * ever cache our own DTOs inside standard collections, so restrict
     * deserialization to exactly that surface.
     *
     * <p>Wire-format caveat: this is NOT byte-identical to the stock serializer —
     * it types {@code NON_FINAL} (the stock one types EVERYTHING) and registers
     * {@code JavaTimeModule} (the stock one doesn't). All currently cached shapes
     * (non-final DTOs of Strings inside {@code java.util} collections/maps) read
     * back fine either way, and the 12h TTL self-heals any stragglers. Do NOT
     * cache a record, enum, or {@code java.time} field through this template
     * without re-checking round-tripping and the allowlist below.
     */
    private GenericJackson2JsonRedisSerializer allowlistedJsonSerializer() {
        BasicPolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.curio.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .build();
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.activateDefaultTyping(typeValidator, ObjectMapper.DefaultTyping.NON_FINAL,
                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        GenericJackson2JsonRedisSerializer valueSerializer = allowlistedJsonSerializer();
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(valueSerializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(valueSerializer);

        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(allowlistedJsonSerializer()))
                .entryTtl(Duration.ofMinutes(5));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .build();
    }
}
