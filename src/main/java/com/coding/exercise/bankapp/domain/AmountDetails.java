package com.coding.exercise.bankapp.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request body for single-account deposit and withdrawal: {"amount": 100}
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class AmountDetails {

	private Double amount;
}
