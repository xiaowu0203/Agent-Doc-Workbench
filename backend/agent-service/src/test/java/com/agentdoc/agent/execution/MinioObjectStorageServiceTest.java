package com.agentdoc.agent.execution;

import com.agentdoc.common.minio.config.MinioAutoConfiguration;
import com.agentdoc.common.minio.config.MinioProperties;
import com.agentdoc.common.minio.service.MinioObjectStorageService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MinioObjectStorageServiceTest {

    @Test
    void missingCredentialsFailFast() {
        MinioProperties properties = new MinioProperties();

        assertThatThrownBy(() -> new MinioAutoConfiguration().minioClient(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("凭证未配置");
    }

    @Test
    void delegatesObjectOperationsWithConfiguredBucket() throws Exception {
        MinioClient client = mock(MinioClient.class);
        MinioProperties properties = new MinioProperties();
        properties.setBucket("skills");
        MinioObjectStorageService storage = new MinioObjectStorageService(client, properties);
        Path source = Files.createTempFile("minio-test-", ".txt");
        Files.writeString(source, "content", StandardCharsets.UTF_8);

        storage.put("skill/1.zip", source, "application/zip");
        storage.get("skill/1.zip");
        doReturn(null).when(client).statObject(any(StatObjectArgs.class));
        assertThat(storage.exists("skill/1.zip")).isTrue();
        storage.delete("skill/1.zip");

        verify(client).putObject(any(PutObjectArgs.class));
        verify(client).getObject(any(GetObjectArgs.class));
        verify(client).statObject(any(StatObjectArgs.class));
        verify(client).removeObject(any(RemoveObjectArgs.class));
        source.toFile().deleteOnExit();
    }
}
