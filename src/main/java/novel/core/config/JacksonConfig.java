package novel.core.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigInteger;

/**
 * Jackson 全局序列化配置
 * 针对 Long 和 BigInteger 进行字符串转换，防止前端 JavaScript 丢失 64 位长整数精度
 * 同时保证 Float、Double、Integer 等基础数值类型不受影响，确保 WebClient / Spring AI 调用大模型等外部 API 时报文格式合法
 *
 * @author xiongxiaoyang / AI
 * @date 2026/08/31
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return builder -> {
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            builder.serializerByType(Long.TYPE, ToStringSerializer.instance);
            builder.serializerByType(BigInteger.class, ToStringSerializer.instance);
        };
    }
}
