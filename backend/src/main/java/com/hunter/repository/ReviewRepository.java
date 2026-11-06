package com.hunter.repository;

import com.hunter.model.Review;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, String> {

    @EntityGraph(attributePaths = {"owner"})
    List<Review> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"owner", "findings"})
    Optional<Review> findDetailedById(String id);
}
