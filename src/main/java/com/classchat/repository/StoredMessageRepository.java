package com.classchat.repository;

import com.classchat.model.StoredMessage;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredMessageRepository extends JpaRepository<StoredMessage, Long> {

    List<StoredMessage> findByConversationIdOrderByIdDesc(Long conversationId, Pageable pageable);

    List<StoredMessage> findByConversationIdAndIdLessThanOrderByIdDesc(
            Long conversationId, Long beforeId, Pageable pageable);

    List<StoredMessage> findTop1ByConversationIdOrderByIdDesc(Long conversationId);

    long countByConversationIdAndIdGreaterThanAndSenderIdNot(
            Long conversationId, Long lastReadMessageId, Long senderId);
}
