package com.mall.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AlertSnapshot {
    private int outboxFailed;
    private int outboxStalePending;
    private boolean open;
}
