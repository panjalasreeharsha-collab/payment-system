package com.example.paymentsystem.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.paymentsystem.entity.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("select transaction from Transaction transaction "
            + "where lower(trim(transaction.senderUpiId)) = lower(trim(:upiId)) "
            + "or lower(trim(transaction.receiverUpiId)) = lower(trim(:upiId)) "
            + "order by transaction.timestamp desc")
    List<Transaction> findHistoryByUpiId(@Param("upiId") String upiId, Pageable pageable);
}