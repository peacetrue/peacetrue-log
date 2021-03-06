package com.github.peacetrue.log.type;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.peacetrue.jackson.ObjectMapperWrapper;
import com.github.peacetrue.serialize.SerializeService;
import com.github.peacetrue.spring.web.method.support.ConcreteClassAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * @author xiayx
 */
@Configuration
@EnableConfigurationProperties(TypeLogProperties.class)
@PropertySource("classpath:peacetrue-log-type.properties")
public class TypeLogAutoConfiguration {

    @ConditionalOnClass(ConcreteClassAutoConfiguration.class)
    @AutoConfigureBefore(ConcreteClassAutoConfiguration.class)
    public static class Controller {

    }

    @ConditionalOnClass({SerializeService.class, ObjectMapperWrapper.class})
    public static class Service {

        @Autowired
        private TypeLogProperties properties;

        @Bean
        @ConditionalOnMissingBean(ObjectMapper.class)
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        @ConditionalOnMissingBean(ObjectMapperWrapper.class)
        public ObjectMapperWrapper objectMapperWrapper(ObjectMapper objectMapper) {
            return new ObjectMapperWrapper(objectMapper);
        }

        @Bean
        public SerializeService<String> inputSerializeService(ObjectMapperWrapper objectMapperWrapper) {
            return new LimitSerializeService(objectMapperWrapper, properties.getLengths().getInput());
        }

        @Bean
        public SerializeService<String> outputSerializeService(ObjectMapperWrapper objectMapperWrapper) {
            return new LimitSerializeService(objectMapperWrapper, properties.getLengths().getOutput());
        }

        @Bean
        public SerializeService<String> exceptionSerializeService(ObjectMapperWrapper objectMapperWrapper) {
            return new LimitSerializeService(objectMapperWrapper, properties.getLengths().getException());
        }
    }


}
