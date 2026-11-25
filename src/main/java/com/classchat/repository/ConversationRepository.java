package com.classchat.repository;

import com.classchat.model.Conversation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Query("""
            select c from Conversation c
            join ConversationMember m on m.conversation = c
            where m.user.id = :userId
            order by c.createdAt desc
            """)
    List<Conversation> findAllForUser(@Param("userId") Long userId);

    @Query("""
            select c from Conversation c
            where c.type = com.classchat.model.ConversationType.DIRECT
              and exists (select m1.id from ConversationMember m1
                          where m1.conversation = c and m1.user.id = :firstUserId)
              and exists (select m2.id from ConversationMember m2
                          where m2.conversation = c and m2.user.id = :secondUserId)
              and (select count(m3.id) from ConversationMember m3 where m3.conversation = c) = 2
            """)
    List<Conversation> findDirectBetween(@Param("firstUserId") Long firstUserId,
                                         @Param("secondUserId") Long secondUserId);
}
