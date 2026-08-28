package novel.core.task;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.xxl.job.core.biz.model.ReturnT;
import novel.dao.mapper.BookInfoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 同步 MySQL 小说数据到 Elasticsearch 单元测试类
 */
@ExtendWith(MockitoExtension.class)
public class BookToEsTaskTest {

    @Mock
    private BookInfoMapper bookInfoMapper;

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @InjectMocks
    private BookToEsTask bookToEsTask;

    @Test
    void testSyncAllBooksToEs() {
        when(bookInfoMapper.selectList(any())).thenReturn(Collections.emptyList());
        ReturnT<String> result = bookToEsTask.saveToEs();
        assertThat(result).isEqualTo(ReturnT.SUCCESS);
    }
}
