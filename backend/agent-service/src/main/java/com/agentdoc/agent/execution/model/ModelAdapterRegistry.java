package com.agentdoc.agent.execution.model;

import com.agentdoc.agent.enums.ModelAdapterType;
import com.agentdoc.agent.enums.ModelProvider;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 模型适配器注册中心
 * <p>
 * 负责收集Spring容器中所有{@link ModelAdapter}适配器Bean，维护 {@link ModelAdapterType} → {@link ModelAdapter} 的映射。
 * 上层Agent Runtime统一通过本类获取适配器实例，<b>上层业务只感知本类与ModelAdapter SPI接口，不感知各厂商具体实现类</b>。
 * </p>
 * <p>
 * 核心能力：
 * <ul>
 *     <li>容器启动阶段自动注册全部适配器；同一个适配器可支持多个{@link ModelAdapterType}</li>
 *     <li>重复注册检测：同一个type不允许被多个适配器覆盖，启动直接报错</li>
 *     <li>根据{@link ModelEntity}模型配置查找对应适配器</li>
 *     <li>模型能力校验：校验适配器是否满足任务所需能力（如工具调用），不满足直接抛出业务异常阻断任务</li>
 * </ul>
 * <p>
 * 约束：
 * <ol>
 *     <li>所有适配器实现类必须添加{@code @Component}，Spring才能自动注入到构造器入参List集合；</li>
 *     <li>内部Map使用{@code Map.copyOf}生成不可变集合，运行期禁止修改注册表，保证线程安全；</li>
 *     <li>不做模型调用、不做ChatModel缓存；缓存逻辑由{@code ModelChatModelCache}处理；</li>
 *     <li>返回对外类型永远为{@link ModelAdapter} SPI接口，屏蔽底层厂商实现细节。</li>
 * </ol>
 */
@Component
public class ModelAdapterRegistry {

    /**
     * 适配器注册表：key为模型适配器类型，value为对应的适配器实例
     * <p>构造完成后为不可变Map，线程安全，运行时不再变更</p>
     */
    private final Map<ModelAdapterType, ModelAdapter> adapters;

    /**
     * 构造器：Spring自动注入容器内所有{@link ModelAdapter}实现Bean，完成适配器注册
     * <p>遍历每个适配器的{@link ModelAdapter#supportedTypes()}，建立type到适配器的映射；
     * 如果同一个{@link ModelAdapterType}被多次注册，启动阶段抛出异常，防止配置冲突。
     * </p>
     * @param modelAdapters Spring容器中所有实现ModelAdapter接口的Bean集合
     * @throws IllegalStateException 同一个适配器类型重复注册时抛出
     */
    public ModelAdapterRegistry(List<ModelAdapter> modelAdapters) {
        // 构建枚举Map，key为适配器类型，value为适配器实例
        EnumMap<ModelAdapterType, ModelAdapter> registry = new EnumMap<>(ModelAdapterType.class);
        modelAdapters.forEach(adapter -> adapter.supportedTypes().forEach(type -> {
            // 检测重复注册：一个type不能被多个适配器实现
            if (registry.put(type, adapter) != null) {
                throw new IllegalStateException("模型适配器重复注册: " + type.getCode());
            }
        }));
        // 转为不可变集合，运行期只读，线程安全
        this.adapters = Map.copyOf(registry);
    }

    /**
     * 根据模型实体获取对应的模型适配器，不做能力校验
     * <p>根据model中adapterType、provider推导最终{@link ModelAdapterType}，从注册表查找。</p>
     * @param model 数据库加载的模型实体配置
     * @return 匹配的适配器SPI实例
     * @throws BusinessException 注册表找不到该适配器类型时抛出业务异常
     */
    public ModelAdapter require(ModelEntity model) {
        // 从模型实体的provider编码，解析出模型提供者枚举
        ModelProvider provider = ModelProvider.fromCode(model.getProvider());
        // 解析适配器类型：优先使用model上保存的adapterType；为空时根据provider推导默认适配器类型
        ModelAdapterType type = ModelAdapterType.fromCodeOrDefault(model.getAdapterType(), provider);
        // 根据适配器类型从注册表获取适配器实例
        ModelAdapter adapter = adapters.get(type);
        // 注册表不存在该适配器：代表该type没有对应的适配器Bean被注册，抛出业务异常
        if (adapter == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "模型适配器未启用: " + type.getCode());
        }
        return adapter;
    }

    /**
     * 按任务要求校验模型能力并返回适配器。
     * <p>先通过{@link #require(ModelEntity)}拿到适配器；再校验适配器能力是否满足任务最低要求。
     * Agent工具循环类任务必须调用本重载，确保适配器具备工具调用能力。
     * </p>
     * @param model    模型配置实体
     * @param required 当前任务要求的模型能力（例如必须支持工具调用）
     * @return 满足能力要求的适配器SPI实例
     * @throws BusinessException 适配器不存在 或者 适配器不满足任务所需能力时抛出业务异常
     */
    public ModelAdapter require(ModelEntity model, ModelCapabilities required) {
        // 第一步：获取原始适配器实例（内部会做适配器是否存在的判断）
        ModelAdapter adapter = require(model);
        // 校验适配器实际能力是否 >= 任务要求的能力
        if (!adapter.capabilities().supports(required)) {
            // 区分失败原因：是否是缺少工具调用能力，否则判定为不支持并行工具调用
            String reason = required.toolCalling() && !adapter.capabilities().toolCalling()
                    ? "模型不支持工具调用"
                    : "模型不支持并行工具调用";
            // 能力不满足，抛出业务异常阻断任务执行
            throw new BusinessException(ErrorCode.BAD_REQUEST, reason + "，无法执行当前任务");
        }
        return adapter;
    }
}
