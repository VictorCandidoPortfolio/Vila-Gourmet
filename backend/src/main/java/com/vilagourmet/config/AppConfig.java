package com.vilagourmet.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;

/** Page é serializada como PagedModel: { content: [...], page: { size, number, totalElements, totalPages } }. */
@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {
}
