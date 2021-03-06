package br.com.gamemods.universalcoinsserver.datastore;

import br.com.gamemods.universalcoinsserver.UniversalCoinsServer;
import br.com.gamemods.universalcoinsserver.api.UniversalCoinsServerAPI;
import br.com.gamemods.universalcoinsserver.tile.TilePackager;
import br.com.gamemods.universalcoinsserver.tile.TileSignal;
import br.com.gamemods.universalcoinsserver.tile.TileSlots;
import br.com.gamemods.universalcoinsserver.tile.TileVendor;
import net.minecraft.item.ItemStack;

import java.util.UUID;

public final class Transaction
{
    private UUID id = UUID.randomUUID();
    private long time = System.currentTimeMillis();
    private Machine machine;
    private Operator operator;
    private ItemStack product;
    private ItemStack trade;
    private Operation operation;
    private boolean infiniteMachine;
    private int quantity;
    private int price;
    private int totalPrice;
    private CoinSource userCoinSource;
    private CoinSource ownerCoinSource;

    public Transaction(Machine machine, Operator operator, ItemStack product, ItemStack trade, Operation operation, boolean infiniteMachine, int quantity, int price, int totalPrice, CoinSource userCoinSource, CoinSource ownerCoinSource)
    {
        this.machine = machine;
        this.operator = operator;
        this.product = product;
        this.trade = trade;
        this.operation = operation;
        this.infiniteMachine = infiniteMachine;
        this.quantity = quantity;
        this.price = price;
        this.totalPrice = totalPrice;
        this.userCoinSource = userCoinSource;
        this.ownerCoinSource = ownerCoinSource;
    }

    public Transaction(TileVendor vendor, Operation operation, int quantity,
                        CoinSource userSource, CoinSource ownerSource, ItemStack product)
    {
        this.operation = operation;
        machine = vendor;
        operator = vendor.getOperator();
        infiniteMachine = vendor.infinite;
        this.quantity = quantity;
        price = vendor.price;
        totalPrice = price * quantity;
        userCoinSource = userSource;
        ownerCoinSource = ownerSource;
        trade = vendor.getStackInSlot(TileVendor.SLOT_TRADE);
        if(trade != null) trade = trade.copy();
        this.product = product.copy();
    }

    public Transaction(TileSignal signal, Operation operation, int time, Operator operator,
                       CoinSource userSource, CoinSource ownerSource, ItemStack product)
    {
        this.operation = operation;
        this.machine = signal;
        this.operator = operator;
        this.quantity = time;
        this.userCoinSource = userSource;
        this.ownerCoinSource = ownerSource;
        this.price = signal.fee;
        this.totalPrice = signal.fee;
        this.product = product;
    }

    public Transaction(TilePackager packager, Operation operation, Operator operator, int size,
                       CoinSource userSource, ItemStack product)
    {
        this.operator = operator;
        this.machine = packager;
        this.operation = operation;
        this.userCoinSource = userSource;
        this.quantity = size;
        this.price = packager.price[size];
        this.totalPrice = price;
        this.product = product;
    }

    public Transaction(TileSlots slots, Operation operation, Operator operator,
                       CoinSource userSource, ItemStack product)
    {
        this.operation = operation;
        this.machine = slots;
        this.operation = operation;
        this.quantity = 1;
        this.userCoinSource = userSource;
        this.price = slots.fee;
        this.totalPrice = slots.fee;
        this.product = product;
        this.operator = operator;
    }

    public Transaction(Machine machine, Operation operation, Operator operator,
                       CoinSource ownerCoinSource, CardCoinSource cardCoinSource,
                       ItemStack product)
    {
        if(operation != Operation.DEPOSIT_TO_ACCOUNT_FROM_MACHINE
            && operation != Operation.WITHDRAW_FROM_ACCOUNT_TO_MACHINE
            && operation != Operation.TRANSFER_ACCOUNT
            && operation != Operation.DEPOSIT_TO_ACCOUNT_BY_API
            && operation != Operation.WITHDRAW_FROM_ACCOUNT_BY_API)
            throw new IllegalArgumentException();

        this.operator = operator;
        this.operation = operation;
        this.machine = machine;
        this.quantity = 1;
        this.userCoinSource = cardCoinSource;
        this.ownerCoinSource = ownerCoinSource;
        this.price = cardCoinSource.getBalanceAfter() - cardCoinSource.getBalanceBefore();
        this.totalPrice = price;
        this.product = product;
    }

    public Transaction(PlayerOperator playerOperator, InventoryCoinSource inventoryCoinSource, CardCoinSource cardCoinSource, int amount)
    {
        operation = Operation.DEPOSIT_TO_ACCOUNT_FROM_CARD;
        operator = playerOperator;
        ownerCoinSource = cardCoinSource;
        userCoinSource = inventoryCoinSource;
        price = totalPrice = amount;
        quantity = 1;
        product = cardCoinSource.getCard();
    }

    public static abstract class CoinSource
    {
        public abstract int getBalanceBefore();
        public abstract int getBalanceAfter();
    }

    public static class CardCoinSource extends CoinSource
    {
        private ItemStack card;
        private AccountAddress accountAddress;
        private int balanceBefore;
        private int balanceAfter;

        public CardCoinSource(ItemStack card, AccountAddress accountAddress, int balanceBefore, int balanceAfter)
        {
            this.card = card;
            this.accountAddress = accountAddress;
            this.balanceBefore = balanceBefore;
            this.balanceAfter = balanceAfter;
        }

        public CardCoinSource(AccountAddress accountAddress, int increment) throws AccountNotFoundException, DataStoreException
        {
            if(accountAddress == null)
                throw new NullPointerException("accountAddress");
            this.accountAddress = accountAddress;
            this.balanceBefore = UniversalCoinsServer.cardDb.getAccountBalance(accountAddress.getNumber());
            this.balanceAfter = balanceBefore + increment;
        }

        public CardCoinSource(ItemStack card, int increment) throws NullPointerException, AccountNotFoundException, DataStoreException
        {
            if(card == null)
                throw new NullPointerException("card");
            this.card = card;
            accountAddress = UniversalCoinsServerAPI.getAddress(card);
            if(accountAddress == null)
                throw new NullPointerException("accountAddress");
            balanceBefore = UniversalCoinsServer.cardDb.getAccountBalance(accountAddress.getNumber());
            balanceAfter = balanceBefore + increment;
        }

        public AccountAddress getAccountAddress()
        {
            return accountAddress;
        }

        public ItemStack getCard()
        {
            return card;
        }

        @Override
        public int getBalanceAfter()
        {
            return balanceAfter;
        }

        @Override
        public int getBalanceBefore()
        {
            return balanceBefore;
        }

        @Override
        public String toString()
        {
            return "CardCoinSource{" +
                    "accountAddress='" + accountAddress + '\'' +
                    ", card=" + card +
                    ", balanceBefore=" + balanceBefore +
                    ", balanceAfter=" + balanceAfter +
                    '}';
        }
    }

    public static class MachineCoinSource extends CoinSource
    {
        private Machine machine;
        private int balanceBefore;
        private int balanceAfter;

        public MachineCoinSource(Machine machine, int balance, int increment)
        {
            this.machine = machine;
            this.balanceBefore = balance;
            this.balanceAfter = balance + increment;
        }

        @Override
        public int getBalanceAfter()
        {
            return balanceAfter;
        }

        @Override
        public int getBalanceBefore()
        {
            return balanceBefore;
        }

        public Machine getMachine()
        {
            return machine;
        }

        @Override
        public String toString()
        {
            return "MachineCoinSource{" +
                    "balanceAfter=" + balanceAfter +
                    ", machine=" + machine +
                    ", balanceBefore=" + balanceBefore +
                    '}';
        }
    }

    public static class InventoryCoinSource extends CoinSource
    {
        private Operator operator;
        private int balanceBefore;
        private int balanceAfter;

        public InventoryCoinSource(Operator operator, int balanceBefore, int increment)
        {
            this.operator = operator;
            this.balanceBefore = balanceBefore;
            this.balanceAfter = balanceBefore+increment;
        }

        public Operator getOperator()
        {
            return operator;
        }

        @Override
        public int getBalanceBefore()
        {
            return balanceBefore;
        }

        @Override
        public int getBalanceAfter()
        {
            return balanceAfter;
        }

        @Override
        public String toString()
        {
            return "InventoryCoinSource{" +
                    "operator=" + operator +
                    ", balanceBefore=" + balanceBefore +
                    ", balanceAfter=" + balanceAfter +
                    "} " + super.toString();
        }
    }

    public enum Operation
    {
        BUY_FROM_MACHINE,
        SELL_TO_MACHINE,
        DEPOSIT_TO_MACHINE,
        SLOTS_WIN_5_MATCH, SLOTS_WIN_4_MATCH, WITHDRAW_FROM_MACHINE,
        WITHDRAW_FROM_ACCOUNT_TO_MACHINE, TRANSFER_ACCOUNT, DEPOSIT_TO_ACCOUNT_FROM_CARD, DEPOSIT_TO_ACCOUNT_FROM_MACHINE,
        DEPOSIT_TO_ACCOUNT_BY_API, WITHDRAW_FROM_ACCOUNT_BY_API
    }

    public UUID getId()
    {
        return id;
    }

    public boolean isInfiniteMachine()
    {
        return infiniteMachine;
    }

    public Machine getMachine()
    {
        return machine;
    }

    public Operation getOperation()
    {
        return operation;
    }

    public Operator getOperator()
    {
        return operator;
    }

    public CoinSource getOwnerCoinSource()
    {
        return ownerCoinSource;
    }

    public int getPrice()
    {
        return price;
    }

    public ItemStack getProduct()
    {
        return product;
    }

    public int getQuantity()
    {
        return quantity;
    }

    public long getTime()
    {
        return time;
    }

    public int getTotalPrice()
    {
        return totalPrice;
    }

    public ItemStack getTrade()
    {
        return trade;
    }

    public CoinSource getUserCoinSource()
    {
        return userCoinSource;
    }

    @Override
    public String toString()
    {
        return "Transaction{" +
                "id=" + id +
                ", time=" + time +
                ", machine=" + machine +
                ", operator=" + operator +
                ", product=" + product +
                ", trade=" + trade +
                ", operation=" + operation +
                ", infiniteMachine=" + infiniteMachine +
                ", quantity=" + quantity +
                ", price=" + price +
                ", totalPrice=" + totalPrice +
                ", userCoinSource=" + userCoinSource +
                ", ownerCoinSource=" + ownerCoinSource +
                '}';
    }
}
