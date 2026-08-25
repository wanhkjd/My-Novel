package io.github.xxyopen.novel.crawl.controller;

import io.github.xxyopen.novel.crawl.common.ApiResult;
import io.github.xxyopen.novel.crawl.task.TaskInfo;
import io.github.xxyopen.novel.crawl.task.TaskManager;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 任务查询与取消接口
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TaskController {

    // 任务管理器
    private final TaskManager taskManager;

    /**
     * 查询任务列表（按创建时间倒序）
     *
     * @return 任务列表
     */
    @GetMapping("/tasks")
    public ApiResult<List<TaskInfo>> list() {
        return ApiResult.ok(taskManager.list());
    }

    /**
     * 按ID查询单个任务
     *
     * @param id 任务ID
     * @return 任务信息
     */
    @GetMapping("/tasks/{id}")
    public ApiResult<TaskInfo> get(@PathVariable("id") String id) {
        TaskInfo task = taskManager.get(id);
        if (task == null) {
            return ApiResult.fail("任务不存在：" + id);
        }
        return ApiResult.ok(task);
    }

    /**
     * 请求取消任务（执行中的任务处理完当前章节/批次后停止）
     *
     * @param id 任务ID
     * @return 取消结果说明
     */
    @PostMapping("/tasks/{id}/cancel")
    public ApiResult<String> cancel(@PathVariable("id") String id) {
        boolean accepted = taskManager.cancel(id);
        if (!accepted) {
            return ApiResult.fail("任务不存在或已结束，无法取消");
        }
        return ApiResult.ok("已发出取消请求");
    }

}
