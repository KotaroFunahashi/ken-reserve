package com.coha9nus.kenreserve;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    // Singleton Container パターン: JVM 内で 1 インスタンスのみ起動
    static final MySQLContainer MYSQL_CONTAINER =
            new MySQLContainer(DockerImageName.parse("mysql:8.0"));

    static {
        MYSQL_CONTAINER.start();
    }

    @Bean
    @ServiceConnection
    MySQLContainer mysqlContainer() {
        return MYSQL_CONTAINER;
    }
}
