package com.coding.exercise.bankapp.repository;

import java.util.Optional;

import javax.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.coding.exercise.bankapp.model.Account;

@Repository
public interface AccountRepository extends CrudRepository<Account, String> {

	Optional<Account> findByAccountNumber(Long accountNumber);

	/**
	 * Load the account with a pessimistic write lock (SELECT ... FOR UPDATE) held
	 * until the surrounding transaction ends, so a balance check and the balance
	 * update happen in the same critical section.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from Account a where a.accountNumber = :accountNumber")
	Optional<Account> findByAccountNumberForUpdate(@Param("accountNumber") Long accountNumber);
}
