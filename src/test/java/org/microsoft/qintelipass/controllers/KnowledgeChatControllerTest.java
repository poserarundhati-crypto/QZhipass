package org.microsoft.qintelipass.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.microsoft.qintelipass.request.KnowledgeChatRequest;
import org.microsoft.qintelipass.services.CurrentUserService;
import org.microsoft.qintelipass.services.chat.AgentChatService;
import org.microsoft.qintelipass.services.chat.ChatEventSink;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeChatControllerTest {
    private static final Long CURRENT_USER_ID = 501L;

    @Mock
    private AgentChatService agentChatService;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private HttpServletRequest httpRequest;

    @Test
    void chatEndpointProducesEventStreamAndPassesAuthenticatedUserIdToService() throws Exception {
        when(currentUserService.requireUserId(httpRequest)).thenReturn(CURRENT_USER_ID);
        TaskExecutor directExecutor = Runnable::run;
        KnowledgeChatController controller = new KnowledgeChatController(
                agentChatService,
                currentUserService,
                directExecutor);
        KnowledgeChatRequest request = new KnowledgeChatRequest(
                7001L,
                8001L,
                "qwen3",
                List.of(),
                null);

        SseEmitter emitter = controller.chat(request, httpRequest);

        Method chatMethod = KnowledgeChatController.class.getMethod(
                "chat",
                KnowledgeChatRequest.class,
                HttpServletRequest.class);
        PostMapping mapping = chatMethod.getAnnotation(PostMapping.class);
        assertNotNull(mapping);
        assertArrayEquals(
                new String[]{MediaType.TEXT_EVENT_STREAM_VALUE},
                mapping.produces());
        assertEquals(Long.valueOf(120_000L), emitter.getTimeout());
        verify(currentUserService).requireUserId(httpRequest);

        ArgumentCaptor<String> requestId = ArgumentCaptor.forClass(String.class);
        verify(agentChatService).streamChat(
                eq(CURRENT_USER_ID),
                same(request),
                requestId.capture(),
                any(ChatEventSink.class));
        assertFalse(requestId.getValue().isBlank());
    }
}
