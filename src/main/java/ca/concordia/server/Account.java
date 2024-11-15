package ca.concordia.server;

public class Account {
    //represent a bank account with a balance and withdraw and deposit methods
    private int balance;
    private int id;

    public Account(int balance, int id){

        this.balance = balance;
        this.id = id;
    }

    public int getBalance(){
        return balance;
    }

    public void withdraw(int amount){
        balance -= amount;
    }

    public void deposit(int amount){
        balance += amount;
    }
}
