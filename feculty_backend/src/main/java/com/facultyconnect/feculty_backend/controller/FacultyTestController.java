package com.facultyconnect.feculty_backend.controller;

import com.facultyconnect.feculty_backend.model.Faculty;
import com.facultyconnect.feculty_backend.repository.FacultyRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class FacultyTestController {

    private final FacultyRepository facultyRepository;

    public FacultyTestController(FacultyRepository facultyRepository) {
        this.facultyRepository = facultyRepository;
    }

    @PostMapping("/api/faculty-test")
    public Faculty createTestFaculty() {
        Faculty faculty = new Faculty();
        faculty.setFacultyId("FAC001");
        faculty.setFullName("Test Faculty");
        faculty.setEmail("test.faculty@facultyconnect.com");
        faculty.setPassword("temporary-password");
        faculty.setDepartment("Computer Science");
        faculty.setDesignation("Assistant Professor");
        faculty.setSubjects(List.of("Java", "DBMS"));
        faculty.setProfileImage("");
        faculty.setBio("Temporary test faculty");
        faculty.setRole("FACULTY");
        return facultyRepository.save(faculty);
    }

    @GetMapping("/api/faculty-test")
    public List<Faculty> getAllTestFaculty() {
        return facultyRepository.findAll();
    }
}
