package io.github.xxyopen.novel.crawl.task;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 后台任务信息（爬取任务 / TXT 导入任务的进度与结果）
 */
@Data
public class TaskInfo {

    // 任务状态常量：执行中
    public static final String STATUS_RUNNING = "RUNNING";

    // 任务状态常量：成功
    public static final String STATUS_SUCCESS = "SUCCESS";

    // 任务状态常量：失败
    public static final String STATUS_FAILED = "FAILED";

    // 任务状态常量：已取消
    public static final String STATUS_CANCELLED = "CANCELLED";

    // 任务ID
    private String id;

    // 任务类型：CRAWL-爬取 TXT-文本导入
    private String type;

    // 书名（解析出书籍信息后回填）
    private volatile String bookName;

    // 小说ID（book_info.id，入库后回填；用字符串避免前端 JS 精度丢失）
    private volatile String bookId;

    // 任务状态：RUNNING / SUCCESS / FAILED / CANCELLED
    private volatile String status = STATUS_RUNNING;

    // 本次需处理的章节总数
    private volatile int total;

    // 已处理章节数
    private AtomicInteger done = new AtomicInteger(0);

    // 本次新增入库的章节数
    private AtomicInteger saved = new AtomicInteger(0);

    // 结果说明 / 错误信息
    private volatile String message = "";

    // 创建时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime = LocalDateTime.now();

    // 结束时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private volatile LocalDateTime endTime;

    // 是否收到取消请求（不参与 JSON 序列化）
    @JsonIgnore
    private volatile boolean cancelRequested = false;

    /**
     * 标记任务成功结束
     *
     * @param message 结果说明
     */
    public void markSuccess(String message) {
        this.status = STATUS_SUCCESS;
        this.message = message;
        this.endTime = LocalDateTime.now();
    }

    /**
     * 标记任务失败结束
     *
     * @param message 失败原因
     */
    public void markFailed(String message) {
        this.status = STATUS_FAILED;
        this.message = message;
        this.endTime = LocalDateTime.now();
    }

    /**
     * 标记任务被取消
     *
     * @param message 取消说明
     */
    public void markCancelled(String message) {
        this.status = STATUS_CANCELLED;
        this.message = message;
        this.endTime = LocalDateTime.now();
    }

}
