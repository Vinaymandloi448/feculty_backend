package com.facultyconnect.feculty_backend.service;

import com.facultyconnect.feculty_backend.dto.CreateGroupRequest;
import com.facultyconnect.feculty_backend.dto.GroupResponse;
import com.facultyconnect.feculty_backend.model.Faculty;
import com.facultyconnect.feculty_backend.model.Group;
import com.facultyconnect.feculty_backend.repository.FacultyRepository;
import com.facultyconnect.feculty_backend.repository.GroupRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final FacultyRepository facultyRepository;

    public GroupService(GroupRepository groupRepository,
                        FacultyRepository facultyRepository) {
        this.groupRepository = groupRepository;
        this.facultyRepository = facultyRepository;
    }

    /**
     * Create a new group.
     *
     * - Creator is determined from JWT, NOT from request body.
     * - Creator is automatically added to memberIds.
     * - groupId is auto-generated and unique.
     *
     * @param email   the authenticated faculty email (from JWT)
     * @param request the group creation DTO
     * @return GroupResponse
     * @throws IllegalArgumentException if validation fails
     */
    public GroupResponse createGroup(String email, CreateGroupRequest request) {
        // --- Validate required fields ---
        validateRequired(request.getName(), "name", 100);
        validateMaxLength(request.getDescription(), "description", 500);
        validateMaxLength(request.getDepartment(), "department", 100);
        validateMaxLength(request.getSubject(), "subject", 100);

        // --- Build group ---
        Group group = new Group();
        group.setGroupId(generateGroupId());
        group.setName(request.getName());
        group.setDescription(request.getDescription());
        group.setDepartment(request.getDepartment());
        group.setSubject(request.getSubject());
        group.setCreatedBy(email);
        group.setMemberIds(new ArrayList<>(List.of(email)));

        // --- Save to MongoDB ---
        Group saved = groupRepository.save(group);
        return toResponse(saved);
    }

    /**
     * Get all groups where the logged-in faculty is a member.
     *
     * @param email the authenticated faculty email (from JWT)
     * @return list of GroupResponse
     */
    public List<GroupResponse> getMyGroups(String email) {
        List<Group> groups = groupRepository.findByMemberIdsContaining(email);
        return groups.stream().map(this::toResponse).toList();
    }

    /**
     * Get a single group by groupId.
     * Only accessible if the logged-in faculty is a member.
     *
     * @param email   the authenticated faculty email (from JWT)
     * @param groupId the group's unique identifier
     * @return GroupResponse
     * @throws NoSuchElementException if group not found
     * @throws SecurityException      if faculty is not a member
     */
    public GroupResponse getGroupById(String email, String groupId) {
        Group group = groupRepository.findByGroupId(groupId)
                .orElseThrow(() -> new NoSuchElementException("Group not found"));

        if (!group.getMemberIds().contains(email)) {
            throw new SecurityException("You are not a member of this group");
        }

        return toResponse(group);
    }

    /**
     * Delete a group.
     * Only the creator can delete the group.
     *
     * @param email   the authenticated faculty email (from JWT)
     * @param groupId the group's unique identifier
     * @throws NoSuchElementException if group not found
     * @throws SecurityException      if faculty is not the creator
     */
    public void deleteGroup(String email, String groupId) {
        Group group = groupRepository.findByGroupId(groupId)
                .orElseThrow(() -> new NoSuchElementException("Group not found"));

        if (!group.getCreatedBy().equals(email)) {
            throw new SecurityException("Only the group creator can delete this group");
        }

        groupRepository.delete(group);
    }

    /**
     * Add a faculty member to a group.
     *
     * - Only the group creator can add members.
     * - The target faculty is looked up by institutional facultyId (e.g. FAC002).
     * - The faculty's email is stored in memberIds.
     * - Duplicate members are rejected.
     *
     * @param requesterEmail the authenticated creator's email (from JWT)
     * @param groupId        the group's unique identifier
     * @param facultyId      the institutional faculty ID to add
     * @return updated GroupResponse
     */
    public GroupResponse addMember(String requesterEmail, String groupId, String facultyId) {
        // --- Find the group ---
        Group group = groupRepository.findByGroupId(groupId)
                .orElseThrow(() -> new NoSuchElementException("Group not found"));

        // --- Only the creator can add members ---
        if (!group.getCreatedBy().equals(requesterEmail)) {
            throw new SecurityException("Only the group creator can add members");
        }

        // --- Look up the target faculty by institutional facultyId ---
        Faculty faculty = facultyRepository.findByFacultyId(facultyId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Faculty not found with ID: " + facultyId));

        String targetEmail = faculty.getEmail();

        // --- Check for duplicate membership ---
        if (group.getMemberIds().contains(targetEmail)) {
            throw new IllegalStateException(
                    "Faculty " + facultyId + " is already a member of this group");
        }

        // --- Add and save ---
        group.getMemberIds().add(targetEmail);
        Group saved = groupRepository.save(group);
        return toResponse(saved);
    }

    /**
     * Remove a faculty member from a group.
     *
     * - Only the group creator can remove members.
     * - The creator cannot remove themselves.
     * - The target faculty is looked up by institutional facultyId.
     *
     * @param requesterEmail the authenticated creator's email (from JWT)
     * @param groupId        the group's unique identifier
     * @param facultyId      the institutional faculty ID to remove
     * @return updated GroupResponse
     */
    public GroupResponse removeMember(String requesterEmail, String groupId, String facultyId) {
        // --- Find the group ---
        Group group = groupRepository.findByGroupId(groupId)
                .orElseThrow(() -> new NoSuchElementException("Group not found"));

        // --- Only the creator can remove members ---
        if (!group.getCreatedBy().equals(requesterEmail)) {
            throw new SecurityException("Only the group creator can remove members");
        }

        // --- Look up the target faculty by institutional facultyId ---
        Faculty faculty = facultyRepository.findByFacultyId(facultyId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Faculty not found with ID: " + facultyId));

        String targetEmail = faculty.getEmail();

        // --- Creator cannot remove themselves ---
        if (group.getCreatedBy().equals(targetEmail)) {
            throw new SecurityException("The group creator cannot be removed from the group");
        }

        // --- Check that the faculty is actually a member ---
        if (!group.getMemberIds().contains(targetEmail)) {
            throw new IllegalStateException(
                    "Faculty " + facultyId + " is not a member of this group");
        }

        // --- Remove and save ---
        group.getMemberIds().remove(targetEmail);
        Group saved = groupRepository.save(group);
        return toResponse(saved);
    }

    // --- Helper: generate a unique, human-readable group ID ---
    private String generateGroupId() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return "GRP-" + uuid.substring(0, 8).toUpperCase();
    }

    // --- Helper: convert Group entity to GroupResponse ---
    private GroupResponse toResponse(Group group) {
        GroupResponse response = new GroupResponse();
        response.setId(group.getId());
        response.setGroupId(group.getGroupId());
        response.setName(group.getName());
        response.setDescription(group.getDescription());
        response.setDepartment(group.getDepartment());
        response.setSubject(group.getSubject());
        response.setCreatedBy(group.getCreatedBy());
        response.setMemberIds(group.getMemberIds());
        response.setCreatedAt(group.getCreatedAt());
        response.setUpdatedAt(group.getUpdatedAt());
        return response;
    }

    // --- Helper: validate required + max length ---
    private void validateRequired(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + " must not exceed " + maxLength + " characters");
        }
    }

    // --- Helper: validate optional field max length ---
    private void validateMaxLength(String value, String fieldName, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + " must not exceed " + maxLength + " characters");
        }
    }
}
