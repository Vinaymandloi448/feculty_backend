package com.facultyconnect.feculty_backend.controller;

import com.facultyconnect.feculty_backend.dto.CreateGroupRequest;
import com.facultyconnect.feculty_backend.dto.GroupResponse;
import com.facultyconnect.feculty_backend.service.GroupService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Group management controller.
 *
 * All endpoints require a valid JWT.
 * Faculty identity is always read from SecurityContext (JWT),
 * never from the request body.
 */
@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    /**
     * POST /api/groups
     *
     * Create a new group. Creator and initial member are set from JWT.
     * Returns 201 CREATED on success.
     */
    @PostMapping
    public ResponseEntity<?> createGroup(Authentication authentication,
                                         @RequestBody CreateGroupRequest request) {
        try {
            String email = authentication.getName();
            GroupResponse response = groupService.createGroup(email, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * GET /api/groups
     *
     * Get all groups where the authenticated faculty is a member.
     */
    @GetMapping
    public ResponseEntity<List<GroupResponse>> getMyGroups(Authentication authentication) {
        String email = authentication.getName();
        List<GroupResponse> groups = groupService.getMyGroups(email);
        return ResponseEntity.ok(groups);
    }

    /**
     * GET /api/groups/{groupId}
     *
     * Get a single group by groupId.
     * Only accessible if the authenticated faculty is a member.
     */
    @GetMapping("/{groupId}")
    public ResponseEntity<?> getGroupById(Authentication authentication,
                                          @PathVariable String groupId) {
        try {
            String email = authentication.getName();
            GroupResponse response = groupService.getGroupById(email, groupId);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * DELETE /api/groups/{groupId}
     *
     * Delete a group. Only the group creator can delete it.
     * Returns 204 NO CONTENT on success.
     */
    @DeleteMapping("/{groupId}")
    public ResponseEntity<?> deleteGroup(Authentication authentication,
                                         @PathVariable String groupId) {
        try {
            String email = authentication.getName();
            groupService.deleteGroup(email, groupId);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * POST /api/groups/{groupId}/members/{facultyId}
     *
     * Add a faculty member to a group.
     * Only the group creator can add members.
     * The facultyId is the institutional ID (e.g. FAC002), not the email.
     */
    @PostMapping("/{groupId}/members/{facultyId}")
    public ResponseEntity<?> addMember(Authentication authentication,
                                       @PathVariable String groupId,
                                       @PathVariable String facultyId) {
        try {
            String email = authentication.getName();
            GroupResponse response = groupService.addMember(email, groupId, facultyId);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * DELETE /api/groups/{groupId}/members/{facultyId}
     *
     * Remove a faculty member from a group.
     * Only the group creator can remove members.
     * The creator cannot remove themselves.
     */
    @DeleteMapping("/{groupId}/members/{facultyId}")
    public ResponseEntity<?> removeMember(Authentication authentication,
                                          @PathVariable String groupId,
                                          @PathVariable String facultyId) {
        try {
            String email = authentication.getName();
            GroupResponse response = groupService.removeMember(email, groupId, facultyId);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", ex.getMessage()));
        }
    }
}
