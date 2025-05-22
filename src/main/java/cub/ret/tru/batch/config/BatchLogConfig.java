package cub.ret.tru.batch.config;

import cub.ret.tru.batch.service.BatchLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 批次日誌配置類
 * 負責初始化 DatabaseAppender 與 BatchLogService 的關聯
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchLogConfig implements CommandLineRunner {

    private final BatchLogService batchLogService;

    @Override
    public void run(String... args) throws Exception {
        // 將 BatchLogService 設定到 DatabaseAppender 中
        DatabaseAppender.setBatchLogService(batchLogService);
        log.info("DatabaseAppender 已成功配置 BatchLogService");
    }
}