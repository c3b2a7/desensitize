package me.lolico.desensitize.mybatis;

import com.baomidou.mybatisplus.spring.boot.starter.MybatisPlusAutoConfiguration;
import org.apache.ibatis.plugin.Interceptor;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MybatisInterceptorProperties.class)
@ConditionalOnClass({DesensitizeInterceptor.class, Interceptor.class})
@Conditional(MybatisInterceptorAutoConfiguration.MybatisVersionCondition.class)
@AutoConfigureBefore(DataSourceAutoConfiguration.class)
public class MybatisInterceptorAutoConfiguration {

    private final MybatisInterceptorProperties properties;

    public MybatisInterceptorAutoConfiguration(MybatisInterceptorProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean
    public DesensitizeInterceptor desensitizeInterceptor() {
        DesensitizeInterceptor interceptor = new DesensitizeInterceptor();
        interceptor.setProperties(properties.getProperties());
        return interceptor;
    }

    static class MybatisVersionCondition extends AnyNestedCondition {
        public MybatisVersionCondition() {
            super(ConfigurationPhase.PARSE_CONFIGURATION);
        }

        @ConditionalOnClass(MybatisAutoConfiguration.class)
        static class Mybatis {
        }

        @ConditionalOnClass(MybatisPlusAutoConfiguration.class)
        static class MybatisPlus {
        }
    }
}
