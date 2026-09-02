package com.agentdoc.agent.execution;

import com.agentdoc.agent.enums.SkillStatus;
import com.agentdoc.agent.enums.SkillVersionStatus;
import com.agentdoc.agent.pojo.dto.SkillCreateDTO;
import com.agentdoc.agent.pojo.entity.SkillEntity;
import com.agentdoc.agent.pojo.vo.SkillVO;
import com.agentdoc.agent.pojo.vo.SkillVersionVO;
import com.agentdoc.agent.service.SkillImportService;
import com.agentdoc.agent.service.SkillService;
import com.agentdoc.agent.service.SkillVersionService;
import com.agentdoc.agent.skill.archive.ParsedSkillPackage;
import com.agentdoc.agent.skill.archive.SkillPackageValidator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillImportServiceTest {

    @Test
    void importsPackageMetadataAndCreatesDraftVersion() {
        SkillService skillService = mock(SkillService.class);
        SkillVersionService versionService = mock(SkillVersionService.class);
        SkillPackageValidator validator = mock(SkillPackageValidator.class);
        SkillImportService service = new SkillImportService(skillService, versionService, validator);
        MockMultipartFile file = new MockMultipartFile("file", "review.zip", "application/zip",
                new byte[]{1, 2, 3});
        ParsedSkillPackage parsed = new ParsedSkillPackage("document-review", "检查文档质量", "执行检查",
                List.of(), List.of(), 3, 3, 1, 0, "sha256");
        SkillEntity entity = new SkillEntity();
        entity.setId(10L);
        entity.setSpaceId(7L);
        entity.setName(parsed.name());
        entity.setDisplayName(parsed.name());
        entity.setDescription(parsed.description());
        entity.setStatus(SkillStatus.ACTIVE.getCode());
        SkillVO skillVO = new SkillVO(10L, 7L, parsed.name(), parsed.name(), parsed.description(),
                SkillStatus.ACTIVE, 1, 0, null, 1L, null, null);
        SkillVersionVO versionVO = new SkillVersionVO(20L, 10L, 1, SkillVersionStatus.DRAFT,
                parsed.description(), "sha256", 3L, List.of(), List.of(), 1L, null, null);

        doNothing().when(skillService).requireManage(7L);
        when(validator.validate(any(Path.class))).thenReturn(parsed);
        when(skillService.create(any())).thenReturn(entity);
        when(versionService.upload(10L, file)).thenReturn(versionVO);
        when(skillService.toVO(entity)).thenReturn(skillVO);

        var result = service.importPackage(7L, "文档审查", "管理侧文档审查能力", file);

        ArgumentCaptor<SkillCreateDTO> captor = ArgumentCaptor.forClass(SkillCreateDTO.class);
        verify(skillService).create(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new SkillCreateDTO(
                7L, "document-review", "文档审查", "管理侧文档审查能力"));
        assertThat(result.skill()).isEqualTo(skillVO);
        assertThat(result.version()).isEqualTo(versionVO);
    }
}
