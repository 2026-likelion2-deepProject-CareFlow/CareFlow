package com.careflow.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * @Async 어노테이션 활성화 설정.
 * 이 설정이 없으면 @Async 는 아무 효과 없이 무시되며,
 * NotificationEventListener 등 비동기 처리를 의도한 코드가 정상 동작하지 않는다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
