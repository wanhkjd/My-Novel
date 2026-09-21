package novel.core.task;

import com.xxl.job.core.biz.model.ReturnT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 真实全量同步小说数据到 Elasticsearch
 */
@SpringBootTest
public class EsRealSyncTest {

    @Autowired
    private BookToEsTask bookToEsTask;

    @Test
    void realSyncAllBooksToEs() {
        // 触发真实同步：从 MySQL 批量读取有字数的小说并 bulk 写入 ES
        ReturnT<String> result = bookToEsTask.saveToEs();
        System.out.println("同步完成，返回状态码: " + result.getCode());
    }
}