package com.magiclamp.phoenixkey_db.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Cấu hình ThreadPoolTaskScheduler cho {@code @Scheduled} task.
 *
 * Mặc định Spring dùng 1 thread duy nhất cho tất cả scheduled task. Nếu một
 * task chậm (vd: HTTP call tới Vault hang) thì các task khác (cleanup nonce,
 * mark expired invitation) sẽ bị xếp hàng chờ, fire trễ vài chục phút.
 *
 * Pool 4 thread đủ cho 4 scheduled task hiện tại + dư khi mở rộng.
 */
@Configuration
public class SchedulingConfig implements SchedulingConfigurer {

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("pk-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        registrar.setTaskScheduler(scheduler);
    }
}
