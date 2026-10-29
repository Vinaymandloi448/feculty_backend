package com.facultyconnect.feculty_backend.repository;

import com.facultyconnect.feculty_backend.model.Group;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupRepository extends MongoRepository<Group, String> {

    Optional<Group> findByGroupId(String groupId);

    List<Group> findByMemberIdsContaining(String email);

    List<Group> findByCreatedBy(String email);
}
