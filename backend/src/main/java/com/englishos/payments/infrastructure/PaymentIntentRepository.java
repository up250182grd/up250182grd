package com.englishos.payments.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import com.englishos.payments.domain.PaymentIntent;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, Long> {

    Optional<PaymentIntent> findByPublicId(String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentIntent p where p.publicId = :publicId")
    Optional<PaymentIntent> findByPublicIdForUpdate(@Param("publicId") String publicId);

    Optional<PaymentIntent> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    @Query("select p from PaymentIntent p where p.userId = :userId order by p.id desc")
    List<PaymentIntent> findRecentByUser(@Param("userId") Long userId, Pageable pageable);

    @Query("select p from PaymentIntent p order by p.id desc")
    List<PaymentIntent> findRecent(Pageable pageable);

    List<PaymentIntent> findByStatusInAndCreatedAtBetween(Collection<PaymentIntent.Status> statuses, Instant from,
            Instant to);

    List<PaymentIntent> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByIdAsc(Instant from, Instant to);
}
