package com.mall.mapper;

import com.mall.domain.OutboxEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

@Mapper
public interface OutboxMapper {

    int insert(OutboxEvent event);

    List<OutboxEvent> claim(@Param("limit") int limit, @Param("leaseSeconds") int leaseSeconds);

    int markSent(@Param("id") Long id);

    int markRetry(@Param("id") Long id,
                  @Param("error") String error,
                  @Param("delaySeconds") int delaySeconds,
                  @Param("maxAttempts") int maxAttempts);

    int countByStatus(@Param("status") String status);

    int countStalePending(@Param("staleBefore") Instant staleBefore);

    List<OutboxEvent> listByAggregate(@Param("aggregateId") Long aggregateId);
}
