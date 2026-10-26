package minibank.model;

public class PremiumAccount extends SavingsAccount implements Premium {
    private static final long serialVersionUID = 1L;

    public PremiumAccount(Customer owner, long openingBalance, long minimumBalance) {
        super(owner, openingBalance, minimumBalance);
    }

    @Override
    public double interestRate() {
        return 5.0;
    }

    @Override
    public String getAccountType() {
        return "Premium";
    }
}
