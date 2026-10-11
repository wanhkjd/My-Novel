package novel.dao.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import novel.core.annotation.ValidateSortOrder;
import novel.dao.entity.BookInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import novel.dto.req.BookSearchReqDto;
import novel.dto.resp.BookInfoRespDto;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 * 小说信息 Mapper 接口
 * </p>
 *
 * @author xiongxiaoyang
 * @date 2022/05/11
 */
public interface BookInfoMapper extends BaseMapper<BookInfo> {

    /** 章节写入的统一锁；必须在事务中调用，锁持有到提交或回滚。 */
    @Select("SELECT * FROM book_info WHERE id = #{id} FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    BookInfo selectByIdForUpdate(@Param("id") Long id);

    /**
     * 增加小说点击量
     *
     * @param bookId 小说ID
     */
    void addVisitCount(@Param("bookId") Long bookId);

    /**
     * 小说搜索
     * @param page mybatis-plus 分页对象
     * @param condition 搜索条件
     * @return 返回结果
     * */
    List<BookInfo> searchBooks(IPage<BookInfoRespDto> page, @ValidateSortOrder BookSearchReqDto condition);

}
