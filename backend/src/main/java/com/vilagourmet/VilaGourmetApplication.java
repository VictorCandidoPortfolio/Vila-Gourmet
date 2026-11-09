package com.vilagourmet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class VilaGourmetApplication {

    public static void main(String[] args) {
        SpringApplication.run(VilaGourmetApplication.class, args);
    }
}
