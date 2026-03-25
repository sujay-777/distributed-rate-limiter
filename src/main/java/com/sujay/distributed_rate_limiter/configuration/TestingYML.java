package com.sujay.distributed_rate_limiter.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "just-for-fun")
public class TestingYML {

    private int maxCount;

    public int getMaxCount() {
        return maxCount;
    }

    public void setMaxCount(int maxCount){
        this.maxCount = maxCount;
    }


}
