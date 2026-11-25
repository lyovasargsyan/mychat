package com.classchat.repository;

import com.classchat.model.PushSubscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    List<PushSubscription> findAllByUserId(Long userId);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    void deleteByEndpointAndUserId(String endpoint, Long userId);
}
