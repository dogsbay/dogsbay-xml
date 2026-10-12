/*
 * Copyright (C) 2002-2026 DogsBay Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.xagent.core;

/**
 * Accumulated token usage and estimated cost for a session.
 * Thread-safe via synchronization in Agent.
 */
public class UsageStats {

	private long inputTokens;
	private long outputTokens;
	private long totalTokens;
	private int llmCalls;

	// Cost per million tokens (USD). Defaults are for GPT-4-class models.
	private double inputCostPerMillion = 2.50;
	private double outputCostPerMillion = 10.00;

	public synchronized void addUsage(Integer input, Integer output, Integer total) {
		if (input != null) inputTokens += input;
		if (output != null) outputTokens += output;
		if (total != null) {
			totalTokens += total;
		} else {
			totalTokens += (input != null ? input : 0) + (output != null ? output : 0);
		}
		llmCalls++;
	}

	public synchronized void setCostRates(double inputPerMillion, double outputPerMillion) {
		this.inputCostPerMillion = inputPerMillion;
		this.outputCostPerMillion = outputPerMillion;
	}

	public synchronized long inputTokens() { return inputTokens; }
	public synchronized long outputTokens() { return outputTokens; }
	public synchronized long totalTokens() { return totalTokens; }
	public synchronized int llmCalls() { return llmCalls; }

	/**
	 * Estimated cost in USD based on configured rates.
	 */
	public synchronized double estimatedCostUsd() {
		return (inputTokens * inputCostPerMillion / 1_000_000.0)
			+ (outputTokens * outputCostPerMillion / 1_000_000.0);
	}

	/**
	 * Format cost as a human-readable string.
	 */
	public synchronized String formatCost() {
		double cost = estimatedCostUsd();
		if (cost < 0.01) {
			return String.format("$%.4f", cost);
		}
		return String.format("$%.2f", cost);
	}

	/**
	 * Format token count as compact string (e.g. "12.3k", "1.2M").
	 */
	public static String formatTokens(long tokens) {
		if (tokens < 1000) return String.valueOf(tokens);
		if (tokens < 1_000_000) return String.format("%.1fk", tokens / 1000.0);
		return String.format("%.1fM", tokens / 1_000_000.0);
	}

	/**
	 * Set cost rates based on known provider/model combinations.
	 */
	public void configureForModel(String provider, String model) {
		switch (provider) {
			// subscription billing -- no marginal per-token cost
			case "openai-codex" -> setCostRates(0, 0);
			case "openai" -> {
				if (model.contains("gpt-5")) {
					setCostRates(2.50, 10.00);
				} else if (model.contains("gpt-4")) {
					setCostRates(2.50, 10.00);
				} else {
					setCostRates(0.50, 1.50);
				}
			}
			case "anthropic" -> {
				if (model.contains("opus")) {
					setCostRates(15.00, 75.00);
				} else if (model.contains("sonnet")) {
					setCostRates(3.00, 15.00);
				} else if (model.contains("haiku")) {
					setCostRates(0.80, 4.00);
				} else {
					setCostRates(3.00, 15.00);
				}
			}
			case "gemini" -> {
				if (model.contains("flash")) {
					setCostRates(0.075, 0.30);
				} else if (model.contains("pro")) {
					setCostRates(1.25, 5.00);
				} else {
					setCostRates(0.075, 0.30);
				}
			}
			case "ollama" -> setCostRates(0.0, 0.0);
			default -> setCostRates(2.50, 10.00);
		}
	}
}
