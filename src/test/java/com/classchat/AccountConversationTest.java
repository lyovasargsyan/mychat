package com.classchat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.classchat.dto.RegisterRequest;
import com.classchat.dto.CreateGroupRequest;
import com.classchat.dto.PushSubscriptionRequest;
import com.classchat.dto.PasswordResetConfirmRequest;
import com.classchat.model.UserAccount;
import com.classchat.repository.UserAccountRepository;
import com.classchat.repository.PushSubscriptionRepository;
import com.classchat.service.AccountService;
import com.classchat.service.ApiException;
import com.classchat.service.ConversationService;
import com.classchat.service.EmailVerificationService;
import com.classchat.service.PushSubscriptionService;
import com.classchat.service.PasswordResetService;
import com.classchat.TestMailConfiguration.RecordingVerificationEmailSender;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(TestMailConfiguration.class)
class AccountConversationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private UserAccountRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private RecordingVerificationEmailSender verificationEmails;

    @Autowired
    private PushSubscriptionService pushSubscriptionService;

    @Autowired
    private PushSubscriptionRepository pushSubscriptions;

    @Autowired
    private PasswordResetService passwordResetService;

    @Test
    void registrationHashesPasswordsAndNormalizesUserHandles() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserAccount account = accountService.register(
                new RegisterRequest("  " + suffix + "@example.test ",
                        "@Lyova_" + suffix, "safe-password-123"));

        assertEquals("lyova_" + suffix, account.getHandle());
        assertFalse(account.getPasswordHash().equals("safe-password-123"));
        assertTrue(passwordEncoder.matches("safe-password-123", account.getPasswordHash()));
        assertFalse(account.isEmailVerified());
    }

    @Test
    void emailVerificationAcceptsOnlyTheIssuedCodeAndMarksAccountVerified() {
        String email = UUID.randomUUID() + "@example.test";
        emailVerificationService.startRegistration(
                new RegisterRequest(email, "verify_user", "safe-password-123"));
        UserAccount account = users.findByEmailIgnoreCase(email).orElseThrow();

        assertFalse(account.isEmailVerified());
        String issuedCode = verificationEmails.codeFor(email);
        String wrongCode = "000000".equals(issuedCode) ? "000001" : "000000";
        assertThrows(ApiException.class, () -> emailVerificationService.verify(email, wrongCode));

        emailVerificationService.verify(email, issuedCode);
        assertTrue(account.isEmailVerified());
    }

    @Test
    void existingUnverifiedAccountCanRequestAndCompleteVerification() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = suffix + "@example.test";
        UserAccount existingAccount = accountService.register(
                new RegisterRequest(email, "legacy_" + suffix, "safe-password-123"));

        emailVerificationService.resend(email);
        emailVerificationService.verify(email, verificationEmails.codeFor(email));

        assertTrue(existingAccount.isEmailVerified());
    }

    @Test
    void passwordResetRequiresCodeAndReplacesOnlyThePasswordHash() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = suffix + "@example.test";
        UserAccount account = accountService.register(
                new RegisterRequest(email, "reset_" + suffix, "old-password-123"));
        account.markEmailVerified();

        assertTrue(passwordResetService.requestReset(email).contains("verified account exists"));
        String issuedCode = verificationEmails.resetCodeFor(email);
        String wrongCode = "000000".equals(issuedCode) ? "000001" : "000000";
        assertThrows(ApiException.class, () -> passwordResetService.confirmReset(
                new PasswordResetConfirmRequest(email, wrongCode, "new-password-123")));
        passwordResetService.confirmReset(
                new PasswordResetConfirmRequest(email, issuedCode, "new-password-123"));

        assertFalse(passwordEncoder.matches("old-password-123", account.getPasswordHash()));
        assertTrue(passwordEncoder.matches("new-password-123", account.getPasswordHash()));
        assertTrue(account.isEmailVerified());
    }

    @Test
    void passwordResetDoesNotSendCodesToUnverifiedAddresses() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = suffix + "@example.test";
        accountService.register(new RegisterRequest(email, "pending_" + suffix, "old-password-123"));

        passwordResetService.requestReset(email);

        assertNull(verificationEmails.resetCodeFor(email));
    }

    @Test
    void pushSubscriptionsRejectUntrustedEndpointsAndPersistSupportedOnes() {
        UserAccount user = createUser("push_" + UUID.randomUUID().toString().substring(0, 8));
        PushSubscriptionRequest.Keys keys = new PushSubscriptionRequest.Keys("p256dh_value", "auth_value");

        assertThrows(ApiException.class, () -> pushSubscriptionService.save(user,
                new PushSubscriptionRequest("http://127.0.0.1/admin", keys)));

        String endpoint = "https://fcm.googleapis.com/fcm/send/test-subscription";
        pushSubscriptionService.save(user, new PushSubscriptionRequest(endpoint, keys));
        assertTrue(pushSubscriptions.findByEndpoint(endpoint).isPresent());
    }

    @Test
    void groupHistoryIsPersistedAndOnlyMembersCanReadIt() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserAccount owner = createUser(suffix + "owner");
        UserAccount invited = createUser(suffix + "invited");
        UserAccount outsider = createUser(suffix + "outsider");

        var group = conversationService.createGroup(owner,
                new CreateGroupRequest("Study group", List.of("@" + invited.getHandle())));
        var sent = conversationService.storeMessage(group.id(), owner.getHandle(), "Saved for later");
        var second = conversationService.storeMessage(group.id(), owner.getHandle(), "Another saved message");
        var history = conversationService.history(invited, group.id(), null, 50);

        assertEquals(2, conversationService.list(invited).stream()
                .filter(conversation -> conversation.id().equals(group.id()))
                .findFirst().orElseThrow().unreadCount());
        assertEquals(0, conversationService.list(owner).stream()
                .filter(conversation -> conversation.id().equals(group.id()))
                .findFirst().orElseThrow().unreadCount());
        conversationService.markRead(invited, group.id());
        assertEquals(0, conversationService.list(invited).stream()
                .filter(conversation -> conversation.id().equals(group.id()))
                .findFirst().orElseThrow().unreadCount());

        assertEquals(2, history.size());
        assertEquals(sent.id(), history.get(0).id());
        assertEquals("Saved for later", history.get(0).content());
        assertEquals(List.of(sent.id()), conversationService.history(owner, group.id(), second.id(), 1)
                .stream().map(message -> message.id()).toList());
        assertThrows(ApiException.class,
                () -> conversationService.storeMessage(group.id(), owner.getHandle(), "x".repeat(501)));
        ApiException denied = assertThrows(ApiException.class,
                () -> conversationService.history(outsider, group.id(), null, 50));
        assertEquals(403, denied.getStatus().value());
    }

    @Test
    void onlyGroupOwnerCanAddMembers() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserAccount owner = createUser(suffix + "owner");
        UserAccount invited = createUser(suffix + "invited");
        UserAccount nextMember = createUser(suffix + "next");
        var group = conversationService.createGroup(owner,
                new CreateGroupRequest("Study group", List.of("@" + invited.getHandle())));

        assertThrows(ApiException.class,
                () -> conversationService.addGroupMember(invited, group.id(), "@" + nextMember.getHandle()));
        var updated = conversationService.addGroupMember(owner, group.id(), "@" + nextMember.getHandle());

        assertTrue(updated.members().contains("@" + nextMember.getHandle()));
        var afterRemoval = conversationService.removeGroupMember(owner, group.id(), invited.getHandle());
        assertFalse(afterRemoval.members().contains("@" + invited.getHandle()));
        assertFalse(conversationService.memberHandles(group.id()).contains(invited.getHandle()));
        assertThrows(ApiException.class,
                () -> conversationService.storeMessage(group.id(), invited.getHandle(), "Still here?"));
    }

    private UserAccount createUser(String handle) {
        return users.save(new UserAccount(handle + "@example.test", handle,
                passwordEncoder.encode("test-password-123"), clock.instant()));
    }
}
