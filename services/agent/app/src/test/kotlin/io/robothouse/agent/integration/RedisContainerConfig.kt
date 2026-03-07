package io.robothouse.agent.integration

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistrar
import org.testcontainers.containers.GenericContainer

@TestConfiguration(proxyBeanMethods = false)
class RedisContainerConfig {

    @Bean
    fun redisContainer(): GenericContainer<*> =
        GenericContainer("redis:7-alpine")
            .withExposedPorts(6379)

    @Bean
    fun redisProperties(@Qualifier("redisContainer") redis: GenericContainer<*>): DynamicPropertyRegistrar =
        DynamicPropertyRegistrar { registry ->
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.firstMappedPort }
        }
}
