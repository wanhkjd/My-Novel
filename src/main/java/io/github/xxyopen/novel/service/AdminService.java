package io.github.xxyopen.novel.service;

import io.github.xxyopen.novel.core.common.req.PageReqDto;
import io.github.xxyopen.novel.core.common.resp.PageRespDto;
import io.github.xxyopen.novel.core.common.resp.RestResp;
import io.github.xxyopen.novel.dto.req.AdminAuditReqDto;
import io.github.xxyopen.novel.dto.req.AdminBookCategoryReqDto;
import io.github.xxyopen.novel.dto.req.AdminFriendLinkReqDto;
import io.github.xxyopen.novel.dto.req.AdminHomeBookReqDto;
import io.github.xxyopen.novel.dto.req.AdminLoginReqDto;
import io.github.xxyopen.novel.dto.req.AdminNewsReqDto;
import io.github.xxyopen.novel.dto.req.AdminStatusReqDto;
import io.github.xxyopen.novel.dto.resp.AdminLoginRespDto;
import java.util.List;
import java.util.Map;

public interface AdminService {

    RestResp<AdminLoginRespDto> login(AdminLoginReqDto dto);

    RestResp<Map<String, Object>> info(Long adminId);

    RestResp<List<Map<String, Object>>> menus();

    RestResp<Map<String, Object>> dashboard();

    RestResp<PageRespDto<Map<String, Object>>> listUsers(PageReqDto pageReqDto, String keyword, Integer status);

    RestResp<Map<String, Object>> getUser(Long id);

    RestResp<Void> updateUserStatus(Long id, AdminStatusReqDto dto);

    RestResp<PageRespDto<Map<String, Object>>> listBooks(PageReqDto pageReqDto, String keyword, Integer status);

    RestResp<Void> updateBookStatus(Long id, AdminStatusReqDto dto);

    RestResp<PageRespDto<Map<String, Object>>> listCategories(PageReqDto pageReqDto, String keyword);

    RestResp<Void> saveCategory(AdminBookCategoryReqDto dto);

    RestResp<Void> updateCategory(Long id, AdminBookCategoryReqDto dto);

    RestResp<Void> deleteCategory(Long id);

    RestResp<PageRespDto<Map<String, Object>>> listComments(PageReqDto pageReqDto, String keyword, Integer auditStatus);

    RestResp<Void> auditComment(Long id, AdminAuditReqDto dto);

    RestResp<Void> deleteComment(Long id);

    RestResp<PageRespDto<Map<String, Object>>> listHomeBooks(PageReqDto pageReqDto);

    RestResp<Void> saveHomeBook(AdminHomeBookReqDto dto);

    RestResp<Void> updateHomeBook(Long id, AdminHomeBookReqDto dto);

    RestResp<Void> deleteHomeBook(Long id);

    RestResp<PageRespDto<Map<String, Object>>> listAuthors(PageReqDto pageReqDto, String keyword, Integer status);

    RestResp<Map<String, Object>> getAuthor(Long id);

    RestResp<Void> updateAuthorStatus(Long id, AdminStatusReqDto dto);

    RestResp<PageRespDto<Map<String, Object>>> listNews(PageReqDto pageReqDto, String keyword);

    RestResp<Void> saveNews(AdminNewsReqDto dto);

    RestResp<Void> updateNews(Long id, AdminNewsReqDto dto);

    RestResp<Void> deleteNews(Long id);

    RestResp<PageRespDto<Map<String, Object>>> listFriendLinks(PageReqDto pageReqDto, String keyword);

    RestResp<Void> saveFriendLink(AdminFriendLinkReqDto dto);

    RestResp<Void> updateFriendLink(Long id, AdminFriendLinkReqDto dto);

    RestResp<Void> deleteFriendLink(Long id);

}

