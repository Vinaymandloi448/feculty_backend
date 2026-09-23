package com.facultyconnect.feculty_backend.repository;

import com.facultyconnect.feculty_backend.model.MongoTest;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MongoTestRepository extends MongoRepository<MongoTest, String> {
}
