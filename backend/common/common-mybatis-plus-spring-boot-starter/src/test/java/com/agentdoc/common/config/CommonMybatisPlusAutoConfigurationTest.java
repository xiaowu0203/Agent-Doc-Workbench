package com.agentdoc.common.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class CommonMybatisPlusAutoConfigurationTest {

    private final CommonMybatisPlusAutoConfiguration config = new CommonMybatisPlusAutoConfiguration();

    @Test
    void defaultInterceptorHasPaginationOnly() {
        MybatisPlusInterceptor interceptor = config.mybatisPlusInterceptor(false);
        List<InnerInterceptor> interceptors = interceptor.getInterceptors();
        assertEquals(1, interceptors.size());
        assertInstanceOf(PaginationInnerInterceptor.class, interceptors.get(0));
    }

    @Test
    void interceptorAddsOptimisticLockWhenEnabled() {
        MybatisPlusInterceptor interceptor = config.mybatisPlusInterceptor(true);
        List<InnerInterceptor> interceptors = interceptor.getInterceptors();
        assertEquals(2, interceptors.size());
        assertInstanceOf(PaginationInnerInterceptor.class, interceptors.get(0));
        assertInstanceOf(OptimisticLockerInnerInterceptor.class, interceptors.get(1));
    }
}
