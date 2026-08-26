package com.agentdoc.common.minio.service;

/**
 * 对象存储自定义运行时异常
 * <p>
 * 封装对象存储操作过程中产生的各类异常：上传、下载、查询、删除、遍历等，
 * 上层业务捕获该异常即可统一处理存储相关错误，无需直接依赖MinIO底层异常类。
 * </p>
 */
public class ObjectStorageException extends RuntimeException {

    /**
     * 构造存储异常
     *
     * @param message 异常描述信息
     * @param cause   原始底层异常（MinIO SDK异常、IO异常等）
     */
    public ObjectStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
