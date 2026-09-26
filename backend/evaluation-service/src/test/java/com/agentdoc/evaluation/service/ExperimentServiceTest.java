package com.agentdoc.evaluation.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.feign.vo.AgentCandidateConfigVO;
import com.agentdoc.common.feign.vo.ReplaySourceVO;
import com.agentdoc.evaluation.enums.EvaluationVersionStatus;
import com.agentdoc.evaluation.enums.ExperimentStatus;
import com.agentdoc.evaluation.mapper.EvaluationDatasetCaseMapper;
import com.agentdoc.evaluation.mapper.EvaluationDatasetMapper;
import com.agentdoc.evaluation.mapper.EvaluationDatasetVersionMapper;
import com.agentdoc.evaluation.mapper.EvaluationRunMapper;
import com.agentdoc.evaluation.mapper.EvaluationTestCaseMapper;
import com.agentdoc.evaluation.mapper.EvaluationTestCaseVersionMapper;
import com.agentdoc.evaluation.mapper.EvaluatorVersionMapper;
import com.agentdoc.evaluation.mapper.ExperimentMapper;
import com.agentdoc.evaluation.mapper.ExperimentVariantMapper;
import com.agentdoc.evaluation.mapper.TestCaseEvaluatorMapper;
import com.agentdoc.evaluation.pojo.dto.ExperimentCreateDTO;
import com.agentdoc.evaluation.pojo.dto.ExperimentStartDTO;
import com.agentdoc.evaluation.pojo.dto.PromptCandidateCreateDTO;
import com.agentdoc.evaluation.pojo.entity.EvaluationDatasetCaseEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationDatasetEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationDatasetVersionEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationTestCaseEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationTestCaseVersionEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluatorVersionEntity;
import com.agentdoc.evaluation.pojo.entity.ExperimentEntity;
import com.agentdoc.evaluation.pojo.entity.ExperimentVariantEntity;
import com.agentdoc.evaluation.pojo.entity.TestCaseEvaluatorEntity;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExperimentServiceTest {

    @Mock private ExperimentMapper experimentMapper;
    @Mock private ExperimentVariantMapper variantMapper;
    @Mock private EvaluationDatasetVersionMapper datasetVersionMapper;
    @Mock private EvaluationDatasetMapper datasetMapper;
    @Mock private EvaluationDatasetCaseMapper datasetCaseMapper;
    @Mock private EvaluationTestCaseVersionMapper testCaseVersionMapper;
    @Mock private EvaluationTestCaseMapper testCaseMapper;
    @Mock private TestCaseEvaluatorMapper testCaseEvaluatorMapper;
    @Mock private EvaluatorVersionMapper evaluatorVersionMapper;
    @Mock private EvaluationRunMapper runMapper;
    @Mock private ExperimentPersistenceService persistenceService;
    @Mock private EvaluationRunPersistenceService runPersistenceService;
    @Mock private EvaluationRunService evaluationRunService;
    @Mock private WorkerCapabilitySegmentService segmentService;
    @Mock private SpaceAccessService spaceAccessService;
    @Mock private TaskFeign taskFeign;
    @Mock private AgentFeign agentFeign;

    private ExperimentService service;

    @BeforeEach
    void setUp() {
        service = new ExperimentService(experimentMapper, variantMapper, datasetVersionMapper, datasetMapper,
                datasetCaseMapper, testCaseVersionMapper, testCaseMapper, testCaseEvaluatorMapper,
                evaluatorVersionMapper, runMapper, persistenceService, runPersistenceService,
                evaluationRunService, segmentService, spaceAccessService, taskFeign, agentFeign);
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("501")
                .claim(JwtConstant.CLAIM_SCOPE, JwtConstant.SCOPE_USER).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsIdempotentlyAndCalculatesPreflightBudget() {
        AtomicReference<ExperimentEntity> savedExperiment = new AtomicReference<>();
        AtomicReference<List<ExperimentVariantEntity>> savedVariants = new AtomicReference<>();
        when(experimentMapper.selectOne(any())).thenAnswer(invocation -> savedExperiment.get());
        when(variantMapper.selectList(any())).thenAnswer(invocation -> savedVariants.get());
        stubManifestSources();
        doAnswer(invocation -> {
            savedExperiment.set(invocation.getArgument(0));
            savedVariants.set(List.copyOf(invocation.getArgument(1)));
            return null;
        }).when(persistenceService).create(any(), any());
        ExperimentCreateDTO request = new ExperimentCreateDTO(9L, "request-1", 10L,
                List.of(new PromptCandidateCreateDTO("prompt-a", "更简洁地处理文档")));

        var created = service.create(request);
        var repeated = service.create(request);

        assertThat(created.id()).isEqualTo(repeated.id());
        assertThat(created.variants()).extracting("role").containsExactly("BASELINE", "CANDIDATE");
        assertThat(created.variants().get(1).candidateConfigRef()).isEqualTo(61L);
        verify(agentFeign, times(1)).createCandidateConfig(any());
        verify(persistenceService, times(1)).create(any(), any());

        when(experimentMapper.selectById(created.id())).thenReturn(savedExperiment.get());
        when(taskFeign.getReplaySource(101L, 9L)).thenReturn(Result.ok(source()));
        when(agentFeign.getCandidateConfigIdentity(61L, 9L, "d".repeat(64)))
                .thenReturn(Result.ok(candidate()));
        var preflight = service.preflight(created.id());
        assertThat(preflight.eligible()).isTrue();
        assertThat(preflight.plannedTaskCount()).isEqualTo(2);
        assertThat(preflight.plannedTokenBudget()).isEqualTo(2_000L);
        assertThatThrownBy(() -> service.start(created.id(), new ExperimentStartDTO(1_999L, 600L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("BUDGET_LIMIT_EXCEEDED");
    }

    @Test
    void repeatedStartReturnsRunningExperimentWithoutRecheckingSource() {
        ExperimentEntity experiment = new ExperimentEntity();
        experiment.setId(71L);
        experiment.setSpaceId(9L);
        experiment.setStatus(ExperimentStatus.RUNNING.name());
        experiment.setAuthorizedTokenBudget(2_000L);
        when(experimentMapper.selectById(71L)).thenReturn(experiment);
        when(variantMapper.selectList(any())).thenReturn(List.of());

        var result = service.start(71L, new ExperimentStartDTO(2_000L, 600L));

        assertThat(result.status()).isEqualTo(ExperimentStatus.RUNNING.name());
        verifyNoInteractions(taskFeign, agentFeign, persistenceService);
    }

    private void stubManifestSources() {
        EvaluationDatasetVersionEntity datasetVersion = new EvaluationDatasetVersionEntity();
        datasetVersion.setId(10L);
        datasetVersion.setDatasetId(11L);
        datasetVersion.setSpaceId(9L);
        datasetVersion.setStatus(EvaluationVersionStatus.PUBLISHED.name());
        EvaluationDatasetEntity dataset = new EvaluationDatasetEntity();
        dataset.setId(11L);
        dataset.setArchived(false);
        EvaluationDatasetCaseEntity binding = new EvaluationDatasetCaseEntity();
        binding.setTestCaseVersionId(31L);
        EvaluationTestCaseVersionEntity caseVersion = new EvaluationTestCaseVersionEntity();
        caseVersion.setId(31L);
        caseVersion.setTestCaseId(32L);
        caseVersion.setSpaceId(9L);
        caseVersion.setStatus(EvaluationVersionStatus.PUBLISHED.name());
        caseVersion.setSourceTaskId(101L);
        EvaluationTestCaseEntity testCase = new EvaluationTestCaseEntity();
        testCase.setId(32L);
        testCase.setArchived(false);
        TestCaseEvaluatorEntity evaluatorBinding = new TestCaseEvaluatorEntity();
        evaluatorBinding.setEvaluatorVersionId(41L);
        EvaluatorVersionEntity evaluator = new EvaluatorVersionEntity();
        evaluator.setId(41L);
        evaluator.setSpaceId(9L);
        evaluator.setStatus(EvaluationVersionStatus.PUBLISHED.name());
        evaluator.setEvaluatorKey("task-terminal-status");
        evaluator.setContentHash("f".repeat(64));
        when(datasetVersionMapper.selectById(10L)).thenReturn(datasetVersion);
        when(datasetMapper.selectById(11L)).thenReturn(dataset);
        when(datasetCaseMapper.selectList(any())).thenReturn(List.of(binding));
        when(testCaseVersionMapper.selectBatchIds(List.of(31L))).thenReturn(List.of(caseVersion));
        when(testCaseMapper.selectById(32L)).thenReturn(testCase);
        when(testCaseEvaluatorMapper.selectList(any())).thenReturn(List.of(evaluatorBinding));
        when(evaluatorVersionMapper.selectBatchIds(List.of(41L))).thenReturn(List.of(evaluator));
        when(taskFeign.getReplaySource(101L, 9L)).thenReturn(Result.ok(source()));
        when(agentFeign.createCandidateConfig(any())).thenReturn(Result.ok(candidate()));
    }

    private ReplaySourceVO source() {
        return new ReplaySourceVO(true, null, 101L, 201L, 9L, 301L, 1_000L,
                101L, "ORIGINAL", 0, 1, "b".repeat(64), 3, "c".repeat(64),
                401L, 7L, "a".repeat(64));
    }

    private AgentCandidateConfigVO candidate() {
        return new AgentCandidateConfigVO(61L, 9L, 301L, 101L, 201L, 3,
                "c".repeat(64), 3, "d".repeat(64), "e".repeat(64),
                List.of("snapshot.systemPrompt"));
    }
}
