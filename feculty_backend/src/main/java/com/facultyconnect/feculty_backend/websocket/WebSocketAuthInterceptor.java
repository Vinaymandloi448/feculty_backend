package com.facultyconnect.feculty_backend.websocket;

import com.facultyconnect.feculty_backend.model.Conversation;
import com.facultyconnect.feculty_backend.repository.ConversationRepository;
import com.facultyconnect.feculty_backend.service.JwtService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

/**
 * ChannelInterceptor to authenticate STOMP WebSocket connections via JWT
 * and authorize topic subscriptions.
 *
 * Flow:
 *   1. CONNECT frame: Validates "Authorization: Bearer <JWT>" header via JwtService,
 *      attaching authenticated Principal (email) to the session.
 *   2. SUBSCRIBE frame: Verifies the subscriber belongs to conversation participantIds
 *      before allowing topic subscription to /topic/conversation/{conversationId}.
 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final ConversationRepository conversationRepository;

    public WebSocketAuthInterceptor(JwtService jwtService,
                                    ConversationRepository conversationRepository) {
        this.jwtService = jwtService;
        this.conversationRepository = conversationRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null) {
            // --- 1. Authenticate STOMP CONNECT frame ---
            if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                String authHeader = accessor.getFirstNativeHeader("Authorization");

                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    String token = authHeader.substring(7);

                    if (jwtService.isTokenValid(token)) {
                        String email = jwtService.getEmailFromToken(token);
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        email,
                                        null,
                                        List.of(new SimpleGrantedAuthority("ROLE_FACULTY"))
                                );
                        accessor.setUser(authentication);
                    } else {
                        throw new IllegalArgumentException("Invalid or expired JWT token");
                    }
                } else {
                    throw new IllegalArgumentException(
                            "Missing or invalid Authorization header in STOMP CONNECT frame");
                }
            }
            // --- 2. Authorize STOMP SUBSCRIBE frame ---
            else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                String destination = accessor.getDestination();
                if (destination != null && destination.startsWith("/topic/conversation/")) {
                    String conversationId = destination.substring("/topic/conversation/".length());

                    Principal user = accessor.getUser();
                    if (user == null || user.getName() == null) {
                        throw new SecurityException("Unauthenticated subscription request");
                    }

                    String email = user.getName();
                    Conversation conversation = conversationRepository
                            .findByConversationId(conversationId)
                            .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));

                    if (!conversation.getParticipantIds().contains(email)) {
                        throw new SecurityException(
                                "Unauthorized subscription: You are not a participant in this conversation");
                    }
                }
            }
        }

        return message;
    }
}
