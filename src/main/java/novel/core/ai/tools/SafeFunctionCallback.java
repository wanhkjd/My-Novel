package novel.core.ai.tools;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.FunctionCallback;

import java.util.function.Function;

/**
 * 健壮安全的 Spring AI 工具回调包装器
 * 解决大模型在流式输出（SSE）模式下调用工具时传递空字符串 "" 导致的 Jackson MismatchedInputException 异常
 *
 * @param <I> 工具请求参数类型
 * @param <O> 工具响应类型
 * @author xiongxiaoyang / AI
 * @date 2026/08/31
 */
@Slf4j
public class SafeFunctionCallback<I, O> implements FunctionCallback {

    private final String name;
    private final String description;
    private final Class<I> inputType;
    private final Function<I, O> function;
    private final ObjectMapper objectMapper;
    private final String schema;

    public SafeFunctionCallback(String name, String description, Class<I> inputType, Function<I, O> function) {
        this.name = name;
        this.description = description;
        this.inputType = inputType;
        this.function = function;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.schema = ModelOptionsUtils.getJsonSchema(inputType, false);
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    @Override
    public String getInputTypeSchema() {
        return this.schema;
    }

    @Override
    public String call(String functionArguments) {
        log.info("[AI Tool Callback] 工具 [{}] 收到调用入参报文: '{}'", this.name, functionArguments);
        I request = null;
        if (functionArguments != null && !functionArguments.isBlank()) {
            String trimmed = functionArguments.trim();
            if (!trimmed.isEmpty() && !trimmed.equals("{}")) {
                try {
                    request = this.objectMapper.readValue(trimmed, this.inputType);
                } catch (Exception e) {
                    log.warn("[AI Tool Callback] 工具 [{}] 入参反序列化失败，将使用默认参数。入参内容: '{}'", this.name, functionArguments);
                }
            }
        }

        O response = this.function.apply(request);
        if (response instanceof String strResp) {
            return strResp;
        }

        try {
            return this.objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            return String.valueOf(response);
        }
    }
}
