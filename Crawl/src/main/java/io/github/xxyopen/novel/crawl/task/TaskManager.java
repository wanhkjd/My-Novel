package io.github.xxyopen.novel.crawl.task;

import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务管理器：在内存中登记所有爬取/导入任务，供前台查询进度与取消
 */
@Component
public class TaskManager {

    // 任务ID格式化用的时间格式
    private static final DateTimeFormatter ID_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    // 任务列表最多返回条数
    private static final int MAX_LIST_SIZE = 100;

    // 全部任务表：任务ID -> 任务信息
    private final Map<String, TaskInfo> tasks = new ConcurrentHashMap<>();

    /**
     * 创建并登记一个新任务
     *
     * @param type     任务类型（CRAWL / TXT）
     * @param bookName 书名（可先传文件名/URL，解析后更新）
     * @return 新建的任务信息
     */
    public TaskInfo create(String type, String bookName) {
        // 任务表超过上限时先清理最旧的已结束任务，防止长期运行内存无限增长
        evictIfNeeded();
        TaskInfo task = new TaskInfo();
        // 任务ID = 时间串 + 随机串，方便肉眼区分创建先后
        task.setId(task.getCreateTime().format(ID_TIME_FORMAT) + "-"
                + UUID.randomUUID().toString().substring(0, 8));
        task.setType(type);
        task.setBookName(bookName);
        tasks.put(task.getId(), task);
        return task;
    }

    /**
     * 按ID查询任务
     *
     * @param id 任务ID
     * @return 任务信息，不存在返回 null
     */
    public TaskInfo get(String id) {
        return tasks.get(id);
    }

    /**
     * 查询任务列表（按创建时间倒序，最多返回 100 条）
     *
     * @return 任务列表
     */
    public List<TaskInfo> list() {
        return tasks.values().stream()
                .sorted(Comparator.comparing(TaskInfo::getCreateTime).reversed())
                .limit(MAX_LIST_SIZE)
                .toList();
    }

    /**
     * 任务表超过 2 倍列表上限时，清理最旧的已结束任务
     */
    private void evictIfNeeded() {
        if (tasks.size() < MAX_LIST_SIZE * 2) {
            return;
        }
        // 只清理已结束的任务，按创建时间从旧到新删
        tasks.values().stream()
                .filter(t -> !TaskInfo.STATUS_RUNNING.equals(t.getStatus()))
                .sorted(Comparator.comparing(TaskInfo::getCreateTime))
                .limit(Math.max(0, tasks.size() - MAX_LIST_SIZE))
                .forEach(t -> tasks.remove(t.getId()));
    }

    /**
     * 请求取消任务（执行中的任务会在处理完当前章节后停止）
     *
     * @param id 任务ID
     * @return true-已发出取消请求 false-任务不存在或已结束
     */
    public boolean cancel(String id) {
        TaskInfo task = tasks.get(id);
        // 只有执行中的任务才能取消
        if (task == null || !TaskInfo.STATUS_RUNNING.equals(task.getStatus())) {
            return false;
        }
        task.setCancelRequested(true);
        return true;
    }

}
