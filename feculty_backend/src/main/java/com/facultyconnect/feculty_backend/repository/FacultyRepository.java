package com.facultyconnect.feculty_backend.repository;

import com.facultyconnect.feculty_backend.model.Faculty;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FacultyRepository extends MongoRepository<Faculty, String> {

    Optional<Faculty> findByEmail(String email);

    Optional<Faculty> findByFacultyId(String facultyId);
}
