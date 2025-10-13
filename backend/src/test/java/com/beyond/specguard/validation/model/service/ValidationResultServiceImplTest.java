package com.beyond.specguard.validation.model.service;

import com.beyond.specguard.company.common.model.entity.ClientUser;
import com.beyond.specguard.evaluationprofile.model.entity.EvaluationWeight;
import com.beyond.specguard.resume.model.entity.Resume;
import com.beyond.specguard.resume.model.entity.ResumeLink;
import com.beyond.specguard.resume.model.repository.ResumeRepository;
import com.beyond.specguard.validation.model.dto.request.ValidationCalculateRequestDto;
import com.beyond.specguard.validation.model.entity.ValidationIssue;
import com.beyond.specguard.validation.model.entity.ValidationResult;
import com.beyond.specguard.validation.model.repository.CalculateQueryRepository;
import com.beyond.specguard.validation.model.repository.ValidationIssueRepository;
import com.beyond.specguard.validation.model.repository.ValidationResultLogRepository;
import com.beyond.specguard.validation.model.repository.ValidationResultRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidationResultServiceImplTest {

    @InjectMocks
    private ValidationResultServiceImpl validationResultService;

    @Mock
    private CalculateQueryRepository calculateQueryRepository;
    @Mock
    private ValidationResultRepository validationResultRepository;
    @Mock
    private ValidationIssueRepository validationIssueRepository;
    @Mock
    private ValidationResultLogRepository validationResultLogRepository;
    @Mock
    private ResumeRepository resumeRepository;
    @Mock
    private EntityManager em;

    private ClientUser clientUser;
    private UUID resumeId;

    @BeforeEach
    void setUp() {
        clientUser = ClientUser.builder()
                .role(ClientUser.Role.OWNER)
                .build();
        resumeId = UUID.randomUUID();
    }

    @Test
    @DisplayName("calculateAndSave - 정상 실행 시 ValidationResult 저장 및 ID 반환")
    void calculateAndSave_givenCompleteData_returnValidationResultId() {
        // given
        ValidationCalculateRequestDto request = new ValidationCalculateRequestDto(resumeId);

        when(calculateQueryRepository.findTemplateAnalysisKeywordsJson(resumeId))
                .thenReturn(List.of("[\"java\", \"spring\"]"));

        when(calculateQueryRepository.findProcessedContentsByPlatform(resumeId, ResumeLink.LinkType.GITHUB.name()))
                .thenReturn(List.of("{\"commit\":10, \"repo\":2, \"keywords\":[\"java\"]}"));

        when(calculateQueryRepository.findProcessedContentsByPlatform(resumeId, ResumeLink.LinkType.NOTION.name()))
                .thenReturn(List.of("{\"keywords\":[\"spring\"]}"));

        when(calculateQueryRepository.findProcessedContentsByPlatform(resumeId, ResumeLink.LinkType.VELOG.name()))
                .thenReturn(List.of("{\"post\":3, \"date\":2, \"keywords\":[\"jpa\"]}"));

        Map<String, Object> certMap = Map.of("completed", 2, "failed", 1);
        when(calculateQueryRepository.countCertificateVerification(resumeId)).thenReturn(certMap);

        EvaluationWeight w = EvaluationWeight.builder()
                .weightType(EvaluationWeight.WeightType.GITHUB_REPO_COUNT)
                .weightValue(1.0f)
                .build();

        CalculateQueryRepository.WeightRow weightRow = new CalculateQueryRepository.WeightRow() {
            @Override
            public String getWeightType() { return "GITHUB_REPO_COUNT"; }

            @Override
            public Double getWeightValue() { return 1.0; }
        };

        when(calculateQueryRepository.findWeightsByResume(resumeId)).thenReturn(List.of(weightRow));

        when(validationResultRepository.findByResumeId(resumeId)).thenReturn(Optional.empty());

        ValidationIssue issue = ValidationIssue.builder()
                .validationResult(ValidationIssue.ValidationResult.SUCCESS)
                .build();
        when(validationIssueRepository.save(any())).thenReturn(issue);

        ValidationResult savedResult = ValidationResult.builder()
                .id(UUID.randomUUID())
                .resume(Resume.builder().id(resumeId).build())
                .validationIssue(issue)
                .adjustedTotal(0.8)
                .build();
        when(validationResultRepository.save(any())).thenReturn(savedResult);

        when(resumeRepository.updateStatusValidation(resumeId, Resume.ResumeStatus.VALIDATED))
                .thenReturn(1);

        when(em.getReference(Resume.class, resumeId))
                .thenReturn(Resume.builder().id(resumeId).build());

        // when
        UUID resultId = validationResultService.calculateAndSave(clientUser, request);

        // then
        assertEquals(savedResult.getId(), resultId);
        verify(validationResultLogRepository, times(1)).saveAll(anyList());
        verify(resumeRepository).updateStatusValidation(resumeId, Resume.ResumeStatus.VALIDATED);
    }

    @Test
    @DisplayName("calculateAndSave_레포데이터없음_0점계산후정상저장")
    void calculateAndSave_givenNoRepoData_returnZeroScoreSave() {
        // given
        ValidationCalculateRequestDto request = new ValidationCalculateRequestDto(resumeId);

        when(calculateQueryRepository.findTemplateAnalysisKeywordsJson(resumeId)).thenReturn(List.of());
        when(calculateQueryRepository.findProcessedContentsByPlatform(any(), any())).thenReturn(List.of());
        when(calculateQueryRepository.countCertificateVerification(resumeId))
                .thenReturn(Map.of("completed", 0, "failed", 0));
        when(calculateQueryRepository.findWeightsByResume(resumeId)).thenReturn(List.of());

        when(validationResultRepository.findByResumeId(resumeId)).thenReturn(Optional.empty());
        when(em.getReference(Resume.class, resumeId)).thenReturn(Resume.builder().id(resumeId).build());

        ValidationIssue issue = ValidationIssue.builder()
                .validationResult(ValidationIssue.ValidationResult.SUCCESS)
                .build();
        when(validationIssueRepository.save(any())).thenReturn(issue);

        ValidationResult savedResult = ValidationResult.builder()
                .id(UUID.randomUUID())
                .resume(Resume.builder().id(resumeId).build())
                .validationIssue(issue)
                .adjustedTotal(0.0) // 모든 점수 0
                .build();
        when(validationResultRepository.save(any())).thenReturn(savedResult);

        when(resumeRepository.updateStatusValidation(resumeId, Resume.ResumeStatus.VALIDATED))
                .thenReturn(1);

        // when
        UUID resultId = validationResultService.calculateAndSave(clientUser, request);

        // then
        assertNotNull(resultId);
        verify(validationResultLogRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("calculateAndSave_리포지토리예외발생_saveIssueAndLogsOnError호출")
    void calculateAndSave_givenRepositoryException_saveIssueAndLogsOnError() {
        // given
        ValidationCalculateRequestDto requestDto = new ValidationCalculateRequestDto(resumeId);

        // 예외 유도
        when(calculateQueryRepository.findTemplateAnalysisKeywordsJson(resumeId))
                .thenThrow(new RuntimeException("DB 연결 실패"));

        Resume resumeStub = Resume.builder().id(resumeId).build();
        ValidationIssue issueStub = ValidationIssue.builder()
                .id(UUID.randomUUID())
                .validationResult(ValidationIssue.ValidationResult.FAILED)
                .build();
        ValidationResult resultStub = ValidationResult.builder()
                .id(UUID.randomUUID())
                .resume(resumeStub)
                .validationIssue(issueStub)
                .adjustedTotal(0.0)
                .build();

        when(em.getReference(Resume.class, resumeId)).thenReturn(resumeStub);
        when(validationIssueRepository.save(any())).thenReturn(issueStub);
        when(validationResultRepository.save(any())).thenReturn(resultStub);

        // when
        UUID resultId = validationResultService.calculateAndSave(clientUser, requestDto);

        // then
        assertNotNull(resultId);
        assertEquals(resultStub.getId(), resultId);

        // 예외 처리 경로가 제대로 동작했는지 검증
        verify(validationIssueRepository, times(1)).save(argThat(issue ->
                issue.getValidationResult() == ValidationIssue.ValidationResult.FAILED
        ));

        verify(validationResultRepository, times(1)).save(argThat(result ->
                result.getAdjustedTotal() == 0.0
        ));

        verify(validationResultLogRepository, times(1)).save(argThat(log -> {
            String json = log.getKeywordList();
            return json.contains("ERROR") && json.contains("DB 연결 실패");
        }));

        verifyNoMoreInteractions(resumeRepository);
    }

    @Test
    @DisplayName("calculateAndSave_Resume상태업데이트실패_에러로그남김")
    void calculateAndSave_ResumeUpdateFailed_logError() {
        // given
        ValidationCalculateRequestDto request = new ValidationCalculateRequestDto(resumeId);

        when(calculateQueryRepository.findTemplateAnalysisKeywordsJson(resumeId)).thenReturn(List.of());
        when(calculateQueryRepository.findProcessedContentsByPlatform(any(), any())).thenReturn(List.of());
        when(calculateQueryRepository.countCertificateVerification(resumeId)).thenReturn(Map.of());
        when(calculateQueryRepository.findWeightsByResume(resumeId)).thenReturn(List.of());

        when(validationResultRepository.findByResumeId(resumeId)).thenReturn(Optional.empty());
        when(em.getReference(Resume.class, resumeId)).thenReturn(Resume.builder().id(resumeId).build());

        ValidationIssue issue = ValidationIssue.builder()
                .validationResult(ValidationIssue.ValidationResult.SUCCESS)
                .build();
        when(validationIssueRepository.save(any())).thenReturn(issue);

        ValidationResult savedResult = ValidationResult.builder()
                .id(UUID.randomUUID())
                .resume(Resume.builder().id(resumeId).build())
                .validationIssue(issue)
                .adjustedTotal(0.0)
                .build();
        when(validationResultRepository.save(any())).thenReturn(savedResult);

        when(resumeRepository.updateStatusValidation(resumeId, Resume.ResumeStatus.VALIDATED))
                .thenReturn(0); // 업데이트 실패 상황

        // when
        UUID resultId = validationResultService.calculateAndSave(clientUser, request);

        // then
        assertNotNull(resultId);
        verify(validationResultLogRepository).saveAll(anyList());
        verify(resumeRepository).updateStatusValidation(resumeId, Resume.ResumeStatus.VALIDATED);
    }
}