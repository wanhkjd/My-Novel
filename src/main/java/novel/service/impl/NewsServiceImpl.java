package novel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import novel.core.common.resp.RestResp;
import novel.core.constant.DatabaseConsts;
import novel.dao.entity.NewsContent;
import novel.dao.entity.NewsInfo;
import novel.dao.mapper.NewsContentMapper;
import novel.dao.mapper.NewsInfoMapper;
import novel.dto.resp.NewsInfoRespDto;
import novel.manager.cache.NewsCacheManager;
import novel.service.NewsService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 新闻模块 服务实现类
 *
 * @author xiongxiaoyang
 * @date 2022/5/14
 */
@Service
@RequiredArgsConstructor
public class NewsServiceImpl implements NewsService {

    private final NewsCacheManager newsCacheManager;

    private final NewsInfoMapper newsInfoMapper;

    private final NewsContentMapper newsContentMapper;

    @Override
    public RestResp<List<NewsInfoRespDto>> listLatestNews() {
        return RestResp.ok(newsCacheManager.listLatestNews());
    }

    @Override
    public RestResp<NewsInfoRespDto> getNews(Long id) {
        NewsInfo newsInfo = newsInfoMapper.selectById(id);
        QueryWrapper<NewsContent> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.NewsContentTable.COLUMN_NEWS_ID, id)
            .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
        NewsContent newsContent = newsContentMapper.selectOne(queryWrapper);
        return RestResp.ok(NewsInfoRespDto.builder()
            .title(newsInfo.getTitle())
            .sourceName(newsInfo.getSourceName())
            .updateTime(newsInfo.getUpdateTime())
            .content(newsContent.getContent())
            .build());
    }
}
