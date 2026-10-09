package com.example.paymentsystem.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.paymentsystem.entity.Transaction;
import com.example.paymentsystem.entity.TransactionStatus;
import com.example.paymentsystem.entity.User;
import com.example.paymentsystem.entity.Wallet;
import com.example.paymentsystem.repository.TransactionRepository;
import com.example.paymentsystem.repository.UserRepository;
import com.example.paymentsystem.repository.WalletRepository;

@Service
public class WalletService {

    private static final String SYSTEM_UPI_ID = "SYSTEM";
    private static final String UNKNOWN_UPI_ID = "UNKNOWN";

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final PasswordEncoder passwordEncoder;

    public WalletService(UserRepository userRepository, WalletRepository walletRepository,
            TransactionRepository transactionRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User registerUser(String name, String phoneNumber, String upiId, String password) {
        String cleanedName = requireText(name, "Name is required.");
        String cleanedPhone = requireText(phoneNumber, "Phone number is required.");
        String cleanedUpi = normalizeUpiId(upiId);
        String cleanedPassword = requireText(password, "Password is required.");

        if (cleanedPassword.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters.");
        }
        if (userRepository.existsByPhoneNumber(cleanedPhone)
                || userRepository.existsByUpiIdIgnoreCase(cleanedUpi)) {
            throw new IllegalArgumentException("That phone number or UPI ID is already registered.");
        }

        User user = userRepository.save(new User(cleanedName, cleanedPhone, cleanedUpi,
                passwordEncoder.encode(cleanedPassword)));
        return user;
    }

    @Transactional
    public Optional<User> findUser(Long userId) {
        return userRepository.findById(userId);
    }

    @Transactional
    public BigDecimal getBalance(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Sender account was not found."));
        return getOrCreateWallet(user).getBalance();
    }

    @Transactional
    public void addFunds(Long userId, BigDecimal amount) {
        requireValidAmount(amount);
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User account was not found."));
        Wallet wallet = getOrCreateWallet(user);

        wallet.setBalance(wallet.getBalance().add(amount));
        transactionRepository.save(new Transaction(SYSTEM_UPI_ID, wallet.getUser().getUpiId(), amount,
                TransactionStatus.SUCCESS));
    }

    @Transactional
    public TransferResult transferFunds(Long senderUserId, String receiverUpiId, BigDecimal amount) {
        User sender = senderUserId == null ? null : userRepository.findById(senderUserId).orElse(null);
        String senderUpiId = sender == null ? UNKNOWN_UPI_ID : normalizeUpiId(sender.getUpiId());
        String receiver = receiverUpiId == null || receiverUpiId.isBlank()
                ? UNKNOWN_UPI_ID
                : normalizeUpiId(receiverUpiId);

        if (sender == null) {
            return fail(senderUpiId, receiver, amount, "Sender account was not found.");
        }
        if (receiver.equals(UNKNOWN_UPI_ID)) {
            return fail(senderUpiId, receiver, amount, "Recipient UPI ID is required.");
        }
        if (!isValidAmount(amount)) {
            return fail(senderUpiId, receiver, amount, "Enter a positive amount with no more than two decimal places.");
        }
        User recipient = userRepository.findByUpiIdIgnoreCase(receiver).orElse(null);
        if (recipient == null) {
            return fail(senderUpiId, receiver, amount, "Recipient account was not found for UPI ID: " + receiver + ".");
        }
        if (sender.getId().equals(recipient.getId())) {
            return fail(senderUpiId, receiver, amount, "You cannot send money to your own UPI ID.");
        }

        List<User> lockOrder = new ArrayList<>(List.of(sender, recipient));
        lockOrder.sort((left, right) -> String.CASE_INSENSITIVE_ORDER.compare(left.getUpiId(), right.getUpiId()));
        Map<Long, Wallet> lockedWallets = new HashMap<>();
        for (User account : lockOrder) {
            lockedWallets.put(account.getId(), getOrCreateWallet(account));
        }

        Wallet senderWallet = lockedWallets.get(sender.getId());
        Wallet receiverWallet = lockedWallets.get(recipient.getId());
        if (senderWallet.getBalance().compareTo(amount) < 0) {
            return fail(senderUpiId, receiver, amount, "Your wallet does not have enough funds.");
        }

        senderWallet.setBalance(senderWallet.getBalance().subtract(amount));
        receiverWallet.setBalance(receiverWallet.getBalance().add(amount));
        transactionRepository.save(new Transaction(senderUpiId, receiver, amount, TransactionStatus.SUCCESS));
        return new TransferResult(true, "Payment sent successfully.");
    }

    private Wallet getOrCreateWallet(User user) {
        return walletRepository.findByUserIdForUpdate(user.getId())
                .orElseGet(() -> walletRepository.save(new Wallet(user)));
    }

    @Transactional(readOnly = true)
    public List<Transaction> getRecentTransactions(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User was not found."));
        return transactionRepository.findHistoryByUpiId(user.getUpiId(), PageRequest.of(0, 10));
    }

    @Transactional(readOnly = true)
    public List<Transaction> getTransactionHistory(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User was not found."));
        return transactionRepository.findHistoryByUpiId(user.getUpiId(), PageRequest.of(0, 100));
    }

    private TransferResult fail(String senderUpiId, String receiverUpiId, BigDecimal amount, String message) {
        BigDecimal recordedAmount = amount == null || amount.signum() < 0 ? BigDecimal.ZERO : amount;
        transactionRepository.save(new Transaction(senderUpiId, receiverUpiId, recordedAmount,
                TransactionStatus.FAILED));
        return new TransferResult(false, message);
    }

    private static void requireValidAmount(BigDecimal amount) {
        if (!isValidAmount(amount)) {
            throw new IllegalArgumentException("Enter a positive amount with no more than two decimal places.");
        }
    }

    private static boolean isValidAmount(BigDecimal amount) {
        return amount != null && amount.signum() > 0 && amount.stripTrailingZeros().scale() <= 2;
    }

    private static String normalizeUpiId(String upiId) {
        return requireText(upiId, "UPI ID is required.").toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    public record TransferResult(boolean successful, String message) {
    }
}