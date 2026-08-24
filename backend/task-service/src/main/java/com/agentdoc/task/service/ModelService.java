package com.agentdoc.task.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.task.enums.ModelStatus;
import com.agentdoc.task.mapper.ModelMapper;
import com.agentdoc.task.pojo.dto.ModelCreateDTO;
import com.agentdoc.task.pojo.entity.ModelEntity;
import com.agentdoc.task.pojo.vo.ModelVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 模型元数据服务。模型只保存外部调用元数据，不保存密钥。
 */
@Service
@RequiredArgsConstructor
public class ModelService {

    private final ModelMapper modelMapper;

    public List<ModelVO> list(Boolean enabledOnly) {
        // 获取当前用户ID，未登录抛出异常
        AuthUtils.getUserIdOrException();
        LambdaQueryWrapper<ModelEntity> wrapper = new LambdaQueryWrapper<>();
        // 是否仅查看【启用】的模型
        if (Boolean.TRUE.equals(enabledOnly)) {
            wrapper.eq(ModelEntity::getStatus, ModelStatus.ENABLED.getCode());
        }
        return modelMapper.selectList(wrapper).stream().map(ModelVO::from).toList();
    }

    public ModelVO create(ModelCreateDTO dto) {
        AuthUtils.getUserIdOrException();
        ModelEntity entity = dto.toEntity();
        modelMapper.insert(entity);
        return ModelVO.from(entity);
    }

    public ModelVO updateStatus(Long id, Integer status) {
        AuthUtils.getUserIdOrException();
        ModelEntity entity = modelMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "模型不存在");
        }
        entity.setStatus(status);
        modelMapper.updateById(entity);
        return ModelVO.from(entity);
    }
}
