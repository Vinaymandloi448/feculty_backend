package com.facultyconnect.feculty_backend.controller;

import com.facultyconnect.feculty_backend.model.MongoTest;
import com.facultyconnect.feculty_backend.repository.MongoTestRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MongoTestController {

    private final MongoTestRepository mongoTestRepository;

    public MongoTestController(MongoTestRepository mongoTestRepository) {
        this.mongoTestRepository = mongoTestRepository;
    }

    @GetMapping("/api/mongodb-test")
    public String testMongoConnection() {
        MongoTest test = new MongoTest("MongoDB connection is working!");
        mongoTestRepository.save(test);
        return test.getMessage();
    }
}
