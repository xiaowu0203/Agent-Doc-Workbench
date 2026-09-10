package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ChangeOp;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.dto.ChangeItemDTO;
import com.agentdoc.common.feign.vo.DocumentRefVO;
import com.agentdoc.task.enums.ChangeRequestType;
import com.agentdoc.task.mapper.ChangeRequestMapper;
import com.agentdoc.task.pojo.dto.ChangeRequestSubmitDTO;
import com.agentdoc.task.pojo.entity.ChangeRequestEntity;
import com.agentdoc.task.pojo.vo.ChangeRequestVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ChangeRequestService} 单元测试：人工提交和请求读取。
 */
@ExtendWith(MockitoExtension.class)
class ChangeRequestServiceTest {

    private static final long USER_ID = 1001L;
    private static final long DOCUMENT_ID = 3001L;
    private static final long REQUEST_ID = 4001L;

    @Mock
    private ChangeRequestMapper changeRequestMapper;
    @Mock
    private DocumentFeign documentFeign;

    private ChangeRequestService service;

    @BeforeEach
    void setUp() {
        service = new ChangeRequestService(changeRequestMapper, documentFeign);
        // 模拟已登录：SecurityContext 放入以 Jwt 为 principal 的认证信息
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(String.valueOf(USER_ID))
                .claim("username", "tester")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        // lenient：不存在场景不会读取文档或校验空间权限。
        lenient().when(documentFeign.getDocumentRefs(anyList()))
                .thenReturn(Result.ok(List.of(new DocumentRefVO(DOCUMENT_ID, 2001L, "测试文档"))));
        lenient().when(documentFeign.checkSpacePermission(anyLong(), any())).thenReturn(Result.ok());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private ChangeRequestSubmitDTO submitDTO() {
        return new ChangeRequestSubmitDTO(DOCUMENT_ID, ChangeRequestType.FORMAL,
                List.of(new ChangeItemDTO(ChangeOp.REPLACE, null, "新版内容")), 2L, "更新内容");
    }

    @Test
    void shouldSubmitWithPendingStatus() {
        ChangeRequestVO vo = service.submit(submitDTO());
        assertEquals("PENDING", vo.status().name());
        assertEquals(ChangeRequestType.FORMAL, vo.requestType());
        assertEquals("测试文档", vo.documentTitle());
        assertEquals(1, vo.changes().size());
        verify(changeRequestMapper).insert(any(ChangeRequestEntity.class));
    }

    @Test
    void shouldRejectMissingRequest() {
        when(changeRequestMapper.selectById(REQUEST_ID)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.requireRequest(REQUEST_ID));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
    }
}
