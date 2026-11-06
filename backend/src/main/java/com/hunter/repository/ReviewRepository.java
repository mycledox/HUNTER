package com.hunter.repository;

import com.hunter.model.Review;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, String> {

    @EntityGraph(attributePaths = {"owner"})
    List<Review> findAllByOwner_IdOrderByCreatedAtDesc(String ownerId);

    @EntityGraph(attributePaths = {"owner", "findings"})
    Optional<Review> findDetailedByIdAndOwner_Id(String id, String ownerId);

    Optional<Review> findByIdAndOwner_Id(String id, String ownerId);
}
