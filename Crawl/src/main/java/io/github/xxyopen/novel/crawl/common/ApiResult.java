package io.github.xxyopen.novel.crawl.common;

import lombok.Data;

/**
 * 统一的接口返回结构
 *
 * @param <T> 返回数据的类型
 */
@Data
public class ApiResult<T> {

    // 状态码：0-成功 1-失败
    private Integer code;

    // 提示信息
    private String message;

    // 返回数据
    private T data;

    /**
     * 构造成功结果
     *
     * @param data 返回数据
     * @param <T>  返回数据的类型
     * @return 成功的返回结构
     */
    public static <T> ApiResult<T> ok(T data) {
        ApiResult<T> result = new ApiResult<>();
        result.setCode(0);
        result.setMessage("成功");
        result.setData(data);
        return result;
    }

    /**
     * 构造失败结果
     *
     * @param message 失败原因
     * @param <T>     返回数据的类型
     * @return 失败的返回结构
     */
    public static <T> ApiResult<T> fail(String message) {
        ApiResult<T> result = new ApiResult<>();
        result.setCode(1);
        result.setMessage(message);
        return result;
    }

}
