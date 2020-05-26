package io.metersphere.service;

import io.metersphere.base.domain.LoadTest;
import io.metersphere.base.domain.LoadTestExample;
import io.metersphere.base.domain.Project;
import io.metersphere.base.domain.ProjectExample;
import io.metersphere.base.mapper.LoadTestMapper;
import io.metersphere.base.mapper.ProjectMapper;
import io.metersphere.base.mapper.ext.*;
import io.metersphere.commons.exception.MSException;
import io.metersphere.commons.utils.SessionUtils;
import io.metersphere.controller.request.ProjectRequest;
import io.metersphere.dto.ProjectDTO;
import io.metersphere.dto.ProjectRelatedResourceDTO;
import io.metersphere.i18n.Translator;
import io.metersphere.performance.service.PerformanceTestService;
import io.metersphere.track.dto.TestPlanDTO;
import io.metersphere.track.request.testcase.QueryTestCaseRequest;
import io.metersphere.track.request.testcase.QueryTestPlanRequest;
import io.metersphere.track.request.testplan.DeleteTestPlanRequest;
import io.metersphere.track.service.TestCaseService;
import io.metersphere.track.service.TestPlanService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class ProjectService {
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private ExtProjectMapper extProjectMapper;
    @Resource
    private PerformanceTestService performanceTestService;
    @Resource
    private LoadTestMapper loadTestMapper;
    @Resource
    private ExtTestCaseMapper extTestCaseMapper;
    @Resource
    private ExtTestPlanMapper extTestPlanMapper;
    @Resource
    private ExtLoadTestMapper extLoadTestMapperMapper;
    @Resource
    private ExtApiTestMapper extApiTestMapper;
    @Resource
    private TestPlanService testPlanService;
    @Resource
    private TestCaseService testCaseService;

    public Project addProject(Project project) {
        if (StringUtils.isBlank(project.getName())) {
            MSException.throwException(Translator.get("project_name_is_null"));
        }
        ProjectExample example = new ProjectExample();
        example.createCriteria()
                .andWorkspaceIdEqualTo(SessionUtils.getCurrentWorkspaceId())
                .andNameEqualTo(project.getName());
        if (projectMapper.countByExample(example) > 0) {
            MSException.throwException(Translator.get("project_name_already_exists"));
        }
        project.setId(UUID.randomUUID().toString());
        long createTime = System.currentTimeMillis();
        project.setCreateTime(createTime);
        project.setUpdateTime(createTime);
        // set workspace id
        project.setWorkspaceId(SessionUtils.getCurrentWorkspaceId());
        projectMapper.insertSelective(project);
        return project;
    }

    public List<ProjectDTO> getProjectList(ProjectRequest request) {
        if (StringUtils.isNotBlank(request.getName())) {
            request.setName(StringUtils.wrapIfMissing(request.getName(), "%"));
        }
        return extProjectMapper.getProjectWithWorkspace(request);
    }

    public void deleteProject(String projectId) {
        // delete test
        LoadTestExample loadTestExample = new LoadTestExample();
        loadTestExample.createCriteria().andProjectIdEqualTo(projectId);
        List<LoadTest> loadTests = loadTestMapper.selectByExample(loadTestExample);
        List<String> loadTestIdList = loadTests.stream().map(LoadTest::getId).collect(Collectors.toList());
        loadTestIdList.forEach(loadTestId -> {
            DeleteTestPlanRequest deleteTestPlanRequest = new DeleteTestPlanRequest();
            deleteTestPlanRequest.setId(loadTestId);
            performanceTestService.delete(deleteTestPlanRequest);
        });

        // TODO 删除项目下 测试跟踪 相关
        deleteTrackResourceByProjectId(projectId);

        // TODO 删除项目下 接口测试 相关

        // delete project
        projectMapper.deleteByPrimaryKey(projectId);
    }

    private void deleteTrackResourceByProjectId(String projectId) {
        QueryTestPlanRequest request = new QueryTestPlanRequest();
        request.setProjectId(projectId);
        testPlanService.listTestPlan(request).forEach(testPlan -> {
            testPlanService.deleteTestPlan(testPlan.getId());
        });
        testCaseService.deleteTestCaseByProjectId(projectId);
    }

    public void updateProject(Project project) {
        project.setCreateTime(null);
        project.setUpdateTime(System.currentTimeMillis());
        projectMapper.updateByPrimaryKeySelective(project);
    }

    public List<Project> listAll() {
        return projectMapper.selectByExample(null);
    }

    public List<Project> getRecentProjectList(ProjectRequest request) {
        ProjectExample example = new ProjectExample();
        ProjectExample.Criteria criteria = example.createCriteria();
        if (StringUtils.isNotBlank(request.getWorkspaceId())) {
            criteria.andWorkspaceIdEqualTo(request.getWorkspaceId());
        }
        // 按照修改时间排序
        example.setOrderByClause("update_time desc");
        return projectMapper.selectByExample(example);
    }

    public Project getProjectById(String id) {
        return projectMapper.selectByPrimaryKey(id);
    }

    public ProjectRelatedResourceDTO getRelatedResource(String projectId) {
        ProjectRelatedResourceDTO projectRelatedResource = new ProjectRelatedResourceDTO();
        projectRelatedResource.setTestCaseCount(extTestCaseMapper.countByProjectId(projectId));
        projectRelatedResource.setTestPlanCount(extTestPlanMapper.countByProjectId(projectId));
        projectRelatedResource.setLoadTestCount(extLoadTestMapperMapper.countByProjectId(projectId));
        projectRelatedResource.setApiTestCount(extApiTestMapper.countByProjectId(projectId));
        return projectRelatedResource;
    }
}
