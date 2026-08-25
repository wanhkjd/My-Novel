package io.github.xxyopen.novel.crawl.controller;

import io.github.xxyopen.novel.crawl.common.ApiResult;
import io.github.xxyopen.novel.crawl.dto.TxtImportRequest;
import io.github.xxyopen.novel.crawl.service.TxtImportService;
import io.github.xxyopen.novel.crawl.task.TaskInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * TXT 导入接口
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TxtImportController {

    // TXT 导入服务
    private final TxtImportService txtImportService;

    /**
     * 上传 TXT 文件并提交导入任务
     *
     * @param file    TXT 文件（表单字段名 file）
     * @param request 导入参数（书名、作者、类别等，均可空）
     * @return 新建的任务信息（含任务ID，用于轮询进度）
     * @throws IOException 读取上传文件失败时抛出
     */
    @PostMapping("/txt/import")
    public ApiResult<TaskInfo> importTxt(@RequestParam("file") MultipartFile file, TxtImportRequest request)
            throws IOException {
        if (file == null || file.isEmpty()) {
            return ApiResult.fail("请选择要导入的 TXT 文件");
        }
        // 先在请求线程里读出文件字节，再交给后台任务（临时文件在请求结束后会被清理）
        return ApiResult.ok(txtImportService.submit(file.getBytes(), file.getOriginalFilename(), request));
    }

}
