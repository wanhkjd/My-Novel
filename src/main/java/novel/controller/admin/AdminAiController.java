package novel.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import novel.core.common.resp.RestResp;
import novel.core.constant.ApiRouterConsts;
import novel.service.BookCompanionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** AI maintenance operations use the administrator token namespace, not reader authentication. */
@Tag(name = "AdminAiController", description = "平台管理后台-AI维护")
@RestController
@RequestMapping(ApiRouterConsts.API_ADMIN_URL_PREFIX + "/ai")
@RequiredArgsConstructor
public class AdminAiController {

    private final BookCompanionService bookCompanionService;

    @Operation(summary = "管理员触发全量书籍向量同步")
    @PostMapping("/sync-vectors")
    public RestResp<Integer> syncAllBookVectors() {
        return bookCompanionService.syncAllBookVectors();
    }
}
