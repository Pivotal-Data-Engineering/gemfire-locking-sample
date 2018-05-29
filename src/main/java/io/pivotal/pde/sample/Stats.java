package io.pivotal.pde.sample;

/**
 * Stats is not safe for concurrent access
 * 
 * @author rmay
 */
public class Stats {
	private int transactions = 0;
	private int balance = 0;
	
	public void accumulate(Stats other){
		this.transactions += other.transactions;
		this.balance += other.balance;
	}
	
	public int getTransactions() {
		return transactions;
	}

	public int getBalance() {
		return balance;
	}
	
	public void incrementBalance(int bal){
		this.balance += bal;
		this.transactions += 1;
	}
	
}
