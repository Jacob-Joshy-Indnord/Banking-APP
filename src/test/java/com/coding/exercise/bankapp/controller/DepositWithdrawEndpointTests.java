package com.coding.exercise.bankapp.controller;

import static org.junit.Assert.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.coding.exercise.bankapp.domain.AmountDetails;
import com.coding.exercise.bankapp.model.Account;
import com.coding.exercise.bankapp.model.Transaction;
import com.coding.exercise.bankapp.repository.AccountRepository;
import com.coding.exercise.bankapp.repository.TransactionRepository;
import com.coding.exercise.bankapp.service.BankingServiceImpl;

/**
 * Acceptance tests for PUT /accounts/deposit/{accountNumber} (task-UA9t4NLFvYx)
 * and PUT /accounts/withdraw/{accountNumber} (task-EIO84ddLoSC).
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class DepositWithdrawEndpointTests {

	private static final Long ACCOUNT_NUMBER = 5000L;
	private static final Long UNKNOWN_ACCOUNT_NUMBER = 9999L;

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private AccountRepository accountRepository;
	@Autowired
	private TransactionRepository transactionRepository;
	@Autowired
	private BankingServiceImpl bankingService;

	@Before
	public void setUp() {
		transactionRepository.deleteAll();
		accountRepository.deleteAll();
		accountRepository.save(Account.builder()
				.accountNumber(ACCOUNT_NUMBER)
				.accountStatus("ACTIVE")
				.accountType("SAVINGS")
				.accountBalance(500.0)
				.createDateTime(new Date())
				.build());
	}

	// ---------- Deposit ----------

	@Test
	public void deposit_existingAccount_increasesBalanceAndRecordsOneCredit() throws Exception {
		putAmount("deposit", ACCOUNT_NUMBER, "{\"amount\": 100}")
				.andExpect(status().isOk())
				.andExpect(content().string("Success: Amount deposited to Account Number 5000"));

		assertEquals(600.0, balance(), 0.0001);
		List<Transaction> txs = transactions(ACCOUNT_NUMBER);
		assertEquals(1, txs.size());
		assertEquals("CREDIT", txs.get(0).getTxType());
		assertEquals(100.0, txs.get(0).getTxAmount(), 0.0001);
	}

	@Test
	public void deposit_unknownAccount_returns404AndRecordsNothing() throws Exception {
		putAmount("deposit", UNKNOWN_ACCOUNT_NUMBER, "{\"amount\": 100}")
				.andExpect(status().isNotFound())
				.andExpect(content().string("Account Number 9999 not found."));

		assertEquals(0, transactionRepository.count());
	}

	@Test
	public void deposit_nullZeroOrNegativeAmount_returns400AndBalanceUnchanged() throws Exception {
		for (String body : new String[] { "{}", "{\"amount\": null}", "{\"amount\": 0}", "{\"amount\": -5}" }) {
			putAmount("deposit", ACCOUNT_NUMBER, body)
					.andExpect(status().isBadRequest())
					.andExpect(content().string("Invalid amount."));
		}

		assertEquals(500.0, balance(), 0.0001);
		assertEquals(0, transactionRepository.count());
	}

	// ---------- Withdraw ----------

	@Test
	public void withdraw_sufficientFunds_decreasesBalanceAndRecordsOneDebit() throws Exception {
		putAmount("withdraw", ACCOUNT_NUMBER, "{\"amount\": 40}")
				.andExpect(status().isOk())
				.andExpect(content().string("Success: Amount withdrawn from Account Number 5000"));

		assertEquals(460.0, balance(), 0.0001);
		List<Transaction> txs = transactions(ACCOUNT_NUMBER);
		assertEquals(1, txs.size());
		assertEquals("DEBIT", txs.get(0).getTxType());
		assertEquals(40.0, txs.get(0).getTxAmount(), 0.0001);
	}

	@Test
	public void withdraw_entireBalance_isAllowed() throws Exception {
		putAmount("withdraw", ACCOUNT_NUMBER, "{\"amount\": 500}")
				.andExpect(status().isOk());

		assertEquals(0.0, balance(), 0.0001);
	}

	@Test
	public void withdraw_moreThanBalance_returns400AndBalanceUnchanged() throws Exception {
		putAmount("withdraw", ACCOUNT_NUMBER, "{\"amount\": 500.01}")
				.andExpect(status().isBadRequest())
				.andExpect(content().string("Insufficient Funds."));

		assertEquals(500.0, balance(), 0.0001);
		assertEquals(0, transactionRepository.count());
	}

	@Test
	public void withdraw_unknownAccount_returns404() throws Exception {
		putAmount("withdraw", UNKNOWN_ACCOUNT_NUMBER, "{\"amount\": 40}")
				.andExpect(status().isNotFound())
				.andExpect(content().string("Account Number 9999 not found."));

		assertEquals(0, transactionRepository.count());
	}

	@Test
	public void withdraw_nullZeroOrNegativeAmount_returns400AndBalanceUnchanged() throws Exception {
		for (String body : new String[] { "{}", "{\"amount\": null}", "{\"amount\": 0}", "{\"amount\": -5}" }) {
			putAmount("withdraw", ACCOUNT_NUMBER, body)
					.andExpect(status().isBadRequest())
					.andExpect(content().string("Invalid amount."));
		}

		assertEquals(500.0, balance(), 0.0001);
		assertEquals(0, transactionRepository.count());
	}

	@Test
	public void withdraw_concurrentRequestsExceedingBalance_atMostOneSucceeds() throws Exception {
		int threads = 8;
		// each withdrawal is 300 against a balance of 500: only one can fit
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Callable<ResponseEntity<Object>>> calls = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			calls.add(() -> {
				start.await();
				return bankingService.withdraw(new AmountDetails(300.0), ACCOUNT_NUMBER);
			});
		}
		List<Future<ResponseEntity<Object>>> futures = new ArrayList<>();
		for (Callable<ResponseEntity<Object>> call : calls) {
			futures.add(pool.submit(call));
		}
		start.countDown();

		int succeeded = 0;
		for (Future<ResponseEntity<Object>> f : futures) {
			ResponseEntity<Object> response = f.get();
			if (response.getStatusCode() == HttpStatus.OK) {
				succeeded++;
			} else {
				assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
				assertEquals("Insufficient Funds.", response.getBody());
			}
		}
		pool.shutdown();

		assertEquals(1, succeeded);
		assertEquals(200.0, balance(), 0.0001);
		assertEquals(1, transactions(ACCOUNT_NUMBER).size());
	}

	// ---------- helpers ----------

	private ResultActions putAmount(String operation, Long accountNumber, String body) throws Exception {
		return mockMvc.perform(put("/accounts/" + operation + "/" + accountNumber)
				.with(httpBasic("bankapp", "changeit"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private double balance() {
		return accountRepository.findByAccountNumber(ACCOUNT_NUMBER).get().getAccountBalance();
	}

	private List<Transaction> transactions(Long accountNumber) {
		return transactionRepository.findByAccountNumber(accountNumber).orElse(Collections.emptyList());
	}
}
