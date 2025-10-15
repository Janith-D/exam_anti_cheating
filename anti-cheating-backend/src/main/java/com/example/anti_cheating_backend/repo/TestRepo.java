package com.example.anti_cheating_backend.repo;

import com.example.anti_cheating_backend.entity.Test;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestRepo extends JpaRepository<Test,Long> {

}
